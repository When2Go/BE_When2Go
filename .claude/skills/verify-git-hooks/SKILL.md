---
name: verify-git-hooks
description: Diagnose and fix git hook failures in this project (pre-commit, commit-msg, pre-push). Use when a commit or push is blocked by a hook, when hooks appear not to run, or when checking whether scripts/git-hooks/install.sh has been applied.
---

# Verify Git Hooks

Confirm this project's git hooks (`scripts/git-hooks/*`) are installed correctly and diagnose failures.

## 1. When to Use

- `git commit` or `git push` blocked by a hook
- Verifying hooks work after a fresh clone
- Locally testing after modifying a hook script
- User asks "why aren't the hooks running?"

## 2. Do Not Use For

- Designing new hook scripts → that's the spec phase
- CI pipeline (GitHub Actions) issues — unrelated to hooks

## 3. Diagnosis Steps

### Step 1. Verify installation
```bash
git config --get core.hooksPath
```
If the result is not `scripts/git-hooks`, guide the user to run:
```bash
bash scripts/git-hooks/install.sh
```

### Step 2. Check executable permission
```bash
ls -l scripts/git-hooks/pre-commit scripts/git-hooks/pre-push scripts/git-hooks/commit-msg
```
If `-x` is missing:
```bash
chmod +x scripts/git-hooks/pre-commit scripts/git-hooks/pre-push scripts/git-hooks/commit-msg
chmod +x scripts/git-hooks/lib/*.sh
```

### Step 3. Map failure message to cause

| Failure Keyword | Cause | Action |
|-----------------|-------|--------|
| `spotlessCheck FAILED` | Java format violation | Run `./gradlew spotlessApply`, recommit |
| `Secret pattern detected` | Secret regex matched | Check file:line, move value to env var / `.env` |
| `Commit message does not match convention` | Bad commit message | Use `type(scope): message`. Allowed types: feat/fix/chore/test/docs/refactor/style/perf/ci/build/revert |
| `test FAILED` (pre-push) | Test failure | Run `./gradlew test --info` to find cause |
| `doc-sync warning` | Functional change without doc change | Warning only — push proceeds. Update related docs if appropriate |

### Step 4. Isolated manual runs

Run each hook independently of the git lifecycle:
```bash
# secret scan only
bash scripts/git-hooks/lib/secret-scan.sh

# commit-msg check only
echo "bad message" | bash scripts/git-hooks/lib/commit-msg-check.sh /dev/stdin

# doc-sync only
bash scripts/git-hooks/lib/doc-sync-check.sh

# full pre-commit
bash scripts/git-hooks/pre-commit

# full pre-push
bash scripts/git-hooks/pre-push
```

## 4. When Bypass Is Required

To temporarily bypass hooks (emergency hotfix, etc.):
```bash
git commit --no-verify
git push --no-verify
```
Do NOT make this routine — it defeats the purpose. Ask the user why they need to bypass and check whether the actual issue is in scope of the spec.

## 5. Anti-Patterns

- Overusing `--no-verify` without diagnosing the failure
- Disabling hooks via `chmod -x`
- Committing `spotlessApply` output without review (mixes large diffs)
- Only removing a secret value without cleaning the commit history (may need `git filter-repo`)
