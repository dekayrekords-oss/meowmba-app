# Living Desk — preview 173

Living Desk keeps the existing Desk’s real Flow items, panels, and controls, with a cat that moves between the computer, letter tray, board, drawers, and floor. Its grooming, typing, letter, and writing motions are visual only. They do not perform an action, send a message, or confirm that work finished.

## Use the Desk

- Leave Desk open and the cat will choose short walks, hops, grooming, stretches, and exploration between irregular rests.
- Tap a room object as usual. Its panel opens immediately; the cat then walks to the matching part of the room.
- Tap **Ask Meow** in the upper-right corner, type a question, and tap **Ask**. This uses Meow’s existing assistant pipeline. The Desk stays open while Meow listens, thinks, or replies.
- While Meow is thinking, the cat works at the computer. While Meow listens or speaks, the cat changes pose to match. The animations do not change what Meow can do.
- Assistant replies appear in the speech bubble, including streamed text, silent typed replies, and model errors. Long replies scroll inside the bubble. Tap **Close** to dismiss the current reply; a later reply can appear again.

Opening Desk does not replay earlier chat. A reply already on screen stays readable during Meow’s automatic follow-up listening, while a new question or a conversation change clears it.

Motion follows the app’s existing reduced-motion, lifecycle, FPS, and thermal limits. When motion is paused, Desk controls and reply text still work.
Your existing idle-avatar setting still applies and can cover Desk after its chosen inactivity timeout.

## Preview status

Preview **1.5.7 / 173** follows predecessor build 172 at revision `a1f583c`, with the same package and preview signing identity. The signed, optimized APK is built and verified. It passes the real Android Ask/type/send test and the optimized app shows the real answer without leaving Desk. Full daily lint has zero errors; native/model parity and 16 KB alignment pass.

The Fold disconnected before installation. Build 173 is ready at `/Users/prog/Meowmba-Releases/living-desk173-20260924/meow-preview-1.5.7-code173.apk`; the last observed Fold version was 172. Update in place with this APK to preserve the existing app’s data and settings.

The complete validation history, including the Robolectric dialog scenario moved to a passing Android instrumentation test, is recorded in the release folder’s `README.md` and `validation/`.
