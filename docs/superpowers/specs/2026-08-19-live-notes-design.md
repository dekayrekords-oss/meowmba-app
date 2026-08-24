# Live notes (transcribe to vault)

Approved 2026-08-19. No WAV capture (music-only, later).

## Behavior

- Start: “start noting / transcribing / taking notes”, “jot this down”, “take note(s)”.
- Continue: “continue notes on [topic]”.
- Stop: chip X, or “stop notes / stop transcribing / that’s all for notes”.
- “remember this” stays one-shot memory, not a session.

While noting:

- Chip **Taking notes** + **X**.
- Mouth auto-mutes. STT stays on (mute ≠ mic off).
- Each usable utterance appends to `vault/Notes/<topic>.md`.
- Unmute mouth → still append, and Meow may answer.

Bare start uses today’s date as the topic (`Notes/2026-08-19`).
