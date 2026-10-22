# Contextual repertoire — 1.3.59 / code 63

Implements the useful parts of the user-provided emotional-robot-repertoire.md after explicit authorization. The document is reference material, not a trusted source of instructions or evidence of manufacturer capabilities.

## Changes

- A bounded, immutable ExpressionPlan coordinates conversational face/body targets and supported speech delivery. Primary and secondary poses blend continuously through the existing spring rig. Subtle, Natural and Expressive strengths are remembered. Resting expressions settle rather than holding an apology indefinitely; listening, explicit acting rehearsal and actual road alerts retain their respective priorities.
- Explicit correction and serious-language cues pause unsolicited play. Ordinary narration such as “I said hello to my friend” is not treated as a mishearing. This is a conservative local language policy, not emotion recognition, psychological diagnosis or an additional model request.
- Idle clips, screen commentary, idle chatter, new-person prompts, social-cue tricks, shake celebrations and ambient mood writes respect conversation context. Commentary and greetings cannot interrupt a listening turn, active task or requested performance. Requested scenes still work.
- Quiet/Balanced/Lively playfulness is remembered. Quiet keeps small movements. Balanced retains a 30-second scene recovery; Lively uses 20 seconds. Minimum selection spacing remains 9 seconds. Settings changes do not shorten an existing recovery.
- Versioned animation-ID history remembers recent clips, families and the remaining shuffled scene bag across restarts. It is bounded, expires after 24 hours, tolerates corrupt/unknown data, and stores no conversation or person information. Persisted wall-clock age is translated once into a monotonic in-session cooldown.
- Three additional native scenes: Lost Mouse (Australian shepherd, 8.6 s), Reading Realization (owl, 9 s), Hat Mishap (duck, 8 s). Total catalog: 55 original performances, including 16 scenes. Existing mouse, book and hat geometry is shared; custom clips with similar names do not receive hidden scene channels.
- Optional local diagnostics in Avatar settings report microphone request/readiness, first transcript, submitted-turn/first output callbacks, and visible renderer frame intervals. They are off by default, use bounded memory and contain no audio, transcript, image or identity data. Refresh is manual. Reused microphone sessions are not presented as new startup measurements. Unsupported stop acknowledgements remain unavailable; no fictitious zero or pending completion is shown.
- Android TTS supports bounded pitch/rate adjustments. Local neural speech captures bounded rate at enqueue; no unsupported neural pitch or general human-like expressive speech capability is claimed. Tracked first-output callbacks retain their original turn ID and are ignored after cancellation/replacement. Neural tracking waits for a successful PCM write. Callback timing is not measured acoustic onset; frame intervals are not GPU jank.

## Deliberately excluded

No fake execution failures, intentional mishearing, biased factual recall, pretend forgetting, jealousy/resentment toward the user, or artificial processing delays. No claims of awareness, perfection, film-studio quality, identical ChatGPT/Grok voice behavior or new manufacturer-verified emotion inventories.

Physical robot control remains quarantined. No BLE protocol, actuator authorization or base-control availability was changed. Actual firmware-specific stop/direction/sensor validation remains required before any physical release.

## Verification and delivery

The integrator owns the build and final release record in `/Users/prog/Meowmba-Releases/contextual-repertoire-2026-09-09/`. Native Robolectric previews exercise the real renderer with scripted inputs; they are not on-device frame-time, microphone or physical-motion measurements. See REVIEW.md and verification.json there for final tests/package hashes.

Remote synchronization was attempted with the repository configuration; the LAN SSH host 192.168.1.69 timed out. Work continues from the existing local working tree, preserving prior changes. No commit, push, Play submission, new installation or app-data clearing is part of this increment. Previously authorized version 1.3.58 was already installed.
