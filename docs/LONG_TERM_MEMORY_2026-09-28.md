# Native long-term memory — 2026-09-28

Source integration, not an installed release. Adds full-archive FTS candidate lookup before bounded existing hash-embedding ranking, source-linked conversation scoping, explicit correction and pin controls, and additive Room 14→15 memory policy metadata.

In Memory, Correct replaces the saved record and its search representation while preserving its ID. It does not rewrite original conversations or keep previous corrected text. Pin exempts a record from automatic retention cleanup; manual deletion still works. Correction time and source message ID are visible. Export includes revision/pin metadata; encrypted backup includes the policy table and older schema 12–14 imports remain compatible.

Legacy source-less non-PROJECT memories remain shared inside the local profile. Source-less PROJECT entries are excluded from automatic recall because their project cannot be established. Manual search remains available. Pins do not force irrelevant content into every answer. One call-mode path permits recall with a 250 ms coroutine deadline and rejects stale-conversation results.

No Hindsight service, trained semantic model, history upload, sync, profile authentication, automatic goal/task checkpoint summarizer or public source upload is added. Existing goals and verified work receipts remain authoritative. Historical supersession, backup deletion tombstones and device latency/heat validation remain future work. Do not downgrade a Room 15 database to a schema-14 APK.

Tests: LongTermMemoryTest covers archive retrieval, scoped recall, correction/reindex/deletion, pin retention, input quoting and 14→15 migration. MemoryControlsUiTest exercises visible correction and explicit pin confirmation. UserDataBackupStoreTest covers older backup compatibility. Final run receipts are in the Os workspace `work/memory-integration/validation/`; see its IMPLEMENTATION.md for current status and server rollout limits.
