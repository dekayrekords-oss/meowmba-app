# Meow Assistant
## User manual

Version 1.3.96 | Build 152 | Updated September 19, 2026

An animated companion for conversations, creative work and approved everyday tasks.

This edition replaces the September 15 build-124 manual. It covers the current setup,
settings, model connections, companion controls, Computer tasks and troubleshooting.
The Google Play package is `com.meow.meowmba`. The separate private preview package is
`com.example.meowmba`. They can coexist and keep separate data.

Build 152 and its updated store description have been submitted to the Alpha closed
test. Google review and rollout determine when testers receive the update. This is
not a production-release announcement.

The Play edition opens apps and prepares drafts. It does not provide autonomous
Accessibility control of other apps' screens or replay learned screen-navigation steps.
Optional Computer tasks require their own configured connection and approvals.

This is a text-first guide checked against the build-152 source and recorded release
tests. It does not claim that every provider, accessory or workflow has been retested.
Old build-124 screenshots have been replaced by current instructions, not relabelled
as screenshots of the new build.

---
# Start here
## Your first five minutes

1. Install or update from the existing closed test using your approved Google account.
2. Choose a companion and keep the suggested screen fit, or adjust it.
3. Leave microphone and camera off while you explore. Text interaction is available
   without granting those sensor permissions.
4. Open LLM and configure one provider or download a compatible local chat model.
   Save the configuration and run the connection check before relying on it.
5. Try a simple message, then an app-opening request such as "Open Chrome". Review
   what actually happens; an AI reply alone is not proof that a task ran.
6. Open Avatar or Background and try the new scenes. Use Help & feedback if something
   fails or feels confusing.

## Find the right section

- Installation and updates; first-run setup.
- Companion, navigation and care; voice controls.
- AI providers, keys and local downloads.
- Avatar creation, backgrounds and generated media.
- Computer setup, tasks, results and files.
- App launching, projects, agents and integrations.
- Settings reference, neural options and remote connections.
- Driving, privacy, diagnostics and troubleshooting.
- Build-152 changes and a tester checklist.

Each topic starts on a new page. PDF bookmarks provide direct navigation.

## A feature may need more setup

An installed app, model-catalog entry or saved server address does not establish a
working connection. Check the feature's status and test it with a small, non-sensitive
request. Cloud services can have usage charges and availability limits. A local chat
model does not automatically make transcription, image generation or other tools local.

---
# Installation and updates
## Google Play closed test

Use the same Google account that has access to the existing test. If needed, join the
beta group, then open the Play opt-in page and follow its enrollment instructions.
Group membership and Play opt-in are separate steps. Update through Google Play when
the release becomes available; a submitted release may still be awaiting review.

- Beta group: https://groups.google.com/g/meow-assistant-beta
- Play opt-in: https://play.google.com/apps/testing/com.meow.meowmba
- Feature information: https://www.dekayrecords.com/meow-assistant.html

The current release requires Android 12 or newer and a supported 64-bit ARM device.
Local models have additional memory and storage requirements. Read the size and
requirements shown for the particular download rather than assuming one RAM minimum
works for every model.

## Keep your existing data

Update an existing installation in place. Do not uninstall or clear storage as a
routine fix for an update error: that can remove conversations, saved files, settings
and credentials. If Android refuses an update, record the error and check the package,
version and signing identity with support. A matching signature is required for an
in-place APK update. A lower version can also be rejected.

The private preview and Play app are separate installations. Updating one does not
update the other or move its data. Check the app's version in Settings > Help & feedback
before reporting a problem. Keep using the edition you intended to update.

## Owner-provided APKs

If you are deliberately testing a signed APK supplied by the owner, use the intended
package and matching signing identity. Android may ask you to allow that installer
source. Follow Android's own prompts; do not replace an unrelated installation or
disable security protections to force an update. Play testers normally use Play.

---
# First-run setup
## Choose the companion before connecting services

The current wizard starts with a feature tour, explicit companion selection and screen
fitting. It is different from the old twelve-step build-124 walkthrough.

1. Read the feature tour and choose your look or character.
2. On Fitted to your screen, keep the suggested size or choose Adjust size. View at full
   size shows the result. Auto-fit again restores the fitted size. Large sizes can crop
   moving features; rotating or unfolding changes the available space.
3. Set up your phone Computer if you want Linux/Computer features, or choose Set up
   Computer later. Chat does not require Termux.
4. Choose whether to grant microphone and camera access. Not now leaves both off.
5. Connect your AI, or choose Set up AI later. Local and cloud choices are available;
   there is no requirement to use one particular cloud provider.
6. Optionally link GitHub, choose a background and configure a remote connection.
   Skip optional connections you do not need.

## Sensor choices persist

Fresh setup starts with microphone off. Not now, Finish later and Android permission
denial leave microphone/camera off, including after restart. These paths were checked
on the signed build. Existing saved choices are preserved during an upgrade; updating
does not deliberately reset every user's settings to the fresh-install defaults.

You can change your choices later with the app's controls and Android permissions.
Granting an Android permission is separate from selecting the operating mode you want.
Check the visible MIC/CAM status rather than inferring it from an avatar animation.

Finish setup / Start setup in Settings reopens onboarding. Finishing the wizard does
not certify that a model, Ubuntu environment, server or accessory is connected.

---
# Companion and navigation
## The avatar is the home view

Tap the background or swipe down to open the navigation cards. Use Chat for the
conversation and return to Avatar when you want the companion view. Collapse chat to
give the character more room; expand it to read the conversation and task results.

Avatar controls vary with the chosen form. Where the face zones are available, ears
control the microphone, eyes control the camera, the mouth controls speech output,
and the forehead opens Settings. Use the labelled controls and visible privacy status
if a custom form makes a hit zone unclear. Muting speech output is different from
turning the microphone off.

## Common destinations

- LLM: providers, local models, downloads and connection checks.
- Avatar and Background: character setup, size, behavior, animations and environments.
- Computer: configured workspaces, task controls and terminal connections.
- Files: imported files and saved results. A result in a remote workspace may still
  need Save & preview before a verified local copy appears here.
- Memory and Notes: saved information and notes. Review recalled facts for accuracy.
- Projects, Business and Agents: work organization and configured specialist workflows.
- Plugins: supported integrations and setup. A catalog card is not a working account.
- Privacy and Settings: modes, permissions, connections and support.
- NAV, Weather and Auto-related views: location-based help and regional information.

Names and available destinations can differ between editions or configurations.
If a tile reports unavailable, follow its stated dependency instead of assuming the
feature is enabled because the tile exists. Robot actuation remains unavailable.

## Approvals and actual results

Read requested actions before approving them. A chat sentence saying "done" is not
the same as a task receipt or saved file. Check the task result, preview or destination.
Stop and correct the request when the action does not match what you intended.

---
# Voice and listening
## Start with an intentional voice test

Open Settings > Voice & behavior > Voice recognition & voice models. Check that the
chosen speech engine is ready and any model download is complete. Grant microphone
permission when you choose to use voice, turn the mic on, and test a short phrase.
Start in a quiet room before testing noise, Bluetooth audio or background use.

The companion name can affect the wake phrase. Follow the phrase displayed by your
current settings rather than assuming every engine supports every custom phrase.
Text chat remains a useful fallback when a speech model or provider is unavailable.

## Modes have different effects

- Wake phrase: waits for the configured trigger while the required listening mode runs.
- Always-on mic in face mode: listens while the companion view is active when enabled.
- Background wake phrase: uses a foreground-service notification and can increase power use.
- Conversation mode and barge-in: permit follow-up speech or interruption after a reply.
- Idle chatter: allows occasional unsolicited companion speech when enabled.
- Keep screen on in face mode: prevents normal display sleep and increases power use.

Enable only the modes you intend to use. Turning the mic off and muting the companion's
voice are separate controls. Check Android's sensor indicator and the app's mode status.
Screen-awareness features require their own supported setup and system consent; they
are not equivalent to unrestricted control of other apps.

## If voice stops or interrupts incorrectly

Check permission, microphone mode, selected input device, engine readiness and network
requirements. Cancel a pending response and retry with a short request. Build 152
includes the earlier fixes for idle-speech thread handling and cloud-audio cancellation.
Report reproducible failures with the engine/provider and interruption steps. Noisy-room
and provider-specific acceptance remains ongoing; successful automated tests do not
guarantee recognition quality on every phone or headset.

---
# Connect an AI provider
## Configure one working route first

Open LLM, or Settings > AI & models > Open LLM settings. Choose a provider supported
by the app, or use On this phone for a compatible local model. Cloud-provider cards
include OpenAI, Claude/Anthropic, OpenRouter, OpenCode Zen and Custom API.

1. Open the selected provider's in-app API-key link. Check that you are signing into
   the intended provider before creating a key.
2. Create the credential in that provider's account and return to the app. Copy the
   full secret into the matching key field; do not paste it into chat or feedback.
3. Choose a model available to that account. If necessary, enter the supported endpoint
   or API format under the card's advanced settings.
4. Run the connection test with a non-sensitive prompt and read the result.
5. Press Save & use followed by the provider name. Viewing a card or changing its
   draft fields does not save it. A successful test can still ask you to save.
6. Confirm the active provider/model and send one ordinary chat message.

## Routing is a choice

If more than one provider is configured, check the active/default brain. Automatic
routing and fallback can change which service receives a request. Select an explicit
provider when that matters. Vision has separate model settings: use Save vision settings
after editing them, and verify that the selected model actually supports images.

OpenCode Zen may also appear under the older BigPickle name. Use the model list and
current provider terms; do not assume an old default model or a label containing
"free" guarantees today's availability or unlimited use.

API billing can be separate from a chat subscription. This manual does not promise a
free tier or quote prices. Inspect the provider's current account limits and billing
before use. A model-catalog entry is not proof of a successful authenticated request.

---
# Keys and service setup
## Match the credential to the feature

- OpenAI, Claude/Anthropic, OpenRouter and OpenCode Zen: cloud chat and supported model
  features. Use each provider's card in LLM; a key from one provider is not interchangeable
  with another provider's account.
- Custom API: an endpoint you intentionally configure. Match its supported API format,
  model identifier and authentication. Do not assume all compatible servers implement
  vision, tool calls, streaming and audio in the same way.
- Hugging Face: downloads that require authentication or gated-model access. A token
  does not replace accepting the model's access/license requirements with that account.
- Geoapify: supported nearby-place search in NAV. Follow the NAV key/setup action.
- Cloudflare: the image-generation option's account/token setup. Follow the permissions
  requested by the actual image feature, not a broad unrelated account token.
- AI Horde: community image generation. It is a remote service even when a workflow
  works without an account. Prompts leave the phone and queues can be slow.
- GitHub: optional project/repository access. Review the repositories and permissions
  you grant. There is no separate universal "private" token scope; token type and
  repository access determine what can be reached.

## GitHub is optional

During setup you can link an existing repository or request a new private repository.
Read the destination and scope before confirming. Skip this step if you do not need it.
Connecting GitHub does not itself publish all your app files. Review every proposed
upload and keep private implementation and secrets out of public destinations.

## Secrets stay out of support reports

Stored keys and connection passwords use the app's protected local storage. They still
must be sent to the intended service when required for authentication. A Show key or
copy action exposes the credential on screen or the clipboard. Hide it before sharing
screenshots. If a key is disclosed, revoke it through the provider and replace the
saved value. Clearing app data or uninstalling can remove local credentials.

---
# Local models and downloads
## A downloaded file must match its runtime

Use LLM and the voice/image setup pages to inspect supported models and downloads.
Read the model's size, runtime and device requirements before starting. Different
features can need different models: a chat model is not a speech recognizer or an
image-generation model.

1. Check free storage, memory and network availability.
2. Select a model supported by that feature and device. Complete any gated-model
   authorization using the account that owns the token.
3. Keep the download active until it finishes. Use Retry for a reported failure rather
   than repeatedly starting duplicate downloads.
4. Confirm the model is installed/ready and select it for the intended feature.
5. Test a small request. A present file does not prove the runtime can load it.

## Local does not mean every connected feature is offline

A local chat model can answer without a cloud chat service after installation, but
search, navigation, remote transcription, images and Computer connections may still
need a network. Configure those routes independently. Use a deliberately offline test
if you need to establish which of your chosen workflows remains available offline.

## Native compatibility

The current release packages 64-bit ARM native libraries and includes 16 KB alignment
checks. Its bridges use a baseline ARMv8-A build, and the Needle runtime checks a CPU
capability before loading. This reduces compatibility risks but does not certify every
older ARM phone or guarantee that every model will fit in memory.

If loading fails, record the model, runtime, Android version and device. Use another
supported route while the issue is investigated. Do not assume a previous manual's
missing-Sherpa-library diagnosis applies to build 152: native entry points were tested
on the current release. Do not delete unrelated model files as a first response.

---
# Avatar design and creation
## Choose, fit and customize

Use Avatar to choose the look and available character. Look, Space, Behavior and
Animations organize the companion controls. Space includes sizing; a large character
can crop during movement, so check it with chat open and in landscape or unfolded mode.
Appearance in Settings provides related theme and avatar choices.

## Create a design with a configured AI

1. Enter a clear description of the character you want. If you add a photo, choose
   one you are comfortable sending to the selected provider when that workflow is remote.
2. When asked "Do you want an example?", choose Yes, show example to review first.
   The creation screen reports progress and elapsed stages.
3. Inspect the native cartoon preview. This is not a promise of a photorealistic 3D
   reconstruction of a reference image.
4. Enter a change in Refine the example and use Update example if you want another pass.
5. Use Accept & become when the design is ready. The accepted design is the one equipped.
6. Use Cancel to leave review, or Retry example after a reported failure.

The alternative No, just make it proceeds without the example-review path; choose it
only when you intend that behavior. Generation still needs a working configured route.
A failed initial request is reported as a failure, not silently replaced by an invented
result. If optional refinement fails after a valid design exists, the valid design can
remain available instead of being lost.

## Animation and review

Try authored animations first, then supported generated forms or props. An animation
request is different from creating a video file. Check the actual on-screen result or
saved artifact. Appearance changes do not grant sensor access or override privacy
controls. Review dialogs scroll so controls remain reachable with landscape and larger
text; report any clipped or unreachable control with your orientation and text size.

---
# Backgrounds and care
## Five new animated environments

Open Background, or the corresponding environment controls in Avatar, to choose:

- Saffron dunes.
- Alpine cableway.
- Coral twilight.
- Lantern courtyard.
- Crystal cavern.

Existing background options remain available. These scenes share the app's lifecycle
and thermal-controlled animation clock. Motion can slow or pause when the device needs
to settle. Do not turn off thermal protections to force an animation to keep running.

## Backdrop and Habitat

The Care panel offers Backdrop and Habitat choices where supported. Habitat supports
the companion's play space; Backdrop uses the environment as a background. Available
props and reactions depend on the current form and environment.

## Care mode means optional upkeep

Open Care to see the mode and available actions. With Care mode on, food, energy, mood,
affection and hygiene are shown as needs. With Care mode off, the companion is
self-sufficient: needs stay full without care, and you can still play together.
This corrects the reversed explanation in the older manual.

The panel describes gentle needs and a companion that never dies. Try Feed, Rest,
Clean, Talk or the available tricks. Holding to pet and double-tapping to play depend
on the active form and interaction surface. If motion is resting, care actions can
still work. Enabling neural reactions does not independently enable the camera or mic.

## Power expectations

Build 151 reduced excessive avatar redraws and build 152 retains that fix. A measured
USB-connected CPU comparison is not a battery-life guarantee. Brightness, display-on
time, model inference, listening modes and background services all affect power use.
Use ordinary unplugged sessions to evaluate your own setup.

---
# Images, music and video
## Select the image route intentionally

Settings > Images includes Keep current setup, On this phone, AI Horde, Cloudflare
and Your own server options. Read the active route, its dependencies and privacy text.
Press Use this option to save the route you intend; merely viewing an option does not
apply it. A specific route can change automatic fallback behavior.

On this phone needs a compatible downloaded image model and enough available memory.
Remote routes need a working service and any required account, token or server URL.
AI Horde is remote community processing, not an on-device privacy alternative.

1. Choose the destination and complete its setup.
2. Try a small, non-sensitive prompt before a complex request.
3. Watch the actual progress or queue status. Remote queues and local inference may
   take time; avoid submitting repeated jobs while one is still active.
4. Review the resulting image or file. If the app reports an error, correct the stated
   problem or deliberately choose a different configured route.

An error is not a successful image. Do not infer that a cloud job stopped immediately
just because you left a screen; follow the actual cancellation/status message.

## Music and video need supported services

Settings > Music & video provides server configuration for supported generation
features. Enter an endpoint only for a service you intend to use and can reach.
Leaving required service configuration blank means that workflow is unavailable.
A catalog label or a chat promise does not establish a functioning media generator.

Review generated content and saved outputs before sharing them. A companion face
animation, a generated still image and a generated video are different outputs with
different requirements. For failures, report which output you requested and the route
that was selected, not just "creation failed".

---
# Set up your Computer
## Choose the destination

Open Settings > Computer & task tools and its setup flow. Choose This phone for a
Termux Linux workspace, or A Linux server for a machine you own. The desktop helper
under Connections & memory is a separate connection with its own pairing process.

## This phone: Termux, Ubuntu and OpenCode

1. Let the setup screen inspect the installed Termux edition. If absent, use the
   recommended F-Droid link or the official GitHub alternative shown in the app.
2. Keep an existing Termux installation and its files. Do not uninstall it or mix
   add-ons from different distribution sources to get past a permission message.
3. Continue with installed Termux. Compatible editions support automatic command
   access; other editions need the guided handoff shown by the setup card.
4. Follow Connect Ubuntu & OpenCode / Setup computer. Use the current app-provided
   instructions instead of the old manual's fixed Debian command sequence.
5. Read the connection check and open the phone Terminal only when the intended
   connection is verified. Installing Termux alone does not confirm Linux is ready.

Existing compatible Ubuntu environments are recognized. Preserve them rather than
reinstalling or resetting Linux just because a check is incomplete. If setup fails,
record the exact step and message before retrying.

## A Linux server

Enter the intended connection name, address, port, user and credential using the setup
form. Advanced options cover the supported connection type and workspace. Test the
connection, review host identity where shown and save the verified profile. Use only
the machine and workspace you meant to connect. A saved address is not a live test.

Closing a Terminal session or seeing an Idle label does not guarantee that a server
process has stopped. Power and task status refer to specific app components; check
the service's actual state before attributing heat or battery use to it.

---
# Computer tasks, results and files
## Start with a small approved task

Use a verified Computer connection. Request a small synthetic file, review the proposed
action and approve it only if the destination and effect match your request. Watch
the task result rather than relying on the conversational wording alone.

## Save and preview a completed result

1. Open the completed task's result controls.
2. Choose Save & preview result / Save & preview when available for the output.
3. Wait for transfer and verification. The app checks the saved copy rather than
   treating a remote filename as proof of a local file.
4. Open the preview and inspect the actual content.
5. Find the saved copy in Files when you want to reopen it later.

Repeated saves are deduplicated. When the content changes, older copies are preserved
rather than silently replaced. The preview's presence establishes a saved output,
not that every statement or calculation inside it is correct.

## Current limits

The current result path is designed for supported small files. Large transfers,
complete project exports and a fully unified project/session workspace remain under
development. If a size/type limit is reported, keep the remote original and choose
an explicit supported way to retrieve it. Do not assume a large export was saved.

Connection failure, denied approval or cancellation can leave no new local artifact.
Verify what completed before retrying. Avoid giving broad access to unrelated folders
just to resolve one failed task. If a result cannot be previewed, report the file type,
approximate size, task status and error; do not attach private contents by default.

Files can also contain manually imported material. Import a non-sensitive test file
when first learning the workflow. Review the selected item and sharing destination
before sending any file to a cloud provider or another application.

---
# Apps, projects and integrations
## Play edition: launching and drafting

Try a simple request such as "Open Chrome". Drafting can prepare content for review,
and supported actions can open an app or compose screen. Inspect the destination and
finish any required user confirmation. Preparing a draft does not mean it was sent.

Autonomous Accessibility taps, typing and scrolling across other apps are excluded
from this Play edition. Learned screen-navigation replay is also unavailable. Enabling
an Android setting will not add a capability excluded from the build. Older preview
instructions or an old help hint about enabling Accessibility do not change this limit.

## Projects, Business and Agents

Use the project and agent views to organize work, configure available specialists,
assign bounded tasks and inspect results. Supported specialists can take turns on a
shared task. Each provider and tool still needs its own working setup. Review approvals
and the audit/result information; a team name does not imply unrestricted execution.

Existing project organization should not be confused with a complete, unified Computer
project/session workflow. That integration remains unfinished. Keep important original
files and verify any output before relying on it.

## Plugins and optional hardware

Plugins lists integrations. Some are direct service connections; others are setup
templates, app launchers or links. Read the capability/status text and use connection
checks. Do not treat every installed app as a callable service.

Wearables, HUB connections, vehicle adapters and remote neural systems require their
own compatible hardware, software and acceptance checks. A Bluetooth microphone does
not establish that glasses camera support works. Follow an unavailable/update-required
message for the actual installed build. Physical robot actuation remains quarantined
and is not a feature to enable through this manual.

---
# Settings reference
## Current categories

- Voice & behavior: speech models, companion naming, wake/listening modes and conversation.
- Computer & task tools: Computer setup, tasks, connections and power/status controls.
- AI & models: provider routing, LLM settings and model downloads.
- Images: local, community, cloud or self-hosted image generation.
- Music & video: supported media-generation service endpoints.
- Phone & device: supported app/device behavior, cover-screen setup and Home options.
- Appearance: themes, colors and companion appearance.
- Neural / Lumen: avatar reactions, neural-network mode and sensitivity.
- Connections & memory: desktop helper, servers and optional shared memory.
- Auto: driving, navigation and vehicle settings. This replaces the old manual's
  assumption that the category is always labelled Driving.
- Help & feedback: beta help, installed version, feedback and optional diagnostics.

Finish setup / Start setup is available from the settings hub when shown.

## Saving and navigation

Read each control's save behavior. Some toggles apply immediately; provider edits
require Save & use, image-route choices require Use this option, and connection forms
have their own save/test actions. The old blanket statement that leaving Settings
saves every edited field is not correct.

## Home and cover-screen behavior

Supported Home/launcher and folding-phone options depend on Android and your device.
A compact avatar on a supported cover display does not automatically replace the
lock screen or guarantee launch when folded. Read the system's default-Home chooser
and remember which launcher you used before changing it. You can change the selected
Home app through Android's default-app settings.

Keep permission, appearance and service changes separate while troubleshooting. Change
one setting, test the relevant behavior, then decide whether it solved the problem.

---
# Neural modes and connections
## Neural / Lumen

Classic uses authored companion behavior. Shadow runs the selected network for
comparison while Classic retains control. Neural allows the network to influence
bounded companion behavior, with authored constraints and fallback. Available network
choices include local rate/spiking experiments and configured server-backed options.

These are simulations and experiments, not a claim of a complete validated biological
brain, consciousness or a robot-control system. Read the explanation for the selected
network. A remote graph/network requires a compatible running server; a catalog option
alone does not establish a connection.

Brain activity exposes available observations. Settings or covered/paused avatar views
can pause the relevant work, so use the intended view when evaluating behavior. Reflex
sensitivity adjusts reaction strength; it is not a promise of faster learning. Neural
reactions do not bypass microphone or camera controls.

## Desktop helper and servers

Connections & memory includes compatible desktop-helper pairing and saved server
connections. Pair only with the machine you intended, using the code/payload that its
helper presents. Network reachability, helper version and host permissions determine
which tools work. A Linux Computer worker and a desktop bridge are distinct services.

Some desktop workflows, such as supported Logic controls, need a paired Mac helper
and explicit host setup. Do not treat a successful pair as proof of automatic mixing,
support for every custom plugin, or a finished export. Inspect each action and output.

## Shared memory is optional remote data flow

An optional memory-service URL can synchronize relevant conversation facts for a
server-side system. Leaving it unconfigured keeps that optional sync off. Review the
destination and service behavior before enabling it. Local chat storage does not mean
all configured connected services are local. Never assume a saved example host belongs
to you; choose and verify your own connection.

---
# Auto, navigation and driving
## Set it up while parked

Open Settings > Auto for driving, navigation and supported vehicle settings. Configure
the features you want before moving. Location, Bluetooth or optional vehicle data
require their own permissions and supported sources. Check the mode/status rather
than assuming detection is active because the category exists.

Build 152 includes the compact driving summary and a separate scrollable details view,
including better use of landscape space. Real crash/vehicle alerts retain priority
over ordinary cards. An unavailable regional source is not evidence of a detected
hazard and should be read as a source-availability issue.

## Navigation and places

NAV supports the configured navigation and nearby-place workflows. Some place-search
features need their own provider key. A working chat key does not configure every
navigation service. Verify the intended destination in the navigation app before
starting a trip. Weather and road information can be incomplete or delayed.

## Alerts and emergency contact

Where configured, a crash-alert flow can prepare a message with location for an
emergency contact. Preparing or opening a message is not proof it was sent. Review
the destination and any required confirmation. Do not deliberately stage a collision
or dangerous maneuver to test the feature.

This interface is not a certified driving-safety or emergency-response system. Use
ordinary vehicle controls, situational awareness and the appropriate emergency service
when needed. Optional OBD/vehicle and Bluetooth behavior varies with hardware; broader
hardware acceptance is still open.

## Reporting an issue

Report the device, Android version, orientation, permissions, data source and steps.
Use a parked/passenger-safe reproduction or synthetic test. Do not include precise
personal travel details unless you intentionally choose to share them with support.

---
# Privacy and control
## Separate four decisions

1. Android permission: whether the OS permits microphone, camera, location or another
   protected capability.
2. App mode: whether the feature is actually enabled in Meow.
3. Processing route: which local model, cloud provider or connected service handles it.
4. Sharing destination: where a file, draft or support report is sent.

Fresh setup's skipped/denied sensor choices remain off after restart. For an existing
installation, inspect your current choices rather than assuming an update reset them.
Avatar artwork and neural reactions do not replace the visible MIC/CAM status.

## What can leave the phone

Local history is stored on the phone, but cloud chat, transcription, vision, image
generation, search and connected tools can send relevant content to their configured
services. Inputs can include text, audio, images, files and relevant context. AI Horde
also receives remote image prompts. A local default brain does not override every
separate feature's routing.

Optional shared memory, repository connections and Computer services introduce their
own destinations. Review the app's privacy policy and the selected service's terms
for your actual configuration. This guide is not a substitute for those disclosures.

## Keep and remove data deliberately

Use the available in-app deletion controls for the item you intend to remove. Clearing
storage or uninstalling can erase local data and keys, so it is not routine recovery.
Deleting a local conversation does not prove a third-party service erased content it
previously received; use that service's controls where applicable.

Hide credentials before screenshots. Review files and sharing destinations. Do not
put passwords, tokens or private conversation history into a general bug report.

---
# Help, feedback and diagnostics
## Write a useful bug report

Open Settings > Help & feedback. Beta help & feedback shows the installed app version
and provides Write feedback. It opens your email app with basic version/device details;
you review and send the message yourself. Sending is not automatic.

Include:

- What you were trying to do and the smallest sequence that reproduces it.
- What you expected, what happened, and whether it happens once, sometimes or always.
- App version and edition, device, Android version, orientation and text size if relevant.
- Selected provider, speech engine or Computer connection type without its secrets.
- The exact error wording and whether restarting changes the behavior.

## Optional Share diagnostics

The Support section offers Share diagnostics. It creates a bounded report with app
version, runtime health, microphone/camera modes and recent process-exit information,
then opens Android's share chooser. Choose the intended destination and review what
you send. Chats, recordings, credentials and raw logs are excluded from that report.

Sharing diagnostics is optional. It does not automatically enroll you in monitoring,
upload all app data or prove the underlying issue is fixed. Reproduction steps remain
important even when you attach diagnostics.

## Beta participation

Use the approved Google account and remain opted in while testing. A group subscription
is different from Play enrollment. Send honest observations, including confusing
controls or a feature that could not be configured. No public rating is needed to
describe a bug. Testing duration and account requirements are shown in the current
Console; this manual does not guarantee a production launch date.

---
# Troubleshooting
## Start with the actual status

- No chat reply: confirm a configured provider/local model, active selection, explicit
  save, network if needed and a successful connection test. Read authentication,
  quota, model-not-found or runtime errors before changing unrelated settings.
- Voice is silent: check MIC status, Android permission, engine/download readiness
  and audio input. Check speaker mute separately when input works but replies are silent.
- App will not tap another app: autonomous screen control is excluded from Play.
  Use supported launching/drafting instead; there is no Accessibility workaround in
  this edition.
- Download fails: check storage, connectivity, token and model-access authorization.
  Retry the failed item. A gated license and an authenticated token are separate checks.
- Avatar/image creation fails: check the configured route, model availability, queue,
  memory and error. Retry or cancel. A retained valid example after refinement failure
  is different from a new completed result.
- Computer is not ready: distinguish Termux installed, Ubuntu present, service running
  and connection verified. Follow the current guided/automatic path; preserve files.
- Result missing from Files: check whether Save & preview completed and passed
  verification. Inspect type/size limits and retain the remote original.
- Bluetooth stops after a permission change: restore only the permission you intend
  to grant, reconnect and retry. Report the accessory and exact revoked permission.
- App update is rejected: check package/version/signature. Do not uninstall or clear
  personal data as a routine workaround.
- Device is warm: pause heavy inference/generation, reduce display-on time and inspect
  enabled listening/services. Respect thermal throttling. Closing a terminal does not
  necessarily stop its server process.

## If the app crashes

Record what immediately preceded it and reopen normally. Check the installed version.
Use optional diagnostics and a concise reproduction report. Build 152 includes known
speech and redraw fixes, but a new crash still needs its own evidence. Do not describe
historical crash entries as fresh failures without checking their time/version.

---
# What changed in build 152
## Changes since the previous Alpha build

- Speech crash repairs and more reliable cloud-audio cancellation.
- Fresh microphone-off setup, persistent skip/denial choices and clearer permission scope.
- Avatar creation progress, retry, cancellation and preservation of valid reviewed designs.
- Five new animated scenes and a fix for excessive sensor-triggered avatar redraws.
- Compact driving summary, scrollable details and preserved priority alerts.
- Better recognition of installed Termux/Ubuntu and verified small-file Computer results.
- Optional support diagnostics, Android 12 compatibility, Bluetooth cleanup and native
  runtime compatibility improvements.

## What was checked

The recorded release suite contains 2,708 tests: 2,703 passed, five skipped and zero
failures. Full debug/release lint reported zero errors; warnings remain. Focused checks
passed on a physical Samsung Fold and a 16 KB emulator. Signed fresh setup, restart,
speech cancellation and native loading were included in the release evidence.

A same-phone, 90-second USB-connected build-150 versus build-151 comparison reduced
mean CPU use from 87.62% to 36.40% of one core. Build 152 retains the fix. This is not
a build-152 unplugged battery benchmark or an all-day performance guarantee.

## What still needs acceptance

Unplugged endurance, older physical devices, noisy speech, live-provider workflows,
large exports and advertised hardware integrations still need further validation.
Production access, final privacy declarations and actual tester feedback remain
release gates. Submission to Alpha does not close those gates.

This manual was checked against the build-152 application source and its release
receipts. No claim is made that every path in this guide was newly exercised on a
device during the documentation update.

---
# Tester checklist
## A practical session

1. Upgrade normally. Confirm your conversations, preferences, appearance and provider
   configuration remain intact. Record the installed version.
2. On a spare fresh installation only, try Not now, Finish later or Android denial.
   Restart and confirm MIC/CAM remain off. Do not erase your personal app to do this.
3. With intentional mic access, try a short voice request, interruption, silence and
   retry. Compare quiet and ordinary noisy conditions without changing several modes
   at once.
4. Try avatar example, refinement, retry and cancellation. Confirm that only the
   intended accepted design becomes active.
5. Try each new environment. Rotate or unfold the device and check large-text controls.
6. With a verified Computer connection, approve a small synthetic file task, Save &
   preview it, reopen it in Files and confirm older changed copies remain.
7. Observe ordinary unplugged use and report duration, brightness, workload, battery
   change, warmth and charging state. Leave thermal protection enabled.
8. Send specific feedback and optional diagnostics without secrets or private files.

## Reference and revision record

Feature page: https://www.dekayrecords.com/meow-assistant.html#mx-release152

The owner archive retains the original 57-page build-124 manual. This replacement
corrects its installation, setup, provider saving, care-mode, cloud-privacy and
Play-edition instructions. It also replaces old fixed model sizes, prices and
service-availability assertions with the relevant app checks and provider requirements.

Edition: Meow Assistant 1.3.96 (152), September 19, 2026.
Application source: 039da2c5130d878a4751281b914578ac2908f0d2.
Prepared from the current app source and release/test receipts. Provider catalogs,
fees and remote services can change independently of this manual.
