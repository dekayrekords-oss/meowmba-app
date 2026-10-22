package com.example.meowmba.core.robot.cozmo

import java.io.ByteArrayOutputStream

/** Bounded reassembly: a missing, repeated, oversized or mixed frame is discarded, never displayed. */
class CozmoCamera {
    private var imageId = -1
    private var nextChunk = 0
    private var chunks = 0
    private var encoding = 0
    private var resolution = 0
    private val data = ByteArrayOutputStream()
    fun reset() { imageId = -1; nextChunk = 0; data.reset() }
    fun accept(packet: ByteArray): ByteArray? = runCatching {
        require(packet.size >= 20)
        val b = CozmoProtocol.reader(packet)
        val id = b.getInt(4)
        val enc = packet[12].toInt() and 255
        val res = packet[13].toInt() and 255
        val count = packet[14].toInt() and 255
        val chunk = packet[15].toInt() and 255
        val length = b.getShort(18).toInt() and 65535
        require(count in 1..128 && chunk < count && length == packet.size - 20 && res == 4)
        if (chunk == 0) {
            reset(); imageId = id; chunks = count; encoding = enc; resolution = res
        }
        require(id == imageId && chunk == nextChunk && count == chunks && enc == encoding && res == resolution)
        require(data.size() + length <= 128_000)
        data.write(packet, 20, length)
        nextChunk++
        if (nextChunk != chunks) return null
        val raw = data.toByteArray()
        reset()
        when (enc) {
            5 -> raw.takeIf { it.size >= 4 && it[0] == 0xff.toByte() && it[1] == 0xd8.toByte() }
            8 -> miniGray(raw)
            else -> null
        }
    }.getOrElse { reset(); null }

    companion object {
        // Standard Cozmo grayscale JPEG tables, from PyCozmo camera.py (MIT; notice in assets/licenses).
        private const val HEADER = "ffd8ffe000104a46494600010100000100010000ffdb004300100b0c0e0c0a100e0d0e1211101318281a181616183123251d283a333d3c3933383740485c4e404457453738506d51575f626768673e4d71797064785c656763ffc0000b080128019001011100ffc400d20000010501010101010100000000000000000102030405060708090a0b100002010303020403050504040000017d01020300041105122131410613516107227114328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a434445464748494a535455565758595a636465666768696a737475767778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffda0008010100003f00"
        internal fun miniGray(raw: ByteArray): ByteArray? {
            if (raw.size < 2 || raw[0].toInt() != 0) return null
            val header = HEADER.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            header[94] = 0; header[95] = 240.toByte(); header[96] = 1; header[97] = 64
            var end = raw.size
            while (end > 1 && raw[end - 1] == 0xff.toByte()) end--
            if (end <= 1) return null
            return ByteArrayOutputStream().apply {
                write(header)
                for (i in 1 until end) { write(raw[i].toInt() and 255); if (raw[i] == 0xff.toByte()) write(0) }
                write(255); write(217)
            }.toByteArray()
        }
    }
}
