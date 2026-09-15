# Server and app capability acceptance — 14 September 2026

The owner requested real browser, SSH and multiple-agent verification, a
portable full fly-brain package, and installation on Meow with phone access.
This work uses the full annotated MaleCNS graph, not a claim of whole-animal
biological fidelity. See [the package guide](SERVER_PACKAGE.md).

## Installed service and portable package

- Host verified through SSH: `meow`, LAN `192.168.1.69`, NetBird
  `100.77.97.236`. These are interfaces on the same server.
- Persistent user service: `meow-neural.service`, enabled with existing user
  lingering. Bound exclusively to the private VPN interface on TCP 8785.
- Installation: `/home/prog/.local/share/meow-neural-server`, release
  `a02db86beb543253`. Configuration, certificate and paired-phone secret live
  separately in its private `state/` directory. Restart preserves pairing.
- Uploadable archive: `meow-neural-server-20260914-r2.tar.gz` on Meow under
  `/home/prog/research/`; local delivery file is
  `/Users/prog/Meowmba-Releases/neural-server-20260914/meow-neural-server.tar.gz`.
  Size 102,736,309 bytes; SHA-256
  `31b1180c4f0d0f63fb8a358ca02a1eee90d35b60062ee857662168aa95cf7eea`.
- Includes the full and retained graphs, verified Java worker classes/source,
  licenses, provenance, checksums, installer and HTTPS acceptance tools.
  No Android source, keys or existing pairing records are in the archive.
- Only ping and neural step/stop are exposed after HMAC authentication.
  Desktop/file/shell/robot commands are rejected. Pairing needs a private,
  single-use, ten-minute payload. TLS is pinned in the Android client.
- Service request threads are bounded and have timeouts. Silent TLS clients
  cannot block acceptance of other clients. The model has one worker and
  stops after 15 idle seconds. The idle service measured about 18 MB memory.
- The installer was exercised twice, including a version update, then the
  service was restarted and the phone successfully used its saved pairing.

The phone's LAN attempt failed with `EHOSTUNREACH`. Its existing NetBird
connection reached Meow, so no temporary laptop/USB relay is required for this
neural connection. The phone must remain connected to that private network.

## Measured graph and phone behavior

The asset SHA is
`8860e68b59e454d8d4e8bd601dd7a3cb9049d18516f1ea8df34eabe1ae400d08`:
176,422 annotated neurons, 25,862,574 directed pairs, 125,024,863 anatomical
synapse counts aggregated into weights. Unannotated endpoints are excluded
and accounted for in the included provenance.

Both graph profiles passed the eight-scenario, 40-tick worker checks, including
quiet, food, touch, looming, directional motion, duplicate caching, deterministic
reset and profile-change rejection. Full compute p95 was 255.780 ms; retained
116.654 ms. The authenticated full-service run passed 124 stimulus requests
plus duplicate/reset checks, with unsigned requests rejected, compute p95
288.232 ms and maximum steady round trip 390.787 ms on the server self-client.
These are separate measurements, not claimed phone network latency.

The physical Fold7's actual Android transport paired over NetBird, checked
the exact full identity and received feeding/grooming activity. The live
ViewModel/avatar test then passed after a service restart: feeding peaked at
90 Hz, grooming at 180 Hz, phone controller p95 1,431 microseconds, and body
and wing authority became inactive when the avatar was hidden. This was a
short check on debug build 130, not battery/heat endurance or a physical robot
test. The legacy scope label in `avatar-live.json` mentions a paired computer;
the actual selected endpoint was the dedicated direct VPN service, recorded
by `neural-phone.json`.

The model advances 50 ms of simulated neural time per request; the app's
maximum request cadence is every 400 ms. This is not biological real-time
whole-animal simulation. The phone interpolates its avatar independently.

## Browser, SSH and task execution

- The production Android WebView read `https://example.com` and its IANA
  link, then filled and POSTed a synthetic local shop form with item=stickers
  and quantity=12. The test observed the exact POST and read receipt
  `TEST-QUOTE-42`. No actual customer, booking, login or purchase was involved.
- The production Android SSH client read the actual repository's `AGENTS.md`
  on Meow over NetBird. A deliberately wrong pinned server key was rejected.
  The temporary acceptance key was revoked on Meow and removed from the phone
  and Mac. This verifies the capability, not a newly saved permanent SSH login.
- Business mode creates teams through its UI/templates. Its manager can
  delegate to configured specialists within one durable job. They share one
  worker and run sequentially. The model cannot currently create new agents
  or grant itself new tools. Do not describe this as arbitrary automatic
  spawning or simultaneous model workers.

Two real `openrouter/free` team runs exposed reliability problems: build 130
ended BLOCKED after a stock specialist returned findings as a blocker; build
131 ended BLOCKED when a specialist's completion proposals failed validation.
Both failures and partial evidence are retained. The planner now emphasizes
the current subtask and actual remaining prerequisites, supplies a finish
example with observed IDs, and validates allowed tools/delegates/completion
evidence before returning a proposal. The engine still independently enforces
permissions, approvals and evidence. These changes do not establish reliable
autonomy for every free model.

## Build and checks

All 2,526 JVM tests passed with zero failures/errors/skips. New regressions
reject invented, failed, pre-delegation and other-agent-only evidence. The
Python neural controller's ten tests passed. Two new package/service checks
passed on both Mac and Meow, including TLS, single-use pairing, unsigned
rejection, unsupported commands and a stalled TLS client. A package checksum
corruption/path-escape check also passed within that suite.

Debug and signed/minified release builds 131 and 132 (version name 1.3.82) were built.
All 75 debug native/model entries match build 130. The release preserves all
native/model assets; only its two generated ART baseline profiles change as
expected with new DEX. The runtime verifier now reports those generated changes
separately and still rejects missing profiles and changed native/model assets.
Its new regression passed. Release signature matches the existing upload key.
The initial unit-test compile failed on a test-only positional constructor
argument; that was fixed before the successful full run.

Final build 132 release APK SHA-256:
`5d9020a6616a6971ddea5396ed74a8929b69b43b51de460bdc977e66e2b2b34b`.
The earlier code 130 Play submission is separate; this task has not changed
that pending Play review.

Build 132 raises the controller response ceiling from 900 to 4,096 tokens,
because reasoning and final tool arguments share that limit for reasoning
providers. A truncated (`finish_reason=length`) response is explicitly rejected
before action validation. The existing timeouts, response-size limit and
bounded retries remain. This is a likely contributor investigated after a
named free Nemotron model returned prose instead of a usable tool decision;
it is not proof that every prior failure was caused by truncation. See
[OpenRouter's reasoning documentation](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens).

The regular signed app was paired through its actual Neural setup form. It
displayed `Selected · Meow full fly brain`, `100.77.97.236:8785`, and
`Connected · Meow full fly brain · full MaleCNS graph verified`. This replaced
the dedicated service's earlier debug-app pairing; its one-app limit does not
affect the separate desktop bridge. The signed app was then upgraded in place
to build 132, preserving its data.

## Final real team result

After cooling, build 132 completed the same three-agent shop task in 104.294
seconds using `nvidia/nemotron-3-super-120b-a12b:free`. Its zero prompt/completion
price and tool-call support were checked against the live OpenRouter model
catalog. The test used the existing configured OpenRouter key; it did not
change the user's default provider/model or select a paid fallback.

The manager delegated to Accounts and Stock exactly once each. Accounts read
the orders and used the CSV tool to verify unpaid IDs A101/A103 and total
56.75. Stock read the actual stock fixture and found a three-roll shortage.
Both returned successful `agent.result` evidence. The manager requested one
approval, wrote `reports/team.txt`, read it back and completed. There were no
failed or rejected steps in this successful run. The report was 157 bytes,
SHA-256 `bc36d14604c96482b9d70204bc91412286f6dce39a528ca51d6df8807dfdd8ab`.

A preceding build-132 attempt paused for thermal status 2 before doing work;
it was not a model pass or failure. Both apps were stopped and the display
slept until thermal status returned to 1, with no override. The acceptance
harness now waits during a mid-run thermal pause instead of consuming its
transition allowance in a busy loop. This does not change the production gate.

This proves one real model-driven, sequential team workflow, with synthetic
business data and real file tools. It does not establish automatic creation
of arbitrary agents, simultaneous workers, reliable results from every model
behind `openrouter/free`, real business-account integrations, or low-latency
general-purpose phone autonomy. Earlier failed attempts remain in the evidence.

The final signed build 132 cold-launched normally in 414 ms. The phone had
locked itself during cooldown; its post-upgrade Brain screen was therefore
not visually rechecked or unlocked through automation. The dedicated full
server selection and successful UI pairing were verified on signed build 131
immediately before the in-place upgrade. Its existing Shadow mode was retained;
Neural control can be selected in Brain settings. The full live avatar control
and hidden-stage shutdown were exercised by the earlier explicit device test.
