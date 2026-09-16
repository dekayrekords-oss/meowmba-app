# Logic control and restart reliability — 1.3.64/code68

Installed in place on the connected Samsung SM-F966U1 at 14:25:14 on September 9.
Package version, signing identity and APK hash were verified; app data was preserved.

## Delivered

- Project creation now persists a reservation before the database insert and
  reconciles incomplete work after a restart. A failed workspace step or crash
  after database commit does not create a duplicate project on retry.
- The Mac bridge has typed Logic inspection/edit/receipt tools, with actual
  target/value checks and persistent receipts preventing duplicate execution.
- Native tests in a new empty Logic 12.3.1 project verified exclusive selection,
  mute/solo, pan, volume, opening plugins, Controls view, Channel EQ changes,
  and third-party StudioAI output trim changes/restoration.
- Plugin support uses the common Controls view, without a manufacturer list.
  Unsupported controls and ambiguous duplicate instances are excluded.
- WAV verification decodes samples and reports format, duration, source hash,
  silence, sample peak, RMS and full-scale samples. It does not certify a master.
- Approval cards show full bounded Logic target fields. Settings distinguish
  installed adapters, enabled control and actual project readiness.

## Validation

Full Android suite: **2,021 tests, 2,016 passed, five skipped, zero failures/errors**.
Python bridge: **55 passed**. Debug APK assembled and signature verified. All
**125 native/asset entries** from 1.3.63 are byte-identical. Source hashes were
checked around packaging. LAN fetch was attempted but timed out; remote sync is
not claimed. No commit or push was performed.

Native receipts and captured plugin-control fixtures are in this release folder.
The selection hit test also rejected a track that had scrolled under the header;
after revealing it, selection verified. It did not click the obstructing control.
Earlier uncertain receipts remain as diagnostic evidence, not successful tests.

## Remaining work

The Mac helper is installed at `/Users/prog/Meowmba-Desktop-Bridge`, with a new
output workspace `/Users/prog/Meowmba-Logic-Workspace`. Pairing with the phone and
phone-to-Mac acceptance are pending phone availability. The phone was in Facebook,
so further UI interaction was paused and the user was asked to leave Meowmba open.
The current helper process is a test session, not an installed login service.

Full region/timeline editing, plugin insertion/replacement, buses/sends,
automation writing, LUFS/true-peak analysis, mastering and verified bounce delivery
are not complete. Two tested plugins do not prove universal plugin compatibility.
An in-flight native parameter edit has a 20-second bound; phone cancellation does
not yet interrupt that adjustment. Query its receipt before another edit.

Previously reported outstanding items remain: a supported live traffic provider,
all-day background notes, broad service/inbox/business integrations, real-device
voice accuracy/latency acceptance and arbitrary newly generated prop geometry.
Physical robot control remains quarantined. No user song, account transaction,
external message, purchase or robot movement was used in testing.
