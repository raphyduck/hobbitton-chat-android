#!/usr/bin/env bash
#
# Bump the app version in version.properties (the single source of truth).
#
# Usage:
#   scripts/bump-version.sh <bump>
#
# Stable bumps (clear any pre-release suffix):
#   major     X.y.z       -> (X+1).0.0
#   minor     x.Y.z       -> x.(Y+1).0
#   patch     x.y.Z       -> x.y.(Z+1)
#
# Pre-release bumps (append/advance an -rcN suffix):
#   premajor  X.y.z       -> (X+1).0.0-rc1
#   preminor  x.Y.z       -> x.(Y+1).0-rc1
#   prepatch  x.y.Z       -> x.y.(Z+1)-rc1
#   rc        x.y.z-rcN   -> x.y.z-rc(N+1)   (requires an existing -rcN)
#   finalize  x.y.z-rcN   -> x.y.z           (promote a candidate to stable)
#
# A version with an -rcN suffix is treated as a pre-release: the release workflow
# marks the GitHub Release as a pre-release so Obtainium ignores it unless the user
# opts in. versionCode is NOT stored here — it is derived from versionName at build
# time by the Android convention plugin (MAJOR*10000 + MINOR*100 + PATCH), which
# strips the suffix, so an -rcN and its final release share a versionCode.
#
# Output:
#   - prints the new version name to stdout
#   - when running in GitHub Actions, also writes version_name and tag -> $GITHUB_OUTPUT
#
set -euo pipefail

BUMP="${1:-}"
case "$BUMP" in
  major|minor|patch|premajor|preminor|prepatch|rc|finalize) ;;
  *)
    echo "Usage: $0 <major|minor|patch|premajor|preminor|prepatch|rc|finalize>" >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPS="$SCRIPT_DIR/../version.properties"

if [[ ! -f "$PROPS" ]]; then
  echo "version.properties not found at $PROPS" >&2
  exit 1
fi

current_name="$(grep -E '^versionName=' "$PROPS" | cut -d'=' -f2 | tr -d '[:space:]')"

if [[ -z "$current_name" ]]; then
  echo "versionName missing or malformed in $PROPS" >&2
  exit 1
fi

# Parse the semver core (everything before the first '-') and the current rc number.
core_name="${current_name%%-*}"
IFS='.' read -r major minor patch <<< "$core_name"
major="${major:-0}"; minor="${minor:-0}"; patch="${patch:-0}"

# Extract N from a current -rcN suffix, if any (else empty -> not a pre-release).
rc=""
if [[ "$current_name" == *-rc* ]]; then
  rc="${current_name##*-rc}"
fi

pre=""   # set to "-rcN" to emit a pre-release version
case "$BUMP" in
  major)    major=$((major + 1)); minor=0; patch=0 ;;
  minor)    minor=$((minor + 1)); patch=0 ;;
  patch)    patch=$((patch + 1)) ;;
  premajor) major=$((major + 1)); minor=0; patch=0; pre="-rc1" ;;
  preminor) minor=$((minor + 1)); patch=0; pre="-rc1" ;;
  prepatch) patch=$((patch + 1)); pre="-rc1" ;;
  rc)
    if [[ -z "$rc" || ! "$rc" =~ ^[0-9]+$ ]]; then
      echo "'rc' requires a current -rcN pre-release (got '$current_name'); start one with prepatch/preminor/premajor" >&2
      exit 1
    fi
    pre="-rc$((rc + 1))"   # core stays put; only the candidate number advances
    ;;
  finalize)
    if [[ -z "$rc" || ! "$rc" =~ ^[0-9]+$ ]]; then
      echo "'finalize' requires a current -rcN pre-release to promote (got '$current_name')" >&2
      exit 1
    fi
    # core stays put; pre cleared -> stable release
    ;;
esac

new_name="${major}.${minor}.${patch}${pre}"
tag="v${new_name}"

# Rewrite version.properties, preserving the header comments.
tmp="$(mktemp)"
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" == versionName=* ]]; then
    echo "versionName=${new_name}"
  else
    echo "$line"
  fi
done < "$PROPS" > "$tmp"
mv "$tmp" "$PROPS"

echo "$new_name"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "version_name=${new_name}"
    echo "tag=${tag}"
  } >> "$GITHUB_OUTPUT"
fi
