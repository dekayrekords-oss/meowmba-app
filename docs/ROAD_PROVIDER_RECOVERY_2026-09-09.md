# Road provider failure recovery — September 9, 2026

## Observed cause

A read-only network check from this computer, using the existing Austin test
area (not the user's GPS), returned HTTP 403 from
`https://www.waze.com/live-map/api/georss` and HTTP 404 from the legacy
`https://www.waze.com/rtserver/web/TGeoRSS` fallback. The app replaced the
original denial with the fallback's 404. No credential or token was used.
This confirms a provider failure path; it does not establish phone-specific
connectivity or prove that every region returns the same response.

Waze's current official [traffic feed specification](https://support.google.com/waze/partners/answer/13458165?hl=en)
describes a tokenized Partner Hub feed for approved Waze for Cities partners
and approved managed areas. The published [feed access instructions](https://support.google.com/waze/partners/answer/10618035?hl=en)
require Partner Hub access. There is no documented drop-in anonymous URL
replacement established by this review. Live police/traffic service remains
unavailable when Waze denies the request; use the Waze app for its navigation
reports. A supported licensed provider connection remains necessary for
reliable app-native live traffic coverage.

## Implemented changes

- Removed the retired TGeoRSS fallback. Keep the original provider reason and
  show an actionable navigation fallback rather than an unexplained final 404.
- Cache Waze failures across poller restarts: access-denied/missing endpoints
  wait 30 minutes; rate limits honor bounded Retry-After seconds or HTTP dates.
  Successful responses are fetched for the current GPS, not reused for another
  area. Invalid coordinates never trigger a request.
- Keep provider failures separate from successful empty report lists. HTML or
  error responses with HTTP 200 remain invalid results, not an all-clear.
- Isolate exceptions inside asynchronous provider jobs. Previously one thrown
  exception could cancel the parent poller and the other provider before await's
  catch ran. OSM camera reports can continue during Waze failure.
- Respect provider cooldowns during polling and avoid precise coordinate URLs
  in HTTP failure logs. Requests identify the app rather than impersonating a
  browser session.
- Use the [operator's current OSM Overpass endpoint](https://overpass.kumi.systems/)
  at `https://overpass.private.coffee/api/interpreter`, with the independent
  `overpass-api.de` fallback. Kumi is the renamed same service; trying it and
  Private.coffee as two independent providers repeated work during outages.

A single live OSM request timed out on this computer. No live camera coverage
or detection accuracy claim follows from parser or simulated polling tests.
The existing freshness, GPS accuracy, heading and report-age filters remain.
These are community reports and mapped cameras, not a radar/police detector.

## Validation handoff

New `RoadProviderRecoveryTest` covers denial preservation, no legacy fallback,
backoff and recovery, bad payloads, invalid GPS, Retry-After handling, unique OSM
providers, async exception isolation, and independent source refresh. Integrator
must run this class plus `RoadFeedTest`, `RoadFeedPollerTest`, and
`RoadReportPolicyTest`, followed by the full release checks. This worker did not
run Gradle, assemble, install, access the phone, enable sensors, or operate any
physical device.
