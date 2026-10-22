# Play192 preparation — October1, 2026

Based on191 (`5c6bd7c`), isolated branch `release/play-192-20261001`. Version192 /1.5.26 allocated; next193. Original191 source and signed bundle remain immutable.

This candidate removes unsupported location-reminder and learned-place promises from the Play disclosure, Flow setup card and embedded manual, and clarifies on-phone location sample storage versus relevant context sent to a selected cloud AI provider. Optional location feature and permissions are unchanged. No Ring integration, new features, upload, review submission, publication, account permission change or credential change.

Build/test/signing validation passed locally October1. Do not treat this document as release approval. October3 15:09UTC /08:09Arizona is the supplied replacement-key activation time; inspect Console's actual upload certificate before upload.191 was signed with the replacement key locally October1 and verified independently;192 must be signed and verified separately.

Play Console inspected October1: pending key reset; old upload certificate remains displayed; managed publishing off; four changes not sent for review; production draft152 and Alpha152; broad photo/video permission policy issue152 blocks submission across tracks. Need-attention declarations empty for already-uploaded bundles,13 actioned declarations; Data safety ready to send, not approved.192 introduces no extra permissions over191, but191/192 have not reached Console and background-location declaration/evidence remain outstanding.

See `PLAY_RELEASE_192_LOCATION_REVIEW.txt` for narrower draft and evidence needed. No location grant was made by this task. Optional location context is not sufficient proof of a policy-eligible core feature. All affected tracks, listing, Data safety, declarations and Google's checks must be reconciled after an approved upload.

## Completed checks

Full clean build and validation passed October1:104 Gradle tasks executed in29m48s.516 debug suites/3204 cases,0failures/errors,9skips;4 actual release-flag cases,0failures/errors/skips. Release lint0errors,188warnings,12hints. Bundletool validation, JNI exports and16KB ELF alignment passed.94 protected runtime entries match191;757 signed payload entries match192 unsigned. Signed AAB305118305bytes SHA256fdabc624cc22620faf8df5d8fbe7e657a1e4e326d5e2f0a9e5e16ca77fcb06ef. Signer SHA25601:B0:88:88:54:AA:D8:9E:C5:35:43:08:30:28:C5:FB:22:3F:F6:40:C3:33:CE:0D:BA:60:DC:96:B8:BC:FE:AE matches existing replacement public certificate.

Artifact: `/Users/prog/Documents/Codex/2026-10-01/task/artifacts/meow-assistant-1.5.26-code192.aab`. Normal self-signed/trust-chain/absent-timestamp warnings remain; jarsigner verifies the signature.

Native192 device checks were not run; actual optimized191 startup/migration/media boundaries reran successfully in an isolated synthetic emulator. Added no-permission Flow inspection failed a QA harness certificate mismatch; matching original QA key was later identified. No location permissions were granted, no app data was cleared, and the task emulator was shut down. The clone remains recoverable. Background delivery/user-visible benefit remain untested pending explicit permission approval.
