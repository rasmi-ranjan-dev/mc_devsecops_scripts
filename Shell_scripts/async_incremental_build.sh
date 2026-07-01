#!/usr/bin/env bash
set -euo pipefail

BUILD_ROOT="${1:?BUILD_ROOT required}"
PROJECT_NAME="${2:?PROJECT_NAME required}"
VERSION="${3:?VERSION required}"
FOURTH_ARG="${4:?GIT_URL or URL_LIST_FILE required}"
USERNAME="${5:?USERNAME required}"
PASSWORD="${6:?PASSWORD required}"
CHECKOUT_SUBDIR="${7:?CHECKOUT_SUBDIR required}"

DEFAULT_BRANCH="${BRANCH:-master}"

ROOT_TARGET="$BUILD_ROOT/$PROJECT_NAME/$VERSION/$CHECKOUT_SUBDIR"
sudo mkdir -p "$ROOT_TARGET"

INCR_BASE="${INCR_BASE:-$BUILD_ROOT/$PROJECT_NAME/incremental}"
SHARED_BASE="${SHARED_BASE:-/shared_files}"

DEST_INCR_DIR="$SHARED_BASE/$PROJECT_NAME/incremental"
CHANGED_FILE_LIST="$DEST_INCR_DIR/changed_files.txt"

sudo mkdir -p "$DEST_INCR_DIR"

# Clear old changed_files.txt at start of every run
: | sudo tee "$CHANGED_FILE_LIST" >/dev/null

ENCODED_PASSWORD="$PASSWORD"
ENCODED_PASSWORD="${ENCODED_PASSWORD//@/%40}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//:/%3A}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//#/%23}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//\$/%24}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//&/%26}"

echo "=== Unified Git Operation ==="
echo "Root Target: $ROOT_TARGET"
echo "Changed file list: $CHANGED_FILE_LIST"

build_auth_url() {
    local raw_url="$1"
    local stripped proto

    stripped="$(echo "$raw_url" | sed -E 's|^https?://||')"
    proto="$(echo "$raw_url" | sed -E 's|^(https?)://.*|\1|')"

    echo "$proto://$USERNAME:$ENCODED_PASSWORD@$stripped"
}

process_repo() {
    local raw_url="$1"
    local branch="$2"
    local target_dir="$3"

    sudo mkdir -p "$target_dir"

    local auth_url
    auth_url="$(build_auth_url "$raw_url")"

    echo "----------------------------------------"
    echo "Repo:   $raw_url"
    echo "Branch: $branch"
    echo "Target: $target_dir"

    local repo_dir="$target_dir"
    # Fix Git dubious ownership issue
    sudo git config --global --add safe.directory "$repo_dir" || true
    git config --global --add safe.directory "$repo_dir" || true
    if [ -d "$repo_dir/.git" ]; then
        (
            cd "$repo_dir"

            old_rev="$(sudo git rev-parse HEAD 2>/dev/null || true)"

            sudo git remote set-url origin "$auth_url" || true
            sudo git fetch --prune origin

            sudo git rev-parse --verify "origin/$branch" >/dev/null 2>&1 || exit 1

            sudo git checkout -B "$branch" "origin/$branch"
            sudo git reset --hard "origin/$branch"

            new_rev="$(sudo git rev-parse HEAD)"

            changed_count=0

            if [ -n "${old_rev:-}" ] && [ "$old_rev" != "$new_rev" ]; then
                while IFS= read -r rel; do
                    [ -z "$rel" ] && continue

                    # Write only changed .java file path into changed_files.txt
                    printf '%s\n' "$rel" | sudo tee -a "$CHANGED_FILE_LIST" >/dev/null

                    changed_count=$((changed_count + 1))
                done < <(sudo git diff --name-only "$old_rev" "$new_rev" -- '*.java')
            fi

            if [ "$changed_count" -gt 0 ]; then
                echo "Changed .java files written: $changed_count"
            else
                echo "No .java changes found."
            fi
        )
    else
        if [ -d "$target_dir" ] && [ "$(ls -A "$target_dir" 2>/dev/null)" ]; then
            echo "Target directory already exists and is not empty. Skipping clone."
            return 0
        fi

        sudo git clone -b "$branch" "$auth_url" "$target_dir"
        echo "First clone completed. No changed files written."
    fi
}

if [ -f "$FOURTH_ARG" ] && [ -r "$FOURTH_ARG" ]; then
    while IFS= read -r line; do
        [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue

        IFS=';' read -r raw_url branch subdir <<< "$line"

        branch="${branch:-master}"
        subdir="${subdir:-.}"

        process_repo "$raw_url" "$branch" "$ROOT_TARGET/$subdir"
    done < "$FOURTH_ARG"
else
    process_repo "$FOURTH_ARG" "$DEFAULT_BRANCH" "$ROOT_TARGET"
fi

echo "=== Final Output ==="
echo "Location: $CHANGED_FILE_LIST"

if [ ! -s "$CHANGED_FILE_LIST" ]; then
    echo "No .java changes detected."
else
    echo "Changed .java files written to changed_files.txt"
fi

sudo chmod 666 "$CHANGED_FILE_LIST"

echo "=== Git Operation Finished ==="
jenkins@jenkins-soe-host:/shared_files/Orchestrator$ ^C
jenkins@jenkins-soe-host:/shared_files/Orchestrator$
jenkins@jenkins-soe-host:/shared_files/Orchestrator$
jenkins@jenkins-soe-host:/shared_files/Orchestrator$
jenkins@jenkins-soe-host:/shared_files/Orchestrator$
jenkins@jenkins-soe-host:/shared_files/Orchestrator$
jenkins@jenkins-soe-host:/shared_files/Orchestrator$ cat async_incr_build.sh
#!/bin/bash
set -euo pipefail
# INPUTS
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
# PATHS
###############################################################################
REPO_ROOT="${NFS_SHARE}/${PROJECT_NAME}/${VERSION}/${CHECKOUT_SUBDIR}"
INCR_DIR="${NFS_SHARE}/${PROJECT_NAME}/${VERSION}"
CHANGED_FILE_LIST="${INCR_DIR}/changed_files.txt"
SOURCE_CODE_FILE="${INCR_DIR}/incr_source_code.txt"
NORMALIZED_CHANGED_FILE_LIST="${INCR_DIR}/changed_files_normalized.txt"
SOURCE_BIN_FILE="${INCR_DIR}/incr_source_binaries.txt"
MODULES_FILE="${INCR_DIR}/impacted_modules.txt"
BUILT_JAR_FILE="${INCR_DIR}/built_jar_paths.txt"
CHANGED_DEP_JAR_FILE="${INCR_DIR}/changed_dependency_jars.txt"
#sudo cp "${NFS_SHARE}/${PROJECT_NAME}/incremental/changed_files.txt" "${INCR_DIR}/"
# ❗ Avoid creating root-owned files
cp "${NFS_SHARE}/${PROJECT_NAME}/incremental/changed_files.txt" "${INCR_DIR}/"

# ✅ FIX PERMISSIONS (CRITICAL)
echo "Fixing ownership and permissions..."

chown -R sa24459371forti:sa24459371forti "$REPO_ROOT" || true
chmod -R u+rwX,g+rwX "$REPO_ROOT" || true

chown -R sa24459371forti:sa24459371forti "$INCR_DIR" || true
chmod -R u+rwX,g+rwX "$INCR_DIR" || true

###############################################################################
# MAVEN / JAVA SETUP
###############################################################################
MVN_CMD="${MVN_CMD:-mvn}"
SKIP_TESTS="${SKIP_TESTS:-true}"

JAVA_BIN="$(readlink -f "$(which java)" || true)"

if [[ -z "$JAVA_BIN" ]]; then
    echo "ERROR: Java not found"
    exit 1
fi

export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export M2_HOME=/usr/bin/apache-maven-3.6.3
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"

echo "============================================================"
echo "JAVA_HOME = $JAVA_HOME"
echo "M2_HOME   = $M2_HOME"
echo "REPO_ROOT = $REPO_ROOT"
echo "INCR_DIR  = $INCR_DIR"
echo "============================================================"

java -version
mvn -version

###############################################################################
# PREPARE FILES
###############################################################################
mkdir -p "$INCR_DIR"

: > "$NORMALIZED_CHANGED_FILE_LIST"
: > "$SOURCE_CODE_FILE"
: > "$SOURCE_BIN_FILE"
: > "$MODULES_FILE"
: > "$BUILT_JAR_FILE"
: > "$CHANGED_DEP_JAR_FILE"

#chmod -R u+rwX,g+rwX  "$INCR_DIR" || true
chmod u+rw,g+rw  "$INCR_DIR" || true


###############################################################################
# MAVEN HELPER
###############################################################################
mvn_run() {
    local skipflag=""

    if [[ "$SKIP_TESTS" == "true" ]]; then
        skipflag="-Dmaven.test.skip=true -DskipTests"
    fi

    ${MVN_CMD} -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" ${skipflag} "$@"
}

###############################################################################
# VALIDATION
###############################################################################
if [[ ! -d "$REPO_ROOT" ]]; then
    echo "ERROR: Repo root not found: $REPO_ROOT"
    exit 1
fi

if [[ ! -f "$CHANGED_FILE_LIST" ]]; then
    echo "ERROR: changed_files.txt not found: $CHANGED_FILE_LIST"
    exit 1
fi

if [[ ! -s "$CHANGED_FILE_LIST" ]]; then
    echo "No changed files. Nothing to build."
    exit 0
fi

###############################################################################
# NORMALIZE CHANGED FILE PATHS
###############################################################################
normalize_path() {
    local p="$1"

    p="$(echo "$p" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    p="${p#./}"
    p="${p#async_checkout/}"
    p="${p#${CHECKOUT_SUBDIR}/}"
    p="${p#${REPO_ROOT}/}"
    p="${p#${NFS_SHARE}/${PROJECT_NAME}/${VERSION}/${CHECKOUT_SUBDIR}/}"
    p="${p#${BUILD_ROOT}/${PROJECT_NAME}/${VERSION}/${CHECKOUT_SUBDIR}/}"

    echo "$p"
}

while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    normalize_path "$file" >> "$NORMALIZED_CHANGED_FILE_LIST"
done < "$CHANGED_FILE_LIST"

sort -u "$NORMALIZED_CHANGED_FILE_LIST" -o "$NORMALIZED_CHANGED_FILE_LIST"

cp "$NORMALIZED_CHANGED_FILE_LIST" "$SOURCE_CODE_FILE"

echo ""
echo "===== NORMALIZED CHANGED FILES ====="
cat "$NORMALIZED_CHANGED_FILE_LIST"
echo ""

###############################################################################
# DETECT IMPACTED MODULES
###############################################################################
echo "===== MODULE LIST FROM JENKINS ====="
echo "$MODULE_LIST"
echo ""

while IFS= read -r module; do
    [[ -z "$module" ]] && continue

    CLEAN_MODULE="$(normalize_path "$module")"

    if grep -q "^${CLEAN_MODULE}/" "$NORMALIZED_CHANGED_FILE_LIST"; then
        echo "$CLEAN_MODULE" >> "$MODULES_FILE"
        echo "IMPACTED MODULE: $CLEAN_MODULE"
    fi
done <<< "$MODULE_LIST"

sort -u "$MODULES_FILE" -o "$MODULES_FILE"

echo ""
echo "===== IMPACTED MODULES ====="
cat "$MODULES_FILE" || true
echo ""

if [[ ! -s "$MODULES_FILE" ]]; then
    echo "No impacted modules found. Exiting."
    exit 0
fi

###############################################################################
# DETECT POM CHANGES
###############################################################################
POM_CHANGED="false"

if grep -E '(^|/)(pom\.xml|pom-on-prem\.xml|pom-saas\.xml|pom-redis\.xml)$' "$NORMALIZED_CHANGED_FILE_LIST" >/dev/null; then
    POM_CHANGED="true"
fi

echo "POM_CHANGED = $POM_CHANGED"

###############################################################################
# COLLECT CLASSPATH FOR SAST
###############################################################################
collect_classpath() {
    local pom_file="$1"
    local out_file="$2"

    : > "$out_file"

    if [[ -f "$pom_file" ]]; then
        mvn_run -q -f "$pom_file" \
            org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
            -Dmdep.outputFile="${out_file}.raw" \
            -Dmdep.includeScope=compile || true

        if [[ -s "${out_file}.raw" ]]; then
            tr ':' '\n' < "${out_file}.raw" | sed '/^[[:space:]]*$/d' | sort -u > "$out_file"
        fi

        rm -f "${out_file}.raw"
    fi
}

while IFS= read -r module; do
    [[ -z "$module" ]] && continue

    MODULE_POM="${REPO_ROOT}/${module}/pom.xml"

    if [[ -f "$MODULE_POM" ]]; then
        TMP_CP="${INCR_DIR}/${module//\//_}_classpath.txt"
        collect_classpath "$MODULE_POM" "$TMP_CP"

        if [[ -s "$TMP_CP" ]]; then
            cat "$TMP_CP" >> "$SOURCE_BIN_FILE"
        fi
    else
        echo "WARN: pom.xml not found for classpath collection: $MODULE_POM"
    fi
done < "$MODULES_FILE"

sort -u "$SOURCE_BIN_FILE" -o "$SOURCE_BIN_FILE" || true

###############################################################################
# DEPENDENCY JAR COMPARISON HELPERS
###############################################################################
jar_key() {
    local jar="$1"
    echo "${jar%/*/*}"
}

compare_dependency_jars() {
    local before_file="$1"
    local after_file="$2"
    local output_file="$3"

    while IFS= read -r newjar; do
        [[ -z "$newjar" ]] && continue

        newkey="$(jar_key "$newjar")"
        oldjar=""

        while IFS= read -r oldline; do
            [[ -z "$oldline" ]] && continue

            if [[ "$(jar_key "$oldline")" == "$newkey" ]]; then
                oldjar="$oldline"
                break
            fi
        done < "$before_file"

        if [[ -n "$oldjar" && "$oldjar" != "$newjar" ]]; then
            echo "$oldjar -> $newjar" >> "$output_file"
        elif [[ -z "$oldjar" ]]; then
            echo "<not present> -> $newjar" >> "$output_file"
        fi
    done < "$after_file"

    while IFS= read -r oldjar; do
        [[ -z "$oldjar" ]] && continue

        oldkey="$(jar_key "$oldjar")"
        found_new="false"

        while IFS= read -r newline; do
            [[ -z "$newline" ]] && continue

            if [[ "$(jar_key "$newline")" == "$oldkey" ]]; then
                found_new="true"
                break
            fi
        done < "$after_file"

        if [[ "$found_new" == "false" ]]; then
            echo "$oldjar -> <removed>" >> "$output_file"
        fi
    done < "$before_file"
}

###############################################################################
# IF POM CHANGED, DETECT CHANGED DEPENDENCY JARS FOR CVC
###############################################################################
if [[ "$POM_CHANGED" == "true" ]]; then
    echo ""
    echo "============================================================"
    echo "POM changed. Checking changed dependency jars for CVC."
    echo "============================================================"

    while IFS= read -r changed_pom; do
        [[ -z "$changed_pom" ]] && continue

        CHANGED_POM_ABS="${REPO_ROOT}/${changed_pom}"

        if [[ ! -f "$CHANGED_POM_ABS" ]]; then
            echo "WARN: Changed POM not found: $CHANGED_POM_ABS"
            continue
        fi

        SAFE_NAME="${changed_pom//\//_}"

        BEFORE_CP="${INCR_DIR}/${SAFE_NAME}_before_cp.txt"
        AFTER_CP="${INCR_DIR}/${SAFE_NAME}_after_cp.txt"
        PREV_CP="${INCR_DIR}/${SAFE_NAME}_prev_cp.txt"

        : > "$BEFORE_CP"
        : > "$AFTER_CP"

        if [[ -s "$PREV_CP" ]]; then
            echo "Using previous saved classpath for: $changed_pom"
            cp "$PREV_CP" "$BEFORE_CP"
        else
            echo "No previous baseline. Creating initial baseline for: $changed_pom"
            collect_classpath "$CHANGED_POM_ABS" "$BEFORE_CP"
        fi

        collect_classpath "$CHANGED_POM_ABS" "$AFTER_CP"

        compare_dependency_jars "$BEFORE_CP" "$AFTER_CP" "$CHANGED_DEP_JAR_FILE"

        cp -f "$AFTER_CP" "$PREV_CP" || true

    done < <(
        grep -E '(^|/)(pom\.xml|pom-on-prem\.xml|pom-saas\.xml|pom-redis\.xml)$' "$NORMALIZED_CHANGED_FILE_LIST" | sort -u
    )

    sort -u "$CHANGED_DEP_JAR_FILE" -o "$CHANGED_DEP_JAR_FILE" || true
fi

###############################################################################
# FIND REACTOR/PARENT POM CANDIDATES FOR MODULE
###############################################################################
get_reactor_candidates_for_module() {
    local module_path="$1"
    local current_dir
    local rel_module

    current_dir="$(dirname "$module_path")"

    while [[ "$current_dir" == "$REPO_ROOT"* && "$current_dir" != "$REPO_ROOT" ]]; do
        if [[ -f "$current_dir/pom.xml" ]]; then
            rel_module="$(realpath --relative-to="$current_dir" "$module_path" 2>/dev/null || true)"

            if [[ -n "$rel_module" ]]; then
                echo "$current_dir/pom.xml|$rel_module"
            fi
        fi

        current_dir="$(dirname "$current_dir")"
    done
}

###############################################################################
# BUILD IMPACTED MODULES USING BEST AVAILABLE POM
###############################################################################
echo ""
echo "============================================================"
echo "BUILDING IMPACTED MODULES"
echo "============================================================"

echo ""
echo "Full impacted modules for SAST/CVC:"
cat "$MODULES_FILE" || true
echo ""

while IFS= read -r module; do
    [[ -z "$module" ]] && continue

    MODULE_PATH="${REPO_ROOT}/${module}"
    MODULE_POM="${MODULE_PATH}/pom.xml"

    echo "------------------------------------------------------------"
    echo "BUILDING MODULE : $module"
    # ✅ Ensure writable target directory (fix Maven failure)
    #mkdir -p "$MODULE_PATH/target/classes" || true
    #chmod -R u+rwX "$MODULE_PATH/target" || true
    echo "MODULE PATH     : $MODULE_PATH"
    echo "MODULE POM      : $MODULE_POM"
    echo "------------------------------------------------------------"

    if [[ ! -d "$MODULE_PATH" ]]; then
        echo "WARN: Module directory not found: $MODULE_PATH"
        continue
    fi

    if [[ ! -f "$MODULE_POM" ]]; then
        echo "WARN: pom.xml not found for module: $module"
        continue
    fi

    BUILD_SUCCESS="false"

    while IFS= read -r reactor_info; do
        [[ -z "$reactor_info" ]] && continue

        REACTOR_POM="${reactor_info%%|*}"
        REACTOR_MODULE="${reactor_info##*|}"

        echo "Trying reactor/parent POM:"
        echo "REACTOR_POM    = $REACTOR_POM"
        echo "REACTOR_MODULE = $REACTOR_MODULE"

        if mvn_run -f "$REACTOR_POM" -pl "$REACTOR_MODULE" -am clean "$MVN_GOAL"; then
            echo "SUCCESS: Reactor build completed for $module"
            BUILD_SUCCESS="true"
            break
        else
            echo "WARN: Reactor build failed for:"
            echo "REACTOR_POM    = $REACTOR_POM"
            echo "REACTOR_MODULE = $REACTOR_MODULE"
            echo "Trying next parent POM if available..."
        fi

    done < <(get_reactor_candidates_for_module "$MODULE_PATH")

    if [[ "$BUILD_SUCCESS" != "true" ]]; then
        echo "No reactor build worked. Trying direct module build."

        if mvn_run -f "$MODULE_POM" clean "$MVN_GOAL"; then
            echo "SUCCESS: Direct build completed for $module"
            BUILD_SUCCESS="true"
        else
            echo "ERROR: Maven build failed for module: $module"
            exit 1
        fi
    fi

    echo "Collecting built JAR/WAR for module: $module"

    find "$MODULE_PATH/target" -maxdepth 1 -type f \( -name "*.jar" -o -name "*.war" \) >> "$BUILT_JAR_FILE" 2>/dev/null || true

done < "$MODULES_FILE"

sort -u "$BUILT_JAR_FILE" -o "$BUILT_JAR_FILE" || true

###############################################################################
# COPY CHANGED DEPENDENCY JARS FOR CVC
###############################################################################
if [[ -s "$CHANGED_DEP_JAR_FILE" ]]; then
    CVC_DEP_DIR="${INCR_DIR}/changed_dependency_jars_for_cvc"
    mkdir -p "$CVC_DEP_DIR"

    echo ""
    echo "Copying changed dependency jars to: $CVC_DEP_DIR"

    awk -F' -> ' '{print $2}' "$CHANGED_DEP_JAR_FILE" | while read -r jarfile; do
        jarfile="$(echo "$jarfile" | xargs)"

        if [[ -f "$jarfile" ]]; then
            echo "Copying: $jarfile"
            cp -f "$jarfile" "$CVC_DEP_DIR/" || true
        fi
    done
fi

###############################################################################
# FINAL OUTPUT
###############################################################################
echo ""
echo "============================================================"
echo "FINAL OUTPUT FOR SAST AND CVC"
echo "============================================================"

echo ""
echo "1. Changed source files for SAST:"
echo "$SOURCE_CODE_FILE"
cat "$SOURCE_CODE_FILE" || true

echo ""
echo "2. Source binaries/classpath for SAST:"
echo "$SOURCE_BIN_FILE"
cat "$SOURCE_BIN_FILE" || true

echo ""
echo "3. Impacted modules:"
echo "$MODULES_FILE"
cat "$MODULES_FILE" || true

echo ""
echo "4. Built JAR/WAR files for CVC:"
echo "$BUILT_JAR_FILE"
cat "$BUILT_JAR_FILE" || true

echo ""
echo "5. Changed dependency jars for CVC:"
echo "$CHANGED_DEP_JAR_FILE"
cat "$CHANGED_DEP_JAR_FILE" || true

echo ""
echo "============================================================"
echo "INCREMENTAL BUILD COMPLETED SUCCESSFULLY"
echo "============================================================"
