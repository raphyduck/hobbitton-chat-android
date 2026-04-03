---
name: sync-upstream
description: >
  Sync the LibreChat Android client with a newer official LibreChat server release.
  Diffs upstream tags, identifies gaps in the Android app, proposes changes with
  user approval, then implements them. Use when a new LibreChat version is released.
allowed-tools: Bash, Read, Glob, Grep, Write, Edit, Agent, TeamCreate, SendMessage, TaskCreate, TaskUpdate, TaskGet, TaskList
argument-hint: "[target-tag] (optional, defaults to latest stable)"
---

# Sync Upstream

Synchronize the LibreChat Android app with a newer version of the official LibreChat server.
This is a multi-phase, team-based workflow.

You are the **team lead**. You orchestrate at a high level. You NEVER read code, write code,
or grep the codebase yourself. All heavy lifting is delegated to teammates.

## Team Setup

Create an Agent Team with 4 teammates at the start:

| Teammate | Role | Prompt Summary |
|----------|------|----------------|
| **investigator** | Reads upstream diffs, analyzes API/type/UI changes, cross-references with Android codebase | "You are the investigator for a LibreChat Android upstream sync. Your job is to analyze diffs between upstream tags and identify gaps in the Android client. You have read-only responsibilities — never write code." |
| **android-expert** | Technical advisor on Android architecture and best practices. Cross-checks all guidance with online research before advising. | "You are the Android tech lead for a LibreChat Android upstream sync. You provide guidance on Android best practices, Jetpack Compose patterns, Kotlin idioms, and architectural decisions. CRITICAL: You MUST cross-check your knowledge by researching online (WebSearch/WebFetch) before giving advice. Never rely solely on training data — always verify current best practices, especially for Compose, Hilt, Ktor, and Material 3. Return researched guidance to teammates who ask." |
| **implementer** | Writes code changes to Android app following existing patterns and conventions | "You are the implementer for a LibreChat Android upstream sync. Your job is to write code changes following existing architecture patterns. Always read the module's CLAUDE.md before touching it. Follow safeApiCall, Hilt DI, UDF, Ktor, @Serializable patterns. Before making significant architectural decisions, consult the android-expert teammate." |
| **verifier** | Checks implementation correctness, runs builds, validates changes match proposal | "You are the verifier for a LibreChat Android upstream sync. Your job is to independently validate that implementation matches the approved proposal. Run builds, review changes, and flag issues. Message the implementer directly if fixes are needed." |

**Teammate interaction pattern:**
- **investigator** works independently on Phase A and B (diff + gap analysis)
- **android-expert** is consulted by the implementer before architectural decisions and by the
  lead when the proposal involves patterns that may have better modern alternatives. The
  android-expert MUST search online to verify recommendations — do not accept advice that
  hasn't been cross-checked against current documentation.
- **implementer** does all code changes, consulting android-expert for non-trivial patterns
- **verifier** validates after implementer finishes, messages implementer for fixes

**Important:** Teammates retain context across messages. Reuse them for follow-ups — do NOT
spawn new sub-agents (sub-agents lose context). Sub-agents are a last resort fallback only.

## Prerequisites

Run these checks yourself (lightweight, no codebase reading):

### 1. Submodule check
```bash
cd LibreChat-Android && git submodule status
```
If `upstream/` is missing:
```bash
git submodule add https://github.com/danny-avila/LibreChat.git upstream
git submodule update --init
```

### 2. Version file check
```bash
cat UPSTREAM_VERSION
```
If missing, create from current state:
```bash
VERSION=$(grep 'SUPPORTED_BACKEND_VERSION' core/common/src/main/kotlin/com/garfiec/librechat/core/common/BackendVersion.kt | grep -o '"[^"]*"' | tr -d '"')
COMMIT=$(cd upstream && git rev-parse HEAD)
echo "# Upstream LibreChat version this Android build tracks." > UPSTREAM_VERSION
echo "# Updated by the sync-upstream skill. Do not edit manually." >> UPSTREAM_VERSION
echo "tag=v${VERSION}" >> UPSTREAM_VERSION
echo "commit=${COMMIT}" >> UPSTREAM_VERSION
echo "date=$(date +%Y-%m-%d)" >> UPSTREAM_VERSION
```

### 3. Clean working tree
```bash
git status --porcelain
```
If dirty, ask the user to commit or stash before proceeding.

### 4. Context recovery (new session detection)

Check if a prior sync was partially done in another session:
```bash
# Get version from tracking file
TRACKED_TAG=$(grep '^tag=' UPSTREAM_VERSION | cut -d= -f2)

# Get version from BackendVersion.kt
CODE_VERSION=$(grep 'SUPPORTED_BACKEND_VERSION' core/common/src/main/kotlin/com/garfiec/librechat/core/common/BackendVersion.kt | grep -o '"[^"]*"' | tr -d '"')

# Get submodule current position
SUBMODULE_HEAD=$(cd upstream && git rev-parse HEAD)
TRACKED_COMMIT=$(grep '^commit=' UPSTREAM_VERSION | cut -d= -f2)
```

If `TRACKED_TAG` (without `v` prefix) != `CODE_VERSION`, or `SUBMODULE_HEAD` != `TRACKED_COMMIT`:
> "It looks like a sync may have been partially done. UPSTREAM_VERSION says {TRACKED_TAG}
> but BackendVersion.kt says {CODE_VERSION}. Should I treat the higher version as the
> current baseline, or would you like to investigate?"

Wait for user response before continuing.

---

## Phase A: Diff Analysis

**Delegate to: investigator**

### A1. Assign investigation task

Create a task for the investigator with this prompt:

> Analyze upstream changes for a version sync.
>
> 1. Read `UPSTREAM_VERSION` at the repo root to get the current tracked tag and commit.
> 2. Fetch latest tags: `cd upstream && git fetch --tags origin`
> 3. List all tags newer than the current tracked tag:
>    `cd upstream && git tag --sort=-v:refname`
>    Filter OUT any tags containing `-rc`, `-beta`, `-alpha`.
> 4. Generate diffs between the current tag and {target_tag} for these paths
>    (read `${CLAUDE_SKILL_DIR}/reference/upstream-paths.md` for the full list):
>    - `api/server/routes/`
>    - `api/server/controllers/`
>    - `packages/data-provider/src/`
>    - `packages/data-schemas/src/`
>    - `client/src/components/`
>    - `client/src/hooks/`
>    - `client/src/store/`
> 5. Categorize all changes into:
>    - **API changes**: New/modified/removed routes, controller logic
>    - **Type/schema changes**: New/modified data types, request/response shapes
>    - **UI changes**: New components, modified user flows
>    - **Bug fixes**: Fixes that may need Android equivalents
>    - **Infrastructure**: Build, config, deps (usually not relevant)
> 6. Report back a structured summary table.

If `$ARGUMENTS` was provided, use it as the target tag.

### A2. Present results to user

Once investigator reports back:
- If no newer tags exist, tell user "Already up to date with {current_tag}" and stop.
- If no `$ARGUMENTS`, present the list of available tags and ask user to pick one (default: latest stable).
- Show the investigator's categorized summary.

**Done when:** User has seen the diff summary and confirmed the target tag.

---

## Phase B: Gap Analysis

**Delegate to: investigator** (reuses context from Phase A — do NOT spawn a new agent)

### B1. Send follow-up to investigator

> Now cross-reference the upstream changes you found with the Android codebase.
>
> 1. Read these mapping files from `${CLAUDE_SKILL_DIR}/reference/`:
>    - `api-mapping.md` — official routes to Android *Api.kt files
>    - `model-mapping.md` — official TS types to Kotlin data classes
>    - `ui-mapping.md` — web components to Android feature modules
> 2. For each API change from Phase A:
>    - Grep the Android codebase (`core/network/src/`) for the corresponding endpoint
>    - Note if it exists, needs updating, or is missing
> 3. For each type/schema change:
>    - Check `core/model/src/` for matching Kotlin data class
>    - Note new fields, removed fields, type changes
> 4. For each UI change:
>    - Check the corresponding `feature/*/` module
>    - Note if the feature exists, needs updating, or is missing
> 5. Categorize all gaps:
>    - **Breaking** (must fix): Changed request/response shapes, removed/renamed endpoints, auth changes
>    - **Additive** (new feature): New endpoints, new UI features, new optional fields
>    - **Cosmetic** (polish): UI improvements, a11y, i18n
> 6. Report the categorized gap list with specific file paths for both upstream and Android.

### B2. Receive gap report

The investigator now has full context of both the upstream diff AND the Android gaps.
Keep the investigator alive for follow-up questions during Phase C.

**Done when:** You have the categorized gap list from the investigator.

---

## Phase C: Proposal

**Lead presents to user — no delegation needed.**

Take the investigator's gap report and present it as a structured proposal:

```
## Sync Proposal: {current_tag} → {target_tag}

### Breaking Changes ({count}) — must fix before updating version
For each:
- What changed upstream (with file reference)
- What Android file(s) need updating
- Proposed change

### New Features ({count}) — recommended
For each:
- What was added upstream
- Proposed Android implementation approach
- Which module(s) would be affected

### UI Changes Needing User Input ({count})
For each:
- What the web app does (with file reference)
- Why it doesn't translate directly to Android/Compose
- 2-3 concrete options for the user to choose from

### Deferred Items ({count}) — can wait for a future sync
Items that are low priority or require significant new infrastructure.
```

### Consult android-expert on non-trivial items

Before presenting to the user, for any proposed change that involves:
- New architectural patterns (e.g., adding a new module, new DI scope)
- UI patterns that don't have an existing equivalent in the codebase
- Performance-sensitive changes (e.g., new list rendering, image loading)
- Navigation changes

Send these items to the **android-expert** teammate:

> Review these proposed Android changes for best practices. For each item,
> research online to verify the recommended approach is current (especially
> for Compose, Material 3, Hilt, and Ktor). Flag any items where modern
> best practices differ from what we're proposing.
> {list of non-trivial items}

Incorporate the android-expert's feedback into the proposal before showing the user.

### Approval gate

Ask the user:

> Review the proposal above. Reply with:
> - **approve all** — proceed with full implementation
> - **approve with changes** — tell me what to modify in the proposal
> - **approve partial** — list which items to implement now (rest deferred)
> - **cancel** — abort, no changes made

**Do NOT proceed to Phase D until the user explicitly approves.**

If the user requests changes, update the proposal and re-present.

---

## Phase D: Implementation

**Delegate to: implementer, then verifier**

### D1. Assign implementation task

Create a task for the implementer with the approved change list:

> Implement the following approved changes for the upstream sync from {current_tag} to {target_tag}.
>
> **Before touching any module:**
> 1. Read the root `CLAUDE.md` for architecture rules
> 2. Read `${CLAUDE_SKILL_DIR}/reference/android-architecture.md` for patterns
> 3. Read the specific module's `CLAUDE.md` (e.g., `core/network/CLAUDE.md`)
> 4. Read existing similar code as a pattern reference
>
> **Conventions to follow:**
> - `safeApiCall` for all network calls in repositories
> - Hilt `@Inject` for dependency injection
> - Unidirectional data flow: UI → ViewModel → Repository → API/Room
> - `@Serializable` data classes in `core/model/`
> - Ktor client patterns in `core/network/`
>
> **Before making non-trivial architectural decisions** (new patterns, new modules,
> complex UI components), message the **android-expert** teammate for guidance.
> The android-expert will research current best practices online and advise.
>
> **Approved changes:**
> {paste the approved change list here}
>
> **After all code changes:**
> 1. Update `SUPPORTED_BACKEND_VERSION` in `core/common/src/main/kotlin/com/garfiec/librechat/core/common/BackendVersion.kt` to "{new_version}" (without v prefix)
> 2. Advance submodule: `cd upstream && git checkout {target_tag}`
> 3. Update `UPSTREAM_VERSION` at repo root:
>    ```
>    tag={target_tag}
>    commit={full SHA of target_tag}
>    date={today's date YYYY-MM-DD}
>    ```
> 4. Report back the full list of files you created or modified.

### D2. Assign verification task

After implementer reports done, create a task for the verifier:

> Verify the upstream sync implementation from {current_tag} to {target_tag}.
>
> 1. Run `./gradlew assembleDebug` to verify the project compiles
> 2. Review all files the implementer changed against the approved proposal:
>    - Are all approved changes implemented?
>    - Are there any changes that weren't in the proposal?
>    - Do the changes follow existing code patterns?
> 3. Check version consistency:
>    - `SUPPORTED_BACKEND_VERSION` in BackendVersion.kt matches target
>    - `UPSTREAM_VERSION` file has correct tag, commit, date
>    - Submodule is at the correct tag: `cd upstream && git describe --tags`
> 4. If you find issues, message the **implementer** directly to fix them.
>    The implementer retains context of what it changed.
> 5. Report back:
>    - Build status (pass/fail + any errors)
>    - Issues found (if any)
>    - List of user-testable behaviors to verify on device

### D3. Present results to user

Once verifier confirms everything passes:

```
## Sync Complete: {current_tag} → {target_tag}

### Files Changed
{grouped by module}

### What to Test
{specific user-facing behaviors from verifier}

### How to Test
1. Build: `./gradlew assembleDebug`
2. Install on device/emulator
3. Test each behavior listed above

### Version Updates
- SUPPORTED_BACKEND_VERSION: {old} → {new}
- UPSTREAM_VERSION tag: {old_tag} → {new_tag}
- Submodule: pinned to {target_tag}
```

---

## Error Recovery

| Situation | Recovery |
|-----------|----------|
| Submodule missing | `git submodule add https://github.com/danny-avila/LibreChat.git upstream` |
| `UPSTREAM_VERSION` missing | Create from `SUPPORTED_BACKEND_VERSION` + submodule HEAD |
| No newer tags | Report "up to date" and stop |
| Dirty working tree | Ask user to commit/stash |
| Git fetch fails | Show `git remote -v`, check network |
| Build fails after changes | Verifier messages implementer to fix (both retain context) |
| User cancels at Phase C | Stop cleanly, no changes made |
| Version mismatch | Ask user which is the true baseline |
| Teammate unresponsive | Check task status, re-send message; sub-agent as last resort |
