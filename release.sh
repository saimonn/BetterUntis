#!/usr/bin/env bash
set -euo pipefail

# Create a release from the current commit (no local Android build/signing).
# Usage: ./release.sh <version> [remote] [branch]
# Example: ./release.sh 5.0.5 fork feature/message-attachments

VERSION="${1:-}"
REMOTE="${2:-fork}"
BRANCH="${3:-$(git rev-parse --abbrev-ref HEAD)}"

if [[ -z "${VERSION}" ]]; then
	echo "Usage: $0 <version> [remote] [branch]" >&2
	exit 1
fi

if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9]+)*$ ]]; then
	echo "Error: version must look like 5.0.4 or 5.0.5-beta01" >&2
	exit 1
fi

# Ensure we are in a git repo
git rev-parse --git-dir >/dev/null

# Ensure working tree is clean
if [[ -n "$(git status --porcelain)" ]]; then
	echo "Error: working tree not clean. Commit/stash changes first." >&2
	exit 1
fi

COMMIT="$(git rev-parse HEAD)"
echo "Releasing version ${VERSION}"
echo "Remote: ${REMOTE}"
echo "Branch: ${BRANCH}"
echo "Commit: ${COMMIT}"

# Ensure changelog exists
CHANGELOG="fastlane/metadata/android/en-US/changelogs/${VERSION}.txt"
if [[ ! -f "${CHANGELOG}" ]]; then
	echo "Error: missing ${CHANGELOG}" >&2
	echo "Create it with the release notes before running this script." >&2
	exit 1
fi

# Push branch first
git push "${REMOTE}" "${BRANCH}"

# Move tag to current commit if it exists, create if not
if git rev-parse "refs/tags/${VERSION}" >/dev/null 2>&1; then
	echo "Tag ${VERSION} exists, moving it to ${COMMIT}"
	git tag -d "${VERSION}" >/dev/null
	git push "${REMOTE}" ":refs/tags/${VERSION}" >/dev/null || true
fi

git tag "${VERSION}" "${COMMIT}"
git push "${REMOTE}" "${VERSION}"

echo "Done. Tag ${VERSION} pushed to ${REMOTE} pointing to ${COMMIT}."
echo "GitHub Actions (Build App / Deploy App) should start for tag ${VERSION}."
