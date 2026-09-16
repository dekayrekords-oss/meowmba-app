# Music / DAW screen — layout note

No emulator/device display was available for a screenshot on host MEOW.
This note documents the Compose layout for review.

## How to open
1. Tap the companion avatar background zone to open the ecosystem tile grid (`BackgroundTiles`).
2. Tap the **Music** tile (mint/teal, music-note icon).
3. Alias: tile name `"DAW"` is also accepted by the handler.

Back closes the screen (wired in `MainActivity` `BackHandler`).

## Chrome (top → bottom)
1. **Title bar** (`DawRail`): MusicNote icon + "Music / DAW" + music server URL subtitle (or warning if Settings URL empty) + Close.
2. **Transport** (`DawPanel`): Play · Stop · status readout · **Generate** (mint accent).
3. **Prompt row**: prompt `OutlinedTextField` + duration (1–30s).
4. **Arrange**: left track headers (Audio 1–3) + horizontal timeline with ruler, grid, and clip regions.
5. **Clip list** (when non-empty): compact list under the arrange area.

## Colors
Near-black studio: `#0B0D10` bg, `#15181E` / `#1C2129` panels, `#00E5A8` accent, clip greens/blues/purples.

## Generation
Uses `RemoteMusicGenerator(musicServerUrl)` the same way Settings/agent tools do.
WAV bytes land in `cacheDir/music_daw/` and appear as timeline clips; tap clip or Play for `MediaPlayer` preview.
