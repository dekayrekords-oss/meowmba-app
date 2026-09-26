# Facebook composition and stable previews — 184 / 1.5.18

Owner report: requesting a Facebook post produced a browser preview instead of a
Facebook composer, then the preview glitched. September 26, 2026.

## Evidence

Fold R5GL75XFCXD reports installed preview183 / 1.5.17. Claude's apartment branch
24ccc63 was merged before this work, preserving the installed features. Existing
BUILD_MASTER changes were committed intact first; the partial KWS download is untouched.

Locally captured logcat includes September26 13:38–14:14 and old crash-buffer entries
from September25 14:44 and17:08 (the previously repaired WakeWordService startup fault).
No new Meow fatal exception in this capture. Browser tile-memory warnings at13:52
belong to a WebView renderer started for Google Play services; they do not establish
that Meow's preview failed. The exact reported interaction/time is not yet established.
The installed optimized app is not debuggable; run-as cannot retrieve its private chat
or audit database. No credentials, account data or raw logs are published.

Code confirms two independent defects: social routing missed “make a Facebook post”,
and BrowserRequestParser treated a post request containing “page”/“website” as HTML
creation. BrowserAgentLoop also reset steps to zero after done and after action limits,
then resumed after45 seconds, recreating the initial scaffold. Errors retried indefinitely.

## Changes

- Explicit social composition goes to phone navigation before browser creation or
  question answering. Platform mentions in the post body cannot change the destination.
- Facebook tasks first navigate to the signed-in app's actual profile/Page switcher.
  The existing planner proposes only observed indices; code validates the app, picker,
  visible enabled controls, unique identities and bounded entries. A native overlay asks
  which Facebook Page/profile to use. More pages scrolls the actual picker; Cancel stops.
  This uses Accessibility, not a Meta Graph API connection or a fabricated account list.
- Choosing a destination is per-run. Stale selection, missing account identity or an
  ambiguous screen stops safely. Draft text is entered only after the selected label is
  visible. The final posting action retains existing human confirmation/manual handoff.
  A draft request alone does not authorize publishing. No Facebook test post was sent.
- Browser completion/errors/action limits terminate the loop. Wait/say turns are bounded
  too. Cancelled late model replies cannot mutate the page or clear a replacement goal.
  The initial scaffold is loaded by one owner rather than twice.

## Limits / acceptance still needed

The picker shows only Facebook's currently accessible destination rows. It is not a
complete API inventory of every business asset. Facebook layout/localization changes,
composite labels or inaccessible controls can require manual handoff. Live signed-in
account selection/composer delivery have not been accepted on the Fold. Do not claim
Facebook publishing is verified from JVM tests. No phone install is authorized in this
request; leave183 and its settings/data intact until the owner asks to install.

## Validation

Implementation commit `6bbc098`. Full optimized build succeeded in42m40s.
3,117 JVM tests: zero failures/errors, five existing skips. Lint: zero errors,
208 warnings and12 hints. Matching preview signature and16KB ZIP alignment verified.
All existing native/model files unchanged. Strict asset verifier reports only the
intentional user-manual update; its packaged bytes match source. No baseline weakened.
APK SHA256: `d8284c1e5b5d0c92a8b5142e674e486ae3761b55904a61989d5e05805a90e358`.
APK and full receipt: Os workspace `work/facebook-repair-184/`.
Not installed on phone or emulator; live Facebook selection/composer acceptance pending.

The browser classifier also treated any mention of “page” as a new build request;
it now uses the explicit browser-request parser. Regression coverage verifies inspection
leaves the preview alone, completion never restarts it, errors do not repeat, wait loops
are bounded, and late cancelled replies cannot mutate a page or clear a replacement.

