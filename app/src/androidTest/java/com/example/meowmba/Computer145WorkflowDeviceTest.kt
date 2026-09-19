package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.computer.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Synthetic workspace probe. Approves only one uniquely named test file, once.
 * No shell, personal-file reads, account changes or blanket grants are allowed. */
class Computer145WorkflowDeviceTest {
    @Test fun approvedFileCanBeRetrievedAndContextRemembered(): Unit = runBlocking {
        val storage = SecureStorageManager(InstrumentationRegistry.getInstrumentation().targetContext)
        OpenCodeLocalConnection.restore(storage)
        val password = storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty()
        assertTrue("Verify Computer connection first.", password.isNotBlank())
        OpenCodeLocalConnection.verify(password)
        val api = OpenCodeComputer(password)
        val session = api.create("Meow QA 145 · synthetic file and context")
        val token = UUID.randomUUID().toString().replace("-", "")
        val filename = "meow-qa-$token.txt"
        val marker = "MEOW_WORKFLOW_$token"
        var approved = false
        try {
            val directory = api.snapshot(session).directory.trimEnd('/')
            api.send(session, "Use only the write tool to create the NEW file $directory/$filename containing exactly $marker followed by a newline. This is an authorized synthetic QA file. Do not read any files, use bash, list directories, or do anything else. Request approval for that exact file. Then report completion. Remember the exact content marker for a later question.")
            withTimeout(120_000) {
                while (true) {
                    val snapshot = api.snapshot(session)
                    assertNoWorkerError(snapshot)
                    snapshot.permissions.forEach { permission ->
                        val patterns = permission["patterns"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                        val fullPath = "$directory/$filename"
                        // OpenCode1.18 can report root-relative permission patterns.
                        // Bind those to the exact absolute metadata target, not a glob.
                        val target = (permission["metadata"] as? JsonObject)?.get("filepath")?.jsonPrimitive?.content
                        val exact = permission["permission"]?.jsonPrimitive?.content == "edit" &&
                            target == fullPath && patterns.isNotEmpty() &&
                            patterns.all { it == filename || it == fullPath || it == fullPath.removePrefix("/") }
                        val allowed = exact && !approved
                        api.approve(session, permission["id"]!!.jsonPrimitive.content, allowed)
                        assertTrue("Refused unexpected/repeated permission: ${permission["permission"]}; patterns=$patterns; metadata keys=${(permission["metadata"] as? JsonObject)?.keys}. No broad approval granted.", allowed)
                        approved = true
                    }
                    if (filename in snapshot.resultPaths()) break
                    delay(1_000)
                }
            }
            assertTrue("File creation must be permission gated.", approved)
            val exported = api.artifact(session, filename).toString(Charsets.UTF_8)
            // This acceptance checks the actual text content. Track trailing-line
            // normalization separately; this does not establish byte-exact transfer.
            assertEquals("Actual exported content must match, not merely a completion claim.", marker, exported.trimEnd('\r', '\n'))
            val workspace = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "memba_workspace")
            val store = ComputerArtifactStore(workspace)
            val saved = store.save(session, filename, exported.toByteArray(Charsets.UTF_8))
            assertEquals(exported, saved.file.readText())
            assertEquals(saved, store.save(session, filename, exported.toByteArray(Charsets.UTF_8)))
            val written = api.snapshot(session).messages.flatMap { message ->
                message.jsonObject["parts"]?.jsonArray.orEmpty().map { it.jsonObject }
            }.firstOrNull { it["type"]?.jsonPrimitive?.content == "tool" && it["tool"]?.jsonPrimitive?.content == "write" }
                ?.get("state")?.jsonObject?.get("input")?.jsonObject?.get("content")?.jsonPrimitive?.content
            android.util.Log.i("MeowWorkflow145", "Synthetic file: $filename; requested write has newline=${written?.endsWith('\n')}; exported has newline=${exported.endsWith('\n')}; export equals write input=${written == exported}")
            withTimeout(30_000) {
                while (api.snapshot(session).status != "idle") delay(500)
            }
            val before = api.snapshot(session).messages.map { it.jsonObject["info"]!!.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
            api.send(session, "Without using tools or reading any files, reply with exactly CONTEXT_OK: followed immediately by the content marker from the file task in our previous exchange. Nothing else.")
            withTimeout(90_000) {
                while (true) {
                    val snapshot = api.snapshot(session)
                    assertNoWorkerError(snapshot)
                    snapshot.permissions.forEach {
                        api.approve(session, it["id"]!!.jsonPrimitive.content, false)
                    }
                    assertTrue("Context follow-up must not require file/tool access.", snapshot.permissions.isEmpty())
                    val success = snapshot.messages.map { it.jsonObject }.any { message ->
                        val info = message["info"]!!.jsonObject
                        info["id"]!!.jsonPrimitive.content !in before && info["role"]?.jsonPrimitive?.content == "assistant" &&
                            message["parts"]!!.jsonArray.map { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.content == "text" }
                                .joinToString("") { it["text"]?.jsonPrimitive?.content.orEmpty() }.trim() == "CONTEXT_OK:$marker"
                    }
                    if (success) break
                    delay(1_000)
                }
            }
            android.util.Log.i("MeowWorkflow145", "PASS: exact-file approval, artifact text content, and same-session context. Synthetic file: $filename")
        } finally {
            withContext(NonCancellable) { runCatching { api.stop(session) } }
        }
    }

    private fun assertNoWorkerError(snapshot: OpenCodeComputer.Snapshot) {
        val error = snapshot.messages.map { it.jsonObject["info"]!!.jsonObject }.filter {
            it["role"]?.jsonPrimitive?.content == "assistant"
        }.mapNotNull { it["error"] }.firstOrNull { it !is JsonNull }
        assertTrue("Worker reported ${error?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "an error"}; details withheld to protect credentials.", error == null)
    }
}
