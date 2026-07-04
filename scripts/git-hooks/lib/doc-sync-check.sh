#!/usr/bin/env bash
set -euo pipefail

YELLOW=$'\033[0;33m'
GREEN=$'\033[0;32m'
NC=$'\033[0m'

REMOTE="${1:-origin}"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if git rev-parse --verify "${REMOTE}/${BRANCH}" >/dev/null 2>&1; then
  RANGE="${REMOTE}/${BRANCH}..HEAD"
else
  BASE="$(git merge-base HEAD origin/main 2>/dev/null || echo "")"
  if [ -n "$BASE" ]; then
    RANGE="${BASE}..HEAD"
  else
    RANGE="HEAD~1..HEAD"
  fi
fi

CHANGED=$(git diff --name-only "$RANGE" 2>/dev/null || true)

if [ -z "$CHANGED" ]; then
  exit 0
fi

FUNCTIONAL=$(echo "$CHANGED" | grep -E '^src/main/java/.*/(controller|service|domain)/.*\.java$' || true)
DOCS=$(echo "$CHANGED" | grep -E '^(docs/|README)' || true)

if [ -n "$FUNCTIONAL" ] && [ -z "$DOCS" ]; then
  echo "${YELLOW}[doc-sync] WARNING: functional files changed but no doc changes.${NC}"
  echo ""
  echo "  changed functional files:"
  echo "$FUNCTIONAL" | sed 's/^/    - /'
  echo ""
  echo "  consider updating:"
  echo "    - docs/ADR/ (architecture decision records)"
  echo "    - docs/features/ (feature specs)"
  echo "    - README.md (if overview/run steps changed)"
  echo ""
  echo "${YELLOW}push will proceed (warning only).${NC}"
elif [ -n "$FUNCTIONAL" ] && [ -n "$DOCS" ]; then
  echo "${GREEN}[doc-sync] OK: functional and doc changes are both present.${NC}"
fi

exit 0
