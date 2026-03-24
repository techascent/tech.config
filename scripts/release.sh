#!/bin/bash

set -e

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# ── preflight checks ─────────────────────────────────────────────────────────

for cmd in clojure vault aws jq npx node; do
  command -v "$cmd" >/dev/null || { err "Required command not found: $cmd — run scripts/install first"; exit 1; }
done

CURRENTBRANCH=$(git status|awk 'NR==1{print $3}');

if [ ! "$CURRENTBRANCH"=="master" ]; then
    err "Not on master — cannot proceed."
    exit 1
fi

LOCAL=$(git rev-parse @)
REMOTE=$(git rev-parse @{u})

if [ "$LOCAL" != "$REMOTE" ]; then
    err "Your working branch has diverged from the remote master, cannot continue"
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    err "Uncommitted changes — commit or stash first"
    exit 1
fi

# ── AWS credentials for S3 maven repo ────────────────────────────────────────

header "Fetching AWS credentials"
source "$(dirname "${BASH_SOURCE[0]}")/aws-creds" core

# ── test ──────────────────────────────────────────────────────────────────────

header "Running tests"
"$(dirname "${BASH_SOURCE[0]}")/test"

# ── version bump (release) ───────────────────────────────────────────────────

VERSION_FILE="VERSION"
SNAPSHOT_VERSION=$(cat "$VERSION_FILE" | tr -d '[:space:]')
RELEASE_VERSION="${SNAPSHOT_VERSION%-SNAPSHOT}"

if [ "$SNAPSHOT_VERSION" = "$RELEASE_VERSION" ]; then
    err "VERSION ($SNAPSHOT_VERSION) is not a snapshot — cannot release"
    exit 1
fi

info "Releasing version $RELEASE_VERSION"

echo "$RELEASE_VERSION" > "$VERSION_FILE"
git add "$VERSION_FILE"
git commit -m "Release $RELEASE_VERSION"
git tag -a "v$RELEASE_VERSION" -m "v$RELEASE_VERSION"

# ── build & deploy ───────────────────────────────────────────────────────────

header "Building jar"
clojure -T:build jar

JAR_FILE="target/tech.config-${RELEASE_VERSION}.jar"
POM_FILE="target/classes/META-INF/maven/techascent/tech.config/pom.xml"

header "Deploying to S3 maven repo"
S3_BASE="s3://techascent.jars/releases/techascent/tech.config/${RELEASE_VERSION}"
aws s3 cp "$JAR_FILE" "${S3_BASE}/tech.config-${RELEASE_VERSION}.jar"
aws s3 cp "$POM_FILE" "${S3_BASE}/tech.config-${RELEASE_VERSION}.pom"

# ── version bump (next snapshot) ─────────────────────────────────────────────

IFS='.' read -r MAJOR MINOR PATCH <<< "$RELEASE_VERSION"
NEXT_PATCH=$((PATCH + 1))
NEXT_VERSION="${MAJOR}.${MINOR}.${NEXT_PATCH}-SNAPSHOT"

info "Bumping to $NEXT_VERSION"

echo "$NEXT_VERSION" > "$VERSION_FILE"
git add "$VERSION_FILE"
git commit -m "$NEXT_VERSION"

git push origin master
git push --tags origin

ok "Released $RELEASE_VERSION"
