# Candidate151 data-flow inventory and submission gaps

Local review draft. No Console answers or hosted policy changed. This supplements the
candidate150 privacy review; it is not a claim that every supplier has been audited.

## Confirmed app-side behavior

| Feature | Data and destination | Trigger / retained boundary |
| --- | --- | --- |
| Cloud transcription | Recorded speech to the selected OpenAI-compatible or Gemini ASR endpoint | Selected speech provider; separate from chat-provider selection |
| Cloud conversations and tools | Prompt plus selected history/context/files/images to the configured model or service | User-selected provider and existing action approval rules |
| Images/media | Prompt/reference input to the selected generation provider or configured server | Provider-specific flow; AI Horde has its own worker/dataset disclosure |
| Computer/SSH/server connections | Approved instructions, terminal content and files to the configured destination | Existing target/approval rules; no new unrestricted execution |
| Road/weather/maps/search | Queries and relevant location to the selected service | Location/feature configuration and permission rules |
| Local records | Conversations, memories, notes, models and encrypted preferences on the device | App backup disabled; file/database/preference exclusions exist in data extraction rules |
| Diagnostics | Version/API, health, mode switches, at most five exit-code records/timestamps | Explicit Settings share action and Android destination chooser; no automatic upload |
| Play phone control | Accessibility screen-control service excluded from release | No autonomous Accessibility tooling exposed in this edition |

A static source scan recorded 71 literal network hostnames in the artifact archive.
This includes documentation and download URLs, so it is not a list of active recipients.
It also cannot enumerate dynamic user-entered endpoints or SDK-internal destinations.

## Included SDKs that affect disclosure

- ML Kit image labeling 16.0.8 and text recognition 19.0.1 use Play-services artifacts;
  face detection 16.1.7 and pose detection 18.0.0-beta5 are also included.
  Google's disclosure guide describes device/app information, installation identifiers,
  configuration, sizes, events, error codes and performance metrics. It separately notes
  Firebase configuration/installation behavior for pose detection. Therefore an assertion
  that on-device inference means no SDK diagnostic data leaves the phone is not supported.
  The guide covers current SDKs; confirm the declarations for the actual pinned versions.
  [Google ML Kit disclosure guide](https://developers.google.com/ml-kit/android-data-disclosure).
- Meta Wearables SDK 0.8.0 is included; the app manifest sets
  `com.meta.wearable.mwdat.ANALYTICS_OPT_OUT=true`. That opt-out is confirmed in source,
  not a blanket guarantee about all registration, device or SDK data. Verify current
  Meta terms and required disclosures before submission.
- MediaPipe GenAI 0.10.35, image generation 0.10.26.1 and LiteRT-LM 0.16.1 are included.
  Confirm their exact SDK data behavior and all transitive libraries. No advertising
  SDK was added in this batch.

## Remaining submission decisions

Review the final live policy, contact address, permission explanations and Console Data
safety answers against app and SDK flows. Account for connected providers even when
users supply their own keys. Avoid unsupported claims about third-party retention,
deletion or universal HTTPS (configured local-network services can use HTTP).
Collect actual closed-tester feedback for the production-access application. Tester
engagement cannot be inferred from the opt-in count, and the 14-day requirement cannot
be replaced by these automated checks.

## Candidate152 consent follow-up

Fresh setup now persists microphone off before the companion starts, and “Not now”
persists both sensors off. Android setup denials leave the app switches off. The Allow
step requests only microphone/camera; unrelated Calendar, location, Bluetooth and
notification permissions are no longer bundled into that explanation. Existing saved
choices remain on upgrade. This fixes first-run consent behavior; it does not replace
review of the SDK disclosures or final Console answers above.
