package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.audio.WakePhraseMatcher

/**
 * Routes user text to an agent intent.
 *
 * Phase 5 uses an honest keyword router: what the companion can actually do
 * is a small, explicit set — anything else falls through to normal
 * conversation. When the model provider drives tool-calling later, this
 * router is replaced by structured tool calls; the orchestrator behind it
 * does not change.
 *
 * Pure Kotlin, fully unit-testable.
 */
object AgentRouter {

    data class AgentIntent(
        val agentId: String,
        val toolId: String,
        val args: Map<String, String>,
        val title: String
    )

    /** Anything that reads as an address is treated as the recipient. */
    private val EMAIL_ADDRESS = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /** Words that make an address a request to write, rather than a mention of one. */
    private val COMPOSE_CUES = listOf(
        "email", "e-mail", "message", "write", "send", "compose", "tell", "reply",
    )

    /** Where the message text starts, longest first so the specific forms win. */
    private val BODY_MARKERS = listOf(
        "that says ", "which says ", "that reads ", "with the message ",
        "saying that ", "saying ", "says ", "tell them ", "telling them ",
    ).sortedByDescending { it.length }

    /**
     * Where the subject starts. Searched anywhere in the sentence, not just at
     * the start — "email X with the subject Y" names it mid-sentence. ("With the
     * subject " and "subject " end at the same place, so the short form suffices.)
     */
    private const val SUBJECT_MARKER = "subject "

    /**
     * Where an extracted value ends and the rest of the sentence begins.
     *
     * " to " is here because a message request usually ends by naming the recipient.
     */
    private val BOUNDARIES = listOf(
        " and ", " then ", " so that ", " so i ", ", ", " please", " for me",
        " after that", " as well", " to ",
    )

    /** Longer than this is a sentence, not the name of an app. */
    private const val MAX_APP_NAME_WORDS = 3

    fun route(text: String): AgentIntent? {
        val raw = text.trim()
        val normalized = WakePhraseMatcher.normalize(raw)

        // Exact-phrase commands (normalized for tolerance)
        if (normalized in setOf("list files", "list the files", "show files", "show me the files",
                "what files do i have", "show workspace", "list workspace")) {
            return AgentIntent("file", "file.list", emptyMap(), "list workspace files")
        }
        if (normalized in setOf("list tasks", "show tasks", "what tasks are running",
                "what are you working on", "show agent tasks")) {
            return AgentIntent("main", "task.list", emptyMap(), "list agent tasks")
        }

        // Prefix commands: match case-insensitively, but extract arguments
        // from the RAW text so filenames keep their punctuation ("notes.txt")
        // and projects keep their capitalization ("Website").
        extractAfter(raw, listOf("create a new file called ", "create a new file named ",
            "create new file called ", "create a file called ", "create a file named ",
            "create file called ", "create file named ", "create file "))?.let { name ->
            return AgentIntent("file", "file.write", mapOf("path" to name, "content" to ""), "create file $name")
        }

        extractAfter(raw, listOf("delete the file ", "delete file "))?.let { name ->
            return AgentIntent("file", "file.delete", mapOf("path" to name), "delete file $name")
        }

        extractAfter(raw, listOf("read the file ", "read file ", "open the file ", "open file "))?.let { name ->
            return AgentIntent("file", "file.read", mapOf("path" to name), "read file $name")
        }

        extractAfter(raw, listOf("create a new project called ", "create a new project named ",
            "create new project called ", "create a project called ", "create a project named ",
            "create project called ", "create project named ", "create a new project ",
            "create new project ", "create a project ", "create project "))?.let { name ->
            return AgentIntent("project", "project.create", mapOf("name" to name), "create project $name")
        }

        // Project session: start a project to save all sessions and files under
        extractAfter(raw, listOf("start a project called ", "start a new project called ",
            "start project ", "begin a project called ", "new project "))?.let { name ->
            return AgentIntent("project", "memory.save", mapOf("content" to "Project started: $name", "tags" to "project"),
                "start project: $name")
        }

        extractAfter(raw, listOf("search my memory for ", "search memory for ",
            "search memories for "))?.let { query ->
            return AgentIntent("memory", "memory.search", mapOf("query" to query), "search memory: $query")
        }

        // Deep-link search: "search X in/on <app>" jumps straight into the app's
        // search screen instead of the browser. Checked before the web-search
        // block so "search cats on youtube" doesn't become a web query.
        routeDeepLink(normalized)?.let { dl ->
            return AgentIntent("phone", "phone.open_url", mapOf("url" to dl.url),
                "search ${dl.query} in ${dl.app}")
        }

        // Research / web search
        extractAfter(raw, listOf("search the web for ", "search web for ", "search online for ",
            "look up ", "search for "))?.let { query ->
            return AgentIntent("research", "web.search", mapOf("query" to query), "web search: $query")
        }

        // Email. Checked BEFORE the phone section, and by content rather than by
        // sentence-opening prefix: "open gmail and write a message that says hi to
        // someone@example.com" starts with "open", so a prefix-only rule handed the whole
        // sentence to the app launcher and it reported no app by that name.
        composeEmail(raw)?.let { return it }
        extractAfter(raw, listOf("send an email to ", "send email to ", "email "))?.let { target ->
            val to = boundTarget(target) ?: return@let
            return AgentIntent("email", "email.send", mapOf("to" to to, "subject" to "", "body" to ""),
                "send email to $to")
        }
        extractAfter(raw, listOf("draft an email to ", "draft email to "))?.let { target ->
            // Same trailing-clause rule as app names: "draft email to bob and then
            // relax" is addressed to bob, not to "bob and then relax".
            val to = boundTarget(target) ?: return@let
            return AgentIntent("email", "email.draft", mapOf("to" to to),
                "draft email to $to")
        }

        // Calendar
        if (normalized in setOf("what's on my calendar", "whats on my calendar",
                "show my calendar", "what are my upcoming events", "list my events",
                "show me my calendar", "calendar", "my events")) {
            return AgentIntent("calendar", "calendar.list_events", mapOf("days" to "7"),
                "list upcoming events")
        }
        extractAfter(raw, listOf("create an event called ", "create a calendar event called ",
            "add an event called ", "schedule an event called ", "create event ",
            "add event "))?.let { title ->
            return AgentIntent("calendar", "calendar.create_event", mapOf("title" to title),
                "create event: $title")
        }

        // Desktop bridge
        if (normalized in setOf("what's on my desktop", "whats on my desktop",
                "list my desktop", "desktop status", "bridge status")) {
            return AgentIntent("computer", "desktop.run_build", mapOf("name" to "status"),
                "check desktop status")
        }
        extractAfter(raw, listOf("write a file on my desktop called ", "write a file on desktop called ",
            "create a file on desktop called ", "write on desktop "))?.let { _ ->
            return AgentIntent("computer", "desktop.write_file", emptyMap(),
                "write file on desktop")
        }

        // Run a script on the desktop
        extractAfter(raw, listOf("run the script ", "run script ",
            "execute the script ", "execute script "))?.let { path ->
            if (path.endsWith(".py") || path.endsWith(".sh")) {
                return AgentIntent("computer", "desktop.run_script", mapOf("path" to path),
                    "run script: $path")
            }
        }
        // "run build" still goes to run_build
        extractAfter(raw, listOf("run build "))?.let { name ->
            return AgentIntent("computer", "desktop.run_build", mapOf("name" to name),
                "run build: $name")
        }

        // ── Phone control (accessibility-based) ──────────────────────────
        // Single-shot commands: immediate execution via ui.automate
        if (normalized in setOf("go back", "press back", "hit back", "back")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "back"), "go back")
        }
        if (normalized in setOf("go home", "press home", "home button")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "home"), "go home")
        }
        if (normalized in setOf("show recents", "recent apps", "recents")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "recents"), "show recents")
        }
        if (normalized in setOf("show notifications", "open notifications", "pull down notifications")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "notifications"), "show notifications")
        }
        if (normalized in setOf("scroll down", "scroll the page down", "page down")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "scroll", "direction" to "down"), "scroll down")
        }
        if (normalized in setOf("scroll up", "scroll the page up", "page up")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "scroll", "direction" to "up"), "scroll up")
        }
        if (normalized in setOf("swipe left", "swipe to the left")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "swipe", "direction" to "left"), "swipe left")
        }
        if (normalized in setOf("swipe right", "swipe to the right")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "swipe", "direction" to "right"), "swipe right")
        }
        if (normalized in setOf("read the screen", "whats on screen", "what's on screen",
                "what do you see", "what is on the screen")) {
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "read_compact"), "read screen")
        }
        // "tap on X" / "tap X" / "click X" / "press X"
        extractAfter(raw, listOf("tap on ", "tap the ", "tap ", "click on ", "click the ",
            "click ", "press on ", "press the "))?.let { target ->
            val name = boundTarget(target) ?: return@let
            if (name.split(' ').size <= 5) {
                return AgentIntent("phone_control", "ui.automate", mapOf("action" to "tap", "text" to name), "tap " + name)
            }
        }
        // "type X" / "type X in Y"
        extractAfter(raw, listOf("type "))?.let { rest ->
            val inIdx = rest.lowercase().indexOf(" in ")
            if (inIdx > 0) {
                val value = rest.substring(0, inIdx).trim()
                val field = rest.substring(inIdx + 4).trim()
                return AgentIntent("phone_control", "ui.automate",
                    mapOf("action" to "type", "field" to field, "value" to value), "type in " + field)
            } else {
                val value = boundTarget(rest) ?: return@let
                return AgentIntent("phone_control", "ui.automate",
                    mapOf("action" to "type", "field" to "", "value" to value), "type: " + value)
            }
        }
        // "long press X" / "hold X"
        extractAfter(raw, listOf("long press on ", "long press ", "hold on ", "hold "))?.let { target ->
            val name = boundTarget(target) ?: return@let
            return AgentIntent("phone_control", "ui.automate", mapOf("action" to "long_press", "text" to name), "long press " + name)
        }

        // Phone control. "open the file X" is matched earlier, so a bare
        // Alarms and timers. Deterministic and checked BEFORE the app-launch
        // prefix: "set an alarm" is a thing to DO, not an app to hunt for, and
        // the system already knows where alarms live — ACTION_SET_ALARM is
        // handled by the Clock app, so the companion never needs to open Clock
        // and drive its UI. This used to exist only inside WakeWordService, so
        // it worked when spoken to the background service and silently fell
        // through to the language model everywhere else.
        parseAlarm(raw)?.let { return it }
        parseTimer(raw)?.let { return it }

        // "open X" reaching here means an app, not a workspace file.
        if (normalized in setOf("what apps do i have", "list my apps", "show my apps",
                "list apps", "show apps", "what apps are installed")) {
            return AgentIntent("phone", "phone.list_apps", emptyMap(), "list installed apps")
        }
        // Order matters: the specific "open ..." forms must be tested before the
        // bare "open " app prefix, or it swallows them.
        if (normalized in setOf("open settings", "open my settings", "open phone settings")) {
            return AgentIntent("phone", "phone.open_settings", emptyMap(), "open settings")
        }
        extractAfter(raw, listOf("open the settings for ", "open settings for ",
            "open my settings ", "open settings "))?.let { page ->
            return AgentIntent("phone", "phone.open_settings", mapOf("page" to page),
                "open $page settings")
        }
        extractAfter(raw, listOf("open the website ", "open the site ", "go to the website ",
            "open the url ", "browse to "))?.let { url ->
            return AgentIntent("phone", "phone.open_url", mapOf("url" to url), "open $url")
        }
        extractAfter(raw, listOf("open the app ", "open app ", "launch the app ",
            "launch app ", "launch ", "start the app ", "open "))?.let { rest ->
            // Bounded: an app name is a name, not the rest of the sentence.
            val name = boundTarget(rest) ?: return@let
            // More than a few words is a sentence, not an app — "open up about your
            // feelings" must stay conversation rather than becoming a launch request
            // for an app called "up about your feelings".
            if (name.split(' ').size > MAX_APP_NAME_WORDS) return@let
            return AgentIntent("phone", "phone.launch_app", mapOf("name" to name), "open $name")
        }

        // ── Assistant-class phone actions (Phase F4) ─────────────────────────
        // Volume: "turn the volume to 50", "volume up", "mute it".
        val volumeText = raw.lowercase().trim()
        if (volumeText.contains("volume") || volumeText.contains("turn it down") ||
            volumeText.contains("turn it up") || volumeText.contains("mute")) {
            val levelMatch = Regex("(\\d{1,3})\\s*(%|percent)?").find(volumeText)
            if (volumeText.contains("volume") || volumeText.contains("mute") ||
                volumeText.contains("turn it down") || volumeText.contains("turn it up")) {
                val level = levelMatch?.groupValues?.get(1)?.toIntOrNull()
                val action = when {
                    level != null -> "level"
                    volumeText.contains("down") || volumeText.contains("lower") -> "down"
                    volumeText.contains("up") || volumeText.contains("raise") || volumeText.contains("louder") -> "up"
                    volumeText.contains("mute") || volumeText.contains("silent") -> "mute"
                    else -> null
                }
                if (action != null) {
                    return AgentIntent(
                        "phone", "phone.set_volume",
                        if (action == "level") mapOf("level" to level.toString()) else mapOf("action" to action),
                        "set volume"
                    )
                }
            }
        }
        // Brightness: "set the brightness to 70", "screen too dark".
        if (volumeText.contains("brightness")) {
            val level = Regex("(\\d{1,3})\\s*(%|percent)?").find(volumeText)?.groupValues?.get(1)?.toIntOrNull()
            if (level != null) {
                return AgentIntent("phone", "phone.set_brightness", mapOf("level" to level.toString()), "set brightness")
            }
        }
        // Camera: "take a picture", "take a photo", "open the camera".
        if (normalized in setOf("take a picture", "take a photo", "take a pic", "open the camera",
                "open camera", "take some photos", "take a selfie")) {
            return AgentIntent("phone", "phone.take_photo", emptyMap(), "open the camera")
        }
        // Messaging: "text mom", "send a text to mom saying ...", "sms dad".
        extractAfter(raw, listOf("text ", "send a text to ", "send a text ", "sms ",
            "send an sms to ", "send an sms "))?.let { rest ->
            val pieces = rest.trim().split(" saying ", " saying ", " that ", " the message ")
            val recipient = pieces.firstOrNull()?.takeIf { it.isNotBlank() }
            val body = pieces.getOrNull(1)?.trim()
            if (recipient != null && recipient.split(' ').size <= 4) {
                return AgentIntent(
                    "phone", "phone.compose_sms",
                    if (body.isNullOrBlank()) mapOf("to" to recipient) else mapOf("to" to recipient, "body" to body),
                    "text $recipient"
                )
            }
        }
        // Calling: "call mom", "call the store" — but not "call the robot/base".
        if (normalized.startsWith("call ") && !normalized.contains("robot") &&
            !normalized.contains("base") && !normalized.contains("looi")) {
            val rest = normalized.removePrefix("call ").trim()
            if (rest.isNotBlank() && rest.split(' ').size <= 4) {
                return AgentIntent("phone", "phone.dial_call", mapOf("to" to rest), "call $rest")
            }
        }
        // Media transport: pause/play/resume/skip the music without the full loop.
        if (normalized in setOf("pause the music", "pause music", "stop the music",
                "resume the music", "resume music", "play the music", "next song",
                "next track", "skip song", "skip track", "skip this song",
                "previous song", "previous track", "go back a song")) {
            val action = when {
                normalized.contains("pause") || normalized.contains("stop") -> "pause"
                normalized.contains("resume") || normalized.contains("play") -> "play"
                normalized.contains("next") || normalized.contains("skip") -> "next"
                else -> "previous"
            }
            return AgentIntent("phone", "phone.media_control", mapOf("action" to action), "$action music")
        }


        if (normalized in setOf("list my notes", "show my notes", "what notes do i have",
                "show notes", "list notes", "my notes", "open notes", "open my notes",
                "open vault", "show vault", "show me my notes")) {
            return AgentIntent("main", "vault.note.list", emptyMap(), "list vault notes")
        }
        extractAfter(raw, listOf("show me the note ", "open the note ", "read the note ",
                "open note ", "read note ", "show note "))?.let { title ->
            return AgentIntent("main", "vault.note.read", mapOf("title" to title.trim()), "read note: $title")
        }
        extractAfter(raw, listOf("search my notes for ", "search notes for ",
                "find notes about ", "find a note about ", "search vault for "))?.let { query ->
            return AgentIntent("main", "vault.note.search", mapOf("query" to query.trim()), "search notes: $query")
        }
        extractAfter(raw, listOf("create a note called ", "make a note called ",
                "new note called ", "create a note titled ", "make a note titled ",
                "write a note called "))?.let { title ->
            return AgentIntent("main", "vault.note.write",
                mapOf("title" to title.trim(), "body" to ""), "new note: $title")
        }
        extractAfter(raw, listOf("delete the note ", "delete note "))?.let { title ->
            return AgentIntent("main", "vault.note.delete",
                mapOf("title" to title.trim()), "delete note: $title")
        }

        // Avatar performance. Deterministic because a trick must be instant and must
        // ALWAYS land on the face: the video tool's bare "animate " prefix used to
        // swallow these, and the user got a workspace file instead of a performance.
        // Frames stay with the tool (built-in/store lookup by name) — the router
        // only has to say WHICH clip.
        if (normalized in setOf("do a trick", "show me a trick", "do an animation",
                "show me an animation", "perform a trick", "do something cute",
                "animate yourself", "wiggle your ears")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "ear wiggle"), "ear wiggle")
        }
        if (normalized in setOf("dance", "do a dance", "show me a dance", "dance for me")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "happy bop"), "happy bop")
        }
        if (normalized in setOf("slow blink", "do a slow blink", "blink at me", "give me a slow blink")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "slow blink"), "slow blink")
        }
        if (normalized in setOf("lick your paw", "lick your paws", "paw lick", "do a paw lick")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "paw lick"), "paw lick")
        }
        if (normalized in setOf("flatten your ears", "flatten ears", "ear flatten", "put your ears back")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "ear flatten"), "ear flatten")
        }
        if (normalized in setOf("nose twitch", "twitch your nose", "sniff", "sniff around")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "nose twitch"), "nose twitch")
        }
        if (normalized in setOf("chirp", "do a chirp", "make a chirping sound", "chirp at me")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "chirp"), "chirp")
        }
        if (normalized in setOf("yawn", "do a yawn", "yawn for me", "show me a yawn")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "yawn"), "yawn")
        }
        if (normalized in setOf("hunting focus", "hunting mode", "focus", "lock on", "stare")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "hunting focus"), "hunting focus")
        }
        if (normalized in setOf("zoomies", "do zoomies", "go crazy", "run around")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "zoomies"), "zoomies")
        }
        if (normalized in setOf("stretch", "do a stretch", "stretch out")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "stretch"), "stretch")
        }
        if (normalized in setOf("headshake", "shake your head", "head shake", "no")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "headshake"), "headshake")
        }
        if (normalized in setOf("trill", "do a trill", "make a trill", "purr trill")) {
            return AgentIntent("main", "avatar.animate", mapOf("name" to "trill"), "trill")
        }
        // Describe-animation routes. These cover moods, emotions, and behaviors
        // that don't have a dedicated built-in clip. The describer maps keywords
        // to pre-baked keyframes — no model call needed.
        if (normalized in setOf("be happy", "look happy", "smile", "cheer up",
                "look excited", "get excited", "be excited")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("look sad", "be sad", "feel sad", "cheerless")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("get angry", "look angry", "be mad", "be angry",
                "look pissed")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("look scared", "be scared", "get scared",
                "look frightened", "you're scared")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("look curious", "be curious", "wonder",
                "what's that", "hmm")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("look sleepy", "be sleepy", "get sleepy",
                "take a nap", "feel drowsy")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("purr", "purr for me", "do a purr")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("sneeze", "do a sneeze", "achoo")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("jump", "do a jump", "hop", "do a hop")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("laugh", "do a laugh", "haha", "that's funny")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("cry", "do a cry", "boohoo", "weep")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("wave", "wave at me", "say hi")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("nod", "nod your head", "yes yes")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        if (normalized in setOf("glow", "sparkle", "glow for me")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
        // "spin" deliberately does NOT live here any more. It used to generate a
        // face animation, but a face cannot spin — the base can, and that is
        // what a person means by it. It falls through to the robot rule below.
        // The robot body — checked AFTER every face rule above.
        //
        // The face wins any phrase it owns ("wiggle your ears", "shake your
        // head"), and that is not a compromise: FaceMotorMap already drives the
        // servos from the played clip, so a face performance moves the physical
        // head too. What reaches here is everything the face cannot do —
        // driving, spinning in place, the headlight — where the hardware is the
        // only possible meaning. Deterministic, because a physical action
        // should not wait on a model call, and "turn off the lights" would
        // otherwise be a coin flip between the headlight and a smart-home tool.
        parseRobotMotion(raw)?.let { return it }

        // "play the ear wiggle animation" — replay a saved or built-in clip by name.
        extractAfter(raw, listOf("play the ", "play "))?.let { rest ->
            val name = rest.removeSuffix(" animation").removeSuffix(" Animation").trim()
            if (name.isNotBlank() && name != rest.trim()) {
                return AgentIntent("main", "avatar.animate", mapOf("name" to name), "play $name")
            }
        }

        // Media / image generation
        extractAfter(raw, listOf("generate an image of ", "generate an image of a ",
            "create an image of ", "make an image of ", "draw ", "paint ",
            "generate an image called ", "create an image called ",
            "generate image ", "create image "))?.let { prompt ->
            return AgentIntent("media", "media.generate_image", mapOf("prompt" to prompt),
                "generate image: $prompt")
        }

        // Video generation. No bare "animate " prefix: it swallowed avatar
        // performances ("animate yourself" became a video OF "yourself" — a file,
        // not a performance). "make an animation of X" is still clearly a video.
        extractAfter(raw, listOf("generate a video of ", "create a video of ",
            "make a video of ", "generate video ", "create video ",
            "make an animation of "))?.let { prompt ->
            return AgentIntent("media", "media.generate_video", mapOf("prompt" to prompt),
                "generate video: $prompt")
        }

        // Audio / song generation
        extractAfter(raw, listOf("generate a song ", "generate a sound ",
            "create a song ", "make a song ", "generate audio ",
            "create audio ", "generate a tone ", "generate sound ",
            "create a melody "))?.let { prompt ->
            return AgentIntent("media", "media.generate_audio", mapOf("prompt" to prompt),
                "generate audio: $prompt")
        }

        return null
    }

    /**
     * An email compose request, wherever the address sits in the sentence.
     *
     * People say "open gmail and write a message that says hi to X" — one sentence that
     * opens an app AND writes a message. `email.send` does both: it opens the mail app
     * with the recipient and body already filled in, so this is one action, not two.
     *
     * Saying "draft" keeps the word's original meaning: `email.draft` saves a file in
     * the workspace instead of opening the composer.
     */
    private fun composeEmail(raw: String): AgentIntent? {
        val address = EMAIL_ADDRESS.find(raw)?.value ?: return null
        val lower = raw.lowercase()
        // An address alone is not a request to write — "my email is x@y.com" isn't.
        if (COMPOSE_CUES.none { lower.contains(it) }) return null

        val bodyMatch = BODY_MARKERS.asSequence()
            .mapNotNull { marker ->
                val idx = lower.indexOf(marker)
                if (idx < 0) null else idx to raw.substring(idx + marker.length)
            }
            .firstOrNull()
        val body = bodyMatch?.second
            // The address often trails the message ("says hi to x@y.com") — it is the
            // recipient, not part of what to say.
            ?.replace(EMAIL_ADDRESS, "")
            ?.trim()
            ?.removeSuffix(" to")
            ?.trim()
            ?.trim('"', '\'', '.', ',')
            .orEmpty()
        val bodyStart = bodyMatch?.first ?: raw.length

        // The subject search stops where the message begins: "saying the subject is
        // green" is a message ABOUT a subject, not a subject.
        val subject = lower.indexOf(SUBJECT_MARKER)
            .takeIf { it >= 0 && it < bodyStart }
            ?.let { idx ->
                val rest = raw.substring(idx + SUBJECT_MARKER.length)
                val cut = (BODY_MARKERS + BOUNDARIES)
                    .mapNotNull { m -> rest.lowercase().indexOf(m).takeIf { i -> i > 0 } }
                    .minOrNull() ?: rest.length
                rest.substring(0, cut).trim().trim('"', '\'', '.', ',')
            }
            .orEmpty()

        val drafting = lower.contains("draft")
        return AgentIntent(
            agentId = "email",
            toolId = if (drafting) "email.draft" else "email.send",
            args = mapOf("to" to address, "subject" to subject, "body" to body),
            title = if (drafting) "draft email to $address" else "email $address",
        )
    }

    /**
     * Cuts an extracted value where the sentence moves on.
     *
     * Every prefix extractor here used to return the whole remainder of the input, which
     * is right for a file path at the end of a sentence and wrong for everything else:
     * one trailing clause turned "gmail" into "gmail and write a message that says hi to
     * someone@example.com", and the lookup could only fail.
     */
    private fun boundTarget(raw: String): String? {
        var value = raw.trim()
        BOUNDARIES.forEach { marker ->
            val idx = value.lowercase().indexOf(marker)
            if (idx > 0) value = value.substring(0, idx)
        }
        return value.trim().trim(',', '.', '!', '?').trim().ifBlank { null }
    }

    /**
     * Phrases that mean "move the body", spoken TO the companion.
     *
     * A motion phrase alone is not enough — "I need to turn left at the lights"
     * is navigation, not a command. Requiring either an address to the
     * companion, an imperative opening, or an unambiguous body word keeps
     * conversation out of the motors.
     */
    private val BODY_CUES = listOf(
        "spin", "wiggle", "nod", "shake your head", "headlight", "head light",
        "look up", "look down", "look ahead", "drive", "roll",
    )

    /** "spin around", "turn off the headlights", "shake your head", "stop". */
    private fun parseRobotMotion(raw: String): AgentIntent? {
        val t = raw.lowercase().trim()
        if (t.isBlank()) return null
        val motion = com.example.meowmba.core.robot.RobotManager.Motion
            .fromPhrase(t, includeFaceOwned = false)
            ?: return null

        // Guard against sentences that merely mention a direction. A bare
        // "left"/"right"/"forward"/"back"/"stop" is only a command when it is
        // essentially the whole utterance or clearly addressed to the robot.
        val hasBodyCue = BODY_CUES.any { it in t } ||
            t.startsWith("go ") || t.startsWith("turn ") || t.startsWith("move ") ||
            t.startsWith("stop") || t.startsWith("come ")
        val short = t.split(' ').size <= 4
        if (!hasBodyCue && !short) return null
        // Questions are questions: "can you spin around?" is a request, but
        // "which way did it spin?" is not a command.
        if (t.startsWith("what") || t.startsWith("which") || t.startsWith("why") ||
            t.startsWith("when") || t.startsWith("where") || t.startsWith("did ")
        ) return null

        return AgentIntent(
            "robot",
            "robot.move",
            mapOf("action" to motion.name.lowercase()),
            "robot: ${motion.name.lowercase().replace('_', ' ')}",
        )
    }

    /** A spoken wall-clock time, already converted to 24-hour. */
    data class SpokenTime(val hour: Int, val minute: Int)

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "forty five" to 45, "fortyfive" to 45, "fifty" to 50,
    )

    /**
     * Parse a spoken wall-clock time: "7", "7am", "7:30", "seven thirty",
     * "half past seven", "quarter to eight", "19:05", "noon", "midnight".
     *
     * Returns null when there is no time in the text at all, so the caller can
     * fall through rather than inventing one — an alarm set for a guessed hour
     * is worse than an honest "when?".
     */
    internal fun parseSpokenTime(text: String): SpokenTime? {
        val t = text.lowercase()
        if (Regex("\\bnoon|\\bmidday\\b").containsMatchIn(t)) return SpokenTime(12, 0)
        if (t.contains("midnight")) return SpokenTime(0, 0)

        // NOT \bpm\b: in "7pm" the digit and the letter are both word characters,
        // so there is no word boundary between them and the meridiem was missed
        // entirely — "7pm" set a 7am alarm. Guard on adjacent LETTERS instead, so
        // "7pm" and "7 pm" both match while "spam" and "campus" do not.
        val meridiemPm = Regex("(?<![a-z])(pm|p\\.m\\.?)(?![a-z])").containsMatchIn(t) ||
            Regex("\\b(afternoon|evening|tonight)\\b").containsMatchIn(t)
        val meridiemAm = Regex("(?<![a-z])(am|a\\.m\\.?)(?![a-z])").containsMatchIn(t) ||
            Regex("\\bmorning\\b").containsMatchIn(t)

        // "half past seven" / "quarter past 7" / "quarter to 8"
        Regex("\\b(half|quarter)\\s+(past|to|till|before)\\s+(\\d{1,2}|[a-z]+)\\b")
            .find(t)?.let { m ->
                val base = m.groupValues[3].toIntOrNull() ?: NUMBER_WORDS[m.groupValues[3]]
                if (base != null && base in 1..12) {
                    val offset = if (m.groupValues[1] == "half") 30 else 15
                    val toward = m.groupValues[2] != "past"
                    var hour = if (toward) base - 1 else base
                    val minute = if (toward) 60 - offset else offset
                    if (hour <= 0) hour = 12
                    return SpokenTime(to24(hour, meridiemPm, meridiemAm), minute)
                }
            }

        // Digits: "7", "7:30", "7.30", "1905"
        Regex("\\b(\\d{1,2})[:.](\\d{2})\\b").find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                return SpokenTime(if (h > 12) h else to24(h, meridiemPm, meridiemAm), min)
            }
        }
        Regex("\\b(\\d{1,2})\\s*(am|pm|a\\.m\\.?|p\\.m\\.?)?\\b").find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) {
                return SpokenTime(if (h > 12) h else to24(h, meridiemPm, meridiemAm), 0)
            }
        }

        // Words: "seven thirty", "seven"
        val words = t.split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        words.forEachIndexed { i, w ->
            val h = NUMBER_WORDS[w] ?: return@forEachIndexed
            if (h !in 1..12) return@forEachIndexed
            val min = words.getOrNull(i + 1)?.let { NUMBER_WORDS[it] }?.takeIf { it in 1..59 } ?: 0
            return SpokenTime(to24(h, meridiemPm, meridiemAm), min)
        }
        return null
    }

    /**
     * 12-hour to 24-hour. With no am/pm said aloud, a bare hour of 1–7 means the
     * afternoon far more often than 1am–7am ("set an alarm for 6" is 6pm only if
     * you say so; "wake me at 6" is 6am) — so the default stays AM, which is
     * both the safer miss for an alarm and what the old parser did.
     */
    private fun to24(hour12: Int, pm: Boolean, am: Boolean): Int = when {
        pm && hour12 != 12 -> hour12 + 12
        am && hour12 == 12 -> 0
        !pm && hour12 == 12 -> 0
        else -> hour12
    }

    /** "set an alarm for 7am", "wake me up at 6:30", "alarm for quarter past 8". */
    private fun parseAlarm(raw: String): AgentIntent? {
        val t = raw.lowercase()
        val asked = Regex("\\bset\\s+(an?\\s+)?alarm\\b").containsMatchIn(t) ||
            Regex("\\bwake me( up)?\\b").containsMatchIn(t) ||
            Regex("\\balarm for\\b").containsMatchIn(t)
        if (!asked) return null
        val time = parseSpokenTime(t) ?: return null
        val label = extractAfter(raw, listOf(" labeled ", " called ", " labelled "))
        return AgentIntent(
            "phone",
            "phone.set_alarm",
            buildMap {
                put("hour", time.hour.toString())
                put("minutes", time.minute.toString())
                label?.let { put("label", it) }
            },
            "set alarm for ${time.hour}:${time.minute.toString().padStart(2, '0')}",
        )
    }

    /** "set a timer for 10 minutes", "timer for 90 seconds". */
    private fun parseTimer(raw: String): AgentIntent? {
        val t = raw.lowercase()
        if (!Regex("\\b(set\\s+(a\\s+)?)?timer\\b").containsMatchIn(t)) return null
        val hours = Regex("(\\d{1,3})\\s*(hour|hours|hr|hrs)").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val minutes = Regex("(\\d{1,4})\\s*(min|mins|minute|minutes)").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val seconds = Regex("(\\d{1,4})\\s*(sec|secs|second|seconds)").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val total = (hours ?: 0) * 3600 + (minutes ?: 0) * 60 + (seconds ?: 0)
        if (total <= 0) return null
        return AgentIntent(
            "phone", "phone.set_timer", mapOf("seconds" to total.toString()),
            "set timer for ${total}s",
        )
    }

    /** Case-insensitive prefix match; returns the RAW remainder (punctuation intact). */
    private fun extractAfter(raw: String, prefixes: List<String>): String? {
        val lowered = raw.lowercase()
        for (prefix in prefixes) {
            if (lowered.startsWith(prefix)) {
                return raw.substring(prefix.length).trim().ifBlank { null }
            }
        }
        return null
    }

    // ── Deep-link search ("search X in/on <app>") ───────────────────────────

    data class DeepLinkTarget(val app: String, val query: String, val url: String)

    /**
     * Parse an in-app search request: "search the beatles on spotify",
     * "search spotify for beatles". Returns null unless a KNOWN app is named,
     * so a generic "search the web for X" never becomes an app deep link.
     */
    fun routeDeepLink(normalized: String): DeepLinkTarget? {
        val t = normalized.trim()
        val inOn = Regex("^search (?:for )?(.+?) (?:in|on) (.+)$").find(t)
        val forQuery = Regex("^search (\\w+) for (.+)$").find(t)
        val (app, query) = when {
            inOn != null -> inOn.groupValues[2].trim() to inOn.groupValues[1].trim()
            forQuery != null -> forQuery.groupValues[1].trim() to forQuery.groupValues[2].trim()
            else -> return null
        }
        if (app.isEmpty() || query.isEmpty()) return null
        // "search the web for X on spotify" is a web search that merely names an app.
        if (query.contains("the web") || query.contains("online") || query.contains("internet")) return null
        val url = buildSearchUrl(app, query) ?: return null
        return DeepLinkTarget(app, query, url)
    }

    /** Known-app search URL; null for an app we don't deep-link. */
    fun buildSearchUrl(app: String, query: String): String? {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val a = app.lowercase()
        return when {
            a.contains("youtube music") -> "https://music.youtube.com/search?q=$q"
            a.contains("youtube") -> "https://www.youtube.com/results?search_query=$q"
            a.contains("spotify") -> "https://open.spotify.com/search/$q"
            a.contains("google maps") || a.contains("maps") -> "geo:0,0?q=$q"
            a.contains("gmail") -> "https://mail.google.com/mail/?q=$q"
            a.contains("instagram") -> "https://www.instagram.com/explore/search/keyword/?q=$q"
            a.contains("tiktok") -> "https://www.tiktok.com/search?q=$q"
            a.contains("netflix") -> "https://www.netflix.com/search?q=$q"
            a.contains("reddit") -> "https://www.reddit.com/search/?q=$q"
            a.contains("twitter") || a == "x" -> "https://twitter.com/search?q=$q"
            a.contains("amazon") -> "https://www.amazon.com/s?k=$q"
            a.contains("wikipedia") -> "https://en.wikipedia.org/w/index.php?search=$q"
            a.contains("pinterest") -> "https://www.pinterest.com/search/pins/?q=$q"
            a.contains("google") || a.contains("chrome") -> "https://www.google.com/search?q=$q"
            else -> null
        }
    }

    /**
     * True for the ways people actually ask about the weather.
     *
     * Used by the ViewModel to answer DIRECTLY rather than to build an AgentIntent: routing
     * this through the orchestrator announced "Agent is on it, I'll ask if a step needs your
     * approval" for what should be an instant answer. Deterministic on purpose — leaving it
     * to LLM tool-calling meant it depended on the on-device model emitting valid JSON,
     * which it did not, so "what's the weather" did nothing at all.
     *
     * Requires a weather NOUN, so "how hot is this curry" and "what's the temperature of the
     * oven" do not trigger a forecast. "Do I need a coat/umbrella" are included because they
     * are weather questions in everything but wording.
     */
    internal fun isWeatherQuestion(normalized: String): Boolean {
        val nouns = listOf(
            "weather", "forecast", "temperature", "how hot is it", "how cold is it",
            "how warm is it", "raining", "going to rain", "gonna rain", "snowing",
            "going to snow", "sunny", "cloudy", "humidity", "wind speed",
            "need a coat", "need a jacket", "need an umbrella", "how's it outside",
            "hows it outside", "what's it like outside", "whats it like outside",
        )
        return nouns.any { normalized.contains(it) }
    }

}
