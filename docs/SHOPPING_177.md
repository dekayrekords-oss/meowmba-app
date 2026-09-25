# On-request store and deal assistance — preview 177

Owner request: recognize a store when asked and help find deals, without announcing store arrivals.

## Behavior

- “What store am I in?”, “Which shop is this?”, “What's on sale here?”:
  requests a one-shot fresh location, then checks retail POIs within 200 metres in the
  existing offline pack, or the already-configured Geoapify provider. No new provider.
- A nearby isolated point is described as **likely/nearby**, never proof of being inside.
  Adjacent shops, uncertain fixes, motion and missing data get an honest fallback.
- Location must be at most 30 seconds old, at most 60 metres uncertainty; a likely
  result requires at most 40 metres uncertainty and separation from other POIs.
  These conservative thresholds are engineering heuristics, not calibrated probabilities.
  Indoor/mall/large-building physical testing is still required.
- No arrival listener, geofence, background shopping task, camera activation, persistent
  store tracking, purchase, coupon enrollment or account sign-in is introduced.
- **Privacy default:** an inferred store name is not sent to search engines. The result
  asks the owner to say, e.g., “deals at Target” and explains the disclosure. Automatic
  on-request sharing of an inferred name is pending explicit owner approval.
- “Best deals on coffee at Target” / “deals at Fry's”: searches the existing keyless
  search providers for the explicitly named store/item and the current month/year.
  It never reads or appends GPS coordinates, address or store-visit history to that query.
- Known retailer domains are preferred and checked by hostname boundaries. Other stores
  work through name-matched evidence. Non-HTTPS/private-style links, irrelevant hits and
  explicitly historical/expired promotions are excluded.
- Results are **search leads**, not verified offers or guaranteed best prices. Snippets
  can be stale or incomplete. The app retains the excerpt, source URL and search date;
  reminds the owner to check stock, dates, product size, minimum spend and loyalty terms.
  No cart, private loyalty account, authenticated retailer app or checkout is inspected.
- Full results and tappable source links appear in chat; speech reads only the short
  answer and follows the existing quiet/driving/media rules. A search does not announce
  “I'll ask for approval” or start an unsolicited arrival message.
- Persisted assistant replies are now distinguished from SYSTEM/task progress in the
  compact chat view. “Paws finished” stays in history without replacing the answer or links.

## Boundaries and implementation

`ShoppingAuthorization` grants a single matching action from accepted human input,
scoped to the current conversation and 90 seconds. Recorded notes, model-generated
store names, task goals and earlier location guesses are not new user permission.
`StoreEvidence` keeps observed coordinates separate from inferred venue identity;
`DealEvidence` keeps source leads separate from verified price claims. This applies the
TypeSafe skill's evidence/uncertainty design without adding an API dependency or changing
the existing SemIf/DecisionEngine/Laya stack.

`PlaceRepository.nearbyShops` does not use navigation's widening-to-40-km fallback.
`WebSearchTool.search` exposes typed results and isolates individual engine errors while
preserving cancellation. Tool registration, routing and ownership use the existing phone
agent; Cozmo/LOOI ownership and runtime assets are unchanged.

## Validation / delivery

Final JVM suite: **3,028 tests, zero failures/errors, five existing skips**. Six emulator
UI checks pass, including saved source links after a task-progress message, intentional
send, avatar/app-completion and separate robot controls. Full daily lint: zero errors,
197 warnings, 12 hints. Preview signature, 16 KB alignment and 161 protected runtime entries
against preview176 pass; only generated ART profiles differ. Optimized cold launch: 536 ms,
same PID remains foreground afterward, crash buffer unchanged. Actual saved-chat smoke
test passes on the optimized APK. No physical phone installation authorized or performed.
No real user location or inferred store has been sent to a search provider during development.
No actual live deal or indoor-store accuracy claim is made by the fixture-based tests.
An intermediate emulator smoke test exposed the task-progress display bug; its APK is
retained as superseded preflight evidence, not a delivered artifact. The final check also
tests the transition from a live reply to a saved reply followed by a task-status message.

Final APK/evidence: Os workspace `work/store-177/`. Version 1.5.11 / 177, package
`com.example.meowmba`, SHA-256:
`dce9f1db761e6c8b214ff5c2129763620b740a6e2f591019039d8b1d95caaa3e`.
177 is consumed; next unallocated code178. Fold remains173. No source push performed.
