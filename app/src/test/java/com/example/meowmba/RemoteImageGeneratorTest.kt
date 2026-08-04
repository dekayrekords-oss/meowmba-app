package com.example.meowmba

import com.example.meowmba.core.media.RemoteImageGenerator
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBitmapFactory

@RunWith(RobolectricTestRunner::class)
class RemoteImageGeneratorTest {

    @Test
    fun `isAvailable is true whenever a base URL is configured`() {
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", MockEngine { respond("") })
        assertTrue(generator.isAvailable())
    }

    @Test
    fun `generate posts the prompt and returns a decoded bitmap on success`() = runTest {
        val fakePng = RemoteImageGeneratorTest::class.java
            .getResourceAsStream("/one_pixel.png")!!.readBytes()
        val engine = MockEngine { request ->
            respond(
                content = fakePng,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
            )
        }
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", engine)

        val result = generator.generate("a cat")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `generate reports an honest failure on a non-2xx response`() = runTest {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", engine)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `generate rejects a response larger than the size ceiling`() = runTest {
        val oversized = ByteArray(33 * 1024 * 1024) // 33 MB, over the 32 MB cap
        val engine = MockEngine { request ->
            respond(
                content = oversized,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Image.PNG.toString()),
                    HttpHeaders.ContentLength to listOf(oversized.size.toString())
                )
            )
        }
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", engine)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too large") == true)
    }

    @Test
    fun `generate reports an honest failure on 200 OK with undecodable bytes`() = runTest {
        // Robolectric's shadow BitmapFactory happily decodes any byte array by
        // default (allowInvalidImageData defaults to true) — disable that so the
        // shadow actually mirrors real BitmapFactory's null-on-garbage behavior.
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        try {
            val garbage = "not an image".toByteArray()
            val engine = MockEngine { request ->
                respond(
                    content = garbage,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                )
            }
            val generator = RemoteImageGenerator("http://192.168.1.10:9100", engine)

            val result = generator.generate("a cat")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("undecodable") == true)
        } finally {
            ShadowBitmapFactory.setAllowInvalidImageData(true)
        }
    }
}
