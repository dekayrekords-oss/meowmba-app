# Long-term memory integration — 2026-09-28

Status: native integration implemented locally; final Android validation in progress. Not deployed to the AIOS server or installed on a device. No Hindsight service, paid model, personal-history upload, or cross-device sync was added.

## What changed

### Meowmba

- Automatic recall now searches the complete saved-memory FTS archive before bounded ranking (AND/OR candidates plus recent/pinned candidates, at most 200). This removes the previous hard latest-200 eligibility cutoff. Existing on-device hash embeddings remain; this is not a trained semantic vector index.
- Automatic recall separates source-linked conversation records. Source-less legacy non-project records remain local-profile shared memories; source-less PROJECT records are excluded from automatic recall rather than guessed into a project. Manual search still shows the user's store.
- Memory screen adds **Correct**, **Pin / Unpin**, correction date and source-message ID. A correction replaces saved text and rebuilds its search representation, retaining the record ID. Original conversations are unchanged. Prior corrected text is not versioned.
- Explicit pins survive automatic retention cleanup. Deleting or clearing memories still removes pins. A pin is a retention choice, not a guarantee that every answer includes the record.
- Additive Room 14→15 migration creates `memory_policies`; existing memory rows and keys are retained. Encrypted data backup includes policy metadata; older schema 12–14 imports remain compatible. JSON memory export includes policy metadata.
- One call-mode reply path now permits local recall with a 250 ms coroutine deadline. This is a deadline, not a measured phone latency claim. Stale conversation replies are discarded after retrieval.

### AIOS server

- Additive SQLite FTS5 index searches all saved memory before bounded lexical/optional local embedding ranking. Existing records are indexed in place.
- Explicit project IDs scope saved memories and completed chat-history retrieval. Chats with the same project ID can retrieve older relevant messages across sessions. Blank chat project IDs do not share conversation history with unrelated chats.
- A user-authored pinned project checkpoint can seed a new conversation even for a query such as “continue.” It is labelled as a saved account, not proof that a task succeeded. The UI advises maintaining one current checkpoint and unpinning obsolete ones.
- Saved-memory correction updates the index and invalidates stale embeddings. Pin/correction controls and revision labels are available in Memory.
- Retrieved context is bounded, includes source IDs, and is labelled untrusted historical data. Chat still cannot execute tools or claim actions ran.
- API-backed chat excludes retrieved memory by default. A per-conversation checkbox enables it; switching providers resets the choice. Local Ollama chat can use local memory. This controls additional retrieved memory, not the messages the user submits to an API chat.
- This remains the existing single-admin server. Project IDs organize access inside that account; they are not multiuser authorization boundaries.

## Use

On Android, open the existing Memory screen. Search old records; choose Correct to replace a saved fact or Pin to exempt it from automatic cleanup. Encrypted backup remains the recovery route; export is an inspectable JSON copy.

On AIOS, open Memory, enter a project ID such as `aios`, and save a checkpoint containing the objective, decisions, verified outcomes, blockers, next action and source references. Select Project checkpoint and pin it. In each relevant chat, open Rename / project and use the same project ID. Leave API sharing unchecked unless you want retrieved records sent to that selected provider.

## Verification and rollout limits

- Server: 35 Python tests pass, including old-record lookup behind 10,000 newer records, project separation, correction/reindex/delete, streaming exclusion, pinned checkpoint continuity, API opt-in and provider-change reset. One dependency deprecation warning.
- Browser DOM suite passes: ten routes/six themes plus memory controls, project controls and API sharing off by default. Real local browser preview verified correction persistence and project checkpoint selection using synthetic data; see `validation/memory-ui.png`.
- Android results will be recorded after the final suite. The initial full run caught the CompanionState JVM slot limit and old migration-test registration; both were fixed. A subsequent run hit Mockito dynamic-agent attachment failure, so validation uses the installed Mockito jar as an explicit test JVM agent via `validation/mockito-agent.init.gradle`.
- SSH to `meow@192.168.1.151` timed out. Deployed source/data were not inspected or modified. Reconcile the deployed revision and back up SQLite before applying local server files.
- Claude's apartment branch through `8caa4df` was merged into `feature/meow-flow-core` by `9a24294`. Build 186 is recorded as installed; 187 / 1.5.21 is reserved and unvalidated. No build 187 APK has been released or installed.

## Not yet delivered from the wider plan

Historical supersession chains, deletion tombstones across backup/sync, automatic structured checkpoints sourced from Git/goal/task receipts, full project-file indexing, cross-device identity and sync, and the optional Hindsight/TypeSafe evaluation remain separate follow-up work. No 60-case held-out accuracy, 100k-record, physical-phone latency, heat or battery benchmark has been completed. Paraphrase-only old memories can still miss the lexical candidate stage. Deleted saved text can remain in original conversations or prior backups; this release does not promise “forget everywhere.”

Do not downgrade a Room 15 database to an older APK. Use a forward-compatible fix or an explicitly restored pre-upgrade backup. Hindsight is not a prerequisite for these native changes.

## Cloud handoff

The Android implementation is committed in `534351e`, followed by the merge preserving Claude’s latest work in `9a24294`. Continue from `feature/meow-flow-core`, not GitHub’s historical vc133 `main`. Do not implement these changes a second time.

The final full Android validation run was interrupted and has no successful completion receipt. Earlier focused memory tests passed; final tests must be rerun against the merged source, including MemoryControlsUiTest and the database upgrade. Build 187 remains work in progress. No installation is authorized by this synchronization alone. The pre-existing untracked `app/kws.tar.bz2.part` download is not part of the change.

AIOS and Meowmba are separate products. AIOS source/test results described above live in the Os workspace, not this Android repository. Meowmba’s native memory changes do not require AIOS, and no memory-store synchronization was added. Local validation logs referenced above are not included in this GitHub handoff.
