# Build 118 — choose a MaleCNS server in Neural settings

## User request and result

The user wants MaleCNS to run on Meow while allowing another server to connect.
The neural settings now distinguish the already-paired computer from a separate
brain server. The latter uses the same authenticated Desktop Bridge pairing
protocol and must serve the full MaleCNS graph. Selecting the setup option or
typing a payload does not change the running connection.

## What changed

- `NeuralSetupControls.kt` adds `MaleCnsServerUiState` and a compact connection
  section underneath the remote network choice. It presents the selected server,
  its endpoint, Test current server, and explicit pairing choices. New servers
  use a hidden pairing payload followed by Connect & verify model. A saved
  dedicated server has Change brain server; the previous computer connection
  remains available through Use paired computer.
- The pairing payload is the base64url payload printed by the compatible bridge
  in its pairing window (`meowmba-bridge.py --pair`), not JSON or a web address.
  The form explains this and accepts the payload only on an explicit click.
  Authentication and full-graph checking are the manager's responsibility.
- Pairing input uses password semantics/keyboard, has no reveal switch, and stays
  in ordinary Compose `remember`, never `rememberSaveable`, preferences, logs or
  public state. Successful pairing clears the draft. Changing back to the
  existing computer also clears it. Saved-instance restoration does not restore
  a credential. Non-secret server-choice UI can restore normally.
- Busy work disables connection changes and repeat submission. A status line
  reports the actual result using a polite accessibility live region; errors
  retain the previous selected-server information. Test current server refers
  explicitly to the selected connection even while drafting another.
- `NeuralSettingsSection.kt` carries narrow connection callbacks and presentation
  state through to the controls. All existing neural mode, substrate, reflex,
  sensitivity, animation, trace and computer/task controls remain.
- `NeuralInfoPresentation.kt` and `NeuralInfoPanel.kt` no longer assume Meow or the
  desktop pairing is always the remote brain. The monitor takes the selected
  server's pairing state and name; an independently paired brain server can show
  live measured activity without a paired desktop. An unrelated desktop pairing
  cannot authorize unpaired brain activity. Unpaired setup opens Neural settings
  through the new `onSetupBrainServer` callback. Compute timing is labeled Server
  compute per reply.

## Integration contract for Meow

`MaleCnsServerUiState` has `useDedicated`, `serverName`, `endpoint`, `paired`,
`busy`, `message`, and `isError`. It contains no pairing token, fingerprint or
client secret. Map it from the core manager's current selection after operation
completion; the core must only publish a new selection after its authentication
and exact full-model probe both succeed.

`NeuralSettingsSection` callbacks:

- `onPairMaleCnsServer(payload)` authenticates and verifies the candidate.
- `onUsePairedComputerForMaleCns()` returns to the existing desktop connection.
- `onTestMaleCnsServer()` verifies the currently selected server.
- `onPairComputer()` opens the existing desktop connection setup.

`NeuralInfoPanel` takes `remoteServerPaired`, `remoteServerName` and
`onSetupBrainServer`. The last callback should open Neural settings. Legacy
callers remain source compatible because pairing defaults to `desktopPaired`
and the setup callback defaults to the older `onPairComputer` callback.

## Validation prepared

The integration build owner runs these tests; this worker did not build, push
or change the shared ViewModel/state/screen files.

- `MaleCnsServerUiTest`: eight native Compose tests cover preserved default
  connection, explicit Test/Pair actions, hidden payload and trimmed submission,
  verification failure preserving selection, credential loss on saved-instance
  restore, clearing after success, explicit return to the computer, busy
  operation guards, network-specific visibility, and landscape large text.
- `BrainInfoUiTest`: existing eight tests have generic server-copy expectations;
  two new tests cover a dedicated server without desktop pairing and rejection
  of an unrelated desktop pairing, including the new settings callback.
- Native screenshots are written to `app/build/reports/server118/` for the
  paired computer/new-server form, failed pairing with preserved selection, and
  the connected landscape view at 1.4x text. Phone verification is separate.
- `git diff --check` passes for the implementation before handoff.

## Debugging lessons

A displayed server name is saved configuration, not proof of live connectivity.
The UI says Pairing is saved until an explicit test reports success; the Brain
monitor still requires fresh measured remote diagnostics before enabling its
sensory controls. If the monitor says unpaired while a dedicated server works,
check the root wiring passes the manager's selected `paired` flag instead of
`neuralDesktopPaired`. If it shows the wrong host, check both the selected-server
UI snapshot and the transport rebuild after a successful selection change.

Pairing form drafts are intentionally not durable. A rotation/recreation may
require repasting a payload; never solve that by putting the secret into saved
instance state. Keep network errors bounded and free of raw payloads. Failed
pairing must not call a success callback or overwrite the manager's previous
selection. Do not accept arbitrary unauthenticated URLs or weaken TLS to make a
server appear connected. Opening settings and Brain should create no polling
loop or network side effect.


## Final integration result

All 247 focused Android checks and four Python helper checks passed at built
source `d1a544b`. Both signed APKs and all 53 runtime/19 painted assets are
verified. Final previews were inspected. Build 118 is archived on Meow and is
not installed on the disconnected phone. Exact evidence, artifact hashes and
hardware limits are in [the final Meow handoff](MEOW_BUILD_118_PASS_DOWN.md).
