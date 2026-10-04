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

45 focused ring/UI cases pass, including nine service-level Back cases using
Robolectric's real service/Handler instance and platform action capture. The
phone APK is assembled with the full Meta SDK and matches the installed signing
identity. 16 KB ZIP alignment passes; all 161 protected runtime entries match
verified phone 203 without exceptions. Phone disconnected; 204 not installed yet.
16 actual public-release boundary cases pass; additional Back service cases
will run with an explicit test-filter addition (the normal release suite includes
only the three Play boundary classes). Final lint and signed bundle remain underway.
No 204 Play upload or production submission. 203 remains available to internal
testers; 201 remains in production review. Real hardware retest/reviewer recording
remain pending. Listing/privacy/declaration purposes are unchanged from 203.

Integration branch fetched from private GitHub; no missing commits. LAN origin
remains unreachable (SSH connection timed out). Android
source may only sync to the verified private repository, never public GitHub.
