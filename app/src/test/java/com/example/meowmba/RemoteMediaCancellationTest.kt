package com.example.meowmba

import com.example.meowmba.core.media.RemoteMusicGenerator
import com.example.meowmba.core.media.RemoteVideoGenerator
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

class RemoteMediaCancellationTest {
    @Test fun `music transport cancellation propagates rather than becoming network failure`() = runTest {
        val engine = MockEngine { throw CancellationException("stop requested") }
        try {
            RemoteMusicGenerator("https://music.example", engine).generate("test")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `video transport cancellation propagates rather than becoming network failure`() = runTest {
        val engine = MockEngine { throw CancellationException("stop requested") }
        try {
            RemoteVideoGenerator("https://video.example", engine).generate("test")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
