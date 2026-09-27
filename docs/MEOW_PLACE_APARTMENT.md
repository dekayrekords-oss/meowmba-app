# Meow's place: the Desk becomes an apartment (branch `feature/desk-apartment`)

Owner request, 2026-09-25. Built by Claude in its own worktree, from Codex's `80e28c1` (build 182). **Not built as an APK and not installed.** The integrator decides the build number (next: 183).

## What the owner asked for, and where it is

| Ask | Done | Code |
|---|---|---|
| Desk expands into a full room when the phone is open | Unfolded or wide screens show three rooms side by side: living room, office, bedroom | `DeskHabitat.kt` (`wide`) |
| Room to the right: big window, bed, cat tower, litter box, food tray | Bedroom, x 412–824 | `DeskApartment.drawBedroom*` |
| Old TV with a video game system that opens a MAME arcade | Tapping the old TV or console on the dresser launches MAME4droid (`com.seleuco.mame4d2024`, `…mame4droid`, `…mame4all`). If none is installed, a panel links to its Google Play page. Nothing is downloaded on its own. | `DeskRoute.kt` `RoomApps`, `DeskRoomPanel` |
| Closed screen: cat goes back and forth between rooms | The folded cover screen shows one room. The camera follows the cat (a layer translation, no redraw). While idle the cat heads to another room about 55% of the time (30% unfolded). The "‹ Living room / Bedroom ›" buttons look next door, and the cat follows. | `DeskHabitat.kt`, `DeskCatBehavior.apartmentIdle` |
| Computer on the desk opens the main Computer and Development Studio, and tracks Dev Studio projects | The monitor shows projects. Tapping it opens a panel with a project list plus **Development Studio** and **Open Computer** buttons. Projects come from Development Studio's own status lines (`Project:`, `Checkpoint saved`, `Exit code`, `Built APK`, `Preview`, …), are parsed on the phone and saved in SharedPreferences `dev_studio_projects`. | `core/computer/DevStudioProjects.kt`, a hook in `DevelopmentStudioPanel.kt`, `ComputerToolsLaunch` (a one-time start page) |
| Computer screen a little bigger, sitting on an old Gateway 2000 | Beige CRT with a 148×112 screen (was 130×80) on a Gateway 2000 desktop case with the cow-spot box | `DeskPainter.drawRoom` |
| Left: living room with another cat tower, couch, big-screen TV, bookshelf (audiobooks and the manual), old-school tower stereo with two big speakers | Living room, x −412–0. The stereo's tuner shows the current song; the TV shows the current YouTube video and channel; the bookshelf shows the current audiobook and holds the **Meow manual** (`assets/manual/meow_manual.md`). | `DeskApartment.drawLivingRoom*`, `core/media/RoomMediaWatcher.kt` |
| Much more cat expressions | 13 faces: neutral, happy, purr, love (heart), curious (tilt + ?), surprised (!), focused, sleepy, asleep (Zs), yawn, annoyed (ears back), playful (blep), chew. Also 9 new poses: sleep curled, eat, dig, perch, watch TV, game, bob to music, read, scratch. Faces follow the activity (listening → curious, thinking → focused, speaking → happy), drift during rests, and react to petting. | `DeskCatBehavior.kt`, `DeskPainter.drawCat` |
| Fix the weird little things about the desk | See "Office fixes" below | `DeskPainter.kt`, `DeskRoute.kt` |
| Avatar appears automatically when driving | While a drive is detected, Flow and the Desk switch to the full avatar (drive card and reactions). When the drive ends, the previous home comes back. The switch isn't saved as a preference, and a theme the user picks mid-drive wins. | `DriveAvatarPolicy.kt`, `CompanionViewModel.applyDriveAvatar` |

Tapping the cat pets it: a startled blink, then a happy, purring or love face, and a short line. The bed, food, litter box, couch and cat towers send the cat there. They're animation only, with no care or hunger meters, which respects the earlier "no Care mode on work avatars" rule.

## Office fixes

1. **Sticky note:** it covered the monitor and cut off its text. It now sits on the wall beside the monitor, with a matching tap target.
2. **Version-number note:** the corkboard pin note said "Meow 1.5.16". It now shows "Studio · N projects", or nothing.
3. **Project card gap:** the status now sits right under the title when there's no tally.
4. **Lamp:** its arm ran through the cat's resting spot. It's now a wall-mounted swing-arm lamp over the desk; its glow still means "Meow has an idea".
5. **"$412" jar note:** it floated beside the jar, tilted across the lamp. It's now a label on the jar.
6. **Bare desk half:** a cabinet door balances the drawers.
7. **Office window:** it has curtains.
8. **Unfolded screen:** the tiny desk in a huge empty wall is replaced by the apartment panorama. Menu, Flow and Settings stay at the bottom and no longer float over the room; only very short screens (< 480 dp high) put them down the side.

## Privacy note for the owner and the integrator

`MusicAwareness`'s rule (2026-09-24: titles only when asked, mic-time only) is **unchanged**. The rooms use a separate `RoomMediaWatcher`, because the owner has now explicitly asked the stereo, TV and bookshelf to show what's playing:

- It runs only while the Desk is resumed on screen: started on `ON_RESUME`, stopped on `ON_PAUSE` or dispose.
- It uses media-session callbacks only: no polling, no microphone, no notification text.
- It needs the same notification access as before. Without it, the TV, stereo and bookshelf say so and offer Android's settings.
- Play, Pause and Next forward the user's tap to that app's own media session.

## Rendering and heat

The apartment keeps the Desk's three-layer design:

- The static rooms are drawn once.
- Objects redraw only when `DeskState` changes; that includes the room media and studio data.
- The cat animates only during a performance.
- The camera pan is a `graphicsLayer` translation.
- Thermal MODERATE or reduced motion stops decorative motion as before.
- The living room and bedroom have no animation loops of their own.

## Tests

- **New:** `DeskApartmentTest.kt`, with six unit tests and three Robolectric suites:
  - unfolded panorama and panels (studio, stereo, manual, arcade fallback);
  - folded room buttons, camera, petting and the living room;
  - expressions and poses, with each face asserted to differ from neutral.
- **Updated for the apartment:**
  - `DeskCatBehaviorTest`: objects keep the cat in their own room.
  - `HomeNavigationRegressionTest`: panels only; all rooms in the wide layout.
- **Screenshots:** `app/build/flow-shots/apartment-*.png` and `desk-cat-expressions.png`.
- **Full JVM suite:** 3,103 tests, 0 failures, 0 errors (`testDebugUnitTest`, 2026-09-25). Lint not run.

## For the integrator (Codex)

- Merge the branch into the build line.
- Run lint and the full suite with the daily flags, then build 183 with the usual MWDAT and verified-jni flags.
- **Phone checks:**
  1. Unfolded, all three rooms show.
  2. On the cover screen, the room buttons pan and the cat wanders between rooms.
  3. The stereo, TV and bookshelf show real media, given notification access.
  4. Development Studio projects appear on the monitor after a session.
  5. The console opens MAME4droid, or its Play page if it isn't installed.
  6. A manual drive ("drive mode on") shows the avatar, and turning it off returns to the Desk.

## Update 2026-09-26: a livelier cat, a room-trips setting, a readable stereo

These changes are on branch `feature/desk-apartment`, based on Codex's 184 (`d2313da`).

- **Motion:**
  - A continuous walk cycle with diagonal leg pairs, driven by distance travelled so the paws don't slide. A gentle start and stop, a stroll nearby and a trot across rooms. The body bobs while the head stays steadier. Turning around is a short shuffle instead of an instant mirror.
  - Jumps have anticipation (a crouch), a stretched arc and a landing squash. The cat stands up with a stretch before leaving a sit or a loaf.
- **Resting life** (`rest()` in `DeskHabitat.kt`):
  - The cat breathes, twitches an ear, flicks its tail, glances around and holds the look, blinks, and passes through small moods.
  - After about 5 s it sits (new pose `SIT`), and after about 18 s it may lie down (new pose `LOAF`).
  - This runs at most 12 fps through `AvatarFrameCadence`. At thermal LIGHT or above, or with reduced motion, it falls back to the old still rests with blinks.
- **Setting:** "Desk cat visits other rooms": Never, Rarely, Sometimes (default) or Often (`DeskRoomTrips`, preference `desk_room_trips`, `CompanionFeatures.deskRoomTrips`). Set it in Flow → Adjust → When idle, or long-press the cat on the Desk for "Meow's routine". This replaces the old fixed 30% (open) / 55% (folded) chance.
- **Stereo:**
  - A wider rack (82 units) between slim tower speakers.
  - A 74×44 display shows ▶ PLAYING / ❚❚ PAUSED, the app name, the song (13 characters) and the artist, with level meters.
- **Tests:** `DeskCatLifeTest.kt` covers the setting mapping, that "Never" stays in the room, that the gait, breathing, crouch, sit and loaf all change the drawing, and that a long-press opens the routine sheet.

## Update 2026-09-26 (evening): living room, one moon, landscape rooms

Owner request: only one moon (office window); clouds in the bedroom window; a wider stereo and TV
so what's playing is readable; the cat tower behind the couch with more levels; a customisable
picture; and, in landscape only, a wider bookshelf, a much bigger TV and a longer bed.

- **One moon.** The office window has one crescent moon (drawn as a real crescent, no ghost outline).
  The bedroom window shows clouds day and night, with no sun or moon, so the unfolded view never
  shows two.
- **Living room, left to right:** the bookshelf with the picture above it, then the couch with the
  tower behind it, then the stereo with the TV on the wall above.
  - **Tower:** two sisal posts, five levels, a cubby and a toy. The cat climbs and leaves it via
    the middle platform (`RoomSpots.towerStep`).
  - **Stereo:** now 176 units wide; its receiver display is 120 × 60. Titles are fitted by
    measuring, not by character count.
  - **TV:** the text scales with the set.
  - Geometry lives in one place, `DeskLayout.living` (`LivingPlan`). The painter, the tap frames
    (`DeskObject.frame(layout)`) and the cat spots (`DeskCatBehavior.spots(layout)`) all read it.
- **Picture (`DeskObject.PICTURE`).**
  - Tapping it opens a panel with "Choose a photo" (Android photo picker, no permission needed)
    and "Back to the painting".
  - `DeskPicture` keeps a JPEG copy of at most 720 px in `filesDir/desk/`. The original photo is
    never touched or uploaded.
- **Landscape (`DeskLayout.LANDSCAPE`).** Chosen when the screen is wider than it is tall. It adds
  a 180-unit wing on each side; the office and doorways don't move.
  - Bookshelf 156 wide (was 80).
  - TV 316 × 186 (about 2× the area).
  - Stereo 280 wide.
  - Bed 264 long (was 140), plus a nightstand and lamp.
  - The rooms are laid out clear of the side navigation (`startInset`). Portrait and the unfolded
    portrait screen keep `STANDARD`.
- **Tests:** `DeskLivingRoomTest.kt` covers bounds, overlap, sizes, tower climbing, the picture
  panel, and renders (`apartment-landscape-night`, `apartment-open-night`,
  `apartment-cover-living`, `apartment-cover-bedroom-day`).
