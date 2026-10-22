# Double-tap Back — 204 / 2.0

Based on verified/internal-published 203. Owner reports double-tap Back failing.
Branch: `fix/ring-double-tap-204-20261003`. Codex remains sole build owner.

The end-to-end service tests reproduced three failures in 203: two released taps
40 ms apart resolved as a single tap; reused downTime suppressed a second released
tap; subsequent native Back presses with reused downTime were suppressed.

204 tracks key-down identity only while the key remains held and clears it on
release. It accepts fast, separately released taps and reused timestamps, while
suppressing held repeats and overlapping aliases for the same gesture. The
existing 450 ms multi-tap window, exact gesture verification, consent, allowed
apps, pause and unlock gates remain. A sleeping ring still consumes its first
double tap as wake only; the next double tap can perform the configured Back.
The Media preset still maps double tap to Stop; Navigation/Photo map it to Back.
Custom bindings remain user-controlled. Existing 203 consent and settings remain.

45 focused ring/UI cases and 25 actual public-release cases pass (70 total,
zero failures/errors/skips), including nine service-level Back cases in both
configurations using the real Robolectric service/Handler and platform action
capture. Final debug lint passes: zero errors/fatal issues, 234 warnings.

204 was installed in place on the owner's Samsung Fold with the full Meta SDK,
matching preview certificate, retained original first-install date and app data.
The app launched and retained the same PID with no new crash-buffer entries after
101 seconds. 16 KB ZIP alignment passes; all 161 protected phone runtime entries
match the actual installed 203 baseline. A read-only settings check found consent
accepted, controls unpaused, Navigation selected and Double tap mapped to Back,
but Double tap was not verified. Live service diagnostics show the ring connected,
not paused/testing, and currently asleep. Owner must use Gesture tester to physically
register Double tap, stop testing and unpause. Physical Back acceptance remains
pending; it is not inferred from automated cases or automatically marked verified.

The full-SDK, minified Play bundle is signed with the existing matching upload
certificate. Bundletool, signature verification, all 88 native 16 KB/JNI checks
and all 758 unchanged payload entries pass. All 161 protected public runtime
entries are unchanged from signed 203. Source is frozen at 3eee3f36ad16c146275a1f9e0d8e60e626788c8a.
Signed 204 is **Available to internal testers**, verified on the Play track as
“2.0 (204) – Double-tap Back fix”, released October 3 19:30 (Console display).
Release details: https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/tracks/4701352764658866051/releases/5/details
No 204 production submission. A fresh production check still shows 201 In review;
there is no confirmed public production download. Real reviewer recording remains
pending. Listing/privacy/declaration purposes are unchanged from 203.

Integration branch fetched from private GitHub; no missing commits. LAN origin
remains unreachable (SSH connection timed out). Android
source may only sync to the verified private repository, never public GitHub.
