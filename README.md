# Meow Assistant

## Give your ideas a home.

A living AI workspace for Android. Meet your companion, keep useful ideas, and bring conversations, projects, goals, and files into a space you make your own.

**[Get Meow on Google Play](https://play.google.com/store/apps/details?id=com.meow.meowmba)** · **[Explore Meow](https://www.dekayrecords.com/meow-assistant.html)** · **[Report an issue](https://github.com/dekayrekords-oss/meowmba-app/issues)**

**Current Play release: 2.0.14 (229)** · Published October 14, 2026 · Android 12 or later

<a href="https://www.dekayrecords.com/meow-assistant.html"><img src="https://www.dekayrecords.com/files/theme/meow-project-desk.png" alt="Meow Desk interface preview with a companion and project workspace" width="220"></a>

*Desk interface preview. Explore the app's companion, rooms, and workspace.*

## Start with an idea

- **Meet your companion.** Choose avatars, rooms, and backgrounds, and adjust your companion's appearance.
- **Start a conversation.** Included Basic chat gives short offline replies without a Meow account, API key, or paid subscription.
- **Keep useful work together.** Organize conversations, notes, memories, goals, projects, files, and saved artifacts.
- **Choose your next step.** Flow brings North Stars and next steps into your workspace.
- **Return to your files.** Import photos, videos, and documents with Android's pickers. Imports become local copies.

| Flow | Files |
| --- | --- |
| ![Meow Flow interface preview](https://www.dekayrecords.com/files/theme/meow-project-flow.png) | ![Meow Files interface preview](https://www.dekayrecords.com/files/theme/meow-project-files.png) |
| Goals and next steps | Your local imports and saved work |

*Interface previews. Layout and available features depend on your device and setup.*

## New in 2.0.14

- Animated welcome tour and guided setup for the home screen, AI choices, avatars, rooms, and Flow.
- Bundled **Whistle** speech recognition for offline use. **Whisper** remains an optional download.
- Separate commands for opening apps, websites, settings, and media controls. For example, `open YouTube` opens the installed app.
- Improvements to chat reliability, memory handling, and encrypted backup export.

Basic chat produces short text replies. Supported commands use a separate action path; Basic does not perform live lookups or automatically tap through other apps.

## Choose how you use AI

**Start offline.** Basic chat is included. Advanced local chat, speech, and image models need compatible hardware, enough memory and storage, and explicit downloads. Voice requires microphone permission and a supported engine.

**Connect optional cloud chat.** Opt in and configure your own provider account, key, and model. Supported choices include OpenRouter, OpenAI, Claude, and a compatible HTTPS API. Provider charges and data policies apply. Cloud chat is optional and requires a network connection.

**Choose your permissions.** Camera OCR and labels run on device with camera permission. Optional notification and activity context support Flow. Public web, weather, place, and GitHub lookups and model downloads may use the network. See the [Play listing and Data safety information](https://play.google.com/store/apps/details?id=com.meow.meowmba) before enabling optional features.

## Optional ring controls

A compatible R08/QRing can provide user-operated scrolling and selection after separate consent, Android Accessibility setup, and physical gesture verification. Choose allowed apps and unpause only after setup. You can pause or disable the controls at any time. Availability depends on the ring and firmware.

## Get started

1. Install [Meow Assistant from Google Play](https://play.google.com/store/apps/details?id=com.meow.meowmba).
2. Follow the welcome tour and choose your companion and workspace.
3. Try a short typed conversation with Basic.
4. Explore Flow and local files. Add optional voice, models, cloud chat, or ring controls when you want them.

No Meow account is required for core local chat. Model downloads and optional features have their own requirements.

## Watch and explore

[Visit the website's video section](https://www.dekayrecords.com/meow-assistant.html#mx-films) for interface walkthroughs and concept films. Concept films are labeled and include ideas beyond the current Play edition.

## For developers

This repository contains an earlier Android implementation. Its source and historical build notes are **not a reproducible source package for Play release 2.0.14**. Use Google Play for the current public app.

The [historical developer notes](docs/HISTORICAL_DEVELOPER_NOTES.md) preserve the previous README, including experimental server, desktop, and integration work. Those notes are not a feature list for the Play edition.

The public release does not enable desktop/server automation, external MCP integrations, autonomous Accessibility phone control, or physical robot actuation. Optional cloud **chat** is separate from those integrations.

For bug reports, include your app version, Android version, device model, steps, and expected versus actual behavior. Remove private conversations and credentials from screenshots or logs.

