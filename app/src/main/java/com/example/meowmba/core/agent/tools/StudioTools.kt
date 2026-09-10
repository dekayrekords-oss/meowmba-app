package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.database.CharacterDao
import com.example.meowmba.core.database.CharacterEntity
import com.example.meowmba.core.studio.ComicRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Studio tools: recurring-character memory plus comic/script creation whose
 * output is ALWAYS shown. Every visual tool returns its artifact path on a
 * `MEDIA_FILE:` line — onTaskCompleted opens that in the browser overlay, so
 * "made it but didn't show me" cannot happen by construction.
 *
 * Character consistency rides the `characters` Room table, not fuzzy memory
 * recall: `studio.save_character` upserts by name and `studio.create_comic`
 * loads the exact same sheet back into the rendered page's CAST footer.
 */
class SaveCharacterTool(private val characters: CharacterDao) : Tool {
    override val definition = ToolDefinition(
        id = "studio.save_character",
        displayName = "Save character",
        description = "Save or update a RECURRING CHARACTER sheet so every future comic, " +
            "cartoon, or ad uses the exact same design. Pass the full sheet as markdown/JSON: " +
            "species, hex colors, markings, proportions, outfit, personality, catchphrases, " +
            "and what must never change. Saving again with the same name replaces the sheet.",
        inputSchema = mapOf("name" to "string", "sheet" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE)
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val name = args["name"]?.trim().orEmpty()
        val sheet = args["sheet"]?.trim().orEmpty()
        when {
            name.isEmpty() -> return@withContext ToolResult.Failure("Missing 'name'")
            sheet.isEmpty() -> return@withContext ToolResult.Failure("Missing 'sheet' — include appearance (hex colors), proportions, personality")
        }
        try {
            characters.upsert(CharacterEntity(name = name, sheet = sheet))
            ToolResult.Success(
                "Character \"$name\" saved as a locked design. Use studio.get_character before " +
                    "drawing them again so comics and videos stay identical."
            )
        } catch (e: Exception) {
            ToolResult.Failure("Could not save character: ${e.message}")
        }
    }
}

class GetCharacterTool(private val characters: CharacterDao) : Tool {
    override val definition = ToolDefinition(
        id = "studio.get_character",
        displayName = "Get character",
        description = "Read a saved character's locked design sheet. ALWAYS call this before " +
            "writing or drawing an existing character in any comic, video, or ad.",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        auditLogged = false
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val name = args["name"]?.trim().orEmpty()
        if (name.isEmpty()) return@withContext ToolResult.Failure("Missing 'name'")
        try {
            val sheet = characters.getByName(name)?.sheet
                ?: return@withContext ToolResult.Failure(
                    "No character named \"$name\". Saved: " +
                        (characters.getAllOnce().joinToString { it.name }.ifEmpty { "(none yet)" })
                )
            ToolResult.Success("Sheet for \"$name\":\n$sheet")
        } catch (e: Exception) {
            ToolResult.Failure("Could not read character: ${e.message}")
        }
    }
}

class CreateComicTool(
    private val workspace: Workspace,
    private val characters: CharacterDao? = null
) : Tool {
    override val definition = ToolDefinition(
        id = "studio.create_comic",
        displayName = "Create comic",
        description = "Create a comic page and SHOW it to the user in the browser panel. " +
            "'panels' is a JSON array like [{\"caption\":\"narration\",\"dialogue\":\"what is said\",\"description\":\"what is drawn\"}]. " +
            "Optional 'characters' = comma-separated saved names; their locked sheets are loaded " +
            "into the page so designs stay consistent. Call studio.get_character first for each.",
        inputSchema = mapOf("title" to "string", "panels" to "string", "characters" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE)
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val title = args["title"]?.trim().orEmpty().ifBlank { "Untitled comic" }
        val panels = args["panels"]?.trim().orEmpty()
        if (!panels.startsWith("[")) {
            return@withContext ToolResult.Failure(
                "Missing 'panels' — must be a JSON array of {caption, dialogue, description} objects"
            )
        }
        // Load the locked sheets for every named character. Unknown names are
        // reported honestly instead of silently dropped — a missing sheet means
        // the design is NOT pinned and the user should be told.
        val cast = linkedMapOf<String, String>()
        val unknown = mutableListOf<String>()
        args["characters"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            val sheet = runCatching { characters?.getByName(name)?.sheet }.getOrNull()
            if (sheet != null) cast[name] = sheet else unknown.add(name)
        }

        val html = ComicRenderer.renderComicHtml(title, panels, cast)
        val file = runCatching { workspace.newAsset("comics", "${ComicRenderer.slugify(title)}.html") }
            .getOrElse { return@withContext ToolResult.Failure("Could not reserve comic output: ${it.message}") }
        runCatching {
            file.writeText(html)
        }.onFailure {
            runCatching { file.delete() } // Only this newly reserved, failed output.
            return@withContext ToolResult.Failure("Failed to save the comic: ${it.message}")
        }
        val note = if (unknown.isEmpty()) "" else
            "\nNote: no saved sheet for ${unknown.joinToString { "\"$it\"" }} — those designs are not locked yet."
        ToolResult.Success(
            "Comic \"$title\" (${panels.count { it == '{' }} panels${if (cast.isNotEmpty()) ", cast: ${cast.keys.joinToString()}" else ""}) created and opening now.$note\nMEDIA_FILE:${file.absolutePath}"
        )
    }
}

class WriteScriptTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "studio.write_script",
        displayName = "Write script",
        description = "Write a YouTube script, advertisement, or post draft and SHOW it to the " +
            "user in the browser panel. 'body' is the full text (# headings, - bullets, **bold**). " +
            "Use kind=script for YouTube videos, kind=ad for ads, kind=post for social posts.",
        inputSchema = mapOf("title" to "string", "body" to "string", "kind" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE)
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val title = args["title"]?.trim().orEmpty().ifBlank { "Untitled draft" }
        val body = args["body"]?.trim().orEmpty()
        val kind = args["kind"]?.trim()?.lowercase().takeIf { it in listOf("script", "ad", "post") } ?: "script"
        if (body.isEmpty()) return@withContext ToolResult.Failure("Missing 'body'")

        val html = ComicRenderer.renderScriptHtml(title, kind, body)
        val dir = when (kind) { "ad" -> "ads"; "post" -> "posts"; else -> "scripts" }
        val file = runCatching { workspace.newAsset(dir, "${ComicRenderer.slugify(title)}.html") }
            .getOrElse { return@withContext ToolResult.Failure("Could not reserve $kind output: ${it.message}") }
        runCatching {
            file.writeText(html)
        }.onFailure {
            runCatching { file.delete() } // Never remove a previous draft.
            return@withContext ToolResult.Failure("Failed to save the $kind: ${it.message}")
        }
        ToolResult.Success(
            "$kind \"${title}\" drafted and opening now.\nMEDIA_FILE:${file.absolutePath}"
        )
    }
}
