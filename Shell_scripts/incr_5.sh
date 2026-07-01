# checkout.sh
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

ENCODED_PASSWORD="$PASSWORD"
ENCODED_PASSWORD="${ENCODED_PASSWORD//@/%40}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//:/%3A}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//#/%23}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//\$/%24}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//&/%26}"

echo "=== Unified Git Operation ==="
echo "Root Target: $ROOT_TARGET"

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
   # [ -d "$target_dir/$CHECKOUT_SUBDIR/.git" ] && repo_dir="$target_dir/$CHECKOUT_SUBDIR"

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
          out_dir="$INCR_BASE/${BUILD_NUMBER:-manual}"
          changed_count=0

          if [ -n "${old_rev:-}" ] && [ "$old_rev" != "$new_rev" ]; then
              sudo mkdir -p "$out_dir"
              while IFS= read -r rel; do
                  [ -z "$rel" ] && continue
                  [ -f "$rel" ] || continue
                  dest="$out_dir/$rel"
                 sudo mkdir -p "$(dirname "$dest")"
                 sudo cp -p "$rel" "$dest"
                  changed_count=$((changed_count+1))
              done < <(sudo git diff --name-only "$old_rev" "$new_rev")
          fi

          if [ "$changed_count" -gt 0 ]; then
              echo "Changed files copied: $changed_count"
          else
              echo "No files were found (no changes or first run)."
          fi
        )
    else
        if [ -d "$target_dir" ] && [ "$(ls -A "$target_dir")" ]; then
            return 0
        fi

        sudo git clone -b "$branch" "$auth_url" "$target_dir"
        echo "No files were found (first clone)."
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

SRC_INCR_DIR="$INCR_BASE/${BUILD_NUMBER:-manual}"
DEST_INCR_DIR="$SHARED_BASE/$PROJECT_NAME/incremental/${BUILD_NUMBER:-manual}"

 # If no incremental files were produced, skip the copy gracefully
if [ ! -d "$SRC_INCR_DIR" ] || [ -z "$(ls -A "$SRC_INCR_DIR" 2>/dev/null)" ]; then
  echo "=== Final Copy to Shared Location ==="
  echo "Source:      $SRC_INCR_DIR"
  echo "Destination: $DEST_INCR_DIR"
  echo "No incremental changes detected for build ${BUILD_NUMBER:-manual}; skipping copy to shared."
  echo "=== Git Operation Finished ==="
  exit 0
fi

echo "=== Final Copy to Shared Location ==="
echo "Source:      $SRC_INCR_DIR"
echo "Destination: $DEST_INCR_DIR"

sudo mkdir -p "$DEST_INCR_DIR"
sudo cp -r "$SRC_INCR_DIR"/. "$DEST_INCR_DIR"/

#Create output folder even when incremental change
OUTPUT_DIR="$DEST_INCR_DIR/output"
sudo mkdir -p "$OUTPUT_DIR"
sudo chmod 777 "$OUTPUT_DIR"

# ------------------- print trimmed path segments -------------------
# Prints unique path segments after BUILD_NUMBER and up to & including 'src'
# Example:  " Jile/CRM/Server/src "
if [ -d "$DEST_INCR_DIR" ]; then
  echo "Changed file path segment(s) (after BUILD_NUMBER and till 'src'):"
  if find "$DEST_INCR_DIR" -type f -print -quit | grep -q .; then
    find "$DEST_INCR_DIR" -type f -print \
    | while IFS= read -r abs; do
        rel="${abs#$DEST_INCR_DIR/}"
        case "$rel" in
          *"/src/"*)
            prefix="${rel%%/src/*}/src"
            printf ' "%s" \n' "$prefix"
            ;;
        esac
      done \
    | sort -u
  else
    echo "None"
  fi
fi
# -------------------------------------------------------------------

echo "=== Git Operation Finished ==="
