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
