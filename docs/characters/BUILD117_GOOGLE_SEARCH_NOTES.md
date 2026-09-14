# Build 117 — Google app drawer search handoff

## What changed and why

The app drawer previously labeled a button Google but sent submitted web searches
to `showSearchInBrowser`, which prefers Chrome. `searchDrawerQuery` now delegates
to `GoogleAppSearch` and reports the actual outcome through the existing drawer
answer card. The drawer closes only after Android accepts the launch. Failed
searches retain the drawer and typed query with instructions to restore Google.

`GoogleAppSearch` first sends `ACTION_WEB_SEARCH` with `SearchManager.QUERY` to
`com.google.android.googlequicksearchbox`, the Google app. If that entry point is
unavailable, it tries a Google search HTTPS URI scoped to the same package. Every
resolved activity must belong to Google, be enabled, be exported, and have an
enabled application. Both routes use `FLAG_ACTIVITY_NEW_TASK` because the ViewModel
holds an application context. URI construction preserves Unicode and literal
query punctuation instead of letting `&`, `#`, or `+` become URL structure.

The existing Google package visibility declaration serves both speech discovery
and drawer search. A comment now records that dependency; no broad package-query
permission or duplicate declaration was added.

App filtering while typing, unique app launches, Ask Meow, phone tasks, and the
microphone path retain their existing callbacks and `DrawerQuery` classifier.
`submitDrawerQuery` and generic `showSearchInBrowser` are unchanged. No external
app is launched by text changes. There is no silent Chrome/WebView fallback and
no automatic Google installation, account operation, or new permission request.

## Verification and limits

`GoogleAppSearchTest` adds eleven focused Android host tests for actual outgoing
package/action/query, Unicode and URL delimiters, package-scoped compatibility
fallback, unavailable/disabled/wrong-package/unexported targets, security denial,
the resolve-versus-launch update race, and empty input. Keep `DrawerQueryTest` in
the Build 117 test selection to preserve all classifier routes. Root owns build
execution and records the final results in the main passdown.

The phone is disconnected. Tests verify Android intent construction and handling,
not the particular installed Google app version or Google search results. A
successful result means Android accepted an intent to the Google package; it does
not prove network results loaded or that Google's own UI will never use a custom
tab internally. No launch-success caption is emitted after a failed start.

## Teach Meow: diagnosing future search failures

1. Confirm the drawer query classified as Web or the explicit Google action was
   selected. Questions still intentionally ask Meow; typing filters locally.
2. Inspect Google installation and enabled state in the current Android profile.
   Android 11+ package visibility depends on the existing targeted `<queries>`
   package declaration even when the Google app lacks a launcher entry point.
3. Resolve `ACTION_WEB_SEARCH` plus `query` within the Google package. Do not
   hardcode an internal Google activity class; those are not a stable contract.
4. If that route is absent, resolve the package-scoped HTTPS search URI. A null,
   unexported, disabled, or foreign resolver is not permission to use Chrome.
5. Package updates can remove an activity between resolve and start. Catch
   `ActivityNotFoundException`; try only the supported same-app URL route.
   `SecurityException` stops immediately and surfaces a restriction explanation.
6. Keep the drawer and query visible on failure so the user can fix Google or
   choose another existing search-bar action. Do not claim to have searched if
   no Google activity started.

## Primary references checked September 13, 2026

- [Android common intents: web search](https://developer.android.com/guide/components/intents-common#SearchOnWeb)
  documents `ACTION_WEB_SEARCH` and `SearchManager.QUERY` and resolving a handler.
- [Google's Play Store listing](https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox)
  identifies the Google app package. The HTTPS compatibility route is a runtime
  capability check, not an assertion of a permanent Google-specific API.
