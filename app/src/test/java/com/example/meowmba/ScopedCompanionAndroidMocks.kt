package com.example.meowmba

import android.os.SystemClock
import android.util.Log
import com.example.meowmba.core.agentic.AgenticRuntime
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

/** Narrow plain-JVM fixture for camera/preferences VM tests. Keeps Android time
 * deterministic and prevents unrelated agentic Room/Keystore startup. All static
 * mocks and the prior singleton are restored after cancelling the VM scope.
 * This does not alter app/test-wide return-default behavior or mock camera policy.
 */
internal class ScopedCompanionAndroidMocks(nowMs: () -> Long) : AutoCloseable {
    private val clock = mockStatic(SystemClock::class.java)
    private val log = mockStatic(Log::class.java)
    private val runtimeField = AgenticRuntime::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private val priorRuntime = runtimeField.get(null)
    init {
        clock.`when`<Long> { SystemClock.elapsedRealtime() }.thenAnswer { nowMs() }
        clock.`when`<Long> { SystemClock.uptimeMillis() }.thenAnswer { nowMs() }
        runtimeField.set(null, mock(AgenticRuntime::class.java))
    }
    override fun close() {
        runtimeField.set(null, priorRuntime)
        log.close()
        clock.close()
    }
}
