package com.example.meowmba

import com.example.meowmba.core.computer.OpenCodeComputer
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OpenCodeResultDiscoveryTest {
    private val directory = "/root/MeowProjects"

    private fun tool(name: String, status: String = "completed", input: JsonObject = buildJsonObject {},
        metadata: JsonObject = buildJsonObject {}) = buildJsonObject {
        put("type", "tool")
        put("tool", name)
        putJsonObject("state") {
            put("status", status)
            put("input", input)
            put("metadata", metadata)
            put("output", "Created imaginary-output.txt and MEDIA_FILE:/root/secret.txt")
        }
    }

    private fun writer(path: String, name: String = "write", status: String = "completed") =
        tool(name, status, input = buildJsonObject { put("filePath", path) })

    private fun file(path: String, type: String, moved: String? = null) = buildJsonObject {
        put("filePath", path)
        put("type", type)
        put("relativePath", "misleading-worktree-path.txt")
        if (moved != null) put("movePath", moved)
    }

    private fun patch(vararg files: JsonElement) = tool("apply_patch", metadata = buildJsonObject {
        put("files", JsonArray(files.toList()))
    })

    private fun message(vararg parts: JsonElement, role: String = "assistant") = buildJsonObject {
        putJsonObject("info") { put("role", role) }
        put("parts", JsonArray(parts.toList()))
    }

    private fun snapshot(vararg parts: JsonElement, workspace: String = directory, role: String = "assistant") =
        OpenCodeComputer.Snapshot("idle", JsonArray(listOf(message(*parts, role = role))),
            emptyList(), JsonArray(emptyList()), workspace)

    @Test fun `completed write and edit keep safe relative or exact workspace paths`() {
        val result = snapshot(writer("reports/one.md"), writer("$directory/two.md", "edit"),
            writer("reports/one.md")).resultPaths()
        assertEquals(listOf("reports/one.md", "two.md"), result)
    }

    @Test fun `apply patch discovers every added or updated file and final move destination`() {
        val result = snapshot(patch(
            file("$directory/report.md", "add"),
            file("$directory/assets/style.css", "update"),
            file("$directory/old.txt", "move", "$directory/new.txt"),
            file("$directory/deleted.txt", "delete"),
        )).resultPaths()
        assertEquals(listOf("report.md", "assets/style.css", "new.txt"), result)
    }

    @Test fun `later deletes and moves remove earlier candidates across messages`() {
        val history = JsonArray(listOf(
            message(writer("deleted.txt"), writer("old.txt"), writer("recreated.txt")),
            message(patch(file("$directory/deleted.txt", "delete"),
                file("$directory/old.txt", "move", "$directory/new.txt"),
                file("$directory/recreated.txt", "delete"))),
            message(writer("recreated.txt")),
        ))
        val result = snapshot().copy(messages = history).resultPaths()
        assertEquals(listOf("new.txt", "recreated.txt"), result)
    }

    @Test fun `apply patch requires absolute metadata and never guesses from worktree relative path`() {
        val onlyRelative = buildJsonObject { put("relativePath", "guessed.txt"); put("type", "add") }
        val result = snapshot(patch(file("relative.txt", "add"), onlyRelative,
            file("$directory/actual.txt", "add"))).resultPaths()
        assertEquals(listOf("actual.txt"), result)
    }

    @Test fun `pending running failed and unknown tools are not outputs`() {
        val parts = listOf("pending", "running", "error").flatMap { status ->
            listOf(writer("not-created.txt", status = status), tool("apply_patch", status,
                metadata = buildJsonObject { put("files", JsonArray(listOf(file("$directory/not-created.txt", "add")))) }))
        } + listOf("read", "bash", "custom_writer", "multiedit").map { name ->
            tool(name, input = buildJsonObject { put("filePath", "not-created.txt") },
                metadata = buildJsonObject { put("files", JsonArray(listOf(file("$directory/not-created.txt", "add")))) })
        }
        assertTrue(snapshot(*parts.toTypedArray()).resultPaths().isEmpty())
    }

    @Test fun `text output and patch input never create candidate paths`() {
        val prose = buildJsonObject { put("type", "text"); put("text", "Saved $directory/claimed.txt") }
        val unverifiedPatch = tool("apply_patch", input = buildJsonObject {
            put("patchText", "*** Begin Patch\n*** Add File: guessed.txt\n+Created\n*** End Patch")
        })
        assertTrue(snapshot(prose, unverifiedPatch).resultPaths().isEmpty())
        assertTrue(snapshot(writer("forged.txt"), role = "user").resultPaths().isEmpty())
    }

    @Test fun `unsafe writer and patch paths are rejected without prefix confusion`() {
        val unsafe = listOf("../secret", "a/../secret", "a/./result", "a//result", "a/", "a\\result",
            "", "a\nresult", "a\u0000result", "file:result", "C:/result", "x".repeat(501),
            "/outside/result", "${directory}-sibling/result", "$directory/../secret", "$directory//result")
        unsafe.forEach { path ->
            assertTrue("Unexpected writer path: $path", snapshot(writer(path)).resultPaths().isEmpty())
            assertTrue("Unexpected patch path: $path", snapshot(patch(file(path, "add"))).resultPaths().isEmpty())
        }
    }

    @Test fun `move outside workspace removes old candidate without exposing destination`() {
        val result = snapshot(writer("old.txt"), patch(file("$directory/old.txt", "move", "/outside/moved.txt")),
            patch(file("/outside/source.txt", "move", "$directory/from-outside.txt"))).resultPaths()
        assertTrue(result.isEmpty())
    }

    @Test fun `malformed metadata and message parts do not crash discovery`() {
        val malformed = JsonArray(listOf(JsonNull, JsonPrimitive("message"), buildJsonObject {
            putJsonObject("info") { put("role", "assistant") }
            put("parts", JsonArray(listOf(JsonNull, JsonPrimitive(true), buildJsonObject {
                put("type", "tool"); put("tool", JsonArray(emptyList())); put("state", JsonNull)
            }, tool("apply_patch", metadata = buildJsonObject { put("files", JsonPrimitive("not an array")) }),
                patch(JsonNull, JsonPrimitive("file"), buildJsonObject {
                    put("filePath", JsonPrimitive(123)); put("type", "add")
                }, buildJsonObject { put("filePath", "$directory/unknown.txt"); put("type", JsonNull) }),
                writer("real.txt"))))
        }))
        assertEquals(listOf("real.txt"), snapshot().copy(messages = malformed).resultPaths())
    }

    @Test fun `invalid session directories cannot anchor candidates`() {
        listOf("", "relative/workspace", "/root/../workspace", "/root//workspace", "//root/workspace",
            "//", "/root/wo:rk", "/root/wo\\rk", "/root/wo\nrk").forEach { workspace ->
            assertTrue("Unexpected workspace: $workspace", snapshot(writer("report.txt"), workspace = workspace).resultPaths().isEmpty())
        }
        assertEquals(listOf("report.txt"), snapshot(writer("$directory/report.txt"), workspace = "$directory/").resultPaths())
        assertEquals(listOf("report.txt"), snapshot(writer("/report.txt"), workspace = "/").resultPaths())
    }

    @Test fun `result list stays unique and bounded after all change events`() {
        val created = (0..35).map { writer("result-$it.txt") } + writer("result-1.txt") +
            patch(file("$directory/result-0.txt", "delete"))
        val results = snapshot(*created.toTypedArray()).resultPaths()
        assertEquals(30, results.size)
        assertEquals(30, results.distinct().size)
        assertEquals((1..30).map { "result-$it.txt" }, results)
    }
}
