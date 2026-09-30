# Release 191 location review preparation

Status: prepared locally; no background-location declaration has been submitted. The Play upload and review are still pending.

## Feature and disclosure

Optional location context supports Flow and the local context journal: remembering places, showing relevant context, and helping with place-aware reminders. It is not required to use chat, projects, files, or Development Studio. The implementation uses balanced-power fused location with batched PendingIntent updates. Background access lets that context update while Meow is closed. Android permissions remain the enforcement boundary.

The release presents a prominent disclosure before the Flow location request. It says that location is collected while the app is closed if the user chooses Allow all the time, that history is kept on this phone, and that relevant context can be sent to a selected cloud AI provider. Not now declines. Foreground permission is requested first; background permission is a separate visit to Android settings. Returning refreshes the actual permission state.

Published policy: https://dekayrekords-oss.github.io/Meowmba/privacy.html (public static HTML commit 29780be6dbff430abe9b444dbac458dbc6f12f84). Pending Data safety already discloses optional approximate and precise location as collected/shared. Do not claim that every provider retains data only temporarily or that user-configured LAN HTTP is encrypted.

## Required evidence before submitting

1. Inspect the uploaded signed bundle and the declaration that Console actually requests.
2. Record a demonstration on the exact release: enter Flow, choose the location connection, show the disclosure and Not now path, show foreground permission, separately choose background access in Android settings, and return to Flow.
3. Demonstrate the real location/context feature with synthetic QA locations and no personal history. Check the behavior when background access is declined or revoked. Show truthful functionality; do not imply automatic business or retail offers that are not implemented.
4. Host only the review demonstration at an accessible video URL and enter that URL in the requested Console form. This evidence has not yet been recorded or hosted.
5. Complete any other declarations requested by the new bundle. Replace affected active test bundles as well as the production draft so obsolete broad photo/video permissions cannot block submission.

No physical phone data, signing credentials, or private location history belongs in this document or its video.
