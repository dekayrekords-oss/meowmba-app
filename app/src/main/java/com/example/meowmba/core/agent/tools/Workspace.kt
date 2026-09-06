package com.example.meowmba.core.agent.tools

import java.io.File

/**
 * The agent workspace: a sandboxed directory (app-private storage) that is
 * the ONLY place file and project tools may touch. Path resolution rejects
 * anything that escapes the root ("../", absolute paths, symlink tricks),
 * which is what makes the file tools safe to expose to agents at all.
 *
 * Pure JVM (java.io.File), fully unit-testable.
 */
class Workspace(val root: File, private val activeProjectFolder: () -> String? = { null }) {

    init {
        root.mkdirs()
    }

    /** Shared with Files: unique outputs, grouped under the active project when present. */
    fun newAsset(folder: String, name: String): File = com.example.meowmba.core.files.AssetLibrary(root)
        .newOutput(activeProjectFolder()?.let { "$it/$folder" } ?: folder, name)

    /**
     * Resolve a user/agent-supplied relative path against the sandbox root.
     * Returns null when the path tries to escape.
     */
    fun resolve(relativePath: String): File? {
        val clean = relativePath.trim().trimStart('/', '\\')
        if (clean.isBlank()) return null
        val resolved = File(root, clean)
        return try {
            val canonicalRoot = root.canonicalPath
            val canonicalResolved = resolved.canonicalPath
            if (canonicalResolved == canonicalRoot ||
                canonicalResolved.startsWith(canonicalRoot + File.separator)
            ) {
                resolved
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Read a text file. Null = not found / not a file / escape attempt. */
    fun read(relativePath: String): String? {
        val file = resolve(relativePath) ?: return null
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    /** Write a text file (creates parent directories). False = escape/failure. */
    fun write(relativePath: String, content: String): Boolean {
        val file = resolve(relativePath) ?: return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        }.getOrDefault(false)
    }

    /** Delete a file or empty directory inside the sandbox. */
    fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath) ?: return false
        if (!file.exists()) return false
        // Guard against recursive nuking: directories only if empty.
        if (file.isDirectory && (file.listFiles()?.isNotEmpty() == true)) return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /** List files under a sandbox subdirectory ("" = root), one path per line. */
    fun list(relativePath: String = ""): List<String> {
        val dir = if (relativePath.isBlank()) root else resolve(relativePath) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it != dir }
            .map { it.relativeTo(root).path + if (it.isDirectory) "/" else "" }
            .sorted()
            .toList()
    }
}
