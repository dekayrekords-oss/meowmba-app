# Feature video backlog

All rows are **candidates awaiting device verification**. This is a source-based inventory, not a claim that every feature is released. One row represents one video. The tool appendix extends coverage to individual commands. Merge exact duplicates into the matching episode; split different user outcomes.

| ID | Playlist | Video title | Recording proof / prerequisite | Source |
|---|---|---|---|---|
| F01 | Start Here | Set Up Meow Assistant for Your First Conversation | Choose a provider, finish setup, and ask one question. | `app/src/main/java/com/example/meowmba/ui/onboarding/SetupWizardRoute.kt` |
| F02 | Start Here | Talk to Meow with the Microphone Button | Tap the mic, speak, and show the real response. | `app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt` |
| F03 | Start Here | Wake Meow with Your Voice | Enable the wake phrase and demonstrate recognition. | `app/src/main/java/com/example/meowmba/core/audio/WakePhraseMatcher.kt` |
| F04 | Start Here | Keep a Conversation Going with Meow | Ask a follow-up and demonstrate stopping or interrupting speech. | `README.md` |
| F05 | Start Here | Chat with Meow by Typing | Send a message in the chat workspace. | `app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt` |
| F06 | Start Here | Control Meow’s Microphone, Camera, and Voice | Show current explicit controls and demonstrate stopping each input/output. | `app/src/main/java/com/example/meowmba/ui/companion/PrivacyDashboard.kt` |
| F07 | Start Here | Use the Background Wake Phrase | Show opt-in, persistent notification, screen-off behavior, and Stop. | `app/src/main/java/com/example/meowmba/core/audio/WakeWordService.kt` |
| F08 | Companion | Change Meow’s Appearance | Open Look and choose an available avatar. | `app/src/main/java/com/example/meowmba/ui/companion/AvatarScreen.kt` |
| F09 | Companion | Change Meow’s Size and Placement | Open Space and adjust visible controls. | `app/src/main/java/com/example/meowmba/ui/companion/AvatarSetupDesign.kt` |
| F10 | Companion | Adjust Meow’s Personality and Playfulness | Open Behavior and compare two settings. | `app/src/main/java/com/example/meowmba/ui/companion/AvatarSetupDesign.kt` |
| F11 | Companion | Ask Meow to Perform an Animation | Play a built-in animation and show the actual performance. | `app/src/main/java/com/example/meowmba/core/agent/tools/AvatarAnimateTool.kt` |
| F12 | Companion | Save and Replay a Custom Animation | Create, save, and replay an animation in the library. | `app/src/main/java/com/example/meowmba/ui/companion/CustomAnimation.kt` |
| F13 | Companion | Give Meow a New Background | Choose a background and verify the applied scene. | `app/src/main/java/com/example/meowmba/ui/companion/BackgroundScreen.kt` |
| F14 | Companion | Explore Meow’s Habitat | Demonstrate one enabled habitat interaction; verify current wiring. | `app/src/main/java/com/example/meowmba/ui/companion/PetHabitatWorld.kt` |
| F15 | Companion | Feed, Play, and Rest with Meow | Verify current care integration before recording controls. | `app/src/main/java/com/example/meowmba/ui/companion/PetCarePanel.kt` |
| F16 | Productivity | Teach Meow Something to Remember | Save a harmless fact and recall it in a later interaction. | `app/src/main/java/com/example/meowmba/ui/companion/MemoryScreen.kt` |
| F17 | Productivity | Review and Manage Meow’s Memories | Show the memory list and available management controls. | `app/src/main/java/com/example/meowmba/ui/companion/MemoryScreen.kt` |
| F18 | Productivity | Take Voice Notes with Meow | Start notes, dictate test content, stop, and reopen the note. | `app/src/main/java/com/example/meowmba/ui/companion/VaultScreen.kt` |
| F19 | Productivity | Organize Work in Projects | Create a project and reopen a saved document. | `app/src/main/java/com/example/meowmba/ui/companion/ProjectsScreen.kt` |
| F20 | Productivity | Find Your Files in Meow’s Library | Locate a test document and open it. | `app/src/main/java/com/example/meowmba/ui/companion/AssetBrowserScreen.kt` |
| F21 | Productivity | See What Meow’s Agents Are Doing | Run one bounded task and show its real status. | `app/src/main/java/com/example/meowmba/ui/companion/BusinessAgentsScreen.kt` |
| F22 | Productivity | Check the Weather with Meow | Ask for a forecast and show its returned location and time. | `app/src/main/java/com/example/meowmba/ui/companion/WeatherScreen.kt` |
| F23 | Productivity | Get Directions with Meow | Show a route lookup while stationary. | `app/src/main/java/com/example/meowmba/ui/companion/NavScreen.kt` |
| F24 | Create | Save a Recurring Character Design | Save and retrieve one character sheet. | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` |
| F25 | Create | Make a Comic with Meow | Create a comic and open the rendered artifact. | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` |
| F26 | Create | Write a Video Script with Meow | Create a script and reopen it from Studio. | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` |
| F27 | Create | Draft an Advertisement with Meow | Generate and inspect an ad draft without publishing it. | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` |
| F28 | Create | Draft a Social Post with Meow | Generate and inspect a post draft. | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` |
| F29 | Create | Generate an Image with Meow | Show installed backend and distinguish AI output from procedural fallback. | `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt` |
| F30 | Create | Generate a Video with Meow | Verify configured backend, play the actual output, and identify fallback if used. | `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt` |
| F31 | Create | Generate Audio with Meow | Verify configured music server and play the resulting file. | `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt` |
| F32 | Connect | Choose a Local, Server, or Cloud Brain | Select and test one provider; describe what leaves the phone. | `app/src/main/java/com/example/meowmba/ui/companion/ModelManagerScreen.kt` |
| F33 | Connect | Download and Select a Local Model | Show real model installation, selection, and successful response. | `app/src/main/java/com/example/meowmba/ui/companion/ModelManagerScreen.kt` |
| F34 | Connect | Choose Meow’s Speech Models | Show the available speech options and test the selected one. | `app/src/main/java/com/example/meowmba/ui/companion/VoiceModelScreen.kt` |
| F35 | Connect | Connect a Plugin or MCP Server | Connect one working endpoint and show its live result. | `app/src/main/java/com/example/meowmba/ui/companion/PluginsConnectorsScreen.kt` |
| F36 | Connect | Connect Meow to Home Assistant | Use a test device and show a verified service action. | `app/src/main/java/com/example/meowmba/core/plugins/ServiceIntegrations.kt` |
| F37 | Connect | Connect Meow to Spotify | Verify account connection and one supported action. | `app/src/main/java/com/example/meowmba/core/plugins/ServiceIntegrations.kt` |
| F38 | Connect | Connect Meow to GitHub | Demonstrate a read-only repository action. | `app/src/main/java/com/example/meowmba/core/plugins/ServiceIntegrations.kt` |
| F39 | Connect | Pair Meow with Your Computer | Pair and verify one allowed bridge operation. | `bridge/README.md` |
| F40 | Connect | Use Meow with Companion Apps | Verify installed companion and working capture or memory transfer. | `app/src/main/java/com/example/meowmba/core/memory/IpcMetaMemoryConnector.kt` |
| F41 | Lab | Explore the 3D Fruit Fly Companion | Show real rendering; label performance and thermal limitations. | `app/src/main/java/com/example/meowmba/ui/companion/Fly3DRenderer.kt` |
| F42 | Lab | Understand Classic, Shadow, and Neural Modes | Explain authority and compare visible controls; synthetic controller, not a biological brain. | `app/src/main/java/com/example/meowmba/ui/companion/NeuralSetupControls.kt` |
| F43 | Lab | Robot Control: What Still Needs Testing | Development update only: no physical robot verification claimed. | `app/src/main/java/com/example/meowmba/ui/companion/RobotScreen.kt` |

## Individual command videos

Every literal tool ID declared under `core/agent/tools` is listed below. Some tools may be internal, unregistered, unavailable, or require external hardware/services. Check registration and UI reachability before filming. Source-derived titles are working titles; replace them with a natural user outcome after verifying the command. Dynamic tool families require separate inspection.

| Tool | Working video title | Source | Status |
|---|---|---|---|
| `avatar.animate` | Meow Assistant: avatar — animate | `app/src/main/java/com/example/meowmba/core/agent/tools/AvatarAnimateTool.kt` | Verify registration, prerequisites, and real output |
| `avatar.invent` | Meow Assistant: avatar — invent | `app/src/main/java/com/example/meowmba/core/agent/tools/AvatarAnimationInventor.kt` | Verify registration, prerequisites, and real output |
| `avatar.form` | Meow Assistant: avatar — form | `app/src/main/java/com/example/meowmba/core/agent/tools/AvatarFormTool.kt` | Verify registration, prerequisites, and real output |
| `phone.bluetooth_setup` | Meow Assistant: phone — bluetooth setup | `app/src/main/java/com/example/meowmba/core/agent/tools/BluetoothTools.kt` | Verify registration, prerequisites, and real output |
| `memory.save` | Meow Assistant: memory — save | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `memory.search` | Meow Assistant: memory — search | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `file.read` | Meow Assistant: file — read | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `file.write` | Meow Assistant: file — write | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `file.delete` | Meow Assistant: file — delete | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `file.list` | Meow Assistant: file — list | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `project.create` | Meow Assistant: project — create | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `task.list` | Meow Assistant: task — list | `app/src/main/java/com/example/meowmba/core/agent/tools/BuiltinTools.kt` | Verify registration, prerequisites, and real output |
| `calendar.list_events` | Meow Assistant: calendar — list events | `app/src/main/java/com/example/meowmba/core/agent/tools/CalendarTools.kt` | Verify registration, prerequisites, and real output |
| `calendar.create_event` | Meow Assistant: calendar — create event | `app/src/main/java/com/example/meowmba/core/agent/tools/CalendarTools.kt` | Verify registration, prerequisites, and real output |
| `car.status` | Meow Assistant: car — status | `app/src/main/java/com/example/meowmba/core/agent/tools/CarTools.kt` | Verify registration, prerequisites, and real output |
| `car.drive_mode` | Meow Assistant: car — drive mode | `app/src/main/java/com/example/meowmba/core/agent/tools/CarTools.kt` | Verify registration, prerequisites, and real output |
| `car.trip` | Meow Assistant: car — trip | `app/src/main/java/com/example/meowmba/core/agent/tools/CarTools.kt` | Verify registration, prerequisites, and real output |
| `car.eta` | Meow Assistant: car — eta | `app/src/main/java/com/example/meowmba/core/agent/tools/CarTools.kt` | Verify registration, prerequisites, and real output |
| `car.help` | Meow Assistant: car — help | `app/src/main/java/com/example/meowmba/core/agent/tools/CarTools.kt` | Verify registration, prerequisites, and real output |
| `phone.cleanup` | Meow Assistant: phone — cleanup | `app/src/main/java/com/example/meowmba/core/agent/tools/CleanupTool.kt` | Verify registration, prerequisites, and real output |
| `avatar.describe_animation` | Meow Assistant: avatar — describe animation | `app/src/main/java/com/example/meowmba/core/agent/tools/DescribeAnimationTool.kt` | Verify registration, prerequisites, and real output |
| `desktop.control` | Meow Assistant: desktop — control | `app/src/main/java/com/example/meowmba/core/agent/tools/DesktopControlTool.kt` | Verify registration, prerequisites, and real output |
| `desktop.run_goal` | Meow Assistant: desktop — run goal | `app/src/main/java/com/example/meowmba/core/agent/tools/DesktopNavigationTool.kt` | Verify registration, prerequisites, and real output |
| `desktop.blender_scene` | Meow Assistant: desktop — blender scene | `app/src/main/java/com/example/meowmba/core/agent/tools/DesktopNavigationTool.kt` | Verify registration, prerequisites, and real output |
| `desktop.run_build` | Meow Assistant: desktop — run build | `app/src/main/java/com/example/meowmba/core/agent/tools/DesktopRunBuildTool.kt` | Verify registration, prerequisites, and real output |
| `desktop.run_script` | Meow Assistant: desktop — run script | `app/src/main/java/com/example/meowmba/core/agent/tools/DesktopRunScriptTool.kt` | Verify registration, prerequisites, and real output |
| `desktop.write_file` | Meow Assistant: desktop — write file | `app/src/main/java/com/example/meowmba/core/agent/tools/DesktopWriteFileTool.kt` | Verify registration, prerequisites, and real output |
| `email.send` | Meow Assistant: email — send | `app/src/main/java/com/example/meowmba/core/agent/tools/EmailTools.kt` | Verify registration, prerequisites, and real output |
| `email.draft` | Meow Assistant: email — draft | `app/src/main/java/com/example/meowmba/core/agent/tools/EmailTools.kt` | Verify registration, prerequisites, and real output |
| `glasses.take_photo` | Meow Assistant: glasses — take photo | `app/src/main/java/com/example/meowmba/core/agent/tools/GlassesTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.search` | Meow Assistant: inventory — search | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.get` | Meow Assistant: inventory — get | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.add` | Meow Assistant: inventory — add | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.update` | Meow Assistant: inventory — update | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.list_for_sale` | Meow Assistant: inventory — list for sale | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.mark_sold` | Meow Assistant: inventory — mark sold | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.unlist` | Meow Assistant: inventory — unlist | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.delete` | Meow Assistant: inventory — delete | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.stats` | Meow Assistant: inventory — stats | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `inventory.open` | Meow Assistant: inventory — open | `app/src/main/java/com/example/meowmba/core/agent/tools/InventoryTools.kt` | Verify registration, prerequisites, and real output |
| `logic.run_goal` | Meow Assistant: logic — run goal | `app/src/main/java/com/example/meowmba/core/agent/tools/LogicSessionTool.kt` | Verify registration, prerequisites, and real output |
| `media.generate_image` | Meow Assistant: media — generate image | `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt` | Verify registration, prerequisites, and real output |
| `media.generate_video` | Meow Assistant: media — generate video | `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt` | Verify registration, prerequisites, and real output |
| `media.generate_audio` | Meow Assistant: media — generate audio | `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt` | Verify registration, prerequisites, and real output |
| `memory.recall` | Meow Assistant: memory — recall | `app/src/main/java/com/example/meowmba/core/agent/tools/MemoryRecallTool.kt` | Verify registration, prerequisites, and real output |
| `brain.select_model` | Meow Assistant: brain — select model | `app/src/main/java/com/example/meowmba/core/agent/tools/ModelControlTools.kt` | Verify registration, prerequisites, and real output |
| `nav.find_places` | Meow Assistant: nav — find places | `app/src/main/java/com/example/meowmba/core/agent/tools/NavTools.kt` | Verify registration, prerequisites, and real output |
| `nav.navigate` | Meow Assistant: nav — navigate | `app/src/main/java/com/example/meowmba/core/agent/tools/NavTools.kt` | Verify registration, prerequisites, and real output |
| `nav.save_place` | Meow Assistant: nav — save place | `app/src/main/java/com/example/meowmba/core/agent/tools/NavTools.kt` | Verify registration, prerequisites, and real output |
| `nav.list_saved_places` | Meow Assistant: nav — list saved places | `app/src/main/java/com/example/meowmba/core/agent/tools/NavTools.kt` | Verify registration, prerequisites, and real output |
| `phone.run_goal` | Meow Assistant: phone — run goal | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneGoalTool.kt` | Verify registration, prerequisites, and real output |
| `phone.launch_app` | Meow Assistant: phone — launch app | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.play_music` | Meow Assistant: phone — play music | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.list_apps` | Meow Assistant: phone — list apps | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.open_url` | Meow Assistant: phone — open url | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.open_settings` | Meow Assistant: phone — open settings | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.set_alarm` | Meow Assistant: phone — set alarm | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.set_timer` | Meow Assistant: phone — set timer | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.flashlight` | Meow Assistant: phone — flashlight | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.dnd` | Meow Assistant: phone — dnd | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.battery` | Meow Assistant: phone — battery | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.set_volume` | Meow Assistant: phone — set volume | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.take_photo` | Meow Assistant: phone — take photo | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.compose_sms` | Meow Assistant: phone — compose sms | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.dial_call` | Meow Assistant: phone — dial call | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.media_control` | Meow Assistant: phone — media control | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `phone.set_brightness` | Meow Assistant: phone — set brightness | `app/src/main/java/com/example/meowmba/core/agent/tools/PhoneTools.kt` | Verify registration, prerequisites, and real output |
| `prime.refine` | Meow Assistant: prime — refine | `app/src/main/java/com/example/meowmba/core/agent/tools/PrimeHarnessTools.kt` | Verify registration, prerequisites, and real output |
| `prime.goal` | Meow Assistant: prime — goal | `app/src/main/java/com/example/meowmba/core/agent/tools/PrimeHarnessTools.kt` | Verify registration, prerequisites, and real output |
| `prime.state` | Meow Assistant: prime — state | `app/src/main/java/com/example/meowmba/core/agent/tools/PrimeHarnessTools.kt` | Verify registration, prerequisites, and real output |
| `robot.move` | Meow Assistant: robot — move | `app/src/main/java/com/example/meowmba/core/agent/tools/RobotMoveTool.kt` | Verify registration, prerequisites, and real output |
| `ssh.add_host` | Meow Assistant: ssh — add host | `app/src/main/java/com/example/meowmba/core/agent/tools/SshAddHostTool.kt` | Verify registration, prerequisites, and real output |
| `ssh.run` | Meow Assistant: ssh — run | `app/src/main/java/com/example/meowmba/core/agent/tools/SshTool.kt` | Verify registration, prerequisites, and real output |
| `studio.save_character` | Meow Assistant: studio — save character | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` | Verify registration, prerequisites, and real output |
| `studio.get_character` | Meow Assistant: studio — get character | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` | Verify registration, prerequisites, and real output |
| `studio.create_comic` | Meow Assistant: studio — create comic | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` | Verify registration, prerequisites, and real output |
| `studio.write_script` | Meow Assistant: studio — write script | `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` | Verify registration, prerequisites, and real output |
| `ui.automate` | Meow Assistant: ui — automate | `app/src/main/java/com/example/meowmba/core/agent/tools/UiAutomationTool.kt` | Verify registration, prerequisites, and real output |
| `vault.note.list` | Meow Assistant: vault — note — list | `app/src/main/java/com/example/meowmba/core/agent/tools/VaultTools.kt` | Verify registration, prerequisites, and real output |
| `vault.note.read` | Meow Assistant: vault — note — read | `app/src/main/java/com/example/meowmba/core/agent/tools/VaultTools.kt` | Verify registration, prerequisites, and real output |
| `vault.note.write` | Meow Assistant: vault — note — write | `app/src/main/java/com/example/meowmba/core/agent/tools/VaultTools.kt` | Verify registration, prerequisites, and real output |
| `vault.note.delete` | Meow Assistant: vault — note — delete | `app/src/main/java/com/example/meowmba/core/agent/tools/VaultTools.kt` | Verify registration, prerequisites, and real output |
| `vault.note.search` | Meow Assistant: vault — note — search | `app/src/main/java/com/example/meowmba/core/agent/tools/VaultTools.kt` | Verify registration, prerequisites, and real output |
| `weather.get` | Meow Assistant: weather — get | `app/src/main/java/com/example/meowmba/core/agent/tools/WeatherTool.kt` | Verify registration, prerequisites, and real output |
| `web.browser` | Meow Assistant: web — browser | `app/src/main/java/com/example/meowmba/core/agent/tools/WebBrowserTool.kt` | Verify registration, prerequisites, and real output |
| `web.fetch` | Meow Assistant: web — fetch | `app/src/main/java/com/example/meowmba/core/agent/tools/WebFetchTool.kt` | Verify registration, prerequisites, and real output |
| `web.search` | Meow Assistant: web — search | `app/src/main/java/com/example/meowmba/core/agent/tools/WebSearchTool.kt` | Verify registration, prerequisites, and real output |

## Coverage maintenance

Before each release, compare new screens and tool registrations with this backlog. Inspect dynamic families such as `AssetTools` and service/plugin catalogs separately. A catalog card is not a live integration. Each newly verified user-facing action receives its own episode or an explicit mapping to an existing one. Never equate this static inventory with an exhaustive runtime capability audit.

## Known recording caveats

- Build 109 handoff supersedes old README gesture instructions: the video button owns the camera; avatar gestures interact. Film current controls.
- Synthetic neural control and biological/connectome research must remain distinct. Robot transfer is deferred without hardware.
- Cloud speech, AI, connected services, and bridges have different data flows. Do not reuse the historical “everything stays on device” store copy.
- Content Studio must open the real artifact. A chat claim without the saved result is insufficient.
- Media fallbacks must be named; a procedural preview is not proof of AI video/image/music generation.
- Production status fields (version, recording path, URL) remain unset until actual evidence exists.
