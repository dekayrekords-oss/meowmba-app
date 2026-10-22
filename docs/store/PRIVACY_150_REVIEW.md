# Privacy and Data safety review — candidate 150

Local preparation only; no policy page or Console declaration was changed.
The historical hosted policy URL returned HTTP 200 during preparation:
https://dekayrekords-oss.github.io/Meowmba/privacy.html
Reachability does not establish accurate or complete policy content.

Before submission, reconcile the live page and Console forms against these actual flows:

- Cloud speech recognition sends recorded audio to the selected transcription service;
  it is independent of cloud chat-provider selection.
- Cloud conversation, image and other media services can receive prompts and relevant
  conversation/context, images, audio or files for the requested operation. Identify
  each advertised service, authentication, purpose and retention/deletion terms.
- Connected Computer/server tools can receive approved task instructions, relevant
  context and files. Distinguish a user-controlled local server from a public provider.
- Support diagnostics are opt-in via Android sharing. They contain build/API,
  memory/thermal state, mode switches and up to five process-exit codes/timestamps.
  They do not read chat, recordings, raw logs, exception descriptions, credentials,
  endpoints or account/device identifiers. The user chooses the destination.
- The Play edition excludes the Accessibility screen-control service and autonomous
  phone-control tools. Remove obsolete claims that this edition provides them.
- Confirm actual permission disclosures, microphone/camera indicators and background
  behavior, local storage/deletion/backup behavior, support contact and age audience.
- Do not say all voice or vision data stays on the phone unless the chosen workflow
  has been verified as entirely local. Do not claim third-party retention guarantees
  or fill Data safety answers without auditing every included SDK/provider.

Historical docs/PRIVACY.md and PLAY_LISTING.md are reference drafts, not current
submission-ready declarations. A complete provider/SDK data inventory and owner
review of the final live policy remain release gates.
