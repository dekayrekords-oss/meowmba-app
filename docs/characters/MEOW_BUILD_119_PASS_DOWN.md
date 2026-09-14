# Build 119 — describe and become a new cartoon form

The owner requested that the avatar turn into anything the user describes.
This build extends `avatar.form` beyond a preset whitelist: explicit voice/text
requests and Avatar → Look → Become anything use the selected AI model to create
validated native cartoon geometry. Presets remain immediate; saved request/name
matches reuse the actual design without inference. These are animated vector
interpretations, not newly generated 3D meshes or guaranteed exact likenesses.

## What changed and why

- `AvatarFormRequest`, `AvatarFormTool`, `AvatarFormInventor`, and both tool
  prompts preserve rich descriptions, route to the actual on-screen avatar,
  validate one bounded JSON proposal and permit one repair. A tool cannot report
  success until the form has been saved and applied. New designs need a working
  selected model; errors leave the prior appearance intact.
- `GeneratedAvatarForms` permits at most 32 outlined ellipse, rounded rectangle
  or polygon parts. All geometry, colors, face placement, motion labels and
  payload lengths are bounded. Model output is data; there is no executable
  drawing code, remote texture, arbitrary file path or new tool permission.
- `GeneratedAvatarPainter` uses the existing Lumen frame for gaze, blinking,
  mouth, expression, perspective and neural wing beat. Arms, legs, antennae and
  tail use the same active clock. Habitat movement, touch play, avatar zoom and
  the fixed privacy markers remain in the existing renderer. The same neural
  system and memory continue across appearance changes.
- `GeneratedAvatarStore` keeps up to 24 forms, with content-derived IDs, atomic
  file replacement, bounded reads, corrupt-file rejection and no silent
  eviction. A separate encrypted selected ID restores the chosen form; selecting
  a preset clears that ID in the existing preference transaction.
- Setup has a description field, examples, explicit Create/Cancel, real status
  and errors, and saved-form selection/removal. Presets remain available while
  a model is working. Selecting or deleting a form does not change camera,
  microphone, neural mode, habitat choice or care preferences.
- ViewModel generation tokens, an independent native cancellation element and
  a scoped job reject stale results after cancellation, a newer choice, a new
  conversation or foreground exit. Validated save/select is a short serialized
  commit; no suspended late write may resurrect a cancelled form.
- The review exposed a pre-existing orchestrator leak: a tool throwing
  cancellation left its task RUNNING. Terminal-first, idempotent cancellation
  now releases approvals/slots, advances queued work only while the parent scope
  is active, and prevents late success from overwriting cancellation. The form
  tool has a 100-second deadline around its 90-second generation budget.

## Teach Meow: debugging this path

1. If the avatar only talks about a change, inspect the deterministic route and
   tool result. `avatar.form` is the appearance action; media generation makes
   files and cannot satisfy this request.
2. If a new form fails, check the chosen model and validated response. Do not
   fabricate a fallback shape under the requested name. The previous form must
   remain visible. Saved designs can be selected without network/model access.
3. If a late response replaces a newer choice, inspect `_form_generation`,
   `avatarFormGeneration`, the independent cancellation scope and conversation
   ownership. Presets need the same queued-request guard as generated forms.
4. If future tasks stay queued, inspect terminal task states and reserved jobs.
   Cancellation must release its slot exactly once, including cancellation
   before coroutine dispatch and during an approval wait.
5. If a design restores incorrectly, compare its canonical content hash and
   encrypted `generated_avatar_id`. Never use a generated display name as a
   filesystem path. A corrupt form is skipped; a preset remains usable.
6. If touch lands on scenery, inspect `GeneratedAvatarPlacement` against the
   exact draw center/unit. Privacy markers stay outside generated geometry.
   Frozen animation must use the frozen shared clock; never add a second timer.

## Verification and deployment

Implementation checkpoint: validation/build/visual inspection are in progress.
Do not infer that Build 119 is installed. USB ADB currently reports no devices;
the last verified phone build remains 112 and the owner asked to continue
without the phone. Build 118's camera and portable MaleCNS work is retained.
Physical graphics, camera, battery/thermal and robot checks require the device
or hardware and are not replaced by JVM/native render tests.

Worker details: `BUILD119_FORM_COMMAND_NOTES.md` and
`BUILD119_GENERATED_RENDER_NOTES.md`. Final artifact hashes and test totals will
be recorded after the root-owned private Meow build.
