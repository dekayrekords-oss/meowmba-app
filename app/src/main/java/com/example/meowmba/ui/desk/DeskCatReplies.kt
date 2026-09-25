package com.example.meowmba.ui.desk

/** Bridges live text to its durable final event without replaying old chat on Desk entry. */
internal class DeskCatReplies(
    private var conversation: Long,
    private var sequence: Long,
    private var live: String?,
) {
    private var activity = DeskCatActivity.IDLE
    private var shown: DeskCatReply? = null

    fun observe(conversationId: Long, finalSequence: Long, finalConversation: Long, finalText: String?,
        liveConversation: Long, liveText: String?, nextActivity: DeskCatActivity): DeskCatReply? {
        if (conversationId != conversation) {
            conversation = conversationId; sequence = finalSequence; live = liveText
            activity = nextActivity; shown = null
            return null
        }
        // Listening can be Meow's automatic follow-up window after speaking. Keep the
        // answer readable then; a new inference turn (or conversation) replaces it.
        if (nextActivity != activity && nextActivity == DeskCatActivity.THINKING) shown = null
        if (finalSequence > sequence && finalConversation == conversationId && !finalText.isNullOrBlank()) {
            shown = DeskCatReply("$conversationId:$finalSequence", finalText)
        } else if (liveText != live && liveConversation == conversationId && !liveText.isNullOrBlank()) {
            shown = DeskCatReply("$conversationId:${finalSequence + 1}", liveText)
        }
        sequence = finalSequence; live = liveText; activity = nextActivity
        return shown
    }
}
