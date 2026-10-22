package com.example.meowmba.core.agent

import java.io.File
import java.io.InputStream

/** Imports into staging first: a failed picker read must never destroy a working model. */
object LocalModelImport {
    const val MAX_BYTES = 11L * 1_073_741_824L

    fun copy(input: InputStream, directory: File, fileName: String, expectedBytes: Long? = null): File {
        require(ModelCatalog.isValidModelFileName(fileName)) { "Choose a .task, .litertlm or .cact model file" }
        require(expectedBytes == null || expectedBytes in 1..MAX_BYTES) { "The model is empty or exceeds the 11 GB import limit" }
        directory.mkdirs()
        val destination = File(directory, fileName)
        require(!destination.exists()) { "A model named $fileName is already installed. Use that copy, or rename your file before importing." }
        val staging = File.createTempFile("model-import-", ".part", directory)
        try {
            var count = 0L
            staging.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    count += read
                    check(count <= MAX_BYTES) { "The model exceeds the 11 GB import limit" }
                    out.write(buffer, 0, read)
                }
            }
            check(count > 0 && (expectedBytes == null || expectedBytes == count)) { "The model file was empty or incomplete" }
            // Refuse a same-name file that appeared during the copy.
            return java.nio.file.Files.move(staging.toPath(), destination.toPath()).toFile()
        } finally {
            staging.delete()
        }
    }
}
