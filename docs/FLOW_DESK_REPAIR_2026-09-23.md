# Flow + Desk repair after build 157 review (2026-09-23)

Owner report: "the interface does not feel like what you made", "the desk is not usable, you can't click
anything", "the desk should be a mode like Professional and Flow".

## Root causes found
- Flow was starved of data: only intentions captured by a model reached it; with Laya/Jev off, nothing did.
  Reminders, projects, calendar and messages never became items, so Flow was always blank.
- Codex's bottom nav bar and a Material dialog broke the paper/ink look.
- The night look was shown in the morning (OFF work state treated as evening).
- Desk panels waited for the cat's walk and were gated by `covered`; taps felt dead. Objects showed placeholders.
- Desk was an avatar style, not a mode.

## Fixes
- `AppIntentions` builds Flow items from app data: user reminders (≤12 h), the latest project, calendar
  (24 h), setup "Allow" items for missing access. Synced every foreground minute via `FlowRuntime.syncManaged`,
  which keeps user choices (snooze/done) and closes stale managed items.
- `FlowNotificationHub`: RoadAlertListener forwards sender + app label (never message text) → "Reply to X".
- Rules-only chat capture for explicit phrasing ("remind me to…", "I need to…", "…is out").
- `WorkEstimate.beforeWork`: early morning is not evening. Weather is an ambient signal.
- Dateline links MENU · DESK · ADJUST replace the nav bar; paper-style composer + adjust sheets.
- Items use their own action label (Open / Allow / Reply). The interrupting item is not repeated below the band.
- Desk is `AppTheme.DESK` (menu tile + Flow dateline); stored avatar "desk" migrates. Panels open on tap;
  every object shows real items with Done/Later.
- `flowRuntime` init no longer crashes chat when there's no context (test doubles).
- CartoonInterfaceUiTest labels updated to "Meow menu" / "Close menu".

## Verification
- `:app:testDebugUnitTest` 2846 tests, 0 failures; `:app:lintDebug` clean.
- Renders: `build/flow-shots/flow-phone-morning.png`, `desk-mode-01.png`, `desk-mode-02-tray.png`.
- NOT installed on the phone: per BUILD_MASTER, Codex integrates and installs.

## Owner action for full value
Grant notification access and usage access (Flow shows "Allow" rows that open the exact screens).
