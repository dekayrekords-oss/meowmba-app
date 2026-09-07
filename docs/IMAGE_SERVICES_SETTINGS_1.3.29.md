# Image services and category settings — 1.3.29 / code 33

## Delivered behavior

- Settings is an icon-card overview. Each category opens a separate full-screen
  option page with a pinned Back button. Back and Done save provider edits.
- Categories: Voice & behavior; AI & models; Images; Music & video; Phone & device;
  Appearance; Connections & memory; Driving; Help & feedback.
- Voice and AI model pages link directly to their existing model-management screens.
  Appearance exposes system/light/dark mode, app theme and avatar style.
- OpenRouter catalog loading is deferred until AI & models is opened. Other pages
  no longer trigger a catalog request or rewrite unrelated provider credentials.
- Hidden API-key fields now use a visual password transformation, preserving the
  real editable value. The old replacement-with-bullets implementation could corrupt
  an existing key when editing it. SSH passwords are masked as well.
- Onboarding has five steps: brain, speech output, speech recognition, images,
  permissions/connections. Image setup is optional and shares the Settings form.

## Provider setup

All existing preferences are preserved on upgrade. Until a new provider is explicitly
saved, the existing local/server routing remains in effect.

**AI Horde:** choose AI Horde, review the anonymous dataset-sharing disclosure, tick
the consent box, and tap Use this option. No account, payment card or personal key is
needed. A personal Horde key is optional. Anonymous requests have low queue priority.
Prompts are sent to community workers; users must not submit private information.
The anonymous-sharing warning follows Horde's published API contract, even if an
individual response reports `shared: false`.

**Cloudflare:** create/sign in to a Cloudflare account, copy its 32-character account
ID, create an API token with Workers AI write/edit access for that account, enter both,
and tap Use this option. Uses FLUX.1 Schnell, four inference steps. Free usage has a
daily allowance; paid accounts may incur usage charges. No billing plan is changed
by Meow. Tokens are stored using the existing Keystore-backed encrypted preferences.

**Check connection** does not generate an image. Horde checks service reachability
(not a personal key or worker capacity). Cloudflare checks account/model-list access
(not generation write permission or available image quota). Status copy states these
limits instead of claiming full generation verification.

**On this phone / Your own server:** retained alongside the two new integrations.
A specific selection is exclusive: there is no silent fallback to a different service.
The provider, credentials and fallback list are snapshotted once per image request;
changing Settings while a request runs cannot redirect that prompt to a new cloud.

## Runtime safeguards

- Horde submission → inexpensive queue polling → final status/image; progress is
  shown in the companion caption. Nine-minute queue ceiling, best-effort remote
  cancellation on failure, timeout or user cancellation. Tool ceiling is ten minutes.
- Requests use a safe-for-work filter and trusted/validated workers. Horde is asked
  for inline base64 WebP, not a worker URL. Meow never follows an image-result URL.
- Both integrations use fixed HTTPS hosts, disable redirects, bound streamed response
  bodies to 16 MiB and validate decoded dimensions before bitmap allocation.
- Safe, authored errors describe authentication, quota, capacity and image failures.
  Raw upstream bodies and credentials are not displayed or logged.
- Successful results use the existing workspace/photos PNG and MEDIA_FILE rendering
  path. Errors never substitute placeholder artwork. Cancellation never starts a fallback.

## Verification

- Live anonymous Horde smoke request returned HTTP 202, completed and supplied a
  43,204-byte WebP (`stable_diffusion`, not censored). SHA-256:
  `99f94d2e79cda59ac089f8736b5b812b2382df8eeba276472febccc96bf8ef80`.
  Only a generic, nonprivate cat-illustration prompt was used; no user files/keys sent.
- Cloudflare REST request/response, account check, quota/auth errors, redirect refusal
  and image bounds are covered with injected HTTP responses. Live Cloudflare generation
  still requires the user's account/token and has not been verified.
- UI rendering and tests cover category navigation, dark/light appearance, consent,
  explicit saving, masked-key editing and optional first-run setup.
- This change does not install an app, clear data or submit to Google Play. Installation
  and device acceptance remain a separate approval gate. Before a public release,
  review store privacy/Data safety disclosures for the newly optional image services.

## Primary API references

- https://aihorde.net/api/swagger.json
- https://github.com/Haidra-Org/AI-Horde/blob/main/README_integration.md
- https://developers.cloudflare.com/workers-ai/models/flux-1-schnell/
- https://developers.cloudflare.com/workers-ai/get-started/rest-api/
