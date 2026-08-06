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


        // Notification queries
        if (normalized in setOf("any notifications", "notifications", "what did i miss",
                "unread messages", "new messages", "any new messages",
                "check notifications", "check my notifications",
                "what notifications do i have", "read notifications",
                "any messages")) {
            return AgentIntent("notification", "notification.list", emptyMap(), "list notifications")
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
        // Screen-contextual "open the video/song/..." routes to phone_control, not app launch.
        if (normalized.startsWith("open ")) {
            val afterOpen = normalized.removePrefix("open ").trimStart()
            val contentTypes = listOf("video", "song", "post", "article", "photo", "picture", "image",
                "file", "document", "link", "message", "chat", "email", "item", "one", "game",
                "movie", "show", "episode", "playlist", "album", "track", "reel", "story", "notification")
            if (contentTypes.any { afterOpen.startsWith("the $it") || afterOpen.startsWith("$it ") }) {
                return AgentIntent("phone_control", "phone.screen_action", mapOf("goal" to raw), raw)
            }
            if (afterOpen.contains("called ") || afterOpen.contains("named ") || afterOpen.contains("titled ")) {
                return AgentIntent("phone_control", "phone.screen_action", mapOf("goal" to raw), raw)
            }
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

        // Vault / Obsidian note tools
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
        if (normalized in setOf("spin", "do a spin", "spin around")) {
            return AgentIntent("main", "avatar.describe_animation",
                mapOf("description" to normalized), normalized)
        }
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

        // ---- Device settings (LUMI edge) ----
        // Brightness
        if (normalized.contains("brightness") || normalized.contains("screen bright")
            || normalized.contains("dim the screen") || normalized.contains("brighten")
            || normalized.contains("how bright")) {
            val level = Regex("""(\d+)""").find(raw)?.value
            val args = if (level != null) mapOf("level" to level)
                       else if (normalized.contains("auto")) mapOf("level" to "auto")
                       else if (normalized.contains("max") || normalized.contains("full")) mapOf("level" to "255")
                       else if (normalized.contains("dim") || normalized.contains("low")) mapOf("level" to "50")
                       else emptyMap()
            return AgentIntent("device", "phone.brightness", args, "screen brightness")
        }
        // Volume
        if (normalized.contains("volume") || normalized.contains("turn it up")
            || normalized.contains("turn it down") || normalized.contains("louder")
            || normalized.contains("quieter") || normalized.contains("mute")
            || normalized.contains("unmute") || normalized.contains("sound level")) {
            val level = Regex("""(\d+)""").find(raw)?.value
            val stream = when {
                normalized.contains("ring") || normalized.contains("ringer") -> "ring"
                normalized.contains("alarm") -> "alarm"
                normalized.contains("notification") || normalized.contains("notif") -> "notification"
                else -> "media"
            }
            val args = mutableMapOf("stream" to stream)
            if (level != null) args["level"] = level
            else if (normalized.contains("mute") || normalized.contains("silence")) args["level"] = "0"
            else if (normalized.contains("max") || normalized.contains("full")) args["level"] = "15"
            return AgentIntent("device", "phone.volume", args, "volume control")
        }
        // Location / GPS
        if (normalized in setOf("where am i", "my location", "current location",
                "what is my location", "gps", "show my location", "location")
            || (normalized.contains("where") && normalized.contains("am i"))) {
            return AgentIntent("device", "phone.location", emptyMap(), "current location")
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
