
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
