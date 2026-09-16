# Provider cards — 1.3.75 / code 79

## Changes

- OpenAI searchable model dropdown refreshes from the account's `/v1/models` on opening, credential/endpoint changes (800 ms debounce), explicit refresh, and every five minutes while the settings screen is started. Background/screen-close cancels discovery. Selection is never silently changed. Failed refresh retains a last successful list for unchanged credentials; manual model IDs remain usable.
- Claude / Anthropic card stores its own key/model/endpoint in encrypted preferences and uses the native Messages API, version 2023-06-01. Streaming errors, truncated replies, empty text, and incomplete connections are not treated as success.
- Custom API card stores an independent key, HTTPS base URL, model, and OpenAI-compatible or Claude Messages format. Endpoint normalization accepts a trailing `/v1`. Redirects are disabled for authenticated requests.
- OpenCode Zen card exposes the existing `opencode` credential and `bigpickle` route. The model is now configurable. Its live catalog is filtered to supported protocol families. OpenCode CLI is already installed on this Mac (1.18.29); it is a separate coding agent, not a phone model/runtime.
- Per-card reply tests send a short real request only on explicit click and report the actual result. Catalog discovery alone never claims chat compatibility or a working reply. No paid key/credit purchase or key extraction is performed.
- Claude and custom providers participate in chat, agent planning, phone accessibility and Logic/desktop control through the existing orchestrator. Anthropic native tool calls receive the actual controller schema, and exactly one validated action is required before execution. Existing device permissions, approval policy and audit remain enforced. Hardware control remains limited by implemented tools, device support and Android permissions.

## Limits

OpenAI's model-list response does not declare endpoint compatibility. The chat picker hides known audio/image/embedding/Responses-only families and preserves manual IDs. OpenCode Responses-only/Google-format models are not implemented in this card. Custom providers must support one of the two advertised formats; this is not arbitrary API-protocol compatibility. Claude chat is not an audio transcription provider. Previous OpenRouter transcription HTTP 402 remains an account/billing issue and is not fixed by adding cards.

## Primary API references

- OpenAI model listing: https://developers.openai.com/api/reference/python/resources/models/methods/list
- Anthropic models: https://platform.claude.com/docs/en/api/models/list
- Anthropic streaming: https://platform.claude.com/docs/en/build-with-claude/streaming
- Anthropic tools: https://platform.claude.com/docs/en/agents-and-tools/tool-use/parallel-tool-use
- OpenCode installation: https://opencode.ai/docs/
- OpenCode Zen protocols/catalog: https://opencode.ai/docs/zen/

## Verification

Full provider build: 2,077 tests, zero failures/errors, five skips. A final wording-only fix marks the custom URL required; all nine provider UI tests passed again after that edit. Debug build and signature checks passed. All 125 runtime assets match 1.3.73 byte for byte. No app data or keys were cleared.

Phone acceptance on the connected SM-F966U1 verified the OpenAI dropdown, all three new cards and their fields. Live OpenAI account discovery returned 54 models; selected gpt-4o-mini remained unchanged. The explicit reply test returned HTTP 429 with “no credits remaining.” Catalog authentication works, but paid generation is blocked by account credits. Claude, custom and OpenCode Zen keys are not configured; their wire protocols are verified with deterministic mock HTTP tests, not live account responses.

The release directory is `/Users/prog/Meowmba-Releases/providers-2026-09-09/`, including both the first candidate and final corrected APK, hashes, test logs, runtime comparison and redacted phone acceptance results. USB installation verified version1.3.75/code79 at18:17:03 with the original first-install date preserved. The corrected required custom-URL label was verified on the phone. The final process had no observed fatal exception; USB desktop pairing/tunnel remains intact. See installed-version.json.
