#!/usr/bin/env bash
set -euo pipefail

# -------------------------
# Input arguments
# -------------------------
BUILD_ROOT="${1:?BUILD_ROOT required}"
PROJECT_NAME="${2:?PROJECT_NAME required}"
VERSION="${3:?VERSION required}"
GIT_URL="${4:?GIT_URL required}"
USERNAME="${5:?USERNAME required}"
PASSWORD="${6:?PASSWORD required}"
CHECKOUT_SUBDIR="${7:?CHECKOUT_SUBDIR required}"

DEFAULT_BRANCH="${BRANCH:-master}"

# -------------------------
# Target repo directory
# -------------------------
ROOT_TARGET="$BUILD_ROOT/$PROJECT_NAME/$VERSION/$CHECKOUT_SUBDIR"
mkdir -p "$ROOT_TARGET"

# -------------------------
# Incremental change file
# -------------------------
INCR_BASE="${INCR_BASE:-/shared_files/$PROJECT_NAME/incremental}"
CHANGE_TXT="$INCR_BASE/changed_files.txt"
mkdir -p "$INCR_BASE"


# -------------------------
# FIX PERMISSIONS FOR INCREMENTAL DIR
# -------------------------
sudo chown -R jenkins:jenkins "$INCR_BASE" || true
sudo chmod -R 775 "$INCR_BASE" || true
touch "$CHANGE_TXT" || true


# -------------------------
# Encode password
# -------------------------
ENCODED_PASSWORD="$PASSWORD"
ENCODED_PASSWORD="${ENCODED_PASSWORD//@/%40}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//:/%3A}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//#/%23}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//\$/%24}"
ENCODED_PASSWORD="${ENCODED_PASSWORD//&/%26}"

echo "=== Unified Git Operation ==="
echo "Repo directory : $ROOT_TARGET"
echo "Change file    : $CHANGE_TXT"

# -------------------------
# Build authenticated URL
# -------------------------
build_auth_url() {
    local raw_url="$1"
    local proto stripped
    proto="$(echo "$raw_url" | sed -E 's|^(https?)://.*|\1|')"
    stripped="$(echo "$raw_url" | sed -E 's|^https?://||')"
    echo "$proto://$USERNAME:$ENCODED_PASSWORD@$stripped"
}

AUTH_URL="$(build_auth_url "$GIT_URL")"

# -------------------------
# Git logic
# -------------------------
cd "$ROOT_TARGET"

if [ -d ".git" ]; then
    echo "Existing repo detected"

    OLD_COMMIT="$(git rev-parse HEAD 2>/dev/null || true)"

    git remote set-url origin "$AUTH_URL" || true
    git fetch --prune origin
    #git reset --hard HEAD || true && git clean -fdx || true
    git checkout -B "$DEFAULT_BRANCH" "origin/$DEFAULT_BRANCH"
    git reset --hard "origin/$DEFAULT_BRANCH"

    NEW_COMMIT="$(git rev-parse HEAD)"

    # Clear old file
    : > "$CHANGE_TXT"
    CHANGE_COUNT=0

    if [ -n "$OLD_COMMIT" ] && [ "$OLD_COMMIT" != "$NEW_COMMIT" ]; then

        # ✅ FIX: No subshell (important)
        while read -r file; do
            [ -z "$file" ] && continue
            [ -f "$file" ] || continue

            echo "$file" >> "$CHANGE_TXT"
            CHANGE_COUNT=$((CHANGE_COUNT + 1))

        done < <(git diff --name-only "$OLD_COMMIT" "$NEW_COMMIT")
    fi

    # Debug logs
    echo "DEBUG: CHANGE_COUNT=$CHANGE_COUNT"

    if [ "$CHANGE_COUNT" -gt 0 ]; then
        echo "Incremental changes detected: $CHANGE_COUNT files"
        echo "Changed file list written to: $CHANGE_TXT"
        chmod 644 "$CHANGE_TXT" || true
    else
        echo "No incremental changes happened"
        rm -f "$CHANGE_TXT"
    fi

else
    echo "First-time clone"
    git clone -b "$DEFAULT_BRANCH" "$AUTH_URL" "$ROOT_TARGET"
    echo "Initial clone completed; no incremental diff"
fi

echo "=== Git Operation Finished ==="
