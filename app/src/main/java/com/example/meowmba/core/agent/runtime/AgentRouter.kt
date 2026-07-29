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

        // Research / web search
        extractAfter(raw, listOf("search the web for ", "search web for ", "search online for ",
            "look up ", "search for "))?.let { query ->
            return AgentIntent("research", "web.search", mapOf("query" to query), "web search: $query")
        }

        // Email
        extractAfter(raw, listOf("send an email to ", "send email to ", "email "))?.let { target ->
            return AgentIntent("email", "email.send", mapOf("to" to target, "subject" to "", "body" to ""),
                "send email to $target")
        }
        extractAfter(raw, listOf("draft an email to ", "draft email to "))?.let { target ->
            return AgentIntent("email", "email.draft", mapOf("to" to target),
                "draft email to $target")
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
        extractAfter(raw, listOf("open the app ", "open app ", "launch the app ",
            "launch app ", "launch ", "start the app ", "open "))?.let { name ->
            return AgentIntent("phone", "phone.launch_app", mapOf("name" to name), "open $name")
        }

        // Media / image generation
        extractAfter(raw, listOf("generate an image of ", "generate an image of a ",
            "create an image of ", "make an image of ", "draw ", "paint ",
            "generate an image called ", "create an image called ",
            "generate image ", "create image "))?.let { prompt ->
            return AgentIntent("media", "media.generate_image", mapOf("prompt" to prompt),
                "generate image: $prompt")
        }

        // Video generation
        extractAfter(raw, listOf("generate a video of ", "create a video of ",
            "make a video of ", "generate video ", "create video ",
            "make an animation of ", "animate "))?.let { prompt ->
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
}
