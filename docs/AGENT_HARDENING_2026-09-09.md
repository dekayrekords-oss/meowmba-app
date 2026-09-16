# Agent hardening audit — September 9, 2026

**1.3.62 / code66**, built after tests passed; not installed.

The user requested remaining issues be fixed and verified before packaging. This audit covers the new verified phone controller and learned-search implementation. It does not claim every pre-existing app behavior or real-device interaction has been verified.

## Reproduced defects and repairs

Six new regression tests were run against 1.3.61 source before fixes. All six failed, demonstrating:

1. A search could be marked complete merely because its app was open.
2. A stale results heading could be reused as evidence for a newly typed query.
3. Typing silently discarded a supplied result expectation.
4. A missing target could match a control whose view ID was also null.
5. Malformed cached recipes could contain early finish actions and no query entry.
6. A role-only editable selector could be learned from a message field and later target another screen.

All six now pass. Search completion requires the resolved installed app, the current query in an identified search field, and query-specific visible evidence or an explicitly observed new result title. Generic headings, editor echoes, and changed clocks are insufficient. New result titles can verify a single planned run but are not generalized into automatic recipes. Typing verifies both the entered value and any supplied expectation. Absent or contradictory targets are rejected.

## Additional repairs

- A coherent, bounded accessibility traversal now supplies prompt text, verification controls, and native dispatch nodes. It supports up to 200 controls and rejects oversized trees instead of treating a truncated set as complete. Protected fields/subtrees are excluded. Hints, roles, enabled/checked/selected state, bounds, classes, and control identities participate in freshness checks.
- Native dispatch uses the nodes from its fresh snapshot, refreshes the selected node, and checks its semantics before performing an action. It does not look up the old index again in a different tree. Rejected native taps stop; there is no automatic unverified coordinate retry in this path.
- Search words inside an existing draft no longer classify that draft as an editable search field. Empty selectors and contradictory numeric targets are rejected.
- Horizontal scroll requests preserve their direction through the gesture path.
- Payment/account/permission screens and destructive control labels retain manual handoff. That handoff cannot be converted into the separate social-send approval path. Refreshed parent controls are checked again for visibility, enablement, and consequential labels.
- Model-call metrics now distinguish actual model calls from planning turns that may use native hints. Step progress updates the caption without speaking over the user.
- Result verification has an eight-second bounded allowance for asynchronous loading and still returns early when the required result is stable; the old 1.5-second deadline could reject legitimate slower results. Planning, cancellation, and whole-run budgets remain enforced.

## Learned recipe safeguards

Recipe format 2 rejects the previous weaker format and malformed steps. Each accepted recipe has exactly one query-entry step, one final result check, supported actions only, bounded fields, and grounded search/navigation selectors. Role-only automatic typing, arbitrary view-ID-only navigation, and silent omission of unsupported steps are rejected.

Every step includes a structural screen signature derived from control roles, IDs, classes, bounds, and a fixed vocabulary of navigation words. Query values and arbitrary result titles are excluded. The signature distinguishes supported screen contexts within the same app and changes in layout. App/version/context mismatches require planning again.

An in-flight replay marker is written atomically before the first replayed action. A new process treats an unfinished marker as suspended and must relearn through verified planning. A verified replay clears the marker; failures suspend the recipe. Settings revocation still prevents a stopped or forgotten recipe from being written back. Parameter replacement remains structured data, never generated executable code.

## Validation order and evidence

1. `regression-before.log`: six reproduced failures before repair.
2. Targeted regression and Android dispatch tests passed, including delayed results, process interruption, stale metadata, more than 50 controls, native typing exactly once, protected fields, conflicting indices, unrelated screen context, and consequential-action handoff.
3. Full `:app:testDebugUnitTest` passed **before** release assembly: **1929 tests, 1924 passed, 5 skipped, zero failures/errors**, 45 seconds. There are 21 new tests in this audit, plus strengthened existing tests.
4. Kotlin/build-file hashes matched the source frozen before full testing. Other source/resources matched the pre-audit snapshot. A full source manifest was checked before and after assembly.
5. Only then ran `:app:assembleDebug`: successful in 10 seconds.
6. Package/version and debug signer verified. All **125 prior native-library/asset entries** remain unchanged relative to 1.3.61. `git diff --check` passed.

APK SHA-256: `caed2b53bf6cf5139c6630dc424d6e00a091b89043661df1d2f2cd8e430b156b`.

Artifacts: `/Users/prog/Meowmba-Releases/agent-hardening-2026-09-09/` contains the APK, test XML/logs, source hash manifests, package/signature checks, source archives, and incremental patch.

No live phone control, microphone/camera capture, model inference, installation, or physical-base operation was performed. Real-device accuracy, loading behavior, and latency remain to be verified. Physical control remains quarantined. LAN SSH fetch timed out, so remote currency is unverified; existing local changes were preserved. Passing this audit is not a guarantee of zero undiscovered bugs.
