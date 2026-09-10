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
        .newOutput(projectFolder()?.let { "$it/$folder" } ?: folder, name)

    private fun projectFolder(): String? = com.example.meowmba.core.work.ProjectExecutionContext.folder(activeProjectFolder)

    /**
     * Relative reads must round-trip project-scoped writes. A full projects/…
     * path or leading / explicitly addresses the workspace root, including a
     * reference in another project. Otherwise prefer an existing project file,
     * then retain access to existing shared library references at their paths.
     */
    fun resolveRead(relativePath: String): File? {
        val path = relativePath.trim()
        val folder = projectFolder()
        if (folder != null && path.isNotBlank() && !path.startsWith('/') &&
            !path.startsWith("projects/") && !path.contains('\\') && path.split('/').none { it == ".." }) {
            val local = runCatching {
                com.example.meowmba.core.files.AssetLibrary(root).resolve("$folder/$path")
            }.getOrNull()
            if (local?.isFile == true) return local
        }
        return resolve(path)
    }

    /** Preserve the Files browser's bounds and hidden/symlink exclusions. */
    fun scanFiles(limit: Int = 5000): com.example.meowmba.core.files.AssetScan {
        val library = com.example.meowmba.core.files.AssetLibrary(root)
        val folder = projectFolder() ?: return library.scan(limit)
        val dir = library.resolve(folder)
        if (!dir.isDirectory) return com.example.meowmba.core.files.AssetScan(emptyList(), false)
        val scan = com.example.meowmba.core.files.AssetLibrary(dir).scan(limit)
        return scan.copy(entries = scan.entries.map { it.copy(path = "$folder/${it.path}") })
    }

    /** New text outputs live with the captured project; explicit other project paths are rejected. */
    fun resolveWrite(relativePath: String): File? {
        val folder = projectFolder() ?: return resolve(relativePath)
        val path = relativePath.trim()
        if (path.isBlank() || path.startsWith('/') || path.contains('\\') ||
            path.split('/').any { it == ".." }) return null
        if (path.startsWith("projects/") && !path.startsWith("$folder/")) return null
        return resolve(if (path.startsWith("$folder/")) path else "$folder/$path")
    }

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
        val file = resolveRead(relativePath) ?: return null
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    /** Write a text file (creates parent directories). False = escape/failure. */
    fun write(relativePath: String, content: String): Boolean {
        val file = resolveWrite(relativePath) ?: return false
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
