#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

GREEN=$'\033[0;32m'
BLUE=$'\033[0;34m'
NC=$'\033[0m'

echo "${BLUE}[install] setting git core.hooksPath to scripts/git-hooks${NC}"
git config core.hooksPath scripts/git-hooks

echo "${BLUE}[install] granting executable permission to hook files${NC}"
chmod +x scripts/git-hooks/pre-commit
chmod +x scripts/git-hooks/pre-push
chmod +x scripts/git-hooks/commit-msg
chmod +x scripts/git-hooks/lib/*.sh

echo ""
echo "${GREEN}[install] done.${NC}"
echo "  current hooksPath: $(git config --get core.hooksPath)"
echo ""
echo "  verify: stage any file, then"
echo "    bash scripts/git-hooks/pre-commit"
