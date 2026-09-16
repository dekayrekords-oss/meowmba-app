# Build 109 — neural and avatar setup

## What changed and why

The owner requested understandable neural settings and a cleaner avatar setup.
`NeuralSetupControls` now presents control authority first: Classic (LEGACY),
Shadow comparison and Neural control each have an explanation beside their
selectable row. Recurrent and spiking choices explain processing tradeoffs and
that both are synthetic. The page explains reflex sensitivity, camera/mic
independence, animation FPS and thermal throttling. Optional trace/reset controls
explain their effects before use. Computer and task controls remain available
under a disclosure instead of preceding the avatar settings.

The existing ViewModel persistence callbacks are unchanged. Opening settings does
not change the selected mode, start tracing, enable sensors or reset learning.
No animation clock, timer or new neural loop was added. Diagnostic information
continues to use the existing once-per-second snapshot.

Avatar setup has Look, Space, Behavior and Animations tabs. Space owns mode and
size; Behavior owns expression and playfulness; Animations retains search and
the saved performance library. App appearance and diagnostics remain optional.
Whole-fly users no longer see unrelated floating-animal or fixed-palette color
controls. The old claim that tapping an eye enables the camera was corrected:
avatar gestures interact; the video button owns the camera; swipe down opens
the cards. These are UI organization changes, not a renderer redesign.

## Validation and installation

On Meow, `CompanionSetupUiTest` (2) and `IdleBehaviorTest` (6) passed with no
failures, errors or skips. Tests cover callback authority, no side effects from
opening the page, hidden optional tooling, tab separation and selection
preservation. Narrow 360dp host renders were inspected; the preview slot is
intentionally empty in that test harness. Both real phone pages were then
opened and inspected, including the live avatar preview and Space tab.

Debug 109 / name 1.3.81 was installed in place on SM_F966U1, RFCY61NJEVM.
Package manager lastUpdateTime: 2026-09-13 14:11:26. The phone's existing Classic
mode, recurrent selection and disabled mic/camera were preserved during menu
inspection. Build 109 also includes the prior committed Brain tile, which had
not reached the phone in the previous transfer attempt.

APK SHA-256: cbc661d78e22a3a83fcbe6375291f15901d1137b83b66a791e626d5ca30b701f.
Certificate SHA-256: b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff.
The existing lossless delta script reused 430,965,795 bytes from verified build
108. Only 3,699,926 patch bytes crossed the slow link. Reconstruction checked the
base, every reused block and final signed APK hash. Future Meow agents should
reuse this workflow instead of restarting 439 MB transfers or splitting APKs
into many parallel transfers. The previous partial downloads are ignored build
artifacts and must not be installed.

## Remaining prior work

No new endurance pass is claimed: build 108's sustained-motion gate failed
because of thermal pauses, despite obtaining real battery measurements. The
paused-render instrumentation test is compiled but has not yet been run; no
renderer optimization based on that hypothesis is claimed. Biological synapse
to muscle control remains research, not the running avatar controller. The
owner has no robot available, so physical transfer stays deferred and disabled.
See the build 108 passdown for the underlying evidence and research provenance.
