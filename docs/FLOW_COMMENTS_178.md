# Guided access and contextual Flow comments — preview178

Owner request: guide users through calendar-write/tool/Gmail access, and offer
Reply / Later / Comment so a comment such as “this event is canceled” can change
the actual calendar occurrence.

## Using it

- Flow → Adjust → Connect apps & tools; or Settings → Connect apps & tools.
  Calendar, notification and usage connect cards also open the guide. Existing
  activity/location setup paths stay unchanged.
- Each step explains the scope, opens Android's relevant screen, and rechecks
  access on return. Calendar requests READ_CALENDAR and WRITE_CALENDAR together.
  Denied/permanently denied access has an App permissions fallback. Every step
  is optional; the app never grants itself permission.
- Gmail's step opens the installed Gmail app for the user's own sign-in. It
  distinguishes draft handoff (the user taps Send), notification previews and
  optional Accessibility-based on-screen tools. There is no new Gmail OAuth API,
  inbox grant, account inspection, silent sign-in or unrestricted app control.
- Main/more/attention/interrupt Flow cards have Comment beside their existing
  action and Later. Message items retain Reply; calendar items retain Open.
  The third Android notification action is Comment and opens that exact card's
  composer, including on cold launch. It does not mark the card done or run a tool.
- Type a note, then Save comment. The last comment is stored in the Flow item's
  existing Room facts and survives source refresh/restart. It is not sent to the
  message sender. This release does not interpret arbitrary comments as actions.
- On a calendar card, “this event is canceled”, “this meeting has been cancelled”,
  or “cancel it” changes the submit label to Cancel this occurrence. The composer
  shows the occurrence/date and explains the single-occurrence change before
  submission. Questions, negations, quotations and whole-series requests do not
  enter this destructive route. Typing alone never runs it.
- Missing access leaves the event unchanged and links to the guide; the draft
  remains while the guide is open. Granting permission does not resubmit a change.

## Identity, verification and persistence

CalendarSource retains provider EVENT_ID, full title, occurrence start and end.
Calendar Flow IDs include event ID and start, so identical titles/times in two
calendars do not collide. Unique legacy title-hash cards keep their Later/Done
state during the transition. Ambiguous legacy identities are not transplanted.

The selected card is rechecked before execution; the provider is then reread and
must still match event ID, title, start and end. Read-only, revoked, stale, missing
and ambiguous events fail without a write. Repeating events use only an exception
for the selected occurrence. Provider status is reread before success is shown or
the corresponding Flow reminder is retired. Other occurrences remain active.

Model-facing calendar tool arguments remain original-query only; the exact-target
entry point belongs to deliberate UI submission, not model-generated IDs.
Sync, comment saving and cancellation retirement serialize managed writes. User
comments are preserved separately from refreshed provider facts.

The TypeSafe skill's live state/function-calling guidance informed the separation
of user instruction, provider evidence, target identity and execution authority.
No paid API, new model provider or change to the existing SemIf/Laya stack is added.

## Validation and delivery

Final build and validation passed; see Os workspace `work/flow-access-178/validation/`.
Full JVM: **3,043 tests, zero failures/errors, five existing skips**, across472 suites.
Eight emulator UI checks pass in45.743seconds: comments wait for deliberate submission,
Reply/Later/Comment callbacks remain separate, guide scrolling/close, busy-state duplicate
submission prevention, and existing autocomplete/avatar/source-link behavior.
Daily lint: zero errors, 200warnings,12hints. Three added warnings are nonfunctional
UseKtx suggestions (`Uri.parse` versus `String.toUri`), reviewed and retained.

The actual app's setup-card route opened the guide; its notification-style deep link
opened the exact comment composer in both debug and optimized variants without submitting
anything. Screenshots and UI XML are in the receipt. Optimized COLD launch2485ms,
samePID31865 remains foreground after36seconds, and no new crash-buffer entries.
An additional attempted cold comment-route check reported WARM, so it is warm-route
evidence only; cold-start timing is from `optimized-launch.txt` without that extra.
The emulator test app was stopped afterward without clearing data.

The first focused run compiled and ran162 checks: the new checks passed; an older
night-paper screenshot assertion sampled relocated text. It now samples the
empty paper margin. An initial daily-unit-test command was unsupported; JVM
checks use the project's debug test variant. The intermediate combined build
was deliberately interrupted to include notification Comment routing in the
same final source snapshot. Neither intermediate result is release evidence.

No new physical-phone installation, real calendar mutation, account sign-in,
permission grant, source push, public map download or inferred-store disclosure
has been performed. Fold remains last verified177. Preview178 / 1.5.12 is consumed;
next unallocated code179. Final immutable APK: Os
`work/flow-access-178/meow-preview-1.5.12-code178.apk`, package `com.example.meowmba`,
368,509,121bytes, SHA256 `fb1fe9472bcea72b45b0956ab90e626fc05b734eb34693cdca30fb48f3a9647c`.
Preview certificate `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`,
16KB alignment and all161 protected runtime entries versus177 pass; only ART profiles differ.
Full build/lint completed in13m4s with existing MWDAT/private-properties/native flags,
8GB Gradle heap, two workers and no parallel projects. No source push performed.
