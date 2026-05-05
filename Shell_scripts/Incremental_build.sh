#!/bin/bash
set -euo pipefail

###############################################################################
# 1) INPUT ARGUMENTS
###############################################################################
BUILD_ROOT="$1"
PROJECT_NAME="$2"
VERSION="$3"
CHECKOUT_SUBDIR="$4"
NFS_SHARE="$5"
MODULE_LIST="$6"
MAVEN_REPO_LOCAL="${7:-/u01/jileM2/.m2/repository}"
MVN_GOAL="${8:-install}"

###############################################################################
# 2) RUNTIME CONFIGURATION
###############################################################################
MVN_CMD="${MVN_CMD:-mvn}"
MVN_ARGS="${MVN_ARGS:-}"
SKIP_TESTS="${SKIP_TESTS:-true}"

REPO_ROOT="${NFS_SHARE}/${PROJECT_NAME}/${VERSION}/${CHECKOUT_SUBDIR}"
INCR_DIR="${NFS_SHARE}/${PROJECT_NAME}/incremental"

CHANGED_FILE_LIST="${INCR_DIR}/changed_files.txt"
SOURCE_CODE_FILE="${INCR_DIR}/incr_source_code.txt"
SOURCE_BIN_FILE="${INCR_DIR}/incr_source_binaries.txt"
MODULES_FILE="${INCR_DIR}/impacted_modules.txt"
BUILT_JAR_FILE="${INCR_DIR}/built_jar_paths.txt"
CHANGED_DEP_JAR_FILE="${INCR_DIR}/changed_dependency_jars.txt"
PARENT_BUILD_LIST="${INCR_DIR}/parent_build_poms.txt"

###############################################################################
# 3) ENVIRONMENT SETUP
###############################################################################
export M2_HOME=/usr/bin/apache-maven-3.6.3
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-17.0.18.0.8-1.el8.x86_64
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"

###############################################################################
# 4) PERMISSION HELPERS
###############################################################################
umask 0002

fix_path_permissions() {
    local path="$1"
    sudo mkdir -p "$path" || true
    sudo chown -R "$(whoami):$(id -gn)" "$path" || true
    sudo chmod -R u+rwX,g+rwX "$path" || true
}

prepare_module_build_area() {
    local module_dir="$1"
    fix_path_permissions "$module_dir"
    mkdir -p "$module_dir/target/classes" "$module_dir/target/test-classes" || true
    chmod -R u+rwX,g+rwX "$module_dir/target" || true
}

###############################################################################
# 5) MAVEN HELPERS
###############################################################################
mvn_run() {
    local skipflag=""
    [[ "${SKIP_TESTS}" == "true" ]] && skipflag="-DskipTests"
    # shellcheck disable=SC2086
    ${MVN_CMD} ${MVN_ARGS} -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" ${skipflag} "$@"
}

collect_classpath() {
    local pom_file="$1"
    local out_file="$2"

    : > "$out_file"

    if [[ -f "$pom_file" ]]; then
        mvn_run -q -f "$pom_file" \
            org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
            -Dmdep.outputFile="$out_file.raw" \
            -Dmdep.includeScope=compile || true

        if [[ -s "$out_file.raw" ]]; then
            tr ':' '\n' < "$out_file.raw" | sed '/^[[:space:]]*$/d' | sort -u > "$out_file"
        fi

        rm -f "$out_file.raw"
    fi
}

###############################################################################
# 6) POM / JAR CHANGE HELPERS
###############################################################################
is_pom_file() {
    local file="$1"
    [[ "$file" =~ (^|/)(pom\.xml|pom-on-prem\.xml|pom-saas\.xml|pom-redis\.xml)$ ]]
}

resolve_parent_build_pom() {
    local changed_pom_rel="$1"

    local changed_pom_abs="${REPO_ROOT}/${changed_pom_rel}"
    local top_project
    local project_root
    local current

    top_project="$(echo "$changed_pom_rel" | cut -d'/' -f1)"
    project_root="${REPO_ROOT}/${top_project}"

    [[ -f "$changed_pom_abs" ]] || return 1

    current="$(dirname "$changed_pom_abs")"
    current="$(dirname "$current")"

    while [[ "$current" == "${project_root}"* ]] && [[ "$current" != "/" ]]; do
        if [[ -f "$current/pom.xml" ]]; then
            if grep -q "<modules>" "$current/pom.xml" 2>/dev/null || \
               grep -q "<packaging>[[:space:]]*pom[[:space:]]*</packaging>" "$current/pom.xml" 2>/dev/null; then
                echo "$current/pom.xml"
                return 0
            fi
        fi

        [[ "$current" == "$project_root" ]] && break
        current="$(dirname "$current")"
    done

    if [[ -f "${project_root}/pom.xml" ]]; then
        echo "${project_root}/pom.xml"
        return 0
    fi

    echo "$changed_pom_abs"
    return 0
}

prepare_previous_pom_from_git() {
    local current_pom_abs="$1"
    local output_prev_pom="$2"

    local module_dir
    local git_root
    local rel_path

    module_dir="$(dirname "$current_pom_abs")"

    git_root="$(git -C "$module_dir" rev-parse --show-toplevel 2>/dev/null || true)"
    [[ -n "$git_root" ]] || return 1

    rel_path="$(realpath --relative-to="$git_root" "$current_pom_abs" 2>/dev/null || true)"
    [[ -n "$rel_path" ]] || return 1

    git -C "$git_root" show "HEAD~1:${rel_path}" > "$output_prev_pom" 2>/dev/null
}

jar_identity_key() {
    local jar_path="$1"
    echo "${jar_path%/*/*}"
}

append_changed_jars() {
    local label="$1"
    local before_file="$2"
    local after_file="$3"
    local report_file="$4"

    local tmp_diff
    local newjar
    local oldjar
    local newkey
    local oldkey

    tmp_diff="$(mktemp)"

    while IFS= read -r newjar; do
        [[ -z "$newjar" ]] && continue

        newkey="$(jar_identity_key "$newjar")"

        oldjar="$(
            while IFS= read -r line; do
                [[ -z "$line" ]] && continue
                [[ "$(jar_identity_key "$line")" == "$newkey" ]] && { echo "$line"; break; }
            done < "$before_file"
        )"

        if [[ -n "$oldjar" && "$oldjar" != "$newjar" ]]; then
            echo "$oldjar -> $newjar" >> "$tmp_diff"
        elif [[ -z "$oldjar" ]]; then
            echo "<not present> -> $newjar" >> "$tmp_diff"
        fi
    done < "$after_file"

    while IFS= read -r oldjar; do
        [[ -z "$oldjar" ]] && continue

        oldkey="$(jar_identity_key "$oldjar")"

        newjar="$(
            while IFS= read -r line; do
                [[ -z "$line" ]] && continue
                [[ "$(jar_identity_key "$line")" == "$oldkey" ]] && { echo "$line"; break; }
            done < "$after_file"
        )"

        if [[ -z "$newjar" ]]; then
            echo "$oldjar -> <removed>" >> "$tmp_diff"
        fi
    done < "$before_file"

    if [[ -s "$tmp_diff" ]]; then
        {
            echo "### POM: $label"
            sort -u "$tmp_diff"
            echo ""
        } >> "$report_file"
    fi

    rm -f "$tmp_diff"
}

###############################################################################
# 7) PREPARE WORKING FILES
###############################################################################
fix_path_permissions "$INCR_DIR"

touch "$SOURCE_CODE_FILE" \
      "$SOURCE_BIN_FILE" \
      "$MODULES_FILE" \
      "$BUILT_JAR_FILE" \
      "$CHANGED_DEP_JAR_FILE" \
      "$PARENT_BUILD_LIST"

chmod 664 "$SOURCE_CODE_FILE" \
          "$SOURCE_BIN_FILE" \
          "$MODULES_FILE" \
          "$BUILT_JAR_FILE" \
          "$CHANGED_DEP_JAR_FILE" \
          "$PARENT_BUILD_LIST" || true

: > "$SOURCE_CODE_FILE"
: > "$SOURCE_BIN_FILE"
: > "$MODULES_FILE"
: > "$BUILT_JAR_FILE"
: > "$CHANGED_DEP_JAR_FILE"
: > "$PARENT_BUILD_LIST"

###############################################################################
# 8) HEADER / VALIDATION
###############################################################################
echo ""
echo "============================================================"
echo "          INCREMENTAL BUILD SCRIPT STARTED"
echo "============================================================"
echo "BUILD_ROOT        = ${BUILD_ROOT}"
echo "PROJECT_NAME      = ${PROJECT_NAME}"
echo "VERSION           = ${VERSION}"
echo "CHECKOUT_SUBDIR   = ${CHECKOUT_SUBDIR}"
echo "NFS_SHARE         = ${NFS_SHARE}"
echo "REPO_ROOT         = ${REPO_ROOT}"
echo "INCREMENTAL DIR   = ${INCR_DIR}"
echo "CHANGED FILE LIST = ${CHANGED_FILE_LIST}"
echo "============================================================"
echo ""

[[ -d "$REPO_ROOT" ]] || { echo "ERROR: Repo root not found: $REPO_ROOT"; exit 1; }
[[ -f "$CHANGED_FILE_LIST" ]] || { echo "ERROR: changed_files.txt missing: $CHANGED_FILE_LIST"; exit 1; }
[[ -s "$CHANGED_FILE_LIST" ]] || { echo "No changed files. Nothing to build."; exit 0; }

###############################################################################
# 9) COPY CHANGED FILES TO SOURCE REPORT
###############################################################################
cp "$CHANGED_FILE_LIST" "$SOURCE_CODE_FILE" || true

echo "===== CHANGED FILES ====="
cat "$CHANGED_FILE_LIST" || true
echo ""

###############################################################################
# 10) POM CHANGE FLOW
###############################################################################
mapfile -t CHANGED_POMS < <(
    grep -E '(^|/)(pom\.xml|pom-on-prem\.xml|pom-saas\.xml|pom-redis\.xml)$' "$CHANGED_FILE_LIST" | sort -u || true
)

if [[ ${#CHANGED_POMS[@]} -gt 0 ]]; then
    echo "============================================================"
    echo "     POM CHANGE DETECTED -> FULL PARENT BUILD"
    echo "============================================================"
    echo ""

    for changed_pom in "${CHANGED_POMS[@]}"; do
        echo "Changed POM file detected: $changed_pom"

        changed_pom_abs="${REPO_ROOT}/${changed_pom}"
        [[ -f "$changed_pom_abs" ]] || {
            echo "ERROR: Changed POM not found: $changed_pom_abs"
            exit 1
        }

        parent_pom="$(resolve_parent_build_pom "$changed_pom" || true)"
        [[ -n "${parent_pom:-}" && -f "$parent_pom" ]] || {
            echo "ERROR: Could not resolve parent build POM for changed file: $changed_pom"
            exit 1
        }

        echo "$parent_pom" >> "$PARENT_BUILD_LIST"

        pom_safe_name="${changed_pom//\//_}"
        prev_cp="${INCR_DIR}/${pom_safe_name}_prev_cp.txt"
        before_cp="${INCR_DIR}/${pom_safe_name}_before_cp.txt"
        prev_pom_tmp="${INCR_DIR}/${pom_safe_name}_prev_pom.xml"

        if prepare_previous_pom_from_git "$changed_pom_abs" "$prev_pom_tmp"; then
            echo "Using previous Git version of child pom for comparison: $changed_pom"
            collect_classpath "$prev_pom_tmp" "$before_cp"
        elif [[ -f "$prev_cp" && -s "$prev_cp" ]]; then
            echo "Git previous pom not found. Using previous baseline classpath: $prev_cp"
            cp "$prev_cp" "$before_cp"
        else
            echo "No previous Git pom or baseline found for $changed_pom. Using current classpath as initial baseline."
            collect_classpath "$changed_pom_abs" "$before_cp"
        fi
    done

    sort -u "$PARENT_BUILD_LIST" -o "$PARENT_BUILD_LIST"

    echo ""
    echo "===== RESOLVED PARENT/AGGREGATOR POMS TO BUILD ====="
    cat "$PARENT_BUILD_LIST"
    echo ""

    while IFS= read -r parent_pom; do
        [[ -z "$parent_pom" ]] && continue

        parent_dir="$(dirname "$parent_pom")"

        echo "------------------------------------------------------------"
        echo "FULL BUILD USING PARENT POM: $parent_pom"
        echo "PARENT DIR                : $parent_dir"
        echo "------------------------------------------------------------"

        prepare_module_build_area "$parent_dir"

        mvn_run -f "$parent_pom" clean install || {
            echo "ERROR: Full parent build failed for: $parent_pom"
            exit 1
        }

        find "$parent_dir/target" -maxdepth 1 -type f \( -name "*.jar" -o -name "*.war" \) >> "$BUILT_JAR_FILE" 2>/dev/null || true
    done < "$PARENT_BUILD_LIST"

    : > "$CHANGED_DEP_JAR_FILE"

    for changed_pom in "${CHANGED_POMS[@]}"; do
        changed_pom_abs="${REPO_ROOT}/${changed_pom}"
        pom_safe_name="${changed_pom//\//_}"

        before_cp="${INCR_DIR}/${pom_safe_name}_before_cp.txt"
        after_cp="${INCR_DIR}/${pom_safe_name}_after_cp.txt"
        prev_cp="${INCR_DIR}/${pom_safe_name}_prev_cp.txt"
        prev_pom_tmp="${INCR_DIR}/${pom_safe_name}_prev_pom.xml"

        collect_classpath "$changed_pom_abs" "$after_cp"
        append_changed_jars "$changed_pom_abs" "$before_cp" "$after_cp" "$CHANGED_DEP_JAR_FILE"

        cp -f "$after_cp" "$prev_cp" || true
        rm -f "$prev_pom_tmp" || true
    done

    sort -u "$BUILT_JAR_FILE" -o "$BUILT_JAR_FILE" || true

    echo ""
    echo "===== CHANGED DEPENDENCY JAR PATHS ====="
    cat "$CHANGED_DEP_JAR_FILE" || true
    echo ""

    echo "============================================================"
    echo "  FULL PARENT BUILD COMPLETED SUCCESSFULLY"
    echo "  Incremental build skipped because POM changed"
    echo "============================================================"
    echo ""

    echo "===== BUILT JAR / WAR PATHS ====="
    cat "$BUILT_JAR_FILE" || true
    echo ""

    exit 0
fi

###############################################################################
# 11) SOURCE / JAVA CHANGE FLOW
###############################################################################
echo "===== MODULES (FROM JENKINS) ====="
echo "$MODULE_LIST"
echo ""

normalize_repo_relative_path() {
    local p="$1"

    # Remove leading/trailing spaces
    p="$(echo "$p" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

    # Remove leading ./ if present
    p="${p#./}"

    # Remove async_checkout prefix if Jenkins sends it
    p="${p#async_checkout/}"

    # Remove checkout folder prefix, e.g. jiledevplus/
    p="${p#${CHECKOUT_SUBDIR}/}"

    # Remove repository absolute path if present
    p="${p#${REPO_ROOT}/}"

    # Remove NFS/version/checkout absolute prefix if present
    p="${p#${NFS_SHARE}/${PROJECT_NAME}/${VERSION}/${CHECKOUT_SUBDIR}/}"

    # Remove BUILD_ROOT/version/checkout absolute prefix if present
    p="${p#${BUILD_ROOT}/${PROJECT_NAME}/${VERSION}/${CHECKOUT_SUBDIR}/}"

    echo "$p"
}

echo "Normalizing changed file list..."

NORMALIZED_CHANGED_FILE_LIST="${INCR_DIR}/changed_files_normalized.txt"
: > "$NORMALIZED_CHANGED_FILE_LIST"

while IFS= read -r changed_file; do
    [[ -z "$changed_file" ]] && continue
    normalize_repo_relative_path "$changed_file" >> "$NORMALIZED_CHANGED_FILE_LIST"
done < "$CHANGED_FILE_LIST"

sort -u "$NORMALIZED_CHANGED_FILE_LIST" -o "$NORMALIZED_CHANGED_FILE_LIST"

echo ""
echo "===== NORMALIZED CHANGED FILES ====="
cat "$NORMALIZED_CHANGED_FILE_LIST" || true
echo ""

echo "Detecting impacted modules..."

while IFS= read -r module; do
    [[ -z "$module" ]] && continue

    CLEAN_MODULE="$(normalize_repo_relative_path "$module")"

    [[ -z "$CLEAN_MODULE" ]] && continue

    if grep -q "^${CLEAN_MODULE}/" "$NORMALIZED_CHANGED_FILE_LIST"; then
        echo "$CLEAN_MODULE" >> "$MODULES_FILE"
        echo "IMPACT DETECTED -> $CLEAN_MODULE"
    fi
done <<< "$MODULE_LIST"

sort -u "$MODULES_FILE" -o "$MODULES_FILE"

echo ""
echo "===== IMPACTED MODULES ====="
cat "$MODULES_FILE" || true
echo ""

[[ -s "$MODULES_FILE" ]] || { echo "No impacted modules found. Exiting."; exit 0; }
###############################################################################
# 12) CAPTURE DEPENDENCY CLASSPATH FOR IMPACTED MODULES
###############################################################################
while IFS= read -r module; do
    [[ -z "$module" ]] && continue

    MODULE_PATH="${REPO_ROOT}/${module}"
    [[ -d "$MODULE_PATH" ]] || continue

    BUILD_POM="${MODULE_PATH}/pom.xml"
    [[ -f "$BUILD_POM" ]] || continue

    TMP_SRC_BIN_CP="${INCR_DIR}/${module//\//_}_srcbin_cp.txt"
    : > "$TMP_SRC_BIN_CP"

    mvn_run -q -f "$BUILD_POM" \
        org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
        -Dmdep.outputFile="$TMP_SRC_BIN_CP" \
        -Dmdep.includeScope=compile || true

    if [[ -s "$TMP_SRC_BIN_CP" ]]; then
        tr ':' '\n' < "$TMP_SRC_BIN_CP" | sed '/^[[:space:]]*$/d' >> "$SOURCE_BIN_FILE"
    fi
done < "$MODULES_FILE"

sort -u "$SOURCE_BIN_FILE" -o "$SOURCE_BIN_FILE" || true

echo "===== SOURCE CODE ====="
cat "$SOURCE_CODE_FILE" || true
echo ""

echo "===== SOURCE BINARIES ====="
cat "$SOURCE_BIN_FILE" || true
echo ""

###############################################################################
# 13) BUILD IMPACTED MODULES
###############################################################################
while IFS= read -r module; do
    [[ -z "$module" ]] && continue

    MODULE_PATH="${REPO_ROOT}/${module}"

    echo ""
    echo "------------------------------------------------------------"
    echo "BUILDING MODULE: $module"
    echo "MODULE PATH    : $MODULE_PATH"
    echo "------------------------------------------------------------"

    [[ -d "$MODULE_PATH" ]] || { echo "WARN: No dir: $MODULE_PATH"; continue; }
    [[ -f "$MODULE_PATH/pom.xml" ]] || { echo "WARN: No pom.xml in: $MODULE_PATH"; continue; }

    BUILD_POM="${MODULE_PATH}/pom.xml"
    echo "Using POM: $BUILD_POM"

    prepare_module_build_area "$MODULE_PATH"

    mvn_run -f "$BUILD_POM" clean "$MVN_GOAL" || {
        echo "ERROR: Build failed for $module"
        exit 1
    }

    echo "Collecting JAR/WAR outputs..."
    find "$MODULE_PATH/target" -maxdepth 1 -type f \( -name "*.jar" -o -name "*.war" \) >> "$BUILT_JAR_FILE" 2>/dev/null || true
done < "$MODULES_FILE"

sort -u "$BUILT_JAR_FILE" -o "$BUILT_JAR_FILE" || true

###############################################################################
# 14) FINAL OUTPUT
###############################################################################
echo ""
echo "===== CHANGED DEPENDENCY JAR PATHS ====="
cat "$CHANGED_DEP_JAR_FILE" || true
echo ""

echo "===== BUILT JAR / WAR PATHS ====="
cat "$BUILT_JAR_FILE" || true
echo ""

echo "============================================================"
echo "      INCREMENTAL BUILD COMPLETED SUCCESSFULLY"
echo "============================================================"
echo ""
