package com.example.meowmba.ui.companion

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Bounded before decoding; uses APIs available on every supported Android release. */
internal object CreationPhotoInput {
    fun read(input: InputStream, limit: Int = 12 * 1024 * 1024): ByteArray {
        require(limit > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(output.size().toLong() + count <= limit) { "Choose a photo smaller than 12 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
