# Meowmba User Manual

The Meow assistant on Android — setup, settings and API keys.

<!-- BUILD_STAMP_START -->
**Preview build vc{{VERSION_CODE}} ({{GIT_SHA}}) · package com.example.meowmba · versionName {{VERSION_NAME}} · stamped {{STAMP_DATE}}**
<!-- BUILD_STAMP_END -->

> Maintain this file (`docs/USER_MANUAL.md`). Every Preview ship must run
> `scripts/update-user-manual.sh` so the stamped PDF in
> `app/src/main/assets/docs/meowmba-user-manual.pdf` matches the build.

What Meowmba is. An AI companion that lives on your phone. It shows up as an animated avatar
  (the default is a cat called Lumen) that you talk to, pet and feed. Behind the avatar is an agent that
  can run tools, read your screen, control other apps, remember things, generate images, and — if you
  install Termux — run a small Linux system on the device itself.
  What this manual covers. Everything you can reach from the app: the setup wizard, the companion
  screen, the tile menu, all ten settings categories, and a step-by-step walkthrough of every API key
  the app can use and where to get it.

  This documents a preview build. Two things are knowingly incomplete in vc{{VERSION_CODE}} and are
  called out where they appear: the Meta Ray-Ban glasses integration shows UPDATE
  REQUIRED, and the Sherpa voice-recognition native library is not bundled in the preview APK.
  Everything else described here was exercised on a running device.



## CONTENTS

 1. What you need                                     6. Settings overview
 2. Installing the app                                7. API keys — the complete guide
 3. First run: the setup wizard                       8. Settings reference, category by category
 4. The companion screen                              9. Models and downloads
 5. Getting around: the tile menu                    10. Troubleshooting
                                                     11. Where your keys are stored


## What you need

Requirement       Detail
Android version   Android 12 (API 31) or newer. The app targets API 36.
CPU               arm64 only. The APK ships a single ABI (arm64-v8a) with native inference code. It will not
architecture      install on 32-bit or x86 devices.
RAM               4 GB minimum. 8 GB or more recommended if you want on-device models — the local image
                  model alone asks for 6 GB+.
Storage           The preview APK is ~412 MB. On-device models add several GB each.
A model or a      The app installs and runs without either, but chat needs something to think with. See API
key               keys.
Termux            Optional. Only needed for the on-phone Linux/Computer features.

 No account is required to install. Meowmba never asks you to sign in to a Meowmba service.
 Every credential it uses is one you obtain yourself, from a provider you choose, and store on
 your own device.



## Installing the app

Meowmba is distributed as an APK rather than through the Play Store, so installation means sideloading.
 1 Get the APK onto the phone
   Copy it across by USB, or download it directly on the device. The preview builds follow the naming
   pattern meowmba-preview-vc<code>-<commit>.apk — for this manual, meowmba-preview-
   vc{{VERSION_CODE}}-{{GIT_SHA}}.apk .


 2 Allow installation from this source
   Android will block the first attempt. Open Settings → Apps → Special app access → Install
   unknown apps and allow your file manager or browser to install apps.
 3 Install and open
   Tap the APK, confirm, and wait. It is a large file, so give it a minute. The launcher entry is called
   Meow Assistant Preview.

  If you install over an older build and Android refuses, it is almost always a signing-key
  mismatch. Uninstall the old copy first — you will lose its local data, including saved API keys.



## First run: the setup wizard

The first launch runs a twelve-step wizard. You can leave at any point — Skip is in the top-right corner of
every step, and every decision can be changed later in Settings. You can also re-run the whole thing from
Settings → Finish setup → Start setup.

### STEP 1 — WELCOME





 The opening screen. Nothing is configured yet.
A single screen that sets expectations: the helper lives on this phone, not in the cloud. Let's go starts the
wizard.



### STEP 2 — YOUR COMPUTER IS THIS PHONE





 The Termux step. Every button here is an alternative
 route to the same goal.
Meowmba's Computer features — running commands, editing files, hosting a small Linux environment —
are built on Termux, a terminal emulator for Android. The app deliberately tells you to get it from GitHub
rather than the Play Store, because the Play Store build is deprecated.


Button            What it does
Get Termux        Opens the recommended Termux download.
GitHub            Opens the Termux releases page.
download
NetBird           Opens the NetBird VPN download — only needed if you want to reach this phone from
                  another network.
Open Termux Launches Termux if it is already installed.
I installed it    Confirms installation and moves on.
Show my apps Lists what is installed so you can check.
I already have it Skips straight ahead.



### STEP 3 — OPEN TERMUX AND PASTE THIS





 The setup commands, with a Copy button.
Two commands set up a minimal Linux environment inside Termux. Copy puts them on the clipboard;
paste them into Termux and let them finish.
  pkg update && pkg install proot proot-distro openssh tmux
  proot-distro install debian


What each part is for: proot and proot-distro let you run a Linux distribution without root;
 openssh provides SSH so the app (or your desktop) can connect in; tmux keeps sessions alive when
you switch away; and debian is the distribution being installed.



### STEP 4 — ALMOST DONE





 The confirmation step after the commands have run.
Return here and tap I did that. The app checks whether Termux is actually reachable.



### STEP 5 — IF TERMUX IS NOT READY





 The graceful failure path — you can keep going and
 add a key anyway.
If Termux is not installed or not responding, the wizard says so plainly and offers Continue. This is
intentional: the AI features do not depend on Termux, so you are never stuck at this step. Computer
features simply stay unavailable until you finish it.



### STEP 6 — MICROPHONE AND CAMERA





 Permission request, phrased in terms of what each one
 is for.
The wizard asks for both permissions together and explains the purpose: the microphone is for talking to
the companion, the camera is for face-following. Not now is a real option — both can be granted later,
and both have on-screen toggles on the companion screen.



### STEP 7 — THE AI KEY





 The step most people care about. This is where you
 paste your key.
This is the important one. Meowmba has no brain of its own — it needs a model. The wizard's default
recommendation is a free OpenCode Zen key, which is the shortest path to a working chat.
   Get a free key opens opencode.ai/zen in your browser.
   Paste your key is the field for the key you bring back.
   Start using Meow finishes here.
   Keep going continues into the optional steps below (GitHub, face, background).
  The full key walkthrough — including every other provider the app supports — is in API keys.



### STEP 8 — GITHUB OPTIONAL





 Optional GitHub linking for project work.
Connects a GitHub account so the agent can work with repositories. When you create the token, you
must tick both the repo and private scopes — the screen says so explicitly. You can also name a
new private repo to create, or link an existing one. Skip for now moves on.



### STEP 9 — PICK A FACE





 Avatar selection. Lumen is pre-selected.


Avatar          Notes
Lumen           Default. The cat whose eyes you saw on the earlier screens.
Fly companion   An insect-style companion.
Lumen Cat       A full-body cat variant.
Pearl           Abstract orb.
Jarvis Orb      Ring/orb style.
Godot           A distinctly stylised face.
Classic         The original design.



### STEP 10 — PICK A BACKGROUND





 Background selection, Aurora by default.
Options are None, Aurora (default), Starfield, Nebula, Firefly woodland, Moonlit lagoon and Clear sky.
This is purely cosmetic and is one of the most frequently changed settings, so it lives under Appearance
too.



### STEP 11 — DO YOU HAVE A COMPUTER ON THE INTERNET?





 The optional remote-computer step.
Meowmba can offload work to a machine you own — a laptop or server running the hermes-agent. The
screen is explicit that you do not need one: "This phone is enough." Choose No, I'm done for a fully local
setup.



### STEP 12 — YOU'RE READY





 The end of the wizard.
Chat drops you on the companion screen. Terminal opens the Termux-backed terminal instead.



## The companion screen





 The main screen. The avatar is the interface — you
 interact with it directly.
There is almost no chrome here by design. The avatar fills the screen and the controls are either attached
to it or tucked into the conversation panel at the bottom.
Avatar zones
You interact with the companion by tapping parts of it. These are the hit zones:


 Tap             Effect
 Left or right   Toggle the microphone on and off. Turning it off is a hard privacy cut — listening, wake-word
 ear             detection and any speech in progress all stop.
 Left or right   Toggle the camera (face-following).
 eye
 Mouth           Mute or unmute the companion's speech.
 Forehead        Open Settings.
 Background      Open the tile menu.
There are also two gestures, shown in the Care panel: hold to pet and double-tap to play.



## The Care panel





 The Care panel, opened from the button in the top-left.
The Care button opens a panel covering the avatar's needs and moods. It shows a status line (Together
— Gentle needs · never dies), a Care mode toggle, three Tricks (Peekaboo, Tickle, Dance) and four Care
actions (Feed, Rest, Clean, Talk).
With Care mode on, the companion is described as self-sufficient: its needs stay full without intervention,
so you can play with it without upkeep. Turning it off means the needs decay and feeding matters.
The conversation panel
At the bottom of the screen:
    Chat / Terminal — two tabs. Terminal is the Termux-backed shell.
    Message Meow — the text field. The placeholder uses whatever name you gave the companion.


    Speak (microphone icon) — start voice input.
    Send — submit.
In the panel header there are three icons: Show full conversation, Start voice call, and Collapse chat.
Collapsing the panel is how you get the avatar back to full screen.



## Getting around: the tile menu

The tile menu is the main navigation hub. Open it by swiping down on the companion screen, or by
tapping an empty part of the avatar background.


The tile grid, top.   Scrolled — middle rows.


 Scrolled to the bottom, where Settings
 lives.

The grid holds sixteen tiles. Picking one closes the grid and opens that screen; the Avatar button in the
header returns you to the companion.


Tile         Opens
Memory       What the companion remembers about you.
Files        File browser.
Vending      Vending / shop screen.
LLM          The model manager — providers, models, downloads, system resources.
Brain        Brain activity view.
Robot        Robot body control (LOOI base).
Projects     Project workspaces.
Tools        Agent tools and capabilities.
Agents       Business/agent workflows.
Privacy      Privacy dashboard — microphone, camera and data controls.
Notes        The vault — notes and stored items.
NAV          Navigation and nearby places.
Weather      Weather scene and forecast.
Background   Background picker.
Avatar       Return to the companion (header button).
Settings     Everything documented below.



## Settings overview





 The Settings hub. Ten categories, each with a one-line
 summary.
Settings is organised as a hub rather than one long scroll. The subtitle says it plainly: "Everything in its
place. Tap a category to open its options."


Category                  What it covers
Finish setup              Re-runs the first-run wizard. Sits at the top with a Start setup button.
Voice & behavior          Wake phrase, conversation and listening
AI & models               LLM settings, agent routing and downloads
Images                    AI Horde, Cloudflare and on-phone images
Music & video             Media-generation services
Phone & device            App control, access and device behavior
Appearance                Dark/light mode, themes and avatars
Neural / Lumen            Lumen reflex, Computer power, nervous-system toggles
Connections & memory      Desktop bridge, servers and shared memory
Driving                   Drive mode and emergency contact

 Settings saves as you navigate. Leaving a category commits what you typed — there is no
 separate Save button on the hub. Individual fields may still have their own confirm action.



## API keys — the complete guide

Meowmba has no built-in model and no Meowmba account. To make it think, you give it a key from a
provider you choose. This section covers every key the app understands, what each one is for, and
exactly where to get it.
The keys at a glance
 Key             What it enables             Where to get it                             Cost
 OpenCode        The default chat brain      opencode.ai/zen                             Free tier to
 Zen             ("BigPickle"). Also                                                     start
                 selectable as a full
                 provider card.
 OpenRouter      Cloud models from many      openrouter.ai/settings/keys                 Free models
                 vendors through one key,                                                available
                 including free models.
 OpenAI          GPT models as the cloud     platform.openai.com/api-keys                Paid
                 brain.
 Claude /        Claude models as the        platform.claude.com/settings/keys           Paid
 Anthropic       cloud brain.
 Custom API      Any OpenAI-compatible       Your provider                               Varies
                 endpoint you point it at.
 Hugging         Downloading gated models    huggingface.co/settings/tokens              Free
 Face            (e.g. Gemma).
 Geoapify        Nearby-places search on     myprojects.geoapify.com                     Free tier
                 the NAV screen.
 Cloudflare      Cloud image generation      Cloudflare dashboard → Workers AI token     Free daily
                 (FLUX.1 Schnell).                                                       allowance
 AI Horde        Community image             aihorde.net                                 No account
                 generation.                                                             needed
 GitHub          Repository access for       GitHub → Settings → Developer settings →    Free
                 project work.               Tokens

  Start with OpenCode Zen. It is what the setup wizard recommends, it has a free tier, and it is
  the only key the wizard will set up for you end-to-end. Add the others only if you want specific
  models.



## OpenCode Zen — the recommended path

OpenCode Zen is a curated model service from the OpenCode project. Meowmba uses it as its default
fallback brain, under the name BigPickle, with the model id big-pickle .
 1 OPEN THE KEY PAGE FROM INSIDE THE APP
   Two routes reach the same page, and both were verified:
       During setup: Step 7 → Get a free key
       Any time: Settings → AI & models → Get a BigPickle / OpenCode Zen key
   Either one opens your browser at opencode.ai/zen .


2 LAND ON THE ZEN PAGE




      opencode.ai/zen — the page both in-app links
      open.
     The page describes Zen as "a curated set of AI models that OpenCode has tested and
     benchmarked specifically for coding agents." The primary action is Get started with Zen. Below it
     the page advertises a $20 pay-as-you-go balance option with a card-processing fee.
       The in-app label says "free", and the wizard describes Zen as "a free fallback brain… no
       payment needed to start." The public page leans on the paid balance. Treat "free" as
       "free to start" and expect to check the current terms on the site before you rely on it.

3 SIGN IN AND CREATE THE KEY
  Sign in to OpenCode, create an API key, and copy the full secret. The key is shown once — if you
  lose it, issue another.


4 COME BACK AND PASTE IT




    The in-app OpenCode Zen settings panel, with the key
    field and format selector.
   Back in Settings → AI & models, the OpenCode Zen card expands to show:
      OpenCode Zen API key — the paste field, with a Show key toggle so you can verify what you
      pasted.
      API format — choose OpenAI compatible or Claude Messages. Leave it on OpenAI
      compatible unless you have a specific reason.
      OpenCode Zen chat model — defaults to big-pickle .
      OpenCode Zen base URL — optional. Leave blank for https://opencode.ai/zen/v1 .
   The card also offers Copy BigPickle key, which copies the saved key so you can paste it into
   another tool.


      The helper text is worth reading: "Sign in, create an API key, copy the full secret, then return and
      paste it below. API billing is separate from chat subscriptions." A Zen subscription and Zen API
      credit are billed separately.
 5 CONFIRM IT TOOK
   The card status changes from NOT CONFIGURED to configured, and the message "Add a key to
   load models" is replaced by the model list. Leave the category and the key is committed.
The other providers
All cloud providers live on the same page: Settings → AI & models → Open LLM settings. The page has a
Choose your AI section with one card per provider.


Local and OpenRouter cards.   OpenAI and Claude / Anthropic.


 Custom API and the start of OpenCode    OpenCode Zen and the Vision section.
 Zen.

LOCAL · ON THIS PHONE
Runs a model entirely on the device through MediaPipe LLM Inference. No key, no network. The card
shows the model name, the runtime, the expected path ( /data/local/tmp/llm/gemma.bin ) and
whether the file is actually present. There is a Check local file action to verify and a Test cloud action for
cloud providers. Local is the only option that works offline.
OPENROUTER · CLOUD
One key, many vendors' models. The default model is a free one: nvidia/nemotron-3-super-120b-
a12b:free . Endpoint: https://openrouter.ai/api .

 1. Open openrouter.ai/settings/keys — the app's own button is labelled Get an OpenRouter API
    key.
 2. Create a key and copy it.
 3. Paste it into the OpenRouter card's API key field.
 4. Optionally change the model to any OpenRouter model id — free ones end in :free .


OPENAI · CLOUD
Default model gpt-4o-mini , endpoint https://api.openai.com . Get a key at
 platform.openai.com/api-keys . This is a paid provider — set a spending limit in your OpenAI
account before you paste the key in.
CLAUDE / ANTHROPIC · CLOUD
Endpoint https://api.anthropic.com . Get a key at platform.claude.com/settings/keys .
Paid.
CUSTOM API · CLOUD
For anything OpenAI-compatible that is not covered above — a self-hosted llama.cpp server, LM Studio,
vLLM, a company gateway. You supply both the base URL and the key. Endpoint field reads "Set your
provider URL".
HUGGING FACE — FOR DOWNLOADING MODELS
Not a chat brain. This token exists so the app can download gated models — models whose authors
require you to accept a licence first, such as Gemma. Get it at huggingface.co/settings/tokens .
Without it, gated downloads fail with a permission error even though the model exists.
GEOAPIFY — FOR NAV
Powers nearby-place search on the NAV screen. This key lives on the NAV screen rather than in Settings:
open the NAV tile and tap Get a free API key →. Signup is at myprojects.geoapify.com . Without it,
place search is unavailable; the rest of the app is unaffected.
CLOUDFLARE — FOR CLOUD IMAGE GENERATION
See the Images section below. Requires a Cloudflare account and an API token, with a free daily
allowance.
AI HORDE — NO KEY AT ALL
Community-powered image generation. "No account or card needed." The trade-off is queue time, which
can be slow. There is an optional registration link on the setup screen at aihorde.net if you want
priority.
Choosing a default brain
At the bottom of the AI & models page there is a line reading Default brain: followed by the current
choice. Out of the box it is Auto (Omniroute), which picks the best available provider automatically. The
available values include auto, local, phone, openai, opencode, openrouter and bigpickle.
Setting this explicitly is worth doing once you have more than one provider configured. Auto is convenient
but it means the model answering you can change without you choosing it.


Optional: the Hermes agent endpoint
If you run a desktop or server agent, put its URL in Hermes agent endpoint on the AI & models page —
for example http://192.168.1.10:8642 . The field is described as an OpenAI-compatible agent
(hermes-agent v0.19+) and is explicitly optional: leave it blank for on-device routing only. When set, it
takes over agent routing and the phone stops doing that work itself.


Settings reference, category by category
Voice & behavior




 Top: the voice-model button and the   Middle: the listening and awareness
 companion name.                       toggles.


 Bottom: conversation, chatter and barge-
 in.

Companion Voice is the first section. The first field is the one that matters most: Companion name (wake
phrase: "Hey <name>"). The default is Meow, so the wake phrase out of the box is "Hey Meow".
Changing the name changes the phrase.
A line underneath is worth knowing about: "Also always answers to 'Hey Memba' and 'Hey Meow'." Both
the Meowmba and Memba names are recognised regardless of what you set.
Above the name field is a Voice recognition & voice models button, which opens the speech-model
manager (Whisper by default).
Then eleven toggles. These are the descriptions exactly as the app gives them:


Toggle             What the app says it does
Wake phrase        Always-on listening for the wake phrase while the app is open. Runs on-device.
Always-on mic in   Keeps the mic live while the companion face is up: just talk, no wake phrase needed.
face mode          When Meow sees you, the mic stays on either way and listens a bit longer so mid-
                   sentence pauses are not cut off.
Background wake Keeps listening with the screen off via a persistent notification (foreground service).
phrase             Uses more battery. Requires the wake phrase to be enabled.
Keep screen on in Prevents the display from sleeping while the face is up. Uses a lot of battery — leave off
face mode          unless this is a plugged-in desk pet.
Watch mode (screen Meow glances at your screen every few seconds so it can react and comment on what
awareness)         you're doing.
Commentary mode While watching, Meow may speak a short helpful comment now and then (max once
                   every 5 minutes).
Road alerts        Official AZ511 road work in Arizona, community reports elsewhere, and OpenStreetMap
                   cameras. Coverage is incomplete; mobile police reports are not guaranteed. Requires
                   location.
Random idle scenes Mix chases, reading and occasional hats with quiet gestures while the avatar is resting.
Conversation mode Stay for follow-up questions after responding. Say "stop" to end.
Idle chatter       Occasional unprompted one-liners while idle — at most once per hour.
Barge-in           Keep listening for 8 seconds after every reply so you can cut in without saying the wake
                   word again.

 The rate limits are stated in the app itself — commentary at most once every five minutes, idle
 chatter at most once an hour, barge-in for eight seconds after a reply. If the companion feels
 too talkative or too quiet, those are the numbers to reason about.


AI & models




 The AI & models page — where keys, the agent brain
 and routing live.
Three groups:
 1. Agent brain — the routing layer. The description reads: "Understands natural tool commands ('make a
    file called todo.txt') using the active model. Select a local model on the LLM page for fully offline use
    — a local model file must be present." Below it is the optional Hermes agent endpoint.
 2. BigPickle (OpenCode Zen) — the default fallback brain, with the key field, the base-URL override
    and the copy action described earlier.
 3. Default brain — the active selection, plus buttons through to the full LLM page.


Images




 The Images category. Pick where images are made.
The top section is Choose where images are made, with five options:
 Option                   Description as shown in the app
 Keep current setup       Preserve your existing local/server routing.
 On this phone            Offline and private. Requires a downloaded image model and enough memory.
 AI Horde                 No account or card needed. Community-powered; queues can be slow.
 Cloudflare               FLUX.1 Schnell. Requires a Cloudflare account and API token; free daily allowance.
 Your own server          Connect an image-generation server you manage.


Nothing changes until you press Use this option — the screen says so directly. The note underneath
explains that choosing a specific option disables automatic fallback, which is the behaviour you want if
you are worried about prompts leaving the device.
Further down:




 Model download and the self-hosted server settings.
   Image generation — the on-device model. The listed option is SDXL-Lightning (4-step, Q8_0):
   ByteDance's distilled SDXL, GGUF-quantized for stable-diffusion.cpp, producing 1024px images in
   about four steps. It is ~4.1 GB and wants 6 GB+ RAM, and generation can take several minutes.
   Image server URL (optional) — your own server, e.g. http://192.168.1.10:9100 . Leave blank to
   always generate on-device.
   Prefer server for images — try the server first, fall back to on-device if it is unreachable.


  A downloaded model is required for local images. If generation fails the app reports the error
  rather than substituting a placeholder drawing — so a failure is a real failure, not a silent
  fallback.
Music & video




 Music & video — two server URLs, nothing else.
The shortest category. Two optional server URLs, no bundled on-device models.


 Field                  What the app says
 Music server URL       Your own music-generation server, e.g. http://192.168.1.10:9200 . A working
 (optional)             server is required; leaving this blank disables music generation.
 Video server URL       Your own video-generation server, e.g. http://192.168.1.10:9300 . A working
 (optional)             server is required. Generation can take several minutes per clip.

  Unlike the Images category, there is no on-device fallback and no community service for
  either. Both features are simply off until you point them at a server you run.
Phone & device




 Cover-screen avatar, UI automation and   Home screen and Routines.
 workflows.

Four groups.
COVER-SCREEN AVATAR
For folding phones. The app is careful about what it promises: "Use your selected avatar in a compact
layout when Meow is opened on a supported foldable cover display. Requires your phone's cover-app


setup; does not replace its lock screen or launch automatically when folded." There is a Set up cover-
screen avatar button.
UI AUTOMATION
The accessibility service that lets the agent drive other apps. "Meow can find, tap, type and scroll on-
screen when you ask. It uses the system accessibility service and stays OFF until you enable it. Phone
tasks require approval; consequential actions keep their separate confirmation or manual handoff."
This is the single most important toggle in the app for capability. Android requires you to enable it in
system settings, so there is an Open accessibility settings button. Until you do, the agent cannot interact
with anything on screen.
Beneath it is Learn search workflows: the app learns from verified searches — the example given is "use
my phone to search for birds in YouTube" — and after three matching verified runs it can replay the steps
with a new query. Search terms are not saved in recipes, and app changes or failed checks stop replay. A
counter shows how many are learning versus ready, with Refresh workflows and Forget workflows
actions.
HOME SCREEN
Makes the companion your launcher. When set, the app states "MEOWMBA is your default home screen."
This is reversible from Android's own settings if you change your mind.
ROUTINES
Scheduled moments when the companion speaks up unprompted. Two are present by default: Morning
brief at 08:00 and Memory follow-ups at 20:00.
  Robot base controls are not visible in this build. The source contains a Robot base section —
  enable the body, connect over Bluetooth, paired device MAC, base host and port — but it only
  renders when a physical base is actually attached. On a phone with no LOOI robot paired, the
  section is absent entirely. Do not assume it is missing because it is broken.


Appearance




 Color mode, app theme and the avatar   Scrolled to the end of the avatar list.
 list.

Three settings.
Color mode — System, Light or Dark. System follows your phone.
App theme — Companion or Professional. This is a styling choice, not a colour choice; pair it with Color
mode.
Avatar — the same seven faces as the wizard, and this is where the app actually explains what each one
is:


Avatar          Description as shown in the app
Lumen           One expressive companion with animal forms, playful scenes and a changing wardrobe.
Fly companion   A whole animated fruit fly: explore, play, read and care for it optionally.
Lumen Cat       Living glass eyes, velvet ears and floating whiskers. The same attentive motion, in a cat.
Pearl           Sculpted floating features, expressive eyes and a soft glow. Inspired by the concept.
Jarvis Orb      The Meow OS avatar: every dot a model parameter, thinking ejects them.
Godot           Cat Assistant look — floating features, green almond eyes, Godot motion.
Classic         The original look: soft glowing capsules with rounded pupils.

 Two of these are not purely cosmetic. Fly companion is the avatar that the Neural / Lumen fly-
 brain networks are built around, and Jarvis Orb visualises the model itself. If you plan to
 experiment with the neural settings, pick one of those first.


Neural / Lumen




 Who controls the avatar, and which   The network list, including the server
 network runs.                        brain.


 Sensory reactions and reflex sensitivity.

The most technical category in the app, and the most surprising. Meowmba ships an actual neural
simulation subsystem that can drive the avatar's behaviour.
It opens with Open Brain activity — a live view where you can watch the avatar and send Pet or Feed
inputs. Note the caveat: "The computer brain pauses while settings cover the avatar." Do not judge
responsiveness while this screen is open.
Neural setup then asks two questions.
1 · WHO CONTROLS THE AVATAR?
 Option               What it means
 Classic              Uses the authored behavior system. The neural network does not run; this saves its extra
                      processing.
 Shadow ·             Runs both systems, but Classic still controls the avatar. Useful for observing the network
 compare              before giving it control.
 Neural ·             Lets the network influence attention, drives and allowed actions. Authored animation and
 control              safety rules still apply, with Classic as a fallback.


The default is Shadow — the network runs alongside the authored behaviour without steering it. Shadow
is the right setting if you want to see what the network would do before letting it do it.
2 · CHOOSE A NETWORK
Four options, from cheapest to most exotic. The descriptions are the app's own:
 Network          Description
 Recurrent · rate Uses continuous activity that carries information between steps. The default and faster
                  option in our phone comparison.
 Spiking ·        Uses simulated firing pulses and membrane state. Useful for comparison; it took more
 experimental     processing in our phone test and is not proven more capable.
 BANC fly circuit Routes touch through named leg/thorax nociceptor groups and DNg27 to wing-power
 · experimental neurons from a 76-neuron BANC extract. Connection signs, timing and flight/muscle
                  behavior are bounded authored assumptions; this is not FlyWire or a complete biological fly
                  brain.
 MaleCNS ·        Runs 176,422 fly neurons and 25.86 million connection pairs (125 million anatomical
 server brain     synapse counts) on your chosen server. Keep its MaleCNS helper online. Food, touch and
                  looming cues stimulate named circuits; measured motor activity drives reactions, wings,
                  turning, stopping, landing and grooming in the virtual fly. Requires a connection; Classic
                  takes over when replies are unavailable.

  Read the disclaimers in this section. The app is unusually careful to say what these are not:
  the BANC circuit is "not FlyWire or a complete biological fly brain", and MaleCNS neural
  dynamics and avatar movements are "modeled, not a validated biological fly." These are
  simulations with bounded authored assumptions. They are for behaviour and experimentation,
  not biology.
Switching networks resets that network's transient state and turns off trace recording.
SENSORY REACTIONS
Lumen reflex adds quick visual reactions to available touch, motion, speech and vision signals. Enabling
it does not turn on the camera or microphone — those keep their own controls, which is worth knowing if
you are being careful about privacy. A Sensitivity slider runs from 25% (subtle) to 100% (default) to
150% (stronger reactions), and the app notes this changes response strength, not learning speed.
Below that are the pairing controls for a remote nervous-system server, including test and pairing actions
— the same MaleCNS server referenced above.


Connections & memory




Semantic memory and the Desktop   Pairing, server brains and saved SSH
Bridge.                           hosts.


 The SSH connection form.

Summarised in the app as "Bridge, servers, shared memory and hosted avatar brains." Four parts.
SEMANTIC MEMORY
An optional meow-mem0 service URL. "Leave blank to keep memory on-device only." When set, new
conversation facts sync so a server-side brain can recall them. The placeholder example is
 http://192.168.1.69:11438 .

DESKTOP BRIDGE
Pairs the phone with a desktop helper. "Pair once with the desktop helper. Network first; USB tunnel
when the LAN cannot connect."
To pair, run this on a computer on the same network:
  meowmba-bridge.py --pair


Then press Find desktops, select the machine, and enter the code it shows. There is also USB bridge
discovery (over USB tethering, still with one-time code pairing) and a Pairing payload field for pasting a
payload manually, with a Pair with payload action.


The app describes what the bridge is for: "Bridge 0.9 supports Logic workflows through its menus, track
and region controls, plugin Controls view and export dialogs. Each edit has an approval and audit
receipt." Audio checks include loudness and true peak when the host has libebur128 . Logic must be
enabled in the desktop helper and granted Accessibility. The app is explicit that custom controls and
finished mixes still need human review.
SERVER BRAINS
Hosted neural brains, available once a compatible server is paired. Currently this is MaleCNS · server
brain, the 176,422-neuron fly graph described under Neural / Lumen. It shows as "Unavailable until a
MaleCNS server is paired." Pair it under Neural / Lumen, then select it here.
SERVERS (SSH)
Saved SSH targets. "Previously connected hosts stay saved. Tap one to edit, or add a new server.
Passwords stay encrypted on this phone." Each entry takes a server name, host, port, username and
password, with Save server and Clear form actions. Saved connections are listed at the top with a
Remove action each.
  In the emulator used for this manual, a saved connection named meow-nb ( claude@meow-
  nb:22 ) was already present on a fresh install — a development leftover baked into the
  preview build. If you see it on your own device, remove it; it is not yours.


Driving




 Drive mode and the emergency contact.
Car assistant, with a single Drive mode toggle. The app describes it as sensing when you are in a car —
via GPS speed, your car's Bluetooth, or the spoken phrase "drive mode on" — then showing a speed/trip
card and answering questions like "how fast" or "are we there yet", and finding fuel and food along the
route. It uses GPS and the accelerometer only while enabled.
Below it, Emergency contact phone (optional), explained as: "Used by the crash alert — say 'text mom'
after a detected impact and Meow will open a text with your location ready to send." Note the wording: it
opens a text with your location, it does not send it for you.
When drive mode is active, a collision alert takes the top slot on screen ahead of the weather and drive
cards, with actions to text your emergency contact or dismiss it.



## Models and downloads

Three kinds of model, from three different places:
 Kind            Where it lives                                                             Size
 Chat brain      Settings → AI & models → Open LLM settings. Default path                   1–3 GB
 (local)          /data/local/tmp/llm/gemma.bin , runtime MediaPipe LLM Inference.          typical
 Speech          Voice & behavior → Voice recognition & voice models. Default is Whisper,   Varies
 recognition     running on-device.
 Image           Settings → Images. SDXL-Lightning (4-step, Q8_0).                          ~4.1 GB
 generation
On the LLM page, the top section reports System Resources — RAM, CPU cores, Android API level,
architecture and NPU availability. Check this before downloading anything: the RAM figure is the hard
constraint, and the image model alone asks for 6 GB+.
Downloads are resumable and show a Retry action on failure. Models that are already present show an
Installed badge with a Delete action. Gated models additionally need the Hugging Face token described
above.



## Troubleshooting

THE COMPANION NEVER ANSWERS
Check in this order: (1) is any provider configured? The LLM page shows NOT CONFIGURED on a card
that has no key. (2) Is the key valid — use Test cloud on the LLM page. (3) Is Default brain set to
something you actually have? Auto can silently route to a provider with no key.
CHAT WORKS BUT THE AGENT WILL NOT TAP ANYTHING
The accessibility service is off. Go to Settings → Phone & device → UI automation → Open accessibility
settings and enable it. This is an Android requirement, not an app bug.
A GATED MODEL DOWNLOAD FAILS IMMEDIATELY
You are missing the Hugging Face token. Add it in the LLM area and retry. Also confirm you have
accepted the model's licence on the Hugging Face website — the token alone is not enough if the licence
has not been accepted.
VOICE INPUT DOES NOT RESPOND
In this preview build, the Sherpa voice-recognition native library is not bundled in the APK, so that engine
fails to load and logs library "libsherpa-onnx-jni.so" not found repeatedly. Speech
recognition falls back to Whisper, which is the default anyway. If voice is dead entirely, check that the
microphone is not muted — tap the companion's ear, or look for MIC ON / CAM OFF indicators on screen.
META RAY-BAN GLASSES SHOW UPDATE REQUIRED
Expected in vc{{VERSION_CODE}}. The app states it plainly: "Glasses camera support is not included in this installation. It
requires an app update, not an API key in Settings." You can still pick the paired Bluetooth microphone on
the Voice Recognition page, but whether it works depends on your phone and connection.
THE APK WILL NOT INSTALL
Most likely a signing mismatch from a previous install — uninstall first. If Android says the app is
incompatible, you are on a non-arm64 device; the APK ships arm64-v8a only.
IMAGES FAIL TO GENERATE LOCALLY
Three usual causes: no model downloaded (~4.1 GB), insufficient free RAM (6 GB+ wanted), or the
generation timed out — several minutes is normal. The app reports the actual error rather than quietly
drawing something instead.



## Where your keys are stored

Every API key, token and SSH password is written to encrypted preferences on the device itself, under a
per-provider name of the form api_key_<provider> . The provider names in use are openai ,
 opencode , openrouter , huggingface and geoapify .

Practical consequences:
   Keys never sync anywhere. Reinstalling the app loses them — you will paste them again.
   An uninstall or a "clear app data" wipes them.
   The Show key toggle reveals a saved key on screen, so treat an unlocked phone as a phone with your
   keys on it.
   Cloud providers receive your prompts when they are the active brain. If that matters, use a local
   model or AI Horde for images and set Default brain to local.




## About this manual

Every screen shown here was captured from a running instance of preview build vc{{VERSION_CODE}} ({{GIT_SHA}}) on an
Android 17 / API 37 arm64 emulator with a Pixel 6 device profile. All ten settings categories were opened
and walked on the device — none are described from source alone — and the option lists and toggle
descriptions were cross-checked against the application source at the same commit, so they are
complete rather than only what happened to fit on screen.
Wizard steps and settings categories are the ones present in this build. Because this is a preview, later
versions may add, rename or remove items — particularly around the glasses integration and the voice-
recognition engines.