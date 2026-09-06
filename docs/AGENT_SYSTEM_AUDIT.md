# Meow Assistant agent and media audit

## Verdict

Keep the existing Android tool layer, workspace sandbox, model routing and
task orchestrator. They provide useful foundations. Do not describe the
current system as universal app control or assume that a named agent is an
independent, working autonomous worker. Most agents are permission-scoped tool
groups sharing a model/router; modelAssignment is a definition field, not
proof that a separate model is executing each agent's tasks.

The working target is: user goal → observe → select an installed capability →
check its prerequisites/authorization → act → observe the result → verify →
report the artifact or unresolved blocker. An installed tool and a successful
network connection are both weaker evidence than completing a real task.

## Verified scope and limits

Reviewed `/home/claude/meowmba-sdkbuild` on Meow, starting from release-fix
commit `31ebee0`. Inspected the agent registry, tool orchestrator, keyword/model
routers, phone loop, connector manager, MCP client, media tools, FileProvider
and media opening code. Inspected the live video service and ComfyUI's exposed
node/model inventory without submitting a GPU generation job.

There is no phone attached to Meow's ADB. Installed phone settings, account
tokens, Accessibility permission, UI transitions, live glasses pairing and
actual external-player behavior could not be certified. No email, message,
calendar event or robot command was sent during this review.

Validation result: 103 targeted tests across 14 classes passed, with zero
failures, errors or skips. This covers artifact MIME/grants/provider paths,
media backend routing and rejection, task concurrency/approval/routing,
workspace operations, studio rendering, email routing, desktop protocol,
Needle/Hermes parsing and the browser loop. These are software tests using
test data/fakes where needed, not certification of real accounts or hardware.
The SDK-enabled debug application and test sources compiled successfully.

## Agent-by-agent assessment

| Agent | Actual implementation | Remaining real-world requirement |
|---|---|---|
| Meow | Routes single tool requests and handles conversation, avatar and notes | Working selected model; final result must come from actual tool execution |
| Shelve | Reads/writes/lists/deletes sandboxed workspace files | Not access to every file on the phone; storage access must be granted separately |
| Keeper | Memory save/search and capture controls | Room repository and capture service/permissions; device persistence test |
| Builder | Project folders and manifests | Local workspace; not a deploy/build agent by itself |
| Forge | Code file operations | Does not independently prove that code builds or passes tests |
| Scout | Web search/fetch, memory search, browser tools | Reachable search/network services and response verification |
| Weaver | Browser tools use an offscreen WebView; a separate browser loop also exists | Not the user's Chrome session; browser login state is separate; no general proof of completed transactions |
| Post | Saves a draft or opens a prefilled email composer | User taps Send; this is not a connected Gmail send API |
| Planner | Calendar provider list/create tools | Calendar account/provider and runtime permissions |
| Bridge | Paired desktop tools | Pairing and a reachable desktop service; desktop.control ownership repaired in this change |
| Paws | Android intents and supported phone actions | Target app installed, appropriate permissions and an actual intent handler |
| Claw | Accessibility-driven observe/model/action loop | Accessibility enabled, working brain, inspectable app screens; not universal control |
| Canvas | Configured image/video/audio generators | Working backend/model files; failed image/video jobs now fail honestly |
| Robot base | Physical movement adapter | Attached compatible base; no hardware test performed |
| Ink | Comic/script HTML generation and character storage | Generated HTML is not proof of illustrated AI artwork; provider path/opening repaired |
| Shades | Real Meta SDK camera bridge | SDK exists in release; registration, camera permission, real glasses and fresh frames still need device testing |

## Problems repaired in the app

1. Image failures used to become decorative shapes/text labeled as a generated
   image. They now return a failure and setup guidance. PNG encoding failure
   also fails the task.
2. Video failures used to save a still frame and a manifest referring to
   deleted temporary frames, then announce a completed video. That fallback is
   removed. An obvious non-MP4 response is rejected before it is saved. The
   container-header check is not a full codec/playback validation.
3. Files were forced into Chrome regardless of format. HTML prefers Chrome;
   image/video/audio/document files now use an external compatible viewer with
   the proper MIME type and a temporary content-URI read grant. WEBP, WEBM,
   M4A and PDF no longer fall through to text/plain. The old media overlay is
   cleared when the artifact is opened externally.
4. The FileProvider omitted web_pages, where generated browser pages are saved.
   That directory and desktop_screenshots now have explicit sharing paths.
5. Finding the newest artifact only inspected the top-level workspace. A
   bounded recursive search now finds nested studio/project files and ignores
   empty artifacts. Display requests mentioning Chrome are recognized too.
6. desktop.control was installed but absent from every agent's allowlist. It
   now belongs to Bridge, retaining the tool's existing approval policy.
7. Task concurrency slots were reserved only after the coroutine began. Rapid
   submissions could exceed the agent limit. Slots are now reserved before
   dispatch, with a regression test.
8. Phone-control waits and unreachable-brain retries bypassed the step budget.
   They now consume attempts; model calls have a timeout; terminal paths clear
   the shared active flag; unstructured prose is not announced as success.
9. Every roster card previously defaulted to ACTIVE. Cards now show installed
   tool coverage (NO TOOLS / PARTIAL / TOOLS READY) and explicitly distinguish
   that from verified account/hardware readiness. Email and phone-control
   descriptions no longer promise capabilities their adapters do not provide.

## Remaining blockers before reliable general app control

### MCP is incomplete

ConnectorManager has a secret provider but constructs McpHttpClient without
passing those secrets. The client has no authentication input/header support.
It does not establish/preserve a complete MCP session, notify initialized or
preserve the session ID. Its SSE handling concatenates lines and does not
properly handle multi-event streams. ping currently accepts a JSON-RPC error
as evidence of success. Tool-call results retain only the first text block;
images/resources and other result blocks are discarded. Structured arguments
are flattened into strings.

Dynamic connector/service tool IDs also have no general ownership mapping in
AgentRegistry. The model routers require an owner, so a connector can register
tools that are then unusable through the normal agent dispatcher. Disabling a
connector does not unregister its previously loaded tools. These must be
repaired together, with authentication, session, typed-result and revocation
tests, before MCP can be called reliable. Do not fix this by granting the main
agent an unrestricted wildcard allowlist.

### Separate execution paths undermine verification

The phone loop starts directly from several ViewModel routes rather than
running as one orchestrated, audited task. Its final done action still relies
on the model's interpretation. Some UI automation tools wrap human-readable
failure strings in ToolResult.Success. The browser has multiple paths with
different visible sessions. These need one lifecycle, explicit action outcomes,
post-action observations, user-visible progress/cancel, and honest termination.
Existing permission/approval requirements must carry through every path.

### The live video server cannot use its configured model names

The meow-video-api service is active on port 8002, and its OpenAPI endpoint
exposes the app's expected generate/status/result routes. ComfyUI responds,
and the required workflow node classes are present. However its workflow
references these unavailable models:

- minimax_h3_fl2va_pruned_mixed_int4_int8_convrot.safetensors
- qwen3vl_32b_minimax_h3_int4_convrot.safetensors

The exposed alternatives are an INT8 video checkpoint and an NVFP4/AWQ text
encoder. They must not be substituted blindly: loader compatibility, GPU
support, memory use and output need checking first. Both configured VAEs are
present. No production service configuration or GPU workload was changed.

### Phone/account verification is still required

Connect the Fold to Meow, inspect the actual selected providers/endpoints and
permissions, then test: a sandbox file roundtrip; a note persistence roundtrip;
one browser read/navigation; a draft email; a permitted calendar action; an
accessible app search and stop; one real image/video job and external playback;
paired desktop read-only status; real glasses registration and fresh capture.
Use throwaway test data and only perform external actions authorized by the
user. Mark each capability verified only after observing its result.

## Recommended completion order

1. Merge and device-test these concrete media/agent fixes.
2. Repair the video backend's model configuration and verify a real small clip.
3. Repair MCP authentication/session/typed results, explicit ownership and
   disconnect revocation as one bounded change.
4. Route phone/browser loops through the shared orchestrator and audit log;
   use observed results to determine success rather than fluent narration.
5. Add a capability test panel: Installed / Needs setup / Unreachable /
   Verified, showing the last successful test and what the user must do next.

External file sharing follows [Android's FileProvider approach](https://developer.android.com/reference/androidx/core/content/FileProvider).
