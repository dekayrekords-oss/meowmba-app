# SDD Progress Ledger — Music Generation

Plan: docs/superpowers/plans/2026-08-03-music-generation.md
Worktree: /home/claude/meowmba-app/.claude/worktrees/music-generation
Branch: feat/music-generation

Note: superpowers:subagent-driven-development / using-git-worktrees skills
are not installed in this environment (same situation as Plan 1). Same
hand-executed process: controller (me) does infra/server-side work and
final decisions directly, dispatches implementer subagents for
well-scoped Kotlin app-code tasks, reviews before committing.

## Task 1: meow-music — deploy ACE-Step 1.5 + orchestration wrapper
Status: DONE (infra deployed on this host — meow-nb IS this host,
hostname `meow`, no SSH needed — verified before running any ssh
commands from the plan text, ran everything directly instead)

- Vendored ACE-Step 1.5 at /home/claude/meow-music/vendor, commit
  6d467e4b5081ccb0abf1ec1bf4fdf9051a2d34b0 (2026-06-26).
- Verified the real wire API against docs/en/API.md in that exact
  checkout BEFORE writing any wrapper code, per the plan's explicit
  instruction: POST /release_task -> {"data":{"task_id"}}, POST
  /query_result -> {"data":[{"task_id","status","result"}]} where
  `result` is a JSON-encoded string, GET /v1/audio?path=... -> raw
  bytes. Matches the plan's assumptions exactly.
- Real, live curl round-trip completed successfully (not simulated):
  POST /release_task -> POST /query_result (polled to status:1) ->
  GET /v1/audio -> real 2.88MB WAV file (RIFF/WAVE, 16-bit stereo
  48kHz, ~15.0s — matches requested duration exactly).
- **Deviation 1 (verified live, documented reason): audio_format is
  "wav", not the plan's assumed "mp3".** First attempt with mp3 failed
  with "ffmpeg executable not found" — this host has no sudo access to
  install ffmpeg system-wide for the ACE-Step host process's
  environment. wav needs no extra dependency and was verified working
  end-to-end. If ffmpeg is later added, this is a one-line env var
  change (AUDIO_FORMAT) in orchestrator.py, not a code change.
- **Deviation 2 (verified live): ACE-Step's own server auto-loads a
  5Hz "thinking" LM by default** (its own GPU-tier auto-config decided
  this box's 23.5GB warrants it) alongside the primary DiT model. This
  caused a CUDA OOM on the very first cold-start attempt (compounded by
  Ollama reloading onto the GPU during the ~13min first-time model
  download window — a real operational race between this work and the
  live production qwen3.6 LLM also running on this box). Confirmed this
  LM-load failure is **non-fatal**: ACE-Step's own server logs a
  warning and falls back to DiT-only generation automatically. No
  orchestrator-side code needed to handle this — thinking is never
  requested by the orchestrator, so DiT-only is the expected/desired
  path anyway.
- **Deviation 3 (documented, reasoned): ACE-Step's own acestep-api runs
  as a systemd --user host process, not a Docker container** — the
  plan's own text explicitly allows either. Chose host process because:
  (a) the plan's own default `ACESTEP_BASE = "http://127.0.0.1:8001"`
  assumes exactly this shape, (b) a working, GPU-verified `uv`
  environment already existed at this point in the task and
  Dockerizing the full CUDA+torch+9.4GB-of-weights stack would have
  meant re-solving GPU driver/CUDA-toolkit compatibility inside a
  container for no functional gain, (c) `systemctl --user` with
  `loginctl enable-linger claude` (self-service, no sudo needed —
  verified) gives auto-restart-on-failure and start-on-boot semantics
  equivalent to `docker run --restart unless-stopped`. Unit file at
  ~/.config/systemd/user/acestep-api.service.
- Weights cached at /home/claude/meow-music/vendor/checkpoints (9.4GB):
  acestep-v15-turbo (DiT), acestep-5Hz-lm-1.7B, Qwen3-Embedding-0.6B,
  vae. Persist across restarts since they live on disk, not in any
  container's writable layer.
- orchestrator.py: written fresh (not verbatim from the plan) to
  reflect the verified API shape + the wav/host-process deviations
  above; catches Exception (Python's Throwable-equivalent — there is
  no separate Throwable type in Python, `except Exception` already
  catches everything an HTTP-boundary handler needs to) around every
  outbound call to ACE-Step and Ollama, logs failures via print (this
  container's only diagnostic channel — `docker logs meow-music`),
  never lets a failed Ollama-unload block generation.
- Dockerfile matches the plan's lightweight python:3.11-slim pattern
  exactly (orchestrator has no ML dependencies of its own).
- Deployed: `docker run -d --name meow-music --restart unless-stopped
  --network host meow-music`, port 9200 (--network host because it
  needs to reach the host-process ACE-Step server at 127.0.0.1:8001).
- **Full real end-to-end test through the actual deployed orchestrator**
  (not just direct-to-ACE-Step): POST /generate -> task_id -> polled
  /status (one transient 502 mid-poll from a connection blip, recovered
  automatically on the next poll — the Kotlin client's poll loop already
  tolerates single failed polls by design, confirmed no fix needed) ->
  status:1 after 29s -> GET /result -> real 2.88MB playable WAV.
- Measured wall-clock for a 15-second generation (models warm): 29s
  end-to-end including the orchestrator's own Ollama-unload call and
  HTTP round trips. ACE-Step's own reported DiT time: ~1.3s (matches
  the design doc's "under 2s per full song" claim). This feeds Task 2's
  client-side poll interval/timeout choice.

## Task 2: MusicGenerator interface + RemoteMusicGenerator client
Status: DONE
- Dispatched to an implementer subagent with the real verified wire
  contract from Task 1 (wav not mp3, real measured timing).
- Fixed 3 real bugs in the plan's own reference code (verified by
  actually compiling, not guessed): missing `contentLength` import;
  the plan's private top-level DTOs (`GenerateRequest`/`TaskIdResponse`/
  `StatusResponse`) collide at the package level with identically-named
  private data classes already in `RemoteImageGenerator.kt` (same
  package — Kotlin file-private top-level classes still share one JVM
  namespace slot per package) — renamed to `MusicGenerateRequest`/etc;
  a test method name containing a literal `/` is illegal even inside
  Kotlin backticks.
- 3 tests pass. Committed as 1820a62.

## Task 3: Wire into MediaGenerateAudioTool + Settings
Status: DONE
- Dispatched to an implementer subagent with the wav-not-mp3 deviation
  spelled out explicitly (both the remote and fallback paths now write
  `audio_*.wav` — no longer distinguishable by extension alone the way
  images are `image_*` vs `canvas_*`; documented honestly in a doc
  comment rather than inventing a filename-prefix scheme not worth the
  complexity).
- Settings wiring (`musicServerUrl`) verified to match `imageServerUrl`
  exactly; test uses `mockSecureStorage` + `verify()`, not the
  no-op-construction mistake this codebase's history already had to
  catch once.
- Real deviation found during implementation: the plan's Task 3 Step 1
  test hits `Log.w` on the fallback-after-remote-failure path, which
  throws under plain JUnit (Android `Log` unmocked) — fixed by applying
  `@RunWith(RobolectricTestRunner::class)`, mirroring how the sibling
  `MediaGenerateToolRoutingTest` already solves the identical problem.
- 393/396 tests pass (3 pre-existing unrelated failures), assembleDebug
  BUILD SUCCESSFUL. Committed as 5e15bf5.

## Final whole-branch review
Status: DONE — one Severe + one Medium finding, both fixed
Dispatched to a review subagent with instructions to verify Throwable
discipline, size-guard ordering, resource cleanup, the wav/mp3
consistency, settings-test quality, and to actually run the suite.

Findings (both fixed directly by the controller, commit e379a0f):
1. **Severe**: `MediaGenerateAudioTool.timeoutMs` stayed at the
   inherited `30_000` even after wiring in `RemoteMusicGenerator`,
   whose own poll budget alone is up to 120s (`maxPolls=60 *
   pollIntervalMs=2000`) plus per-call HTTP timeouts. The orchestrator's
   `withTimeoutOrNull` would kill the *entire* tool call — including the
   tone-generator fallback — before a real remote generation could
   finish, surfacing a raw "Tool timed out" error instead of either a
   real result or the promised graceful fallback. Same class of bug the
   image tool (`MediaGenerateTool`) hit first and fixed by raising to
   `300_000`; mirrored here by raising to `200_000` with a comment
   explaining the real math (matches this task's real measured 29s
   warm-generation time with wide margin for cold-start/network
   variance, while not being needlessly larger than the actual budget).
2. **Medium**: `RemoteMusicGeneratorTest.kt` had no coverage for the
   server-reported-failure (status:2), poll-timeout (maxPolls
   exhausted), or oversized-response branches, despite the code's own
   comment claiming "same discipline as RemoteImageGenerator" — whose
   equivalent branches are tested. Added 3 tests mirroring
   `RemoteImageGeneratorTest.kt`'s existing style for the same cases.
- Re-ran full suite after fixes: 399 tests (3 new), 3 failures — same
  pre-existing unrelated `ModelDownloadViewModelTest` failures.
  assembleDebug BUILD SUCCESSFUL.

## FINAL STATUS: PLAN COMPLETE
All 3 app-side tasks done, server-side infra (meow-music) deployed and
verified end-to-end with real generated audio, final review clean after
two fixes.

Branch: feat/music-generation
Worktree: /home/claude/meowmba-app/.claude/worktrees/music-generation
Merge-base: origin/main @ 8c422bb
App commits (5): 688e20b, [Task1 ledger], 1820a62, 5e15bf5, e379a0f
Server infra (NOT in this git repo, per plan): /home/claude/meow-music/
  - orchestrator.py + Dockerfile, deployed as Docker container
    `meow-music` (--network host, port 9200, --restart unless-stopped)
  - ACE-Step 1.5 vendored at /home/claude/meow-music/vendor
    (commit 6d467e4), running as systemd --user service
    `acestep-api.service` (enabled, linger enabled — survives
    logout/reboot), port 8001, weights cached on disk (9.4GB)
Not pushed... will push app-side branch now. Server infra is host
state, not a git artifact — documented here and in the final status
report for a human to find.
