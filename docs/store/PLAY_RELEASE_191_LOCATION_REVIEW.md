# Release 191 location review preparation

Status: prepared locally; no background-location declaration has been submitted. The Play upload and review are still pending.

## Feature and disclosure

Optional location context supports Flow and the local context journal: recording location samples, stamping activity and conversation events with available location, and including relevant location context in conversations. It is not required to use chat, projects, files, or Development Studio. The implementation uses balanced-power fused location with batched PendingIntent updates. Background access lets that context update while Meow is closed. Android permissions remain the enforcement boundary.

The release presents a prominent disclosure before the Flow location request. It says that location is collected while the app is closed if the user chooses Allow all the time, that history is kept on this phone, and that relevant context can be sent to a selected cloud AI provider. Not now declines. Foreground permission is requested first; background permission is a separate visit to Android settings. Returning refreshes the actual permission state.

Published policy: https://dekayrekords-oss.github.io/Meowmba/privacy.html (public static HTML commit 29780be6dbff430abe9b444dbac458dbc6f12f84). Pending Data safety already discloses optional approximate and precise location as collected/shared. Do not claim that every provider retains data only temporarily or that user-configured LAN HTTP is encrypted.

## Required evidence before submitting

1. Inspect the uploaded signed bundle and the declaration that Console actually requests.
2. Record a demonstration on the exact release: enter Flow, choose the location connection, show the disclosure and Not now path, show foreground permission, separately choose background access in Android settings, and return to Flow.
3. Demonstrate the real location/context feature with synthetic QA locations and no personal history. Check the behavior when background access is declined or revoked. Show truthful functionality; do not imply automatic business or retail offers that are not implemented.
4. Host only the review demonstration at an accessible video URL and enter that URL in the requested Console form. This evidence has not yet been recorded or hosted.
5. Complete any other declarations requested by the new bundle. Replace affected active test bundles as well as the production draft so obsolete broad photo/video permissions cannot block submission.

No physical phone data, signing credentials, or private location history belongs in this document or its video.

## Declaration copy — draft, not submitted

**One feature:** Flow's location context journal.

**App purpose:** Meow Assistant combines conversation, projects, files and an optional contextual Flow home. Flow brings relevant work and reminders together; the user decides which phone connections to enable.

**Background-location explanation:** With the user's permission, Meow receives Android location updates while another app is open or Meow is closed. These updates refresh Flow's current context and save location samples in the on-device context journal. Activity events can be stamped with the available location, so later assistance can refer to the context around an event. Foreground-only permission cannot provide these updates while the user is away from Meow. The connection is optional and can be declined or revoked. Relevant context may be included in requests to the user's selected cloud AI provider.

This describes the implementation, not an approved justification. Google evaluates whether the declared feature is sufficiently central to the app and provides a significant user benefit. Do not label an optional journal as safety-critical, claim every place is identified, or promise automatic nearby offers or instant geofence alerts. Permission and migration tests do not establish background delivery. The real feature demonstration remains outstanding.

## Review-video shot list

Aim for about 30 seconds of readable Android footage. Use synthetic locations on an owned QA device, the exact release code and a reviewer-accessible video link. Do not claim named-place learning, geofence reminders, or background delivery until observed. Retain the uncut capture separately; cuts must not imply an update that did not occur.

| Shot | What the reviewer should see |
| --- | --- |
| 1 | Open the normal Flow location-connection card. Show the complete **Location for Flow** disclosure, including optional collection and cloud-provider sharing. |
| 2 | Tap **Continue** and show Android's foreground-location permission prompt and the user's choice. |
| 3 | Open the location connection again. Show **Location while Meow is closed**, then **Open Android settings** → Permissions → Location → **Allow all the time**. |
| 4 | Return, leave Meow in the background and change the synthetic location. Wait for an actual Android-delivered batch; intervals and batching can make this take several minutes. Record the wait rather than fabricating immediate delivery. |
| 5 | Return to Meow and demonstrate the real user-visible effect of that background update. If no such effect can be demonstrated, explain the lack of a background interface in the form and verify the impact before submission. Database fixtures alone are not a feature demo. |

Suggested narration: “Flow can use location context when I enable it. Meow explains what is collected before Android asks. I choose whether to allow updates while Meow is closed. This demonstration uses test locations, and the final screen shows the update Android actually delivered.”

Record a separate decline/revoke check: **Not now** causes no permission request; keeping foreground-only access does not grant background access; revoking location prevents further updates. Confirm chat, files and projects remain usable. Do not present these unrecorded checks as passed.

Guidance checked 30 September 2026: [Google Play background-location declaration and video requirements](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en). Copy the draft into the actual form only after the uploaded bundle, observed feature and requested fields agree.

October 1 correction: draft wording now reflects implemented journal/context behavior. Candidate192 removes unsupported reminder and learned-place claims from its disclosure, setup card and manual. Candidate191 remains immutable and still contains the older wording. No declaration has been submitted.
