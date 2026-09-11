package com.example.meowmba.ui.companion

import java.io.File

/**
 * Local (app-sandbox) shell helpers for [CompanionTerminalSession].
 *
 * Android's piped `/system/bin/sh` often has an empty or SELinux-hostile PATH,
 * so plain `ls` fails with "inaccessible or not found". We:
 * 1. Export a sane PATH on connect (caller).
 * 2. Rewrite simple commands to absolute `/system/bin/<cmd>` or `toybox <cmd>`.
 * 3. Fall back to the Java File API for `ls` / `pwd` so the sandbox always works.
 */
internal object LocalShellBuiltins {
    private val SIMPLE_CMDS = setOf(
        "ls", "pwd", "cat", "echo", "clear", "id", "whoami", "uname",
        "date", "df", "du", "ps", "mkdir", "touch", "rm", "cp", "mv",
        "chmod", "stat", "head", "tail", "wc", "grep", "which", "env",
        "printenv", "hostname", "getprop",
    )

    private val BIN_DIRS = listOf(
        "/system/bin",
        "/system/xbin",
        "/vendor/bin",
        "/product/bin",
    )

    /** Preferred PATH prefix for local persistent sh (exported on connect). */
    fun preferredPathPrefix(): String =
        BIN_DIRS.joinToString(":")

    fun mergePreferredPath(existing: String?): String {
        val parts = LinkedHashSet<String>()
        BIN_DIRS.forEach { parts.add(it) }
        // Termux bins when present (optional).
        parts.add("/data/data/com.termux/files/usr/bin")
        parts.add("/data/data/com.termux/files/usr/bin/applets")
        existing?.split(':')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { parts.add(it) }
        return parts.joinToString(":")
    }

    fun connectExports(home: File): String {
        val path = mergePreferredPath(null)
        val homePath = home.absolutePath
        return buildString {
            append("export PATH=").append(shellEscape(path)).append('\n')
            append("export HOME=").append(shellEscape(homePath)).append('\n')
            append("cd ").append(shellEscape(homePath)).append(" 2>/dev/null || true\n")
        }
    }

    fun bannerLine(cwd: File): String =
        "cwd ${cwd.absolutePath} — Local is app sandbox — use SSH profile for meow\n"

    /**
     * Handle `ls` / `pwd` entirely in-process when the toybox/system binary is
     * missing or inaccessible. Returns non-null output (including trailing \n)
     * when handled; null means "send to the real shell (possibly rewritten)".
     */
    fun tryBuiltin(rawLine: String, cwd: File): String? {
        val line = rawLine.trimEnd('\n', '\r').trim()
        if (line.isEmpty()) return null
        // Only plain commands — no pipes/redirs/subshells.
        if (line.any { it == '|' || it == '>' || it == '<' || it == ';' || it == '&' || it == '`' }) {
            return null
        }
        val tokens = tokenize(line) ?: return null
        if (tokens.isEmpty()) return null
        val cmd = tokens[0]
        val args = tokens.drop(1)

        when (cmd) {
            "pwd" -> {
                if (args.isNotEmpty()) return null
                return cwd.absolutePath + "\n"
            }
            "ls" -> {
                // Prefer Java listing for plain ls / ls -a so sandbox always works.
                val flags = args.filter { it.startsWith('-') }
                val paths = args.filter { !it.startsWith('-') }
                // Leave complex flag sets to real ls/toybox after rewrite.
                val simpleFlags = flags.all { f -> f.removePrefix("-").all { ch -> ch == 'a' } }
                if (!simpleFlags) return null
                val showHidden = flags.any { it.contains('a') }
                val targets = if (paths.isEmpty()) listOf(cwd) else paths.map { resolvePath(cwd, it) }
                return buildString {
                    targets.forEachIndexed { idx, dir ->
                        if (targets.size > 1) {
                            if (idx > 0) append('\n')
                            append(dir.absolutePath).append(":\n")
                        }
                        append(listDir(dir, showHidden))
                    }
                }
            }
            else -> return null
        }
    }

    /**
     * Rewrite a simple command line so the shell invokes an absolute binary
     * or `toybox <cmd>` when PATH lookup would fail.
     */
    fun resolveSimpleCommand(rawLine: String): String {
        val trimmed = rawLine.trimEnd('\n', '\r')
        val line = trimmed.trim()
        if (line.isEmpty()) return trimmed
        if (line.startsWith('/') || line.startsWith('.') || line.startsWith("toybox")) return trimmed
        if (line.any { it == '|' || it == '>' || it == '<' || it == ';' || it == '&' || it == '`' }) {
            return trimmed
        }
        val tokens = tokenize(line) ?: return trimmed
        if (tokens.isEmpty()) return trimmed
        val cmd = tokens[0]
        if (cmd.contains('/')) return trimmed
        if (cmd !in SIMPLE_CMDS) return trimmed

        val abs = findBinary(cmd)
        val rewritten = if (abs != null) {
            listOf(abs) + tokens.drop(1)
        } else if (File("/system/bin/toybox").exists()) {
            listOf("/system/bin/toybox", cmd) + tokens.drop(1)
        } else {
            return trimmed
        }
        return rewritten.joinToString(" ") { shellEscape(it) }
    }

    fun findBinary(cmd: String): String? {
        for (dir in BIN_DIRS) {
            val f = File(dir, cmd)
            if (f.exists() && f.canExecute()) return f.absolutePath
        }
        for (dir in BIN_DIRS) {
            val f = File(dir, cmd)
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    private fun listDir(dir: File, showHidden: Boolean): String {
        if (!dir.exists()) return "ls: ${dir.absolutePath}: No such file or directory\n"
        if (!dir.isDirectory) return dir.name + "\n"
        if (!dir.canRead()) return "ls: ${dir.absolutePath}: Permission denied\n"
        val names = dir.listFiles()
            ?.map { it.name }
            ?.filter { showHidden || !it.startsWith('.') }
            ?.sorted()
            ?: return "ls: ${dir.absolutePath}: Permission denied\n"
        if (names.isEmpty()) return ""
        return names.joinToString("\n", postfix = "\n")
    }

    private fun resolvePath(cwd: File, raw: String): File {
        val f = File(raw)
        return if (f.isAbsolute) f else File(cwd, raw)
    }

    /** Minimal tokenizer: whitespace split, respects simple single/double quotes. */
    private fun tokenize(line: String): List<String>? {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        var i = 0
        var quote: Char? = null
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null -> {
                    if (c == quote) quote = null
                    else buf.append(c)
                }
                c == '\'' || c == '"' -> quote = c
                c.isWhitespace() -> {
                    if (buf.isNotEmpty()) {
                        out.add(buf.toString())
                        buf.clear()
                    }
                }
                else -> buf.append(c)
            }
            i++
        }
        if (quote != null) return null
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    private fun shellEscape(raw: String): String {
        if (raw.isEmpty()) return "''"
        if (raw.all { it.isLetterOrDigit() || it == '/' || it == '_' || it == '-' || it == '.' || it == ':' }) {
            return raw
        }
        return "'" + raw.replace("'", "'\"'\"'") + "'"
    }
}
