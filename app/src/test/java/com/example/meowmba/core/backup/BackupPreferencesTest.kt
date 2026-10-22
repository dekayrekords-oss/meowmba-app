package com.example.meowmba.core.backup

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupPreferencesTest {
    @Test fun chainedCommitWaitsUntilRestoreCheckAndMergeFinish() = verifySerializedWrite(false)
    @Test fun chainedApplyWaitsUntilRestoreCheckAndMergeFinish() = verifySerializedWrite(true)

    private fun verifySerializedWrite(apply: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        val raw = context.getSharedPreferences("lock-test", 0)
        raw.edit().clear().commit()
        val protected = BackupPreferences.protect(raw)
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        lateinit var future: Future<Boolean>
        try {
            BackupPreferences.locked {
                future = executor.submit<Boolean> {
                    val editor = protected.edit()
                    assertSame(editor, editor.putString("key", "new-user-key").putInt("count", 1).remove("unused"))
                    started.countDown()
                    if (apply) { editor.apply(); true } else editor.commit()
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                try { future.get(200, TimeUnit.MILLISECONDS); fail("Writer bypassed the restore lock") } catch (_: TimeoutException) { }
                assertFalse(raw.contains("key"))
            }
            assertTrue(future.get(5, TimeUnit.SECONDS))
            assertEquals("new-user-key", raw.getString("key", null))
        } finally { executor.shutdownNow() }
    }
}
