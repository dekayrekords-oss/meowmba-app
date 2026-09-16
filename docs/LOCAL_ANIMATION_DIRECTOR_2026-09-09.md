# On-device animation invention — 1.3.60 / code 64

The user asked whether requests such as “let's go fishing” and “can you fish?” could make the local AI invent a new avatar performance. The existing avatar.animate tool could generate facial keyframes, but had no general prop tracks; unknown activities could become plain chat, a missing named clip, or a media-file request.

## Implemented behavior

- `avatar.invent` asks the existing on-device LiteRT/MediaPipe provider to produce one bounded JSON scene. It never invokes OmniRoute or a cloud fallback. Missing local models return a setup error without fabricating a performance.
- The user's fishing invitations route directly to this tool before informational chat routing. “Invent an animation of you juggling stars” uses the same generator. Ordinary requests for animation/video files and fishing/travel questions retain their prior routing.
- The model invents facial keyframes and coordinated native prop tracks. Supported props are rod (including line/reel/grip), fish, bobber, water ripples, ball, star and heart. This is an expandable native prop vocabulary, not arbitrary generated geometry, downloaded assets or executable graphics code.
- One invalid proposal receives one correction attempt within the same 45-second generation timeout. Fishing must contain rod and fish props. Unsupported kinds, invalid or nonfinite coordinates, bad timelines, excessive size, code fields and motionless scenes are rejected.
- The actual renderer samples these props and the face on the existing active clock. Gaze can follow a chosen prop. Props fade in/out, remain clipped to the visual stage and cannot change privacy indicators, capture permissions or physical-control state.
- Generated scenes are saved in the existing animation library. “Can you fish?” subsequently replays Fishing without inference. “Make a different fishing animation” creates Fishing 2, Fishing 3, etc., preserving earlier creations.
- A new human turn, explicit performance, Stop or background handoff revokes unfinished creation. Queued requests carry a generation ticket. Late output is cancelled before playback or persistence. Model code runs off the frame/render path under the existing native inference lifetime controls.
- Prop scenes temporarily use the native Lumen renderer where needed without changing the user's stored avatar style. Existing selected animal appearance remains available.

## Limits and validation

New creation is asynchronous and depends on the installed phone model's instruction following and speed. Saved replay avoids inference. No claim of instant arbitrary animation synthesis, universal prop coverage, film-studio animation quality or physical robot skill is made.

The native fishing storyboard uses a deterministic JSON contract fixture to exercise the production parser, sampler and painter. It is **not a live local model's output**. Unit tests inject model responses to verify execution, one bounded repair, strict local routing, round-trip storage, cancellation, variation preservation and request disambiguation. Final build/test/package evidence is in `/Users/prog/Meowmba-Releases/local-animation-director-2026-09-09/`.

An actual on-phone model creation remains unverified until this candidate is installed and exercised with the user's local model. Physical robot control stays quarantined. No new installation, microphone/camera activation, base motion, data clearing, commit, push or publication was performed. Repository-configured Git fetch was attempted; LAN SSH to 192.168.1.69 timed out, so remote currency remains unverified.
