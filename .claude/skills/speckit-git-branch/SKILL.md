---
name: "speckit-git-branch"
description: "Local project hook (not an official Spec Kit extension): create and switch to a new git branch matching the new feature's directory name, before spec.md is written."
argument-hint: "Feature description, forwarded unchanged from /speckit-specify"
compatibility: "Requires spec-kit project structure with .specify/ directory and a git repository"
metadata:
  author: "project-local"
  source: "hand-authored for dental-clinic-app; not part of the upstream github/spec-kit distribution"
user-invocable: false
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

This is the same feature description text passed to `/speckit-specify`. It is used only to
compute the branch name — this command never writes `spec.md` or touches `specs/`; that stays the
job of the core `speckit-specify` command, per its own step 3.

## Purpose

Wired as a **mandatory `before_specify` hook** in `.specify/extensions.yml` so that every new
feature spec starts life on its own git branch, named identically to the feature directory
(`NNN-short-name`), instead of landing on whatever branch happened to be checked out. This mirrors
the naming convention already used by `001-staff-auth-rbac`.

## Execution

1. **Safety check — refuse to run over dirty tracked state**:
   ```bash
   git status --short
   ```
   If this shows any tracked file with a staged or unstaged modification (lines starting with
   anything other than `??`), STOP and report to the user: uncommitted work exists, commit or
   stash it first, no branch was created. Untracked files (`??`) do not block this — they carry
   over to the new branch unharmed.

2. **Compute the branch name using the project's own numbering logic, without side effects**:
   ```bash
   .specify/scripts/bash/create-new-feature.sh --json --dry-run "$ARGUMENTS"
   ```
   This reuses the exact sequential/timestamp numbering and short-name derivation that
   `speckit-specify`'s own directory creation (step 3) will independently arrive at, so the branch
   name and the feature directory name coincide in the common case — without this script's
   `--dry-run` mode touching the filesystem. Parse `BRANCH_NAME` and `FEATURE_NUM` from its JSON
   output.

3. **Resolve the base ref to branch from**, in this priority order:
   - `origin/main` if the remote ref exists and is fetchable (`git rev-parse --verify --quiet origin/main`)
   - else local `main` if it exists (`git rev-parse --verify --quiet main`) — warn the user it may
     be stale relative to `origin/main`
   - else the current `HEAD` — warn the user no `main` was found, branching from the current
     branch instead

4. **Create and switch to the branch**:
   - If a local branch named `BRANCH_NAME` already exists, just switch to it (`git checkout
     BRANCH_NAME`) — idempotent, don't error.
   - Otherwise: `git checkout -b BRANCH_NAME --no-track <resolved base ref>`. The `--no-track` is
     load-bearing: without it, checking out from a remote-tracking ref like `origin/main` makes
     git silently set the new branch's upstream to `origin/main` (depending on
     `branch.autoSetupMerge`), so a later plain `git push` would push this feature branch's
     content straight into `main` — exactly the direct-to-main path the constitution's PR-only
     workflow gate forbids. Verify afterwards with `git status --short --branch`: the first line
     must show just `## BRANCH_NAME`, no `...origin/...`. If it shows tracking anyway, run
     `git branch --unset-upstream` immediately.
   - Do not push the branch. Pushing is a separate, explicit action the user asks for later (e.g.
     when opening a PR).

5. **Report and emit the JSON contract `speckit-specify` expects** (its Outline step 2 reads
   `BRANCH_NAME` and `FEATURE_NUM` from this hook's output):
   ```json
   {"BRANCH_NAME": "<computed>", "FEATURE_NUM": "<computed>"}
   ```
   Also tell the user in plain text which branch was created/switched to and from which base ref.

## Done When

- [ ] Working tree confirmed clean of tracked modifications (untracked files ignored)
- [ ] Branch name computed via `create-new-feature.sh --dry-run`, matching the numbering
      `speckit-specify` will use for the feature directory
- [ ] Base ref resolved (`origin/main` preferred) and branch created or switched to
- [ ] `{BRANCH_NAME, FEATURE_NUM}` reported back for the calling `speckit-specify` step
