# Meow website additions and private Android source — passdown

## Owner instruction

Do not upload any Android app code to public GitHub. Added this persistent rule
near the top of AGENTS.md. Linking the public project profile is not permission
to publish Android implementation. Local commits remain allowed. No GitHub
upload, push, source transfer, or remote change was performed in this task.

## Published change

Updated https://www.dekayrecords.com/meow-assistant.html through its existing
Weebly Custom HTML block. Added #mx-new before #mx-launcher, after the hero and
feature band, using the existing mx-section/mx-features/mx-feature styles.

Headline: “A little more life. A world of its own.” Six summaries cover:

- Cartoon controls, clearer card menu, warm light/deep teal appearance.
- Painted/3D habitats and interactive book/water/ball; quieter cartoon rooms.
- Optional feeding/play/rest care, self-sufficient when disabled, no punishment.
- Full 3D fruit fly and Lumen floating fruit-fly features.
- Look/Space/Behavior/Animations setup tabs and development sizing up to 300%.
- Classic/Shadow/Neural settings and experimental BANC circuit with synthetic
  dynamics; explicitly not a complete biological brain or robot controller.

Clearly labeled September 2026 development preview and warned that Play beta
availability differs. Build 109 is last documented phone install; 110/111 work
is not proof of a Play rollout. Sources: build108/109/110/111 passdowns,
PET_CARE_PASS_DOWN.md, and confirmed PetCarePanel integration in CompanionScreen.

Added links to the owner-supplied technology YouTube channel and Projects page.
Existing content, hosted design assets, and beta enrollment links were retained.
Only website copy/HTML was transmitted to Weebly; no Android implementation.

## How it works and maintenance lessons for Meow

The editable block is a small loader for existing mx-page-v2.css/js, not the
whole rendered page. Keep that loader and background fixes intact. A template
with id mx-new-template holds the added section. A small website-only script
clones it into #meow-experience main before #mx-launcher. It runs immediately
and once on DOMContentLoaded; the #mx-new guard avoids duplicate insertion.
Future changes to the upstream page loader may require revisiting the anchor.

Use Pages > Meow Assistant > Build. Click the customhtml-blocker of the existing
embed, then Edit Custom HTML. In this session AX click on the actual button and
setValue on the source textarea worked; immediate Playwright action snapshots
sometimes showed the prior state. Observe after settling, not only after click.
The textarea had id 330605802175941111CustomHTML (reinspect before reuse).
Click Build to blur/save, then Publish. A prior completed publishing overlay
intercepted clicks; reloading cleared it without losing saved content. Narrow
editor popovers can be clipped; temporary viewport override was reset afterward.

## Verification

Weebly confirmed Website Published. Reloaded the live page and read all six
summaries and release caveats from #mx-new; verified both link destinations.
A 1280px screenshot showed the integrated heading and first feature row with
readable text and the inherited design. No new artwork or app screenshots were
uploaded. No broad device matrix or app tests were run for this website-copy
change. The preexisting shipping-plan upgrade notice did not block publication;
no plan or store setting was changed.

Commit only AGENTS.md and this report locally. No public push. Continue to teach
future agents via this record; no direct message to the server AI was sent.
