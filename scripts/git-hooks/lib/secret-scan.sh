#!/usr/bin/env bash
set -euo pipefail

RED=$'\033[0;31m'
YELLOW=$'\033[0;33m'
NC=$'\033[0m'

if git rev-parse --verify HEAD >/dev/null 2>&1; then
  DIFF_CMD="git diff --cached --unified=0"
else
  DIFF_CMD="git diff --cached --unified=0 --no-index /dev/null"
fi

PATTERNS=(
  'AKIA[0-9A-Z]{16}'
  'AIza[0-9A-Za-z_\-]{35}'
  '-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----'
  "(password|passwd|pwd)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{6,}['\"]"
  "(secret|api[_-]?key|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{6,}['\"]"
)

FOUND=0
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)

if [ -z "$STAGED_FILES" ]; then
  exit 0
fi

for FILE in $STAGED_FILES; do
  [ -f "$FILE" ] || continue

  case "$FILE" in
    scripts/git-hooks/lib/secret-scan.sh|docs/superpowers/specs/*)
      continue
      ;;
  esac

  for PAT in "${PATTERNS[@]}"; do
    MATCH=$(git diff --cached --unified=0 -- "$FILE" | grep -nE "^\+" | grep -vE "^\+\+\+" | grep -E -- "$PAT" || true)
    if [ -n "$MATCH" ]; then
      echo "${RED}[secret-scan] Secret pattern detected in ${FILE}${NC}"
      echo "  pattern: $PAT"
      echo "$MATCH" | head -5 | sed 's/^/  /'
      FOUND=1
    fi
  done
done

if [ $FOUND -eq 1 ]; then
  echo ""
  echo "${YELLOW}Commit blocked. Move secrets to environment variables or .env.${NC}"
  echo "${YELLOW}If already in history, clean with git filter-repo.${NC}"
  exit 1
fi

exit 0
