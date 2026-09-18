# Lumen drive · CarPlay awareness · OBD-II — Preview vc128

Branch: `feat/lumen-drive-carplay-obd2` (from `feat/meta-glasses-meow-bridge` @ `8ac4b9d`).
Preview package `com.example.meowmba`. Flags `lumen_reflex_v2` / `agentic_runtime_v1` remain default **OFF**. No FlyWire.

## What shipped

### P0 — Idle morph + drive reactivity
- **Idle form cycler** rotates through **all** authored `AvatarAnimal` forms (cat, dog, squirrel, raccoon, Australian shepherd, owl, duck, rabbit, pig, alien, robot, panda, fruit fly, Clippy, Wizard) — not only the ambient-performance cat/duck/dog mappings.
- Optional **saved generated forms** join the pool (Settings toggle).
- Configurable interval (20–180 s, default 55 s). Overlay only — does not permanently overwrite the preferred form.
- Pauses while driving, speaking, ambient clips, or backgrounded.
- **Drive emotion boost**: shorter face/social debounce, amplified IMU lean/lurch, emotion + OBD RPM folded into brace. Settings → Driving.

### P0 — Live cameras
- Drive card can show the **driver-facing** (or rear) phone camera via existing `Viewfinder` / VisionManager when CAMERA is permitted.
- Camera source list: front driver, rear phone, optional USB UVC (when host present), optional IP RTSP URL (listed for tools; in-app RTSP decode not shipped).

### P1 — Car awareness
- `VehicleIdentity` from Bluetooth headset profile + Android Auto / automotive feature hints.
- Tool: `car.vehicle`.
- Tool: `car.cameras` (list / switch active phone camera).

### P2 — Media / phone (best effort, not CarPlay ownership)
- Existing `phone.media_control`, `phone.set_volume`, `phone.play_music`, `phone.dial_call` (dialer with user confirm).
- Registered on the new **Cruise** (`car`) agent and on **Paws** (`phone`).

### P3 — Native OBD-II
- Classic Bluetooth SPP ELM327 client: connect, AT init, poll PIDs RPM / speed / coolant / voltage / MIL / fuel.
- Alerts (coolant high, battery low, RPM high, MIL) → Lumen ALERT when driving.
- RPM → soft excitement brace cue.
- Tools: `car.obd`, `car.obd_connect`. Settings for enable + MAC.

## Honest platform limits

| Capability | Status |
|---|---|
| Full Apple CarPlay session control (radio / phone / AA templates as CarPlay owner) | **Not available** to third-party Android apps without Apple CarPlay entitlement / companion iOS |
| Android Auto projection templates | OEM / feature-dependent; Meow only detects likelihood |
| MediaSession play/pause/skip + volume | **Supported** (existing phone tools) |
| Dialer with user pressing Call | **Supported** (`phone.dial_call`) |
| Bluetooth car hands-free as drive cue | **Supported** (existing DrivingDetector) |
| ELM327 Bluetooth SPP OBD | **Supported** (new) |
| ELM327 USB CDC | Detected only; no full USB serial driver in this slice |
| IP RTSP dashcam live decode in Compose | URL listed; playback deferred |
| Idle morph all authored + generated forms | **Supported** |

## Settings
**Driving** page: Drive mode, idle morph, emotion boost, driver camera, RTSP URL, OBD enable + MAC, emergency contact.

## Validation
- Unit: `IdleFormCyclerTest`, `Obd2PidParserTest`, `VehicleAwarenessTest`.
- Build: `:app:compileDebugKotlin`, `:app:assembleDebug` (Preview vc128; MWDAT when props path available).
