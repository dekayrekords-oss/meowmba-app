# Wireless avatar and launcher onboarding — preview181

Owner requests: open the face/avatar on a magnetic wireless charger; offer Meow as
the replacement home-screen launcher during onboarding.

## Behavior

- Settings → Phone & device → Wireless-charger avatar is an explicit, persisted
  opt-in (off initially). It uses Android's wireless-power bit, not battery
  percentage or charging status. USB, AC-only, docks without wireless power and
  plain magnets do not trigger it. It cannot identify a particular charger.
- Process-wide battery/screen/unlock broadcasts; no polling, new foreground
  service, microphone, sensor stream, wake lock or automatic permission request.
  750ms settling; short disconnections under five seconds do not reopen it.
  Repeated battery updates and FULL status do not reopen a dismissed avatar.
- Foreground navigation reuses the menu's **Show avatar** path, including switching
  Flow/Desk to Companion while preserving the selected form and privacy settings.
  Unplugging does not close it; the screen is not kept awake.
- While locked/asleep, navigation waits for unlock/wake and is cancelled on
  disconnect. First-run onboarding is not interrupted. Rerun setup is not closed.
- Background opening has a separate opt-in and requires user-granted Android
  Display over other apps access. Without this, a silent tap-to-open notification
  is offered (requires notifications enabled). A notification is also retained as
  fallback if Android refuses a background start, and removed on foreground entry.
- Android can stop or suspend the process/delay cached-process broadcasts. This
  is not an always-on system screensaver and does not work while force-stopped.
  Reopening Meow while wirelessly powered starts a new process session. A real
  Samsung charger/unlock/background acceptance check remains required.
- Onboarding offers an optional Home screen page after the device check, including
  the Finish later path. Set up opens Android's HOME role chooser; Not now proceeds
  without changing the default. Status is read from RoleManager after return and
  resume, never inferred from tapping the button. Existing home selection is
  recognized; changing back is explained. No default launcher is set via ADB.

## Platform references

- [Battery power source](https://developer.android.com/reference/android/os/BatteryManager#BATTERY_PLUGGED_WIRELESS)
- [Background activity restrictions](https://developer.android.com/guide/components/activities/secure-bal)
- [HOME role](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_HOME)

## Validation

Policy, lifecycle, Compose onboarding and settings tests plus emulator native UI
checks are included. Final build/test/signature/runtime evidence and limitations
belong in the immutable Os `work/charging-181/` receipt. No physical phone install,
permission grant, default-home change, synthetic phone battery state or robot action
is authorized by this implementation request.
