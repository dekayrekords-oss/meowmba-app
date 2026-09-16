# Meowmba privacy policy

**Last updated:** 2026-09-06

Meowmba is a voice companion that runs on your phone. This is the policy you can
host and paste into Play Console.

## What the app uses

- **Microphone** — talk to Meow, wake phrase, optional background listen,
  and live notes. Dictation may use the phone’s speech service (often Google)
  or an on-device recognizer.
- **Camera** — face / hand tracking and photos you ask it to take.
- **Location** — only for navigation / nearby places when you ask.
- **Bluetooth** — optional connected robot base or supported wearable accessory.
- **Calendar** — only when you ask to read or add events.
- **Photos / media** — only if you ask to tidy the phone. Deletes use the
  system confirm sheet.
- **Usage access** — only if you ask which unused apps are taking space.
- **Notification access** — optional road alerts (Waze / Maps). Off until
  you enable it in system Settings.
- **Accessibility** — optional, off until you enable it. Used to tap/type on
  screen when you ask; risky actions still need your OK.
- **Notifications** — wake / download / reply alerts.

## What stays on the phone

- Chat history, memories, live notes, voice prints, and settings.
  Voice prints never leave the phone. They are only used to recognize a
  name you enrolled.
- API keys (encrypted with Android EncryptedSharedPreferences).
- On-device models you download.

Cloud backup of that data is **off**.

## What can leave the phone

Only if **you** turn it on in Settings:

- A cloud brain (OpenRouter, OpenAI, BigPickle) — the text you send that
  provider, under *their* policy.
- Optional image generation — selecting AI Horde sends your image prompt to
  its community workers. Anonymous use requires accepting the dataset-sharing
  disclosure; do not submit private information. With a personal key, Meow requests
  no dataset sharing, but workers still receive the prompt. Selecting Cloudflare
  sends the prompt to Workers AI under your account. Cloudflare account IDs and API
  tokens are used to authenticate requests. Generated images are saved on the phone.
- A Hugging Face token — for model downloads you start.
- A LAN server you typed (Ollama, Hermes, image/music/video). HTTP is allowed
  **only on your local network**. Public sites must be HTTPS.
- Maps / search when you ask for directions or a web lookup.

We do not sell data. We do not run ads. We do not have our own analytics
backend.

Deleting a local image or clearing app data does not delete information already
sent to an optional third-party service. Those services govern retention and deletion
under their own policies. Switching image providers affects new requests only.

## Children

Meowmba is not directed at children under 13.

## Contact

Use the Play listing contact email, or the address you add here when you host
this page.
