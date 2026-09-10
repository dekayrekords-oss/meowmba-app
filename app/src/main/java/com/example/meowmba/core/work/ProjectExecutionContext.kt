package com.example.meowmba.core.work

import kotlinx.coroutines.asContextElement

/** App-owned task scope, not a model argument. Follows coroutine dispatcher switches. */
object ProjectExecutionContext {
    data class Scope(val projectId: Long?, val conversationId: Long?)
    private val current = ThreadLocal<Scope?>()
    val projectId: Long? get() = current.get()?.projectId
    val conversationId: Long? get() = current.get()?.conversationId
    fun element(projectId: Long?, conversationId: Long? = null) = current.asContextElement(Scope(projectId, conversationId))
    fun folder(fallback: () -> String?): String? {
        val scope = current.get() ?: return fallback()
        return scope.projectId?.let { "projects/project-$it" }
    }
}
