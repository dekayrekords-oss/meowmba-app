package com.example.meowmba

import com.example.meowmba.ui.companion.*
import com.example.meowmba.core.vision.FaceTrack
import org.junit.Assert.*
import org.junit.Test
import com.example.meowmba.core.security.SecureStorageManager
import org.mockito.Mockito.*

class PersonalCompanion143Test {
    @Test fun privateAnswersStayInTheirOwnLocalStoreAndCanBeDeleted() {
        val storage = mock(SecureStorageManager::class.java)
        var encryptedValue = "{}"
        `when`(storage.getPreference("private_interview_v1")).thenAnswer { encryptedValue }
        doAnswer { invocation ->
            assertEquals("private_interview_v1", invocation.getArgument<String>(0))
            encryptedValue = invocation.getArgument(1); null
        }.`when`(storage).savePreference(anyString(), anyString())
        val store = PrivateProfileStore(storage)
        val question = PrivateProfileStore.questions.first()
        store.save(question, "Fixture only")
        assertEquals(mapOf(question to "Fixture only"), store.answers())
        assertThrows(IllegalArgumentException::class.java) { store.save("untrusted question", "value") }
        assertThrows(IllegalArgumentException::class.java) { store.save(question, "x".repeat(1501)) }
        store.deleteAll()
        assertTrue(store.answers().isEmpty())
        verify(storage, never()).getApiKey(anyString())
    }
    @Test fun privateCloudNeedsTwoExactApprovalsAndCannotReplay() {
        val gate = PrivateCloudApproval()
        assertThrows(IllegalStateException::class.java) { gate.consume("secret") }
        gate.begin("secret")
        assertThrows(IllegalStateException::class.java) { gate.consume("secret") }
        assertThrows(IllegalStateException::class.java) { gate.acknowledge("edited") }
        gate.acknowledge("secret")
        assertThrows(IllegalStateException::class.java) { gate.consume("edited") }
        assertEquals("secret", gate.consume("secret"))
        assertEquals(0, gate.step)
        assertThrows(IllegalStateException::class.java) { gate.consume("secret") }
        gate.begin("new"); gate.acknowledge("new"); gate.cancel()
        assertThrows(IllegalStateException::class.java) { gate.consume("new") }
    }
    @Test fun remoteQueueBudgetDoesNotIncreaseLocalInferenceHeatBudget() {
        assertEquals(60_000L, InventTimeBudget.prop(false, InventReasonLevel.FAST))
        assertEquals(180_000L, InventTimeBudget.prop(true, InventReasonLevel.FAST))
        assertTrue(InventTimeBudget.prop(true, InventReasonLevel.THOROUGH) <= 240_000)
    }
    private fun face(x: Float, time: Long, count: Int = 1) = FaceTrack(x, 0f,
        confidence = .95f, hasFace = true, faceCount = count, observedAtElapsedMs = time)
    @Test fun repeatedMovementCanBopButOneMovementOrBystandersCannot() {
        val director = PersonalReactionDirector()
        assertNull(director.sample(face(0f, 100), 100, true))
        assertNull(director.sample(face(.2f, 500), 500, true))
        assertNull(director.sample(face(-.2f, 900), 900, true))
        assertNull(director.sample(face(.2f, 1300), 1300, true))
        assertEquals("happy bop", director.sample(face(-.2f, 1700), 1700, true))
        assertNull(director.sample(face(.2f, 2000), 2000, true))
        assertNull(director.sample(face(-.2f, 2400, count = 2), 2400, true))
        assertNull(director.sample(face(.2f, 100), 5000, true))
    }
    @Test fun stationaryFaceNeverInventsEmotionOrDance() {
        val director = PersonalReactionDirector()
        (1..200).forEach { i -> assertNull(director.sample(face(.1f, i * 100L), i * 100L, true)) }
    }
}
