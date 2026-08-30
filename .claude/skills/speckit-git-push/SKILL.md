---
name: "speckit-git-push"
description: "Local project hook (not an official Spec Kit extension): commit and push the current feature branch to origin after a User Story's implementation checkpoint passes."
argument-hint: "Completed User Story id and title (e.g. 'US2 - Lekarz odczytuje pełny obraz uzębienia z jednego widoku')"
compatibility: "Requires spec-kit project structure with .specify/ directory and a git repository with an 'origin' remote"
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

This is the id and title of the User Story phase that was just completed, forwarded from
`/speckit-implement`'s per-user-story hook (e.g. `US1 - Lekarz odnotowuje rozpoznanie na
konkretnej powierzchni zęba`). Used only to build the commit message — this command never touches
tasks.md itself.

## Purpose

Wired as a mandatory `after_user_story` hook in `.specify/extensions.yml` so that every User Story
phase, once its `tasks.md` Checkpoint passes, gets committed and pushed to `origin` on the current
feature branch — keeping remote state (and CI) incrementally in sync through a feature's
implementation instead of one large push at the end. Pairs with the existing `before_specify`
`speckit-git-branch` hook, which already guarantees every feature lives on its own branch.

## Execution

1. **Safety checks — refuse to run in situations this hook isn't meant for**:
   - `git rev-parse --abbrev-ref HEAD` — if the current branch is `main` or `master`, STOP and
     report to the user without pushing. This hook must never push directly to a protected branch;
     that would bypass the constitution's PR-only workflow gate. This should not normally happen,
     since `speckit-git-branch` already puts every feature on its own branch before `spec.md` is
     written.
   - `git remote get-url origin` — if there is no `origin` remote, skip with a clear message: no
     push target configured.

2. **Stage and commit whatever the just-completed User Story produced**:
   - Run `git status --short` to see what changed.
   - If the tree is clean, skip straight to step 3 (there may still be earlier local commits not
     yet on origin).
   - Stage the paths the story actually touched, per `tasks.md`'s file list for that story, rather
     than a blanket `git add -A` — so unrelated in-progress or scratch files aren't swept in. If
     unsure which paths belong, review `git status --short` first and exclude anything that looks
     like a stray build artifact, generated test output, or a potential secret.
   - Commit using this project's existing convention (see `git log --oneline -10` for the current
     feature — style is `feat(<feature-number>): implement <story-id/description>`), and end the
     message with:
     ```
     Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
     ```
   - Never pass `--no-verify`. If a pre-commit hook fails, fix the underlying issue and commit
     again — do not bypass it.

3. **Push the current branch to origin**:
   - If the branch already has an upstream: `git push origin HEAD`.
   - If it doesn't yet: `git push -u origin HEAD`.
   - Never force-push. If the push is rejected (remote has diverged — e.g. another session pushed
     in the meantime), STOP and report to the user instead of force-pushing; this is shared remote
     state and force-pushing could overwrite someone else's work.

4. **Report** to the user: which User Story was committed/pushed, the commit hash (if one was
   made), and confirmation the branch is up to date with `origin` — or, if a step stopped early,
   why.

## Constitution note

This hook only ever pushes the feature branch, never `main`, so it does not touch the PR-only
merge gate or the risk-tiered security/compliance review requirement for changes touching patient
data, auth, authz, or audit logging — those still apply in full at merge time, unaffected by this
automation. It also does not open or modify any pull request.

## Done When

- [ ] Confirmed the current branch is not `main`/`master` and that an `origin` remote exists
- [ ] The completed story's changes are committed, or the tree was already clean
- [ ] The branch is pushed to `origin` without force, or a clear reason is given for why it wasn't
- [ ] User told the outcome
