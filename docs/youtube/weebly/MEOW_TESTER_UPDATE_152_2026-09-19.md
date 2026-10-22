# Meow Assistant — Weebly tester update publication

Published September 19, 2026, under the owner's explicit request to update the existing
Weebly website with the newest app features. App: 1.3.96/code 152; Play Alpha submission
is in review, not a production release or confirmed tester availability.

## Destination and verification

- Existing site: https://www.dekayrecords.com
- Updated page: https://www.dekayrecords.com/meow-assistant.html#mx-release152
- Weebly site ID: 909962486213826644.
- Weebly returned “Website Published!”. The live public page was then opened.
- Desktop screenshot and accessibility text confirmed the new heading, six feature
  cards, all five named scenes, submission status, Play-edition boundary and links.
- Expanded “What we verified—and what testers should try” on the live page and checked
  the 2,708/2,703/five-skipped counts and the qualified 150-to-151 CPU comparison.
- Checked the existing model-capability FAQ; it states that capabilities depend on
  the model and connected tools, and catalog presence does not promise every feature.
- Separate mobile layout validation was not performed in this pass.

## Published content

Headline: “A steadier companion. More ways to create.”

1. Avatar creation: progress, review, retry, cancellation and approved output.
2. Five environments: Saffron dunes, Alpine cableway, Coral twilight, Lantern courtyard,
   Crystal cavern.
3. Fresh setup: microphone/camera off when skipped or denied, retained after restart.
4. Computer results: verified small-file Save & preview, Files access, preservation of
   older copies and improved Termux/Ubuntu setup recognition.
5. Driving interface: compact summary, scrollable details and priority alerts, without
   a driving-safety certification claim.
6. Reliability: speech cancellation/idle crash fixes, reduced redundant avatar redraws,
   Android 12/Bluetooth compatibility and optional bounded diagnostics.

The verification disclosure includes the tester checklist, remaining acceptance work,
provider requirements and the limits of the CPU result. It does not claim all-day battery
life or unrestricted hardware support. Play availability is explicitly subject to Google
review and rollout.

Existing copy about autonomous phone taps/swipes and learned navigation was corrected.
The Play edition supports app launching and drafting; autonomous Accessibility screen
control and learned-screen replay are unavailable. The old learned-navigation section
now reads “Ask. Approve. See the result.” and describes configured, approved Computer
tasks and verified file results. The old “Learn the steps” heading is absent from live text.

Verified destinations:

- https://groups.google.com/g/meow-assistant-beta
- https://play.google.com/apps/testing/com.meow.meowmba
- https://www.dekayrecords.com/watch.html

## Implementation and maintenance

Updated the existing Meow Assistant Custom HTML block under Pages → Meow Assistant →
Build. Preserved the existing mx-page-v2.css/js loader, background fixes, earlier
mx-new-template section, hosted artwork and media. Added a website-only template and
bounded DOMContentLoaded/load handler that inserts #mx-release152 before the earlier
feature section and makes the specific capability-copy replacements. Duplicate-insertion
guards prevent repeated sections. The saved editor HTML was read back and matched before
publication. Future upstream loader changes should recheck these anchors/replacements.

Only website copy/HTML was transmitted. No Android implementation, secrets, new artwork
or personal diagnostics were uploaded to the website. No public GitHub push was made.
The preexisting Real Time Shipping plan-upgrade notice did not block publication; no
plan, payment, store or account settings were changed.

App validation and remaining release gates are recorded in
[the detailed tester report](../../store/TESTER_UPDATE_152_2026-09-19.md).
