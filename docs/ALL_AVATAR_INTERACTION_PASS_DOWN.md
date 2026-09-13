# All-avatar interaction — build 105

## Changes and reasons

All seven avatar styles now share tap (attention), hold (pet), double-tap
(peekaboo/play), and short-stroke (pet) gestures. The Play/care panel is also
available for every style on static backgrounds as well as in the habitat.
Existing habitat prop hit testing and the full fly's 3D touch destination remain.
Lumen's renderer accepts touch gaze with the physical camera off. Jarvis receives
touch-directed particle forces and a pink ring response while being petted.

The old eye/ear/mouth touch zones changed sensor or speech settings unexpectedly
while playing. Avatar taps no longer dispatch those controls; dedicated controls
remain available. Long holds now pet every style instead of opening settings.
The restored downward swipe still opens the cards. Up/left/right navigation and
the build 104 Home-intent return-to-avatar fix are retained.

Petting uses the existing onAvatarPetted nervous-system stimulus and affect path.
Double-tap uses the existing allowlisted peekaboo performance and habitat PLAY
path. This is the existing authored behavior system, not imported FlyWire data.
One short completed drag emits one social event. Navigation swipes emit none;
the prior code fed touch repeatedly on every horizontal pointer movement.
No new sensor, model inference, network, or frame loop was introduced. Preview
renderers retain pointerEnabled=false. Thermal pause policy remains active.

## Teaching Meow: implementation and diagnosis

AvatarRenderer owns gesture dispatch; CompanionScreen wires the shared onPlay
callback and shows PetCarePanel without the former Lumen-only restriction.
If one avatar stops reacting, inspect its rendering branch's effectivePose and
touchGaze consumption, then the shared ViewModel callbacks. Do not toggle camera
capture merely to permit touch gaze: the renderer's gaze-source flag is separate
from the physical camera capture gate. Check card swipes after gesture changes.
Do not restore repeated drag-frame learning events to make a pose look livelier.

## Validation and deployment

Meow SSH reconnected successfully after the prior timeout. The Mac ADB device
list is empty, so phone installation and touch acceptance remain pending.
Build 105 includes the previously uninstalled build 104 card/Home fixes. Preserve
phone data and Meow's signing identity; use adb install -r once the phone returns.
Do not uninstall or substitute the Mac's differently signed debug APK.

Server assembly passed (34 seconds). Focused JVM regression checks passed: Home
navigation 2, camera capture gate 5, appearance preferences 4; 11 total, no
failures/errors. These checks do not replace real-device gesture validation.
Signed APK SHA-256:
9375739e65e07a4af992135a9b45d50428587b714261f1bab4e3d789655187ac.
The lossless delta against installed build 103 is 3,630,635 bytes.

## Installed September 13

USB returned on Samsung RFCY61NJEVM. `adb install -r` succeeded, preserving
existing data. Package manager confirms code 105 / name 1.3.81, lastUpdateTime
2026-09-13 08:47:13 (device time). Live UI hierarchy verified the downward swipe
opens Memba Ecosystem with all 15 cards in three columns. Android Home key then
closed the cards and returned to the Fly companion avatar with Play available.
The UI still reports camera off and microphone off. Full per-avatar gesture
acceptance and sustained thermal/battery validation remain pending.
