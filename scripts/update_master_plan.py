#!/usr/bin/env python3
"""
Re-scans the Meowmba codebase and regenerates MASTER_PLAN.md with updated checkbox states.
Run from the repo root: python3 scripts/update_master_plan.py
"""

import os
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
APP_SRC = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "meowmba"
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
MANIFEST = REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
BRIDGE_PY = REPO_ROOT / "bridge" / "meowmba-bridge.py"


def get_version() -> tuple[str, str]:
    """Read the Android version instead of freezing it in generated docs."""
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    return (name.group(1) if name else "unknown", code.group(1) if code else "unknown")

# Feature → (description, search paths/terms, verification logic)
FEATURES = {
    # Phase 1 — Working Avatar Shell
    "phase1": {
        "title": "Phase 1 — Working Avatar Shell",
        "items": [
            ("Memba 2 face design, resolution-independent line art",
             ["ui/companion/AvatarRenderer.kt"], ["s = min(width, height)", "resolution-independent"]),
            ("CatAvatarStateMachine: interaction state × emotion → AvatarPose",
             ["ui/companion/AvatarAnimation.kt", "ui/companion/AvatarRenderer.kt"], ["CatAvatarStateMachine", "AvatarPose"]),
            ("CatAnimationRegistry: named clips (idle.blink, speech.talk_cycle)",
             ["ui/companion/AvatarAnimation.kt"], ["CatAnimationRegistry", "idle.blink", "speech.talk_cycle"]),
            ("Independent animation layers: blink, ear twitch, breathing, gaze wander, whisker sway, mouth wobble, halo pulse",
             ["ui/companion/AvatarAnimation.kt"], ["blink", "earTwitch", "breathing", "gazeWander", "whisker", "mouth", "halo"]),
            ("Touch zones: ears (mic privacy), eyes (camera privacy), mouth (mute)",
             ["ui/companion/CompanionState.kt"], ["micPrivacy", "cameraPrivacy", "mute", "TapZone"]),
            ("Stroke-to-purrs, swipe-up chat, swipe-down tiles",
             ["ui/companion/AvatarAnimation.kt"], ["purr", "stroke", "swipeUp", "swipeDown"]),
        ],
    },
    # Phase 2 — Real Voice Pipeline
    "phase2": {
        "title": "Phase 2 — Real Voice Pipeline",
        "items": [
            ("Provider-neutral SpeechEngine / SpeechSynthesizer interfaces",
             ["core/audio/SpeechEngine.kt"], ["SpeechEngine", "SpeechSynthesizer"]),
            ("Wake phrases: Hey Memba, Hey Meow, Hey [custom name]",
             ["core/audio/WakePhraseMatcher.kt"], ["Hey Memba", "Hey Meow", "custom"]),
            ("WakePhraseMatcher (pure Kotlin, unit-tested)",
             ["core/audio/WakePhraseMatcher.kt"], ["WakePhraseMatcher"]),
            ("Conversation mode (8s follow-up timeout)",
             ["core/audio/", "ui/companion/CompanionViewModel.kt"], ["conversationMode", "followUp", "8000", "8s"]),
            ("Interruptible speech (phrase-match interrupt only)",
             ["core/audio/SelfVoiceFilter.kt"], ["interrupt", "Interrupt"]),
            ("Audio focus management",
             ["core/audio/"], ["audioFocus", "AudioFocus"]),
            ("Mouth sync via TTS word-boundary callbacks",
             ["ui/companion/AvatarRenderer.kt", "ui/companion/CompanionState.kt"], ["mouthEnergy", "wordBoundary", "word-boundary"]),
            ("Voice modes: push-to-talk, tap-to-toggle, wake phrase, conversation mode",
             ["ui/companion/VoicePolicy.kt", "ui/companion/CompanionState.kt"], ["pushToTalk", "tapToToggle", "wakePhrase", "conversationMode"]),
        ],
    },
    # Phase 2.5 — Background Wake Word
    "phase2_5": {
        "title": "Phase 2.5 — Background Wake Word",
        "items": [
            ("ForegroundService (foregroundServiceType=microphone)",
             ["core/audio/WakeWordService.kt"], ["foregroundServiceType", "microphone"]),
            ("Explicit opt-in, persistent notification with Stop action",
             ["core/audio/WakeWordService.kt"], ["opt-in", "Stop", "notification"]),
            ("Screen-off flow: wake → Yes? → command → high-priority notification",
             ["core/audio/WakeWordService.kt", "ui/companion/CompanionViewModel.kt"], ["screen-off", "high-priority", "Yes?"]),
        ],
    },
    # Phase 3 — Model Routing
    "phase3": {
        "title": "Phase 3 — Model Routing",
        "items": [
            ("Live streaming: Ollama (NDJSON)",
             ["core/agent/RemoteModelProvider.kt", "core/agent/StreamParsers.kt"], ["Ollama", "NDJSON", "/api/generate"]),
            ("OpenAI-compatible (SSE) streaming",
             ["core/agent/RemoteModelProvider.kt", "core/agent/StreamParsers.kt"], ["OpenAI", "SSE", "/v1/chat/completions"]),
            ("On-device MediaPipe / LiteRt inference",
             ["core/agent/LiteRtProvider.kt", "core/agent/MediaPipeProvider.kt"], ["MediaPipe", "LiteRt", "on-device"]),
            ("StreamParsers (pure, unit-tested)",
             ["core/agent/StreamParsers.kt"], ["StreamParsers"]),
            ("Honest networking: HTTP status checked, missing key fails fast",
             ["core/agent/RemoteModelProvider.kt"], ["HTTP status", "missing key", "fails fast", "timeout"]),
            ("Echo-tolerant barge-in (SelfVoiceFilter)",
             ["core/audio/SelfVoiceFilter.kt"], ["SelfVoiceFilter", "barge-in", "echo"]),
            ("Hardware page: provider picker, local file check, cloud health ping",
             ["ui/companion/ModelManagerScreen.kt"], ["provider picker", "Check local file", "Test cloud", "health"]),
        ],
    },
    # Phase 4 — Long-term Memory
    "phase4": {
        "title": "Phase 4 — Long-term Memory (Room v3→v7)",
        "items": [
            ("Memory types: EPISODIC, SEMANTIC, PROJECT, PREFERENCE",
             ["core/memory/MemoryRepository.kt", "core/database/Entities.kt"], ["EPISODIC", "SEMANTIC", "PROJECT", "PREFERENCE"]),
            ("EmbeddingInterface: LocalHashEmbedder (deterministic)",
             ["core/memory/EmbeddingInterface.kt", "core/memory/MemoryCore.kt"], ["LocalHashEmbedder", "EmbeddingInterface"]),
            ("Cosine-similarity recall with explanations",
             ["core/memory/MemoryRepository.kt", "core/memory/MemoryCore.kt"], ["cosine", "recall", "Recalled from memory"]),
            ("Remember that… / remember this commands",
             ["ui/companion/CompanionViewModel.kt", "core/memory/MemoryRepository.kt"], ["remember that", "remember this", "MemorySaveTool", "memory.save"]),
            ("Memory screen: FTS search, type badges, delete, JSON export, retention policy",
             ["ui/companion/MemoryScreen.kt"], ["FTS", "search", "JSON export", "retention"]),
            ("MetaMemoryConnector (interface, no-op default)",
             ["core/memory/MetaMemoryConnector.kt", "core/memory/IpcMetaMemoryConnector.kt"], ["MetaMemoryConnector"]),
        ],
    },
    # Phase 5 — Agents & Tools
    "phase5": {
        "title": "Phase 5 — Agents & Tools",
        "items": [
            ("AgentRegistry: Meow, Shelve, Keeper, Builder, Forge, Scout",
             ["core/agent/runtime/AgentModel.kt"], ["AgentRegistry", "Meow", "Shelve", "Keeper", "Builder", "Forge", "Scout"]),
            ("ToolSystem: ToolDefinition (inputs, risk level, confirmation policy, timeout, audit)",
             ["core/agent/tools/ToolSystem.kt"], ["ToolDefinition", "ToolSystem"]),
            ("Risk levels: SAFE / MODERATE / DESTRUCTIVE",
             ["core/agent/tools/ToolSystem.kt"], ["RiskLevel", "SAFE", "MODERATE", "DESTRUCTIVE"]),
            ("Approval gates: 5min auto-deny, queued approvals",
             ["core/agent/runtime/AgentOrchestrator.kt"], ["auto-deny", "approvalQueue", "5min", "300_000"]),
            ("Sandboxed Workspace (no ../, no absolute paths)",
             ["core/agent/tools/Workspace.kt"], ["Workspace", "../", "absolute", "sandbox"]),
            ("Audit log: every task transition persisted to Room",
             ["core/agent/runtime/AgentOrchestrator.kt"], ["audit_log", "audit", "AuditSink"]),
        ],
    },
    # Phase 6 — Desktop Bridge
    "phase6": {
        "title": "Phase 6 — Desktop Bridge (P6a–P6d)",
        "items": [
            ("P6a: Pairing (UDP discovery, 8-char code, 256-bit secret, Keystore)",
             ["core/bridge/BridgeDiscovery.kt", "core/bridge/DesktopBridgeManager.kt"], ["UDP", "8-char", "Keystore", "pairing"]),
            ("P6a: Cert pinning, HMAC-SHA256 signed requests, 60s replay window",
             ["core/bridge/DesktopBridgeProtocol.kt"], ["HMAC", "SHA256", "replay", "cert pinning", "X-Meowmba"]),
            ("P6b: Read-only file tools (list_files, read_file)",
             ["core/agent/tools/"], ["list_files", "read_file", "DesktopControlTool"]),
            ("P6b: Workspace confinement, denylist, 64KB cap, 500-entry listing cap",
             ["bridge/meowmba-bridge.py"], ["workspace_roots", "denylist", "64 KB", "500"]),
            ("P6c: Approval-gated writes (write_file), atomic writes, .bak backups",
             ["core/agent/tools/DesktopWriteFileTool.kt", "bridge/meowmba-bridge.py"], ["write_file", "atomic", ".bak", "ConfirmationPolicy.ALWAYS"]),
            ("P6d: Named build triggers (run_build), argv-only (no shell)",
             ["core/agent/tools/DesktopRunBuildTool.kt", "bridge/meowmba-bridge.py"], ["run_build", "argv", "no shell", "commands"]),
        ],
    },
    # Phase 7 — Vision
    "phase7": {
        "title": "Phase 7 — Vision",
        "items": [
            ("CameraX integration, face-follow",
             ["core/vision/VisionManager.kt", "core/vision/DeviceVisionProvider.kt"], ["CameraX", "face-follow", "faceFollow"]),
            ("Scene descriptions via multimodal model",
             ["core/vision/VisionProvider.kt"], ["scene", "description", "multimodal"]),
            ("Watch mode: MediaProjection foreground service, 1 frame/8s",
             ["ui/companion/CompanionState.kt", "ui/companion/CompanionViewModel.kt"], ["MediaProjection", "watchMode", "watch_mode"]),
            ("ContextDirector: vision-describes frames, classifies, reactive animations",
             ["core/context/ContextDirector.kt"], ["ContextDirector", "classify", "Commentary"]),
            ("Follow-only mode for lightweight auto-tracking",
             ["core/vision/VisionManager.kt"], ["followOnly", "follow-only", "FollowOnly"]),
        ],
    },
    # Phase 8 — Avatar Invented Animations
    "phase8": {
        "title": "Phase 8 — Avatar Invented Animations",
        "items": [
            ("avatar.animate tool: name + JSON keyframes",
             ["core/agent/tools/AvatarAnimateTool.kt"], ["avatar.animate", "keyframes"]),
            ("CustomAnimations.parse: validates, clamps, reports every clamp",
             ["ui/companion/CustomAnimation.kt"], ["CustomAnimations.parse", "clamp", "validate"]),
            ("Per-field sparse sampling (numbers smoothstep-lerp; booleans step)",
             ["ui/companion/CustomAnimation.kt"], ["smoothstep", "sparse", "lerp"]),
            ("save=true persists to filesDir/custom_animations/",
             ["core/agent/tools/AvatarAnimateTool.kt"], ["save=true", "custom_animations"]),
            ("Built-in clips: ear wiggle, happy bop, curious tilt",
             ["ui/companion/CustomAnimation.kt"], ["ear wiggle", "happy bop", "curious tilt", "BUILT_IN"]),
            ("Self-performance disambiguation: avatar.animate ≠ media.generate_video",
             ["core/agent/tools/AvatarAnimateTool.kt"], ["self-performance", "avatar.animate", "media.generate"]),
        ],
    },
    # Phase B — BigPickle + Recall + SSH
    "phaseB": {
        "title": "Phase B — BigPickle Brain + Recall + SSH",
        "items": [
            ("BigPickle brain (OpenCode Zen key, model big-pickle)",
             ["ui/companion/SettingsScreen.kt", "core/agent/ModelCatalog.kt"], ["BigPickle", "big-pickle", "opencode", "zen"]),
            ("Ask-anything recall: messages_fts FTS4 index, memory.recall tool",
             ["core/memory/MemoryRepository.kt", "core/agent/tools/MemoryRecallTool.kt"], ["messages_fts", "memory.recall", "FTS4"]),
            ("SSH management: encrypted hosts, ssh.run (allowlisted read-only commands)",
             ["core/agent/tools/SshTool.kt", "core/ssh/SshHostRegistry.kt"], ["ssh.run", "allowlisted", "read-only", "TOFO"]),
        ],
    },
    # Phase C — IMU + Watch Mode + ContextDirector
    "phaseC": {
        "title": "Phase C — IMU + Watch Mode + ContextDirector",
        "items": [
            ("MotionSense: avatar leans with phone (±8° low-passed roll)",
             ["core/robot/FaceMotorMap.kt"], ["IMU", "roll", "low-pass", "MotionSense", "lean"]),
            ("Shake (>2.5g) → startled animation",
             ["core/robot/FaceMotorMap.kt", "ui/companion/CompanionViewModel.kt"], ["shake", "2.5g", "startled"]),
            ("Watch mode foreground service",
             ["ui/companion/CompanionState.kt"], ["watchMode", "MediaProjection"]),
            ("ContextDirector vision pipeline",
             ["core/context/ContextDirector.kt"], ["ContextDirector"]),
        ],
    },
    # Phase D — Launcher + App Drawer
    "phaseD": {
        "title": "Phase D — Launcher + App Drawer",
        "items": [
            ("Optional launcher (HOME + DEFAULT intent categories)",
             ["app/src/main/AndroidManifest.xml"], ["HOME", "DEFAULT", "launcher"]),
            ("App drawer: swipe right on face, alphabetical grid, live search",
             ["ui/companion/AppDrawerScreen.kt"], ["AppDrawer", "swipe", "alphabetical", "search"]),
        ],
    },
    # Phase G — LOOI Robot Body
    "phaseG": {
        "title": "Phase G — LOOI Robot Body",
        "items": [
            ("BodyVerb vocabulary (Neck, Light, Drive, Turn)",
             ["core/robot/BodyVerb.kt"], ["BodyVerb", "Neck", "Light", "Drive", "Turn"]),
            ("BodyScheduler: plays body half of every clip on same clock as face",
             ["core/robot/BodyScheduler.kt"], ["BodyScheduler", "play"]),
            ("FaceMotorMap: mood → resting body pose (no drive in resting pose)",
             ["core/robot/FaceMotorMap.kt"], ["mapMood", "resting", "BodyVerb"]),
            ("ConfinementModel: evidence-based envelope (cliff, unknown, link loss)",
             ["core/robot/ConfinementModel.kt"], ["ConfinementModel", "cliff", "Unknown", "link loss"]),
            ("fed9 sensor frames → SensorEvent parsing",
             ["core/robot/LooiSensorParser.kt"], ["fed9", "SensorEvent", "parse"]),
            ("RobotManager: simulated default, LOOI BLE body when connected",
             ["core/robot/RobotManager.kt"], ["RobotManager", "SimulatedMotorController", "connectLooi", "BLE"]),
        ],
    },
    # v1.3.0 — Chrome Browser + MCP + Wake-Tolerant Animations
    "v130": {
        "title": "v1.3.0 — Chrome Browser + MCP + Wake-Tolerant Animations",
        "items": [
            ("Chrome replaces swipe browser",
             ["ui/companion/BrowserScreen.kt"], ["Chrome", "swipe browser", "BrowserScreen"]),
            ("MCP browser server (navigate, read, links, html, click, type)",
             ["core/mcp/McpBrowserServer.kt"], ["browser.navigate", "browser.read", "browser.links", "browser.html", "browser.click", "browser.type"]),
            ("Wake-tolerant animations (don't break on wake-word detection)",
             ["ui/companion/CompanionViewModel.kt", "ui/companion/SpeechIntents.kt"], ["stripAddress", "wake-tolerant", "animationRoute", "says("]),
        ],
    },
    # v1.3.1 — Bundled ASR Pack + Sync
    "v131": {
        "title": "v1.3.1 — Bundled ASR Pack + Sync",
        "items": [
            ("Bundled on-device streaming ASR pack",
             ["core/audio/AsrBackend.kt", "core/audio/SherpaAsrEngine.kt"], ["ASR", "streaming", "Sherpa", "Whisper"]),
            ("Matchable debug key",
             ["app/build.gradle.kts", "local.properties"], ["matchable", "debugKeystore", "debug.*key"]),
            ("Wake-word engine starts off main thread",
             ["core/audio/WakeWordService.kt", "core/audio/SherpaKwsEngine.kt"], ["main thread", "background thread", "Dispatchers"]),
            ("Lazy-load on-device brain, release when idle",
             ["core/agent/LiteRtProvider.kt", "core/agent/MediaPipeProvider.kt"], ["lazy", "release", "idle"]),
            ("Right-edge app rail with swipe-open pinned apps",
             ["ui/companion/AppRailTab.kt"], ["app rail", "AppRail", "swipe", "pinned"]),
        ],
    },
    # v1.3.2–v1.3.10 — Connections, Skills, MCP, and Onboarding
    "v132_v1310": {
        "title": "v1.3.2–v1.3.10 — Connections, Skills, MCP, and Onboarding",
        "items": [
            ("Guided per-provider API-key and model setup", ["ui/companion/SettingsScreen.kt"], ["provider", "API key", "model"]),
            ("Cloud Gemini ASR selectable backend", ["core/audio/AsrBackend.kt"], ["Gemini", "cloud"]),
            ("Plugins & MCP Connectors screen", ["ui/companion/PluginsConnectorsScreen.kt"], ["MCP", "connector"]),
            ("Remote HTTP MCP client", ["core/mcp/McpHttpClient.kt"], ["initialize", "tools/list", "tools/call"]),
            ("Phone-hosted LAN HTTP MCP endpoint", ["core/mcp/McpHttpServer.kt", "core/mcp/PhoneMcpServer.kt"], ["McpHttpServer", "PhoneMcpServer"]),
            ("Curated app gallery", ["core/plugins/AppConnections.kt"], ["AppGallery", "curated"]),
            ("Executable on-device skill runner", ["core/plugins/SkillRunner.kt"], ["SkillRunner", "execute"]),
            ("Live Spotify and GitHub clients", ["core/plugins/ServiceIntegrations.kt"], ["Spotify", "GitHub"]),
            ("Live Home Assistant integration", ["core/plugins/ServiceIntegrations.kt"], ["Home Assistant"]),
            ("Expanded service connection catalog", ["core/plugins/AppConnections.kt"], ["smart", "streaming", "finance"]),
            ("Guided Connect your AI onboarding", ["ui/onboarding/SetupWizardRoute.kt", "core/setup/SetupWiring.kt"], ["Connect your AI", "SetupWizard"]),
        ],
    },
}


def check_file_for_terms(filepath: Path, terms: list[str]) -> bool:
    """Check if any of the terms appear in the file."""
    try:
        content = filepath.read_text(encoding="utf-8", errors="ignore")
        return any(term.lower() in content.lower() for term in terms)
    except Exception:
        return False


def check_items_in_glob(directory: Path, terms: list[str]) -> bool:
    """Check if any file in directory (recursively) contains any of the terms."""
    if not directory.exists():
        return False
    for f in directory.rglob("*.kt"):
        if check_file_for_terms(f, terms):
            return True
    return False


def verify_item(paths: list[str], terms: list[str]) -> bool:
    """Verify a feature item by checking file paths and search terms."""
    for path_str in paths:
        # Try APP_SRC first (relative paths), then REPO_ROOT (absolute from repo root)
        p = APP_SRC / path_str
        if not p.exists():
            p = REPO_ROOT / path_str
        if p.is_file():
            if check_file_for_terms(p, terms):
                return True
        elif p.is_dir():
            if check_items_in_glob(p, terms):
                return True
        else:
            # Try glob pattern from both roots
            for root in [APP_SRC, REPO_ROOT]:
                matches = list(root.glob(path_str))
                for m in matches:
                    if m.is_file() and check_file_for_terms(m, terms):
                        return True
                    elif m.is_dir() and check_items_in_glob(m, terms):
                        return True
    return False


def run_tests() -> dict:
    """Run unit tests and parse results."""
    try:
        result = subprocess.run(
            ["./gradlew", ":app:testDebugUnitTest", "--console=plain"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            timeout=600,
        )
        output = result.stdout + result.stderr

        # Parse test results
        total = 0
        failures = 0
        errors = 0
        skipped = 0

        # Look for summary line like "X tests completed, Y failed, Z skipped"
        match = re.search(r"(\d+) tests completed, (\d+) failed, (\d+) skipped", output)
        if match:
            total = int(match.group(1))
            failures = int(match.group(2))
            skipped = int(match.group(3))

        return {
            "total": total,
            "failures": failures,
            "errors": errors,
            "skipped": skipped,
            "pass": total - failures - errors - skipped,
            "pass_rate": f"{((total - failures - errors - skipped) / total * 100):.1f}%" if total > 0 else "N/A",
        }
    except Exception as e:
        return {"total": 0, "failures": 0, "errors": 0, "skipped": 0, "pass": 0, "pass_rate": f"Error: {e}"}


def get_git_info() -> dict:
    """Get current git SHA and branch."""
    try:
        sha = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=REPO_ROOT, capture_output=True, text=True
        ).stdout.strip()
        branch = subprocess.run(
            ["git", "branch", "--show-current"],
            cwd=REPO_ROOT, capture_output=True, text=True
        ).stdout.strip()
        if not branch:
            branch = "detached HEAD"
        return {"sha": sha, "branch": branch}
    except Exception:
        return {"sha": "unknown", "branch": "unknown"}


def generate_master_plan(test_results: dict, git_info: dict) -> str:
    """Generate the full MASTER_PLAN.md content."""
    lines = []
    lines.append("# MEOWMBA MASTER PLAN")
    lines.append("")
    lines.append(f"> **Project codename:** Meowmba | **App name:** Memba | **Package:** `com.example.meowmba` (debug) / `com.meow.meowmba` (release)")
    version_name, version_code = get_version()
    lines.append(f"> **Version:** {version_name} (versionCode {version_code}) | **Min SDK:** 31 | **Target SDK:** 36 | **Compile SDK:** 37")
    lines.append(f"> **Language:** Kotlin | **UI:** Jetpack Compose (Material 3) | **Build:** Gradle KTS")
    lines.append(f"> **Last scanned:** {subprocess.run(['date', '+%Y-%m-%d %H:%M'], capture_output=True, text=True).stdout.strip()} | **Commit:** `{git_info['sha']}` on `{git_info['branch']}`")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## Update Checklist")
    lines.append("")
    lines.append("**Test Results:**")
    if test_results.get("total", 0) > 0:
        lines.append(f"- Total: {test_results['total']} | Passed: {test_results['pass']} | Failed: {test_results['failures']} | Skipped: {test_results['skipped']}")
        lines.append(f"- Pass rate: {test_results['pass_rate']}")
    else:
        lines.append("- Test results unavailable (run `./gradlew :app:testDebugUnitTest`)")
    lines.append("")
    lines.append("**To update this checklist automatically, run:**")
    lines.append("```bash")
    lines.append("python3 scripts/update_master_plan.py")
    lines.append("```")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 1. What It Is")
    lines.append("")
    lines.append("Meowmba is an on-device Android AI companion represented by an animated cat avatar. It is a voice-first, tool-routing, memory-remembering, robot-controlling, desktop-bridging personal assistant. The companion speaks, listens, remembers, performs animations, controls a LOOI robot body, pairs with a desktop for file/build operations, and routes its \"brain\" across on-device models, LAN servers, and cloud APIs.")
    lines.append("")
    lines.append("**Core identity:** The model proposes; the orchestrator constrains. No shell executor. Honest capabilities only — unavailable adapters are badged UNAVAILABLE, never faked.")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 2. Completed Phases (Shipped)")
    lines.append("")

    total_items = 0
    total_done = 0

    for phase_key, phase_data in FEATURES.items():
        lines.append(f"### {phase_data['title']}")
        lines.append("")

        phase_done = 0
        phase_total = len(phase_data["items"])

        for desc, paths, terms in phase_data["items"]:
            done = verify_item(paths, terms)
            total_items += 1
            phase_total_counted = phase_total
            if done:
                total_done += 1
                phase_done += 1
            lines.append(f"- {'[x]' if done else '[ ]'} {desc}")

        lines.append("")
        lines.append(f"**{phase_data['title']}: {phase_done}/{phase_total} complete**")
        lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("## 3. Summary")
    lines.append("")
    lines.append(f"**Shipped features tracked:** {total_items}")
    lines.append(f"**Verified shipped:** {total_done}")
    lines.append(f"**Unverified shipped checks:** {total_items - total_done}")
    lines.append("")
    lines.append("Completion here applies only to the shipped checklist; roadmap work remains below.")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 4. Key Technical Details")
    lines.append("")
    lines.append("### Brain Routing (OmniRoute order)")
    lines.append("1. **Keyword fast path** — deterministic, free (\"create file called notes.txt\")")
    lines.append("2. **Hermes Agent endpoint** — OpenAI-compatible `/v1/chat/completions` with native function-calling `tools`")
    lines.append("3. **On-device brain** — LlmToolCallRouter, single-JSON tool decision under compact prompt")
    lines.append("")
    lines.append("### Voice Pipeline")
    lines.append("- **Wake:** SherpaKwsEngine (keywords from KwsKeywords.kt — tokens must exist in model vocab)")
    lines.append("- **VAD:** VadGate + WindowCarry (Silero VAD)")
    lines.append("- **ASR:** Whisper / Sherpa / Gemini (multi-engine)")
    lines.append("- **TTS:** SpeechEngine interface (Piper, cloud APIs)")
    lines.append("- **Speaker verification:** SpeakerVerifier (on-device)")
    lines.append("- **Echo suppression:** SelfVoiceFilter (TTS word-boundary tracking)")
    lines.append("- **Turn deduplication:** TurnDeduplicator (8s window)")
    lines.append("")
    lines.append("### Memory Architecture")
    lines.append("- **Room v7:** messages, memories, memories_fts, audit_log, characters")
    lines.append("- **Embeddings:** LocalHashEmbedder (signed feature hashing, L2-normalized)")
    lines.append("- **Recall:** cosine similarity × importance weight, top-5 with explanations")
    lines.append("- **Context window:** persona + capabilities + recalled memories + last 10 messages")
    lines.append("- **Vault:** Obsidian-compatible markdown notes in `filesDir/vault`")
    lines.append("")
    lines.append("### Security Model")
    lines.append("- **No shell executor** — phone side or desktop bridge")
    lines.append("- **Secrets:** Keystore-backed SecureStorageManager")
    lines.append("- **API 30+ package visibility:** `<queries>` entries for metamemory/inventory/maps/speech")
    lines.append("- **Desktop bridge:** HMAC-SHA256 signatures, 60s replay window, cert pinning")
    lines.append("- **SSH:** allowlisted read-only commands, reject shell metacharacters, TOFO host keys")
    lines.append("- **Cloud backup:** chat/keys/files OFF")
    lines.append("- **Cleartext HTTP:** LAN/localhost only (documented in network_security_config.xml)")
    lines.append("")
    lines.append("### Tool System")
    lines.append("- **Registry:** ToolRegistry (register, byId, definitions)")
    lines.append("- **Risk levels:** SAFE (no confirm), MODERATE (confirm per task), DESTRUCTIVE (always confirm)")
    lines.append("- **Approval:** 5min auto-deny, queued (one at a time)")
    lines.append("- **Audit:** every task transition → Room audit_log")
    lines.append("- **No shell:** fixed argv lists only, no string interpolation")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 5. Project Roadmap (Remaining)")
    lines.append("")
    lines.append("| Phase | Description | Status |")
    lines.append("|-------|-------------|--------|")
    lines.append("| Phase 7 | Vision (full) | Partial — follow-only done, Commentary mode built |")
    lines.append("| Phase 8 | Advanced avatar (expanded animation registry) | Started — invented animations shipped |")
    lines.append("| Phase 9 | Desk & car modes | Planned |")
    lines.append("| Phase 10 | Ecosystem | In progress — plugins, remote MCP, phone MCP, service clients, and setup gallery shipped |")
    lines.append("| Phase 11 | Native conversational audio | Planned — Gemini Live/Firebase AI Logic and/or OpenAI audio |")
    lines.append("| Phase 12 | Android AppFunctions | Preview investigation — cross-app execution requires approved system-agent access |")
    lines.append("| — | llama.cpp provider (run any GGUF on-device) | Queued |")
    lines.append("| — | Native on-device image gen (stable-diffusion.cpp) | Queued |")
    lines.append("| — | Music generation | Branch exists |")
    lines.append("| — | Video generation | Branch exists |")
    lines.append("| — | Godot avatar prototype | Branch exists |")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 6. Repositories & Remotes")
    lines.append("")
    lines.append("| Remote | URL | Role |")
    lines.append("|--------|-----|------|")
    lines.append("| `lan` | `meow:/home/claude/git/meowmba-app.git` | **CANONICAL** — gets real work, what phone runs |")
    lines.append("| `prog` | `prog@192.168.1.69:/home/prog/git/meowmba-app.git` | Mirror (was 54 commits behind, now synced) |")
    lines.append("| `origin` | `meow-nb:/home/claude/git/meowmba-app.git` | Notebook (unreachable — Tailscale dead) |")
    lines.append("")
    lines.append("**Working copies:**")
    lines.append("- PRIMARY (edit here): `/Users/bobrossjr/AndroidStudioProjects/Meowmba`")
    lines.append("- Local mirror: `~/meowmba-app`")
    lines.append("- This worktree: `/Users/prog/Documents/Codex/2026-08-25/ssh/meowmba-130`")
    lines.append("")
    lines.append("**Sync rule:** `git fetch --all && git log --oneline -1 lan/main` before building. Building from `prog-main` while `lan/main` moved on silently reverts a week of work onto the phone.")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 7. Build & Test")
    lines.append("")
    lines.append("```bash")
    lines.append("# Build debug APK")
    lines.append("./gradlew :app:assembleDebug")
    lines.append("")
    lines.append("# Run unit tests (~1 min)")
    lines.append("./gradlew :app:testDebugUnitTest")
    lines.append("")
    lines.append("# Install on device")
    lines.append("adb install -r app/build/outputs/apk/debug/app-debug.apk")
    lines.append("")
    lines.append("# Release bundle (Play Store)")
    lines.append("./gradlew :app:bundleRelease")
    lines.append("```")
    lines.append("")
    lines.append("**Pre-build requirements:**")
    lines.append("- `local.properties` with `sdk.dir` (and `debugKeystore=` for matchable debug key)")
    lines.append("- `scripts/fetch_kws_assets.sh` on fresh worktree (~100MB voice models)")
    lines.append("- Bump `versionCode` past on-device build before install")
    lines.append("")
    lines.append("**Known pre-existing test failures (NOT yours):**")
    lines.append("- `ModelDownloadViewModelTest`: 3 JVM-only failures (unmocked `android.content.Intent`)")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 8. Device & Debugging")
    lines.append("")
    lines.append("- **Device:** Galaxy Z Fold 7 (serial RFCY61NJEVM)")
    lines.append("- **Dual-display screencap gotcha:** bare `adb shell screencap` grabs INNER display (OFF) → pure black. Always capture cover display: `adb shell screencap -p -d 4630946872173396372`")
    lines.append("- **On-device diagnosis:** pull Room DB via `adb exec-out run-as com.example.meowmba cat /data/data/com.example.meowmba/databases/meowmba_database` — audit_log + messages tables")
    lines.append("- **Room WAL:** pull `meowmba_database-wal` too, or miss fresh rows")
    lines.append("- **Verify running process predates last install:** `ps -o ETIME`")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 9. Open Work Items (Ranked)")
    lines.append("")
    lines.append("### Tier 0 — Product Completion")
    lines.append("1. **End-to-end speech** — production native audio with local wake, commands, captions, and cascade fallback.")
    lines.append("2. **MCP account linking** — OAuth discovery, PKCE, callback, refresh, logout, and revoked-token recovery.")
    lines.append("3. **Installed-app integrations** — real icons and honest remote MCP/AppFunctions/intent/UI-automation classification.")
    lines.append("4. **Android AppFunctions** — expose Meowmba now; consume cross-app functions only when the OS grants access.")
    lines.append("5. **Connection safety** — approval/audit, conservative risk, prompt-injection defenses, and visible scopes.")
    lines.append("6. **On-device verification** — real logins and read/write calls with audit-log confirmation.")
    lines.append("")
    lines.append("### Tier 1 — Real Engineering")
    lines.append("1. **Face-track staircase** — dead-band + maxStep + second dead-band → discrete jumps. Keep one dead-band, drop maxStep, publish on fixed cadence.")
    lines.append("2. **mouthEnergy recomposes avatar ~14×/sec** — move out of CompanionState into own StateFlow, read as lambda in draw pass.")
    lines.append("3. **IMU/face-track servo writes on main thread at ~50Hz** — route setServo through conflated channel on Dispatchers.IO, register sensor on HandlerThread.")
    lines.append("4. **Duplicate GATT connect storm** — ~8 concurrent LooiBleLink connections in 20ms. May be incidentally fixed; confirm or fix properly.")
    lines.append("5. **ConfinementModel.onPickedUp()** — implemented, tested, never called. Needs IMU signature (free-fall window or gravity vector change).")
    lines.append("6. **SensorEvent.Tof** — parsed but not used as evidence. Needs real frame capture to interpret.")
    lines.append("")
    lines.append("### Tier 2 — Animation Quality")
    lines.append("1. 11 keyframe built-ins permanently unreachable (composed expression owns name)")
    lines.append("2. Four expression pairs effectively identical (head bonk/bunt, content sigh/relief, groom/paw lick, zoomies/zoomies burst)")
    lines.append("3. EyeL.WIDE ≈ EyeL.ALERT once clamped — re-space on pupil size")
    lines.append("4. Body.RECOIL at full amplitude on frame 0 (no anticipation); Body.COIL ends mid-launch")
    lines.append("5. Ear/jaw rhythms on normalized clip time — pass absolute seconds")
    lines.append("6. JawL.PULSE straddles MouthMode threshold → purr strobes HAPPY/SPEAKING")
    lines.append("7. quick blink 0.45s below visibility floor; keyframe built-ins push eyeOpen to 1.4–1.6")
    lines.append("8. applyTo fires sparkle on startle/recoil/surprise — delight VFX on fear beat")
    lines.append("")
    lines.append("### Tier 3 — Verification Needed")
    lines.append("1. Wake word end-to-end (4 causes fixed, never observed triggering a turn)")
    lines.append("2. robot.move reaching motors (routing unit-tested, no spoken command watched hitting fe00)")
    lines.append("3. Settings staying open through idle clip")
    lines.append("4. Alarm end-to-end")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 10. Play Store Submission")
    lines.append("")
    lines.append("- **Package:** `com.meow.meowmba` (release) / `com.example.meowmba` (debug USB)")
    lines.append(f"- **Version:** {version_name} (versionCode {version_code})")
    lines.append("- **AAB:** `app/build/outputs/bundle/release/app-release.aab` (154 MB)")
    lines.append("- **Privacy:** https://dekayrekords-oss.github.io/Meowmba/privacy.html")
    lines.append("- **Native:** 16KB ELF-aligned, arm64-v8a only")
    lines.append("- **Status:** Code-side prep done. Console work (create app, upload AAB, data safety form, permissions declarations, store listing) still required.")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## 11. Conventions")
    lines.append("")
    lines.append("- **Commits:** `<type>(<area>): <plain-english what + why>` — types: feat/fix/chore")
    lines.append("- **Honesty over polish:** never fake a capability")
    lines.append("- **Comment style:** KDoc explaining the *why*, often with observed device behavior")
    lines.append("- **Deterministic fast path first:** keyword routing before any model call")
    lines.append("- **Model proposes, orchestrator constrains:** tools never decide policy")
    lines.append("- **Tests are pure-JVM where possible:** android.net.Uri etc. not mocked; put string logic in static/companion functions")
    lines.append("- **Room migrations:** bump + migrate, never `fallbackToDestructiveMigration`")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append(f"*Generated: {subprocess.run(['date', '+%Y-%m-%d %H:%M'], capture_output=True, text=True).stdout.strip()} from meowmba-130 worktree at commit {git_info['sha']} (v{version_name})*")

    return "\n".join(lines)


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Update MASTER_PLAN.md with current checkbox states")
    parser.add_argument("--skip-tests", action="store_true", help="Skip running unit tests (faster)")
    parser.add_argument("--dry-run", action="store_true", help="Print to stdout instead of writing file")
    args = parser.parse_args()

    print("🔍 Scanning codebase for feature verification...")

    test_results = {"total": 0, "failures": 0, "pass": 0, "pass_rate": "skipped"}
    if not args.skip_tests:
        print("🧪 Running unit tests...")
        test_results = run_tests()
        print(f"   Tests: {test_results.get('total', 0)} total, {test_results.get('pass', 0)} passed, {test_results.get('failures', 0)} failed")

    git_info = get_git_info()
    print(f"📋 Generating master plan at commit {git_info['sha']}...")

    content = generate_master_plan(test_results, git_info)

    if args.dry_run:
        print(content)
    else:
        output_path = REPO_ROOT / "MASTER_PLAN.md"
        output_path.write_text(content, encoding="utf-8")
        print(f"✅ MASTER_PLAN.md updated at {output_path}")

    # Print summary
    total_items = sum(len(p["items"]) for p in FEATURES.values())
    total_done = 0
    for phase_data in FEATURES.values():
        for desc, paths, terms in phase_data["items"]:
            if verify_item(paths, terms):
                total_done += 1

    print(f"\n📊 Feature completion: {total_done}/{total_items} ({total_done/total_items*100:.1f}%)")


if __name__ == "__main__":
    main()
