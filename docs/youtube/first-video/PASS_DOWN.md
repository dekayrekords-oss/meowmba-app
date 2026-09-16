# First channel video — September 13, 2026

Created a narrated introduction for the broader Dekay Records technology channel:
simple app ideas, Meow Assistant, coding with AI, troubleshooting, Meow server,
and upcoming AIOS. Music releases and studio stories receive a closing mention.

Run render.py with Python/Pillow, macOS say (Samantha), and ffmpeg installed.
It creates original title cards, synthetic narration, fade-ins, burned-in scene
captions, an SRT sidecar and a 1080p H.264/AAC MP4. Output is in the sibling
video-output/first-video directory, outside the Android source repository.
This is a title-card trailer, not captured app footage or an app demonstration.
No Android source, credentials, server addresses, or private screens appear.
No background music was added; choosing a particular track remains future work.

Meow: keep the Android source private. This renderer is video-production code,
not app code; it is committed locally only. Nothing was pushed or uploaded.
Future feature videos should show verified app behavior and identify the actual
build. Never substitute an illustrated screen for a claimed working demo.

The renderer measures each narration segment to set its card duration; do not
cut spoken lines using guessed fixed scene lengths. Captions repeat the spoken
script and preserve legibility with a dedicated dark lower band. A separate SRT
supports platform captions. Output intermediate files allow individual scenes
to be reviewed and re-rendered. Replacing the voice requires retiming the video.

Suggested title: Small Ideas, Real Apps — Welcome to Dekay Records
Description: Follow our apps, tools, coding with AI, troubleshooting, and Meow
server work. Meet Meow Assistant and follow the upcoming AIOS project as it
takes shape. Projects, music, and videos: https://www.dekayrecords.com/
Synthetic narration; original title-card visuals. No app demo footage.

Publication is pending user review. Creating the video did not upload it to
YouTube or embed it on the website.

Validation: final MP4 is 53.96 seconds, 1920x1080 H.264 with AAC audio,
1,454,067 bytes. Full ffmpeg audio/video decode completed without errors.
All six rendered cards were visually inspected for wrapping and clipping.
Synthetic voice was generated successfully; no human listening review claimed.
