package com.example.meowmba.core.plugins

/**
 * Curated "apps the AI can work with" catalog, modeled on the Loona Deskmate
 * app-connections surface. Unlike raw launcher discovery (which lists every
 * installable package regardless of whether the AI can actually control it),
 * this is an explicit, human-curated list of services the companion can
 * meaningfully attach to.
 *
 * Each entry carries a category, a plain-English capability line (how the AI
 * uses that service), a badge, and one of three integration states:
 *
 *  - [Integration.BUILT_IN]  — Meowmba already drives this service with no
 *    extra key (e.g. Gmail compose, calendar, music). Shown as "Active".
 *  - [Integration.API_KEY]   — needs an API/access token the user pastes in,
 *    with a [keyLink] straight to where they get it. Shown as "Add key".
 *  - [Integration.COMING_SOON] — no driver yet. Shown as "Soon".
 *
 * The UI mirrors Deskmate: a tile (category + badge + name + capability) that,
 * when tapped, opens a detail sheet with connection options / key link.
 */
object AppGallery {

    /** How the AI attaches to an entry. */
    enum class Integration { BUILT_IN, API_KEY, COMING_SOON }

    data class Entry(
        val id: String,
        val category: String,
        val badge: String,
        val name: String,
        val capability: String,
        val integration: Integration,
        /** Deep link to the page where the user obtains the key (API_KEY only). */
        val keyLink: String? = null,
        /** The SecureStorage key that stores this service's API token (API_KEY only). */
        val storageKey: String? = null,
        /** Meowmba's real app driver this maps to, when it has one. */
        val connectorId: String? = null,
    )

    val all: List<Entry> = listOf(
        // Mail
        Entry(
            "gmail", "Mail", "G", "Gmail",
            "Reads, summarizes and drafts replies — surfaces what matters and handles routine mail.",
            Integration.BUILT_IN,
            connectorId = "com.google.android.gm",
        ),

        // Calendar
        Entry(
            "google_calendar", "Calendar", "31", "Calendar",
            "Schedules, reschedules and briefs you on what's next — read aloud as you start your morning.",
            Integration.BUILT_IN,
            connectorId = "com.google.android.calendar",
        ),
        Entry(
            "outlook_calendar", "Calendar", "O", "Outlook",
            "Reads your calendar and plans your day, Microsoft-style.",
            Integration.API_KEY,
            keyLink = "https://learn.microsoft.com/graph/auth-register-app-v2",
            storageKey = "connector_outlook",
        ),

        // Messaging
        Entry(
            "slack", "Messaging", "#", "Slack",
            "Catch up on channels, get summaries of unread DMs and send quick replies hands-free.",
            Integration.API_KEY,
            keyLink = "https://api.slack.com/apps",
            storageKey = "connector_slack",
        ),
        Entry(
            "messages", "Messaging", "💬", "Text Messages",
            "Reads and sends text messages on this phone.",
            Integration.BUILT_IN,
            connectorId = "com.google.android.apps.messaging",
        ),
        Entry(
            "teams", "Messaging", "T", "Microsoft Teams",
            "Drafts and summarizes messages across your team.",
            Integration.API_KEY,
            keyLink = "https://learn.microsoft.com/graph/auth-register-app-v2",
            storageKey = "connector_teams",
        ),

        // Meetings
        Entry(
            "zoom", "Meetings", "Z", "Zoom",
            "Joins calls to take notes and produce summaries — so you focus on the conversation.",
            Integration.API_KEY,
            keyLink = "https://marketplace.zoom.us/develop/apps",
            storageKey = "connector_zoom",
        ),

        // Docs & notes
        Entry(
            "notion", "Docs", "N", "Notion",
            "Creates and searches notes and pages across your workspace.",
            Integration.API_KEY,
            keyLink = "https://www.notion.so/my-integrations",
            storageKey = "connector_notion",
        ),
        Entry(
            "gdrive", "Docs", "▣", "Google Drive",
            "Finds and drafts files across Drive, Docs, Sheets and Slides.",
            Integration.API_KEY,
            keyLink = "https://console.cloud.google.com/apis/credentials",
            storageKey = "connector_gdrive",
        ),
        Entry(
            "onedrive", "Docs", "1", "OneDrive",
            "Reads and organizes files in your OneDrive.",
            Integration.API_KEY,
            keyLink = "https://learn.microsoft.com/graph/auth-register-app-v2",
            storageKey = "connector_onedrive",
        ),

        // Everyday built-ins Meowmba already drives
        Entry(
            "music", "Media", "♪", "Music",
            "Plays songs, artists and podcasts on command (Spotify / YouTube Music).",
            Integration.BUILT_IN,
            connectorId = "com.spotify.music",
        ),
        Entry(
            "weather", "Info", "☀", "Weather",
            "Reports the forecast where you are.",
            Integration.BUILT_IN,
            connectorId = "com.google.android.apps.weather",
        ),
        Entry(
            "desktop", "Developer", "⌘", "Desktop & SSH",
            "Drives your PC over the bridge — run builds and scripts remotely.",
            Integration.BUILT_IN,
        ),
        Entry(
            "browser", "Media", "◉", "Browser",
            "Searches and visits the web for you.",
            Integration.BUILT_IN,
        ),
    )
}
