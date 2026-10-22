package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

/** Cancel and join real housekeeping jobs before the test's Main dispatcher is
 * removed. These fixtures use an unconfined Main dispatcher, so cleanup can
 * also finish children that resume on Main. */
internal class TestCompanionModels : AutoCloseable {
    private val models = mutableListOf<CompanionViewModel>()
    fun add(model: CompanionViewModel) { models += model }
    override fun close() {
        runBlocking { models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        models.clear()
    }
}
