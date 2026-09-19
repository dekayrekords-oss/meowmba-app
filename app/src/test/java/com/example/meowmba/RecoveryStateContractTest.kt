package com.example.meowmba

import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.ui.companion.CompanionState
import org.junit.Assert.*
import org.junit.Test

class RecoveryStateContractTest {
    @Test fun companionStateStaysBelowJvmArgumentLimit() {
        CompanionState::class.java.declaredMethods.forEach { method ->
            val slots = method.parameterTypes.sumOf { if (it == Long::class.javaPrimitiveType || it == Double::class.javaPrimitiveType) 2 else 1 }
            assertTrue("${method.name} uses $slots JVM slots; group new fields in features", slots < 250)
        }
    }

    @Test fun versionedModelUpdatesKeepTheirRuntimeClassification() {
        assertEquals("needle-3", ModelCatalog.optionForFile("needle3--c9d915eca282.cact")?.id)
        assertFalse(ModelCatalog.optionForFile("needle3--c9d915eca282.cact")!!.runtime.canActivate)
        assertNull(ModelCatalog.optionForFile("../needle3.cact"))
        assertNull(ModelCatalog.optionForFile("needle3--not-a-checksum.cact"))
    }
}
