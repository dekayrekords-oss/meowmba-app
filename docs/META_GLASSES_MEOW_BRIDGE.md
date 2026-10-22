# Meta glasses ↔ Meow Assistant bridge

Short discovery + how the shipped bridge works. Complements [META_GLASSES_SETUP.md](META_GLASSES_SETUP.md).

## What the app already uses (Meta APIs / SDK)

| Piece | Reality in this repo |
| --- | --- |
| **Meta Wearables DAT (MWDAT)** | Optional source set `app/src/mwdat` + deps `mwdat-core` / `mwdat-camera` **0.8.0** from GitHub Packages (`com.meta.wearable`). Gated by `includeMwdat` + external `mwdat.properties` (never commit tokens). |
| **Bridge class** | Reflectively loaded `MwdatGlassesBridge` → `MetaGlassesController` (camera session, registration, frames). |
| **Microphone** | **No public mic API in MWDAT 0.8.0** (verified against the AAR / prior notes in `AsrBackend.META_GLASSES_MIC`). Glasses appear to Android as a **Bluetooth SCO headset**; Meow captures via `SherpaAsrEngine(bluetoothSco=true)`. |
| **Agent tools** | `glasses.take_photo` samples the frame bus; chat tools otherwise unchanged. |
| **Memory** | Optional MetaMemory / IPC tags `meta_glasses` for captures — not a voice command channel. |

Build / Play attestation steps remain in META_GLASSES_SETUP.md. Preview package stays `com.example.meowmba`.

## How voice reaches Meow (strongest real path)

```
Ray-Ban (BT audio) ──SCO──► Phone mic path ──ASR──► Meow wake / tap-to-talk
                                                      │
                                                      ▼
                                          Companion agent tool loop
                                          (same tools as in-app chat)
                                                      │
                          ┌───────────────────────────┼──────────────────────┐
                          ▼                           ▼                      ▼
                     ssh.meow                   opencode.run            server.shell
                  (read-only allowlist)     (SSH → opencode CLI)   (policy + approval)
```

1. Pair glasses in **Meta AI**, then Meow **LLM → Meta Ray-Ban glasses → Setup / Connect** (needs SDK-enabled APK).
2. Enable **Meow bridge** on that card (default ON). Pick default SSH host (e.g. `meow`).
3. On Voice Recognition, select **Meta glasses mic** (or leave Whisper; SCO still works when glasses are the active headset).
4. Say **“Hey Meow”** (or your companion name) / tap the mic — **not** “Hey Meta” — then the command.
5. Meow runs the **same** companion tools (become, computer, files, SSH, …). Bridge-specific helpers:
   - `ssh.meow` — allowlisted read-only on default meow host
   - `opencode.run` — SSH + OpenCode CLI (`~/.opencode/bin`)
   - `server.shell` — broader SSH shell; side effects need approval (matches AgenticSshPolicy / Computer UX)

Saved servers: **Settings → Servers / Connections** (encrypted). Meow host NetBird: `100.77.97.236`, user `prog`.

## What “Hey Meta” cannot do without Meta partner APIs

**“Hey Meta” belongs to Meta’s assistant.** Third-party apps cannot register arbitrary Meta wake intents or fully control Meta AI from outside partner / Wearables voice-forwarding APIs that this project does not have.

Without Meta partner access you **cannot**:

- Hijack “Hey Meta” so Meta AI silently forwards every utterance into Meow
- Inject Meow as a first-class Meta AI skill with full arbitrary app control
- Use a public MWDAT “voice command” stream (0.8.0 has camera/session, not mic ASR)

Possible future upgrades (not shipped here): Wearables / AI glasses SDK voice forwarding, phone companion bridge published by Meta, or registered voice intents once Meta documents them for third parties.

## Flags / product constraints

- No FlyWire
- `lumen_reflex_v2` / `agentic_runtime_v1` remain default **OFF** (bridge tools work on the normal chat tool loop)
- Destructive SSH / OpenCode always go through confirmation; credentials never logged

## Files

- `core/glasses/MetaGlassesMeowBridge.kt` — prefs + transcript normalize / intent hints
- `core/agent/tools/MeowServerTools.kt` — `ssh.meow`, `opencode.run`, `server.shell`
- UI: `ModelManagerScreen` Meta glasses card (bridge toggle + default host)
