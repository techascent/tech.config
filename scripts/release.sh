#!/bin/bash
# scripts/release — build, checksum, and deploy to S3 Maven repo.
#
# Usage:
#   scripts/release.sh
set -e

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$(dirname "${BASH_SOURCE[0]}")/.."

LIB_GROUP=techascent
LIB_NAME=tech.config

# ── preflight checks ─────────────────────────────────────────────────────────

for cmd in clojure vault aws jq; do
  command -v "$cmd" >/dev/null || { err "Required command not found: $cmd"; exit 1; }
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

JAR_FILE="target/${LIB_NAME}-${RELEASE_VERSION}.jar"
POM_FILE="target/classes/META-INF/maven/${LIB_GROUP}/${LIB_NAME}/pom.xml"

header "Generating checksums"
sha1sum "$JAR_FILE" | cut -d' ' -f1 > "${JAR_FILE}.sha1"
md5sum "$JAR_FILE" | cut -d' ' -f1 > "${JAR_FILE}.md5"
sha1sum "$POM_FILE" | cut -d' ' -f1 > "${POM_FILE}.sha1"
md5sum "$POM_FILE" | cut -d' ' -f1 > "${POM_FILE}.md5"
ok "checksums generated"

header "Deploying to S3 maven repo"
S3_BASE="s3://techascent.jars/releases/${LIB_GROUP}/${LIB_NAME}/${RELEASE_VERSION}"
aws s3 cp "$JAR_FILE"       "${S3_BASE}/${LIB_NAME}-${RELEASE_VERSION}.jar"
aws s3 cp "${JAR_FILE}.sha1" "${S3_BASE}/${LIB_NAME}-${RELEASE_VERSION}.jar.sha1"
aws s3 cp "${JAR_FILE}.md5"  "${S3_BASE}/${LIB_NAME}-${RELEASE_VERSION}.jar.md5"
aws s3 cp "$POM_FILE"       "${S3_BASE}/${LIB_NAME}-${RELEASE_VERSION}.pom"
aws s3 cp "${POM_FILE}.sha1" "${S3_BASE}/${LIB_NAME}-${RELEASE_VERSION}.pom.sha1"
aws s3 cp "${POM_FILE}.md5"  "${S3_BASE}/${LIB_NAME}-${RELEASE_VERSION}.pom.md5"
ok "deployed to S3"

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
