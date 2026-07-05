#!/usr/bin/env bash
set -euo pipefail

RED=$'\033[0;31m'
YELLOW=$'\033[0;33m'
NC=$'\033[0m'

MSG_FILE="${1:-}"
if [ -z "$MSG_FILE" ] || [ ! -f "$MSG_FILE" ]; then
  echo "${RED}[commit-msg] Missing message file path.${NC}"
  exit 1
fi

FIRST_LINE=$(sed -n '1p' "$MSG_FILE" | tr -d '\r')

if [[ "$FIRST_LINE" =~ ^(Merge|Revert|fixup!|squash!) ]]; then
  exit 0
fi

PATTERN='^(feat|fix|chore|test|docs|refactor|style|perf|ci|build|revert)(\([^)]+\))?: .+'

if ! [[ "$FIRST_LINE" =~ $PATTERN ]]; then
  echo "${RED}[commit-msg] Commit message does not match convention.${NC}"
  echo ""
  echo "  current message: ${FIRST_LINE}"
  echo ""
  echo "${YELLOW}Allowed format: type(scope): message${NC}"
  echo "  type: feat | fix | chore | test | docs | refactor | style | perf | ci | build | revert"
  echo ""
  echo "  examples:"
  echo "    feat(trip): add route search API"
  echo "    fix(notification): handle FCM token expiry"
  echo "    chore: bump dependencies"
  exit 1
fi

exit 0
