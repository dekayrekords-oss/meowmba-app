package com.example.meowmba

import com.example.meowmba.core.agent.HuggingFaceLocalModels
import com.example.meowmba.core.agent.PhoneModelDevice
import com.example.meowmba.core.agent.PhoneModelDiscovery
import com.example.meowmba.core.agent.PhoneModelFilters
import com.example.meowmba.core.agent.PhoneModelFormat
import com.example.meowmba.core.agent.PhoneModelHttpResponse
import com.example.meowmba.core.agent.PhoneModelSort
import com.example.meowmba.core.agent.PhoneModelSpeed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class PhoneModelDiscoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val gib = PhoneModelDiscovery.GIB
    private val phone = PhoneModelDevice(12 * gib, 40 * gib, arm64 = true, lowRam = false)
    private val sha = "a".repeat(40)

    private fun metadata(
        repo: String = "community/Qwen2.5-0.5B-Instruct",
        files: String = """{"rfilename":"Qwen2.5-0.5B-Instruct_q8_ekv1280.task","size":546660344}""",
        extra: String = "",
    ): String = """{
        "id":"$repo", "sha":"$sha", "library_name":"litert-lm", "pipeline_tag":"text-generation",
        "createdAt":"2025-04-30T16:16:19.000Z", "lastModified":"2026-08-31T13:48:36.000Z",
        "downloads":1234, "likes":8, "gated":false, "cardData":{"license":"apache-2.0"},
        "siblings":[$files] $extra
    }"""

    private fun candidate() = HuggingFaceLocalModels.parseRepository(metadata(), phone).single()

    @Test fun `file metadata gives pinned URL exact bytes and honest provenance`() {
        val result = candidate()
        assertEquals(546660344L, result.sizeBytes)
        assertEquals(500_000_000L, result.parameters)
        assertEquals("Estimated from model name", result.parametersSource)
        assertEquals("Q8", result.quantization)
        assertEquals("File name", result.quantizationSource)
        assertTrue(result.downloadUrl.contains("/resolve/$sha/"))
        assertEquals("apache-2.0", result.license)
        assertEquals(1234L, result.downloads)
        assertEquals(8L, result.likes)
        assertNotEquals(result.createdAtMs, result.modifiedAtMs)
        assertNull(result.releasedAtMs)
        assertNull(result.downloadedAtMs)
        assertTrue(result.assessment.eligible)
        assertFalse(result.assessment.verifiedOnDevice)
        assertEquals(PhoneModelSpeed.FAST, result.assessment.speed)
    }

    @Test fun `all supported weight variants survive while tensors image weights and routers do not`() {
        val files = """
            {"rfilename":"chat-int4.task","size":600000000},
            {"rfilename":"chat-fp16.task","size":2000000000},
            {"rfilename":"chat.litertlm","size":1000000000},
            {"rfilename":"model.safetensors","size":1000000},
            {"rfilename":"weights.gguf","size":1000000},
            {"rfilename":"needle2.cact","size":1000000},
            {"rfilename":"pytorch_model.bin","size":1000000}
        """
        val models = HuggingFaceLocalModels.parseRepository(metadata(files = files), phone)
        assertEquals(3, models.size)
        assertEquals(setOf("INT4", "FP16", null), models.map { it.quantization }.toSet())
        assertTrue(HuggingFaceLocalModels.parseRepository(metadata(repo = "Cactus-Compute/needle2", files = files), phone).isEmpty())
        assertTrue(HuggingFaceLocalModels.parseRepository(metadata(files = files).replace("text-generation", "feature-extraction"), phone).isEmpty())
    }

    @Test fun `NPU desktop web and auxiliary variants are ineligible for CPU GPU runtime`() {
        val files = listOf("chat.litertlm", "chat-gpu.litertlm", "chat-web.task", "chat_Google_Tensor_G5.litertlm",
            "chat_qualcomm_sm8750.litertlm", "chat_intel_LNL.litertlm", "chat-npu.litertlm", "chat-adapter.task", "chat_mt6993.litertlm")
            .joinToString(",") { """{"rfilename":"$it","size":1000000000}""" }
        val models = HuggingFaceLocalModels.parseRepository(metadata(files = files), phone)
        assertEquals(9, models.size)
        assertEquals(setOf("chat.litertlm", "chat-gpu.litertlm"),
            PhoneModelDiscovery.filterAndSort(models).map { it.remoteFile }.toSet())
    }

    @Test fun `legacy MediaPipe bin cannot be offered as eligible download`() {
        val file = """{"rfilename":"chat.bin","size":1000000000}"""
        val model = HuggingFaceLocalModels.parseRepository(metadata(repo = "mediapipe/chat", files = file), phone).single()
        assertEquals(PhoneModelFormat.MEDIAPIPE_BIN, model.format)
        assertFalse(model.assessment.eligible)
        assertTrue(model.assessment.reason.contains("Legacy binary"))
    }

    @Test fun `11 GiB ceiling does not replace actual RAM and storage checks`() {
        assertFalse(PhoneModelDiscovery.assess(phone, 12 * gib, null).eligible)
        assertTrue(PhoneModelDiscovery.assess(phone, gib, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone.copy(ramBytes = 3 * gib), gib, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone.copy(freeBytes = gib), gib, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone.copy(availableRamBytes = gib), gib, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone.copy(arm64 = false), gib, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone.copy(lowRam = true), gib, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone, null, null).eligible)
        assertFalse(PhoneModelDiscovery.assess(phone, -1, null).eligible)
        assertTrue(PhoneModelDiscovery.assess(phone, gib, null).estimatedRamBytes!! > gib)
    }

    @Test fun `effective E2B name is not presented as total parameter count`() {
        val effective = HuggingFaceLocalModels.parseRepository(metadata(repo = "litert-community/gemma-4-E2B-it-litert-lm"), phone).single()
        assertNull(effective.parameters)
        assertEquals(2_000_000_000L, effective.activeParameters)
        val total = HuggingFaceLocalModels.parseRepository(metadata(extra = """, "safetensors":{"total":123456789}"""), phone).single()
        assertEquals(123456789L, total.parameters)
        assertEquals("Hub metadata (model level)", total.parametersSource)
    }

    @Test fun `lfs file length checksum and gate are preserved`() {
        val checksum = "b".repeat(64)
        val file = """{"rfilename":"folder/chat int4.task","size":135,"lfs":{"size":1000000,"sha256":"$checksum"}}"""
        val model = HuggingFaceLocalModels.parseRepository(metadata(files = file).replace("\"gated\":false", "\"gated\":\"manual\""), phone).single()
        assertEquals(1_000_000L, model.sizeBytes)
        assertEquals(checksum, model.sha256)
        assertTrue(model.gated)
        assertTrue(model.downloadUrl.endsWith("/folder/chat%20int4.task"))
        assertFalse(model.fileName.contains('/'))
        assertFalse(model.fileName.contains(' '))
    }

    @Test fun `untrusted file paths and missing immutable revisions do not become downloads`() {
        val files = """{"rfilename":"../chat.task","size":1000},{"rfilename":"/chat.task","size":1000},{"rfilename":"chat.task","size":1000}"""
        assertEquals(1, HuggingFaceLocalModels.parseRepository(metadata(files = files), phone).size)
        val model = HuggingFaceLocalModels.parseRepository(metadata().replace(sha, "main"), phone).single()
        assertFalse(model.assessment.eligible)
        assertTrue(HuggingFaceLocalModels.parseRepository(metadata(repo = "../escape"), phone).isEmpty())
    }

    @Test fun `different repositories cannot overwrite the same custom model filename`() {
        val a = PhoneModelDiscovery.localFileName("first/chat", sha, "model.task")
        val b = PhoneModelDiscovery.localFileName("second/chat", sha, "model.task")
        assertNotEquals(a, b)
        assertTrue(a.endsWith(".task"))
        assertEquals(a, PhoneModelDiscovery.localFileName("first/chat", sha, "model.task"))
    }

    @Test fun `curated filenames remain reusable for matching repository files`() {
        assertEquals("qwen2.5-0.5b-instruct-q8.task", PhoneModelDiscovery.localFileName("litert-community/Qwen2.5-0.5B-Instruct", sha,
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"))
    }

    @Test fun `unknown fields remain unknown and explicit release date is distinct from created`() {
        val body = metadata().replace("\"downloads\":1234, \"likes\":8,", "")
            .replace("\"license\":\"apache-2.0\"", "\"release_date\":\"2026-01-01\"")
        val model = HuggingFaceLocalModels.parseRepository(body, phone).single()
        assertNull(model.downloads)
        assertNull(model.likes)
        assertNull(model.license)
        assertEquals(1767225600000L, model.releasedAtMs)
        assertNotEquals(model.createdAtMs, model.releasedAtMs)
    }

    @Test fun `filters cover speed sizes weights dates and real community counts`() {
        val light = candidate().copy(id = "light", downloadedAtMs = 30, installedPath = "/models/light.task", releasedAtMs = 20)
        val heavy = light.copy(id = "heavy", sizeBytes = 3 * gib, parameters = 7_000_000_000, quantization = "INT4", likes = 100,
            downloads = 50_000, createdAtMs = 50, downloadedAtMs = 40, gated = true,
            assessment = light.assessment.copy(speed = PhoneModelSpeed.HEAVY))
        val unknown = light.copy(id = "unknown", parameters = null, likes = null, sizeBytes = null, installedPath = null, downloadedAtMs = null)
        val all = listOf(heavy, unknown, light)
        assertEquals(listOf(light), PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(maxSizeBytes = gib)))
        assertEquals(listOf(light), PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(maxParameters = 1_000_000_000)))
        assertEquals(listOf(heavy), PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(quantization = "int4")))
        assertEquals(listOf(heavy), PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(speed = PhoneModelSpeed.HEAVY)))
        assertEquals(listOf(heavy), PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(minLikes = 20, minDownloads = 10_000)))
        assertEquals(listOf(heavy), PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(downloadedAfterMs = 35)))
        assertEquals(2, PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(installedOnly = true)).size)
        assertEquals(2, PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(ungatedOnly = true)).size)
        assertTrue(PhoneModelDiscovery.filterAndSort(all, PhoneModelFilters(releasedAfterMs = 21)).isEmpty())
        assertEquals("heavy", PhoneModelDiscovery.filterAndSort(all, sort = PhoneModelSort.LIKES).first().id)
        assertEquals("heavy", PhoneModelDiscovery.filterAndSort(all, sort = PhoneModelSort.DOWNLOADED).first().id)
        assertEquals("unknown", PhoneModelDiscovery.filterAndSort(all, sort = PhoneModelSort.PARAMETERS).last().id)
    }

    @Test fun `file modified date cannot silently stand in for a download date`() {
        val model = candidate().copy(fileName = "local.task", sizeBytes = 4)
        val file = temporary.newFile("local.task").apply { writeBytes(byteArrayOf(1, 2, 3, 4)); setLastModified(1000) }
        val merged = PhoneModelDiscovery.mergeInstalled(listOf(model), listOf(file)).single()
        assertEquals(file.absolutePath, merged.installedPath)
        assertEquals(1000L, merged.localModifiedAtMs)
        assertNull(merged.downloadedAtMs)
        assertEquals(2000L, PhoneModelDiscovery.mergeInstalled(listOf(model), listOf(file), mapOf(file.name to 2000L)).single().downloadedAtMs)
        assertNull(PhoneModelDiscovery.mergeInstalled(listOf(model.copy(sizeBytes = 100)), listOf(file)).single().installedPath)
    }

    @Test fun `every numeric sort accepts missing values and keeps unknown last`() {
        val known = candidate().copy(id = "known", likes = 0L, downloads = 0L, releasedAtMs = 10L, downloadedAtMs = 20L)
        val unknown = known.copy(id = "unknown", sizeBytes = null, parameters = null, likes = null, downloads = null,
            createdAtMs = null, modifiedAtMs = null, releasedAtMs = null, downloadedAtMs = null,
            assessment = known.assessment.copy(speed = PhoneModelSpeed.UNKNOWN))
        PhoneModelSort.entries.filter { it != PhoneModelSort.NAME }.forEach { sort ->
            assertEquals(sort.name, listOf("known", "unknown"),
                PhoneModelDiscovery.filterAndSort(listOf(unknown, known), sort = sort).map { it.id })
        }
    }

    @Test fun `public search retrieves bounded metadata with no token or weight downloads`() = runTest {
        val requests = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
        val service = HuggingFaceLocalModels { url, token ->
            requests += url to token
            if (url.contains("?blobs=true")) PhoneModelHttpResponse(200, metadata())
            else PhoneModelHttpResponse(200, "[${metadata()}]")
        }
        val page = service.search("Qwen", phone).getOrThrow()
        assertEquals(1, page.models.size)
        assertEquals(5, requests.size)
        assertTrue(requests.all { it.first.startsWith("https://huggingface.co/api/models") && it.second == null })
        assertTrue(requests.none { it.first.contains("/resolve/") })
        assertEquals(1, page.scannedRepositories)
    }

    @Test fun `exact repository search needs one metadata call`() = runTest {
        val calls = AtomicInteger()
        val service = HuggingFaceLocalModels { url, token ->
            calls.incrementAndGet()
            assertEquals("https://huggingface.co/api/models/community/Qwen2.5-0.5B-Instruct?blobs=true", url)
            assertEquals("hf_example", token)
            PhoneModelHttpResponse(200, metadata())
        }
        assertEquals(1, service.search("https://huggingface.co/community/Qwen2.5-0.5B-Instruct", phone, "hf_example").getOrThrow().models.size)
        assertEquals(1, calls.get())
    }

    @Test fun `pagination preserves query and rejects hostile links`() = runTest {
        val service = HuggingFaceLocalModels { url, _ ->
            if (url.contains("?blobs=true")) PhoneModelHttpResponse(200, metadata())
            else PhoneModelHttpResponse(200, "[${metadata()}]", if (url.contains("filter=litert-lm"))
                "<$url&cursor=next-page>; rel=\"next\"" else "<https://evil.example/api/models?cursor=stolen>; rel=\"next\"")
        }
        val page = service.search("Qwen", phone).getOrThrow()
        assertNotNull(page.nextCursor)
        assertTrue(service.search("Qwen", phone, cursor = page.nextCursor).isSuccess)
        assertTrue(service.search("Gemma", phone, cursor = page.nextCursor).isFailure)
    }

    @Test fun `malicious cursor cannot send credentials outside Hugging Face`() = runTest {
        val calls = AtomicInteger()
        val service = HuggingFaceLocalModels { _, _ -> calls.incrementAndGet(); error("Should not request") }
        val cursor = Base64.getUrlEncoder().encodeToString(
            """{"query":"Qwen","urls":["https://huggingface.co.evil.example/api/models?full=true&limit=8&search=Qwen"]}""".toByteArray())
        assertTrue(service.search("Qwen", phone, "hf_example", cursor).isFailure)
        assertEquals(0, calls.get())
        listOf("http://huggingface.co/api/models", "https://huggingface.co.evil.example/api/models", "https://user@huggingface.co/api/models",
            "https://huggingface.co:444/api/models", "https://huggingface.co/api/models/../secret", "https://huggingface.co/resolve/file.task")
            .forEach { assertFalse(it, HuggingFaceLocalModels.isSafeApiUrl(it)) }
        assertTrue(HuggingFaceLocalModels.isSafeApiUrl("https://huggingface.co/api/models?search=qwen"))
    }

    @Test fun `error bodies and token echoes are never exposed`() = runTest {
        val service = HuggingFaceLocalModels { _, _ -> PhoneModelHttpResponse(429, "secret hf_example") }
        val error = service.search("Qwen", phone, "hf_example").exceptionOrNull()!!
        assertTrue(error.message!!.contains("rate limiting"))
        assertFalse(error.message!!.contains("hf_example"))
        assertFalse(error.message!!.contains("secret"))
        val malformed = HuggingFaceLocalModels { _, _ -> PhoneModelHttpResponse(200, "secret hf_example malformed JSON") }
            .search("community/chat", phone, "hf_example").exceptionOrNull()!!
        assertFalse(malformed.message!!.contains("hf_example"))
        assertFalse(malformed.message!!.contains("secret"))
    }

    @Test fun `failed repository metadata is reported as partial not silently fitted`() = runTest {
        val service = HuggingFaceLocalModels { url, _ ->
            if (url.contains("?blobs=true")) PhoneModelHttpResponse(503, "unavailable")
            else PhoneModelHttpResponse(200, "[${metadata()}]")
        }
        val page = service.search("Qwen", phone).getOrThrow()
        assertTrue(page.models.isEmpty())
        assertTrue(page.notices.any { it.contains("Metadata for 1 repositories") })
    }

    @Test fun `external cancellation is propagated instead of becoming a displayed failure`() = runTest {
        val started = CompletableDeferred<Unit>()
        var returned = false
        val service = HuggingFaceLocalModels { _, _ -> started.complete(Unit); awaitCancellation() }
        val job = launch { service.search("Qwen", phone); returned = true }
        started.await()
        job.cancelAndJoin()
        assertFalse(returned)
        assertTrue(job.isCancelled)
    }

    @Test fun `invalid token and oversized metadata fail without exposing contents`() = runTest {
        val calls = AtomicInteger()
        val service = HuggingFaceLocalModels { _, _ -> calls.incrementAndGet(); PhoneModelHttpResponse(200, "x".repeat(2 * 1024 * 1024 + 1)) }
        assertTrue(service.search("Qwen", phone, "hf_example\ninjected").isFailure)
        assertEquals(0, calls.get())
        assertTrue(service.search("community/chat", phone).isFailure)
        assertEquals(1, calls.get())
    }
}
