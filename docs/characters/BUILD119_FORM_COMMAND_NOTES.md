# Build 119: natural-language avatar form commands

This is the command/generation worker handoff. Root owns integration, final test
results, versioning, commit, deployment and the complete Meow passdown.

## What changed and why

The former `AvatarFormRequest` routed only a short allowlist of animals (at most
three words / 48 characters), and `AvatarFormTool` rejected everything absent
from `AvatarAnimal`. Thus a user could not turn the live avatar into a coffee
cup, a spaceship, or a dragon with a specific color scheme even when an AI
model was available. Both routing prompt formats also told the model that
unsupported forms must fail.

Explicit transformation verbs now preserve a visual description of up to 500
characters and route it through the existing audited `avatar.form` tool.
Examples include `turn into a green dragon with red and gold wings`, `become a
coffee cup wearing a purple hat`, and `change the avatar to a tiny spaceship`.
The ambiguous conversational verb `be` remains conservative (`be a CEO` and
`be my friend` are not appearance changes). Bare professional roles such as
`become a doctor` stay conversational; `change avatar to doctor` or `turn into a
cartoon doctor` is explicit. Visible nouns (`stop sign`, `open book`, `play
button`) and absent body features (`dragon without wings`) remain descriptive
without allowing coordinated actions or negative transformation requests. Quoted stories, negations, media
file requests and compound external actions are rejected as a whole rather
than executing their first positive fragment. Description conjunctions and
accessories remain valid (`with a book`, `red and gold wings`).

Known presets are still immediate, and exact preset aliases such as `paper
clip` or `fruit_fly` remain valid. Unknown descriptions invoke the configured
host callback, which is responsible for selected-model generation, saved-form
lookup, persistence and application. The tool reports the callback's actual
result. A host that has not supplied generation reports unavailability instead
of silently substituting an animal.

`AvatarFormInventor` takes the currently selected model through an injected
`suspend (String) -> ModelResponse`. The model returns only the bounded native
`GeneratedAvatarForm` drawing schema: at most 32 layered shapes plus a live
face layout. The prompt asks for defining silhouettes, colors and accessories;
existing gaze, blinking, speech and the brain supply expression. No model code,
URLs, assets, images, shaders or robot actions are evaluated. The strict schema
parser rejects unsupported fields, invalid colors/coordinates and trailing
content. A normal fenced JSON object is accepted.

There is at most one validation repair. Model availability errors and unfinished
responses fail immediately. Provider cancellation and host timeout propagate;
cancellation is checked again after each model response. There is no random
fallback design masquerading as the requested form.

## Integration contract

- `AvatarFormTool(onChange, onGenerate, validateGeneration)` is the full
  constructor. One- and two-callback secondary constructors preserve old
  trailing-lambda call sites.
- `onChange: (AvatarAnimal) -> Unit` must persist/apply the preset synchronously
  or throw.
- `onGenerate: suspend (String, Long?) -> ToolResult` receives the full requested
  description and optional `_form_generation` token.
- `validateGeneration: (Long?) -> Unit` runs before either callback. Root rejects
  stale queued tasks by throwing `CancellationException`, including immediate
  presets that otherwise could overwrite a newer selection.
- The tool has a 100-second orchestrator deadline. The host's 90-second creation
  deadline has room to finish validation, persistence and reporting.
- `AvatarFormInventor(modelCallback).invent(description): GeneratedAvatarForm`;
  public `prompt(description)` and `parseResponse(raw)` support verification.
- The root host owns timeout, cancellation generation, selected-provider
  access, saved-form persistence and actual selection.

## Checks and future diagnosis

Worker static check: `git diff --check` passed. The worker did not build or run
Gradle; one root build owner is required. Root should include
`AvatarFormToolTest` (23 tests) and `AvatarFormInventorTest` (10 tests), along
with `ToolCallPromptTest`, `OfficeHelpersTest` and `FruitFlyAvatarTest`.

The new checks cover rich descriptions, command gating, real tool routing,
actual model-output consumption, exactly one schema repair, provider failure,
partial output, cancellation/timeout, executable/trailing output rejection,
preset compatibility and stale queued preset rejection. Model fixtures in
these tests are deliberately labelled fixtures, not evidence of live inference.

If the chat says it transformed without changing appearance, first look for an
`avatar.form` audit result; a response with no corresponding successful tool
entry is not evidence of a renderer failure. If a request still goes to media,
inspect deterministic `AvatarFormRequest` and both `ToolCallPrompt` modes, not
only the natural-language chat prompt. If creation is cut off after 30 seconds,
check the tool timeout as well as the host deadline. If an old form reappears
after a new choice, inspect generation-token stamping at task submission and
both callback entry points. If an available model repeatedly fails design
validation, retain the concrete schema error and simplify its requested
silhouette; do not weaken finite bounds or allow generated drawing code.

## Bounded live Meow model check

The existing `Qwen3.8-Flash-Next` llama-server process on Meow exposed its
localhost OpenAI-compatible API at port 8090. Ollama reported no loaded model;
no Ollama model was loaded for this test. No service was restarted or
reconfigured, no model was installed/downloaded, and no paid fallback was used.

Three actual generator prompts (coffee cup, dragon, spaceship) each reached an
80-second HTTP deadline without a response. A further streaming coffee-cup
retry reached its 90-second total deadline with zero content and zero reasoning
characters. Both ordinary and streaming payloads already set
`chat_template_kwargs.enable_thinking=false`; the served chat template exposes
that flag. The server was idle before the streaming retry. During that retry,
read-only `/props` and `/slots` checks each timed out after five seconds; the
existing process showed Linux state `Dsl`, RSS 6,342,420 KiB and 0.0% reported
CPU. This is evidence of unavailable/stalled inference during the measurement,
not proof of an invalid design or a specific underlying model/server fault.

No live model-generated design was produced, so there is no successful live
schema/render validation to claim. Deterministic fixture tests remain separate
from this evidence. No invalid-output repair was attempted because all failures
were transport deadlines before any output, matching the generator's policy
that provider failure is not a schema repair.

Raw prompts, request settings, timing/failure JSON and reproduction scripts are
retained privately at `/home/prog/research/meowmba-build119-live-forms` on Meow
and `build-out/habitat/build119/live` locally. The three-prompt trial took about
240 seconds; the separate streaming retry took 90 seconds. Root owns final
acceptance and reporting of this live-inference limitation.
