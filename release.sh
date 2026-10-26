#!/usr/bin/env bash
set -euo pipefail

# Create a release from the current commit (no local Android build/signing).
# Usage: ./release.sh <version> [remote] [branch]
# Example: ./release.sh 5.0.5 fork feature/message-attachments
#
# Everything is built and published by GitHub Actions (see
# .github/workflows/deploy.yml, triggered by the pushed tag). This script only
# validates the tree, pushes the branch, moves the tag and then waits for the
# workflow so a broken build is reported here instead of silently producing no
# release for Obtainium.

VERSION="${1:-}"
REMOTE="${2:-fork}"
BRANCH="${3:-$(git rev-parse --abbrev-ref HEAD)}"
TIMEOUT_MINUTES="${RELEASE_TIMEOUT_MINUTES:-25}"

if [[ -z "${VERSION}" ]]; then
	echo "Usage: $0 <version> [remote] [branch]" >&2
	exit 1
fi

if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9]+)*$ ]]; then
	echo "Error: version must look like 5.0.4 or 5.0.5-beta01" >&2
	exit 1
fi

git rev-parse --git-dir >/dev/null

if [[ -n "$(git status --porcelain)" ]]; then
	echo "Error: working tree not clean. Commit/stash changes first." >&2
	exit 1
fi

COMMIT="$(git rev-parse HEAD)"
echo "Releasing version ${VERSION}"
echo "Remote: ${REMOTE}"
echo "Branch: ${BRANCH}"
echo "Commit: ${COMMIT}"

# --- Changelog -----------------------------------------------------------------

CHANGELOG="fastlane/metadata/android/en-US/changelogs/${VERSION}.txt"
if [[ ! -f "${CHANGELOG}" ]]; then
	echo "Error: missing ${CHANGELOG}" >&2
	echo "Create it with the release notes before running this script." >&2
	exit 1
fi

if [[ ! -s "${CHANGELOG}" ]]; then
	echo "Error: ${CHANGELOG} is empty." >&2
	exit 1
fi

# --- Resource validation -------------------------------------------------------
# A single malformed strings.xml (e.g. an unescaped apostrophe) makes AAPT2 fail
# and the workflow then publishes nothing. Catch it before spending a CI run.

if command -v python3 >/dev/null 2>&1; then
	echo "Validating string resources..."
	if ! python3 - "${VERSION}" <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

problems = []

for path in sorted(glob.glob("app/src/main/res/values*/strings.xml")):
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        problems.append(f"{path}: invalid XML: {e}")
        continue

    for element in tree.getroot().iter("string"):
        name = element.get("name")
        text = "".join(element.itertext())
        if not text:
            continue
        # Values wrapped in double quotes are exempt from apostrophe escaping.
        if len(text) > 1 and text.startswith('"') and text.endswith('"'):
            continue
        for index, char in enumerate(text):
            if char != "'":
                continue
            if index > 0 and text[index - 1] == "\\":
                continue
            problems.append(
                f"{path}: <string name=\"{name}\"> has an unescaped apostrophe "
                f"(use \\' or wrap the value in double quotes)"
            )
            break

if problems:
    print("\n".join(problems), file=sys.stderr)
    sys.exit(1)
PY
	then
		echo "Error: string resource validation failed, see above." >&2
		exit 1
	fi
fi

# --- Push ----------------------------------------------------------------------

# Remember the runs that already exist for this tag (e.g. a previous failed
# attempt), so the verification below only ever looks at the run we trigger now.
BASELINE_RUNS=""
if command -v gh >/dev/null 2>&1; then
	BASELINE_RUNS=" $(gh run list --repo "$(git remote get-url "${REMOTE}" \
		| sed -E 's#^(git@|ssh://git@|https?://)github\.com[:/]##; s#\.git$##')" \
		--workflow deploy.yml --limit 100 --json databaseId,headBranch \
		--jq ".[] | select(.headBranch == \"${VERSION}\") | .databaseId" 2>/dev/null \
		| tr '\n' ' ' || true)"
fi

git push "${REMOTE}" "${BRANCH}"

if git rev-parse "refs/tags/${VERSION}" >/dev/null 2>&1; then
	echo "Tag ${VERSION} exists, moving it to ${COMMIT}"
	git tag -d "${VERSION}" >/dev/null
	git push "${REMOTE}" ":refs/tags/${VERSION}" >/dev/null || true
fi

git tag -f "${VERSION}" "${COMMIT}"
git push -f "${REMOTE}" "${VERSION}"

echo "Tag ${VERSION} pushed to ${REMOTE} pointing to ${COMMIT}."

# --- Verification --------------------------------------------------------------
# The release only exists once the workflow succeeded and uploaded the APK.
# Without this check a red workflow looks like a successful release.

if ! command -v gh >/dev/null 2>&1; then
	echo "gh CLI not found, skipping release verification."
	echo "Check https://github.com -> Actions -> 'Deploy App' for tag ${VERSION}."
	exit 0
fi

REMOTE_URL="$(git remote get-url "${REMOTE}")"
REPO="$(printf '%s' "${REMOTE_URL}" \
	| sed -E 's#^(git@|ssh://git@|https?://)github\.com[:/]##; s#\.git$##')"

if [[ ! "${REPO}" =~ ^[^/]+/[^/]+$ ]]; then
	echo "Could not derive the GitHub repository from ${REMOTE_URL}, skipping verification."
	exit 0
fi

echo "Waiting for the 'Deploy App' workflow for tag ${VERSION} on ${REPO}..."

DEADLINE=$(( $(date +%s) + TIMEOUT_MINUTES * 60 ))
RUN_ID=""
while [[ $(date +%s) -lt ${DEADLINE} ]]; do
	RUN_ID="$(gh run list --repo "${REPO}" --workflow deploy.yml --limit 50 \
		--json databaseId,headBranch,headSha \
		--jq ".[] | select(.headBranch == \"${VERSION}\" and (.headSha | startswith(\"${COMMIT:0:12}\"))) | .databaseId" 2>/dev/null \
		| grep -v -w -E "$(printf '%s' "${BASELINE_RUNS:-none}" | tr ' ' '|' | sed 's/^|//; s/|$//')" \
		| head -n 1 || true)"

	if [[ -n "${RUN_ID}" ]]; then
		STATUS="$(gh run view "${RUN_ID}" --repo "${REPO}" --json status,conclusion \
			--jq '.status + " " + (.conclusion // "")' 2>/dev/null || echo "queued ")"

		if [[ "${STATUS}" == "completed success" ]]; then
			echo "Workflow ${RUN_ID} succeeded."
			break
		fi

		if [[ "${STATUS}" == completed* ]]; then
			echo "Error: workflow ${RUN_ID} finished with '${STATUS#completed }'." >&2
			echo "Inspect it with: gh run view ${RUN_ID} --repo ${REPO} --log-failed" >&2
			exit 1
		fi
	fi

	sleep 20
done

if [[ -z "${RUN_ID}" ]]; then
	echo "Error: no 'Deploy App' workflow run appeared for tag ${VERSION} within ${TIMEOUT_MINUTES} minutes." >&2
	exit 1
fi

if ! gh release view "${VERSION}" --repo "${REPO}" --json tagName,assets \
	--jq '.assets[].name' 2>/dev/null | grep -q "BetterUntis-${VERSION}.apk"; then
	echo "Error: release ${VERSION} on ${REPO} has no BetterUntis-${VERSION}.apk asset." >&2
	gh release view "${VERSION}" --repo "${REPO}" 2>&1 | head -20 >&2 || true
	exit 1
fi

echo "Release ${VERSION} is published with BetterUntis-${VERSION}.apk:"
gh release view "${VERSION}" --repo "${REPO}" --json url,assets \
	--jq '.url, (.assets[] | "  " + .name + " (" + (.size | tostring) + " bytes)")'
