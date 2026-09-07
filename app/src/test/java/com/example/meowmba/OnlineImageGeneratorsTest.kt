package com.example.meowmba

import com.example.meowmba.core.media.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlineImageGeneratorsTest {
    private val account = "a".repeat(32)
    private val ticket = "12345678-1234-1234-1234-123456789abc"
    private fun encodedImage() = Base64.getEncoder().encodeToString(javaClass.getResourceAsStream("/one_pixel.png")!!.readBytes())

    @Test fun cloudflarePostsFluxRequestAndDecodesRealImage() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer test-secret", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.encodedPath.endsWith("/ai/run/@cf/black-forest-labs/flux-1-schnell"))
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("a cat", body["prompt"]!!.jsonPrimitive.content)
            assertEquals(4, body["steps"]!!.jsonPrimitive.int)
            respond("""{"success":true,"result":{"image":"${encodedImage()}"}}""")
        }
        val result = CloudflareImageGenerator(account, "test-secret", engine).generate("a cat")
        assertTrue(result.toString(), result.isSuccess)
        result.getOrThrow().recycle()
    }

    @Test fun cloudflareQuotaAndAuthErrorsAreActionableWithoutEchoingSecrets() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests)) {
            val result = CloudflareImageGenerator(account, "test-secret", MockEngine {
                respond("test-secret and private upstream diagnostics", status)
            }).generate("a cat")
            val message = result.exceptionOrNull()!!.message!!
            assertFalse(message.contains("test-secret"))
            assertTrue(message.contains(if (status.value == 401) "token" else "quota"))
        }
    }

    @Test fun cloudflareRejectsMalformedResponseAndOversizedPayload() = runTest {
        val missing = CloudflareImageGenerator(account, "token", MockEngine { respond("""{"success":true,"result":{}}""") }).generate("cat")
        assertTrue(missing.isFailure)
        val huge = CloudflareImageGenerator(account, "token", MockEngine {
            respond("{}", headers = headersOf(HttpHeaders.ContentLength, "20000000"))
        }).generate("cat")
        assertTrue(huge.exceptionOrNull()!!.message!!.contains("oversized"))
    }

    @Test fun cloudflareConnectionCheckDoesNotGenerateOrSpendImageQuota() = runTest {
        val result = CloudflareImageGenerator(account, "token", MockEngine {
            assertEquals(HttpMethod.Get, it.method)
            assertTrue(it.url.encodedPath.endsWith("/ai/models/search"))
            respond("""{"success":true,"result":[]}""")
        }).checkConnection()
        assertTrue(result.getOrThrow().contains("write permission"))
    }

    @Test fun hordeNeedsAnonymousSharingConsentBeforeAnyRequest() = runTest {
        val generator = HordeImageGenerator(engine = MockEngine { error("Must not send a prompt without consent") })
        assertFalse(generator.isAvailable())
        assertTrue(generator.generate("cat").exceptionOrNull()!!.message!!.contains("sharing"))
    }

    // Ktor's mock engine uses a real dispatcher: virtual runTest time otherwise
    // jumps past the queue deadline while a real-thread response is in flight.
    @Test fun hordeQueuesPollsAndDecodesWithoutFollowingImageUrls() = runBlocking {
        var checks = 0
        val progress = mutableListOf<String>()
        val engine = MockEngine { request ->
            assertEquals(HordeImageGenerator.ANONYMOUS_KEY, request.headers["apikey"])
            when {
                request.method == HttpMethod.Post -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertTrue(body["shared"]!!.jsonPrimitive.boolean)
                    assertFalse(body["r2"]!!.jsonPrimitive.boolean)
                    respond("""{"id":"$ticket"}""", HttpStatusCode.Accepted)
                }
                request.url.encodedPath.contains("/check/") -> {
                    checks++
                    respond(if (checks == 1) """{"done":false,"is_possible":true,"queue_position":2,"wait_time":10}""" else """{"done":true}""")
                }
                else -> respond("""{"generations":[{"img":"${encodedImage()}","censored":false}]}""")
            }
        }
        val result = HordeImageGenerator(anonymousSharingAccepted = true, onProgress = progress::add, engine = engine, pollMillis = 1).generate("cat")
        assertTrue(result.toString(), result.isSuccess)
        assertEquals(2, checks)
        assertTrue(progress.any { it.contains("queue position 2") })
        result.getOrThrow().recycle()
    }

    @Test fun hordePersonalKeyRequestsNoDatasetSharingAndCancelsFaultedJob() = runBlocking {
        var deleted = false
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Post -> {
                    assertEquals("personal-test-key", request.headers["apikey"])
                    assertFalse(Json.parseToJsonElement((request.body as TextContent).text).jsonObject["shared"]!!.jsonPrimitive.boolean)
                    respond("""{"id":"$ticket"}""")
                }
                HttpMethod.Delete -> { deleted = true; respond("{}") }
                else -> respond("""{"faulted":true}""")
            }
        }
        val result = HordeImageGenerator(apiKey = "personal-test-key", engine = engine).generate("cat")
        assertTrue(result.isFailure)
        assertTrue(deleted)
    }

    @Test fun cancellationDeletesHordeTicketAndIsNotConvertedToFailure() = runBlocking {
        val polling = CompletableDeferred<Unit>()
        var deleted = false
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Post -> respond("""{"id":"$ticket"}""")
                HttpMethod.Delete -> { deleted = true; respond("{}") }
                else -> { polling.complete(Unit); respond("""{"done":false,"is_possible":true}""") }
            }
        }
        val job = launch { HordeImageGenerator(anonymousSharingAccepted = true, engine = engine).generate("cat"); fail("Cancelled generation must not return") }
        polling.await()
        job.cancelAndJoin()
        assertTrue(deleted)
    }

    @Test fun badAccountNeverSendsCredentialsAndRedirectsAreNotFollowed() = runTest {
        assertTrue(CloudflareImageGenerator("../../other", "token", MockEngine { error("No request allowed") }).generate("cat").isFailure)
        var count = 0
        val result = CloudflareImageGenerator(account, "token", MockEngine {
            count++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.com/steal"))
        }).generate("cat")
        assertEquals(1, count)
        assertTrue(result.isFailure)
    }
}
