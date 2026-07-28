package com.example.meowmba

import com.example.meowmba.core.bridge.DesktopBridgeProtocol
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the desktop-bridge wire protocol.
 *
 * The signing/base64 vectors below were generated with CPython's hmac/hashlib/base64
 * (the same stdlib the bridge daemon uses), so these tests are an interop contract
 * with bridge/meowmba-bridge.py — if they pass, phone and daemon speak the same wire.
 */
class DesktopBridgeProtocolTest {

    private val secret: ByteArray = ByteArray(32) { it.toByte() } // 0x00..0x1f

    // --------------------------------------------------------------
    // Pairing payload decoding
    // --------------------------------------------------------------

    @Test
    fun `decodes a real daemon pairing payload`() {
        // Exact blob produced by Python: base64.urlsafe_b64encode(json) (padding kept)
        val blob = "eyJ2IjoxLCJob3N0IjoiMTkyLjE2OC4xLjUwIiwicG9ydCI6ODc2NSwibmFtZSI6Ind5YXR0LW1hYyIsInBhaXJfdG9rZW4iOiJ0b2sxMjNhYmMiLCJjZXJ0X3NoYTI1NiI6ImFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWEifQ=="
        val payload = DesktopBridgeProtocol.decodePairingPayload(blob).getOrThrow()
        assertEquals(1, payload.v)
        assertEquals("192.168.1.50", payload.host)
        assertEquals(8765, payload.port)
        assertEquals("wyatt-mac", payload.name)
        assertEquals("tok123abc", payload.pairToken)
        assertEquals("a".repeat(64), payload.certSha256)
    }

    @Test
    fun `decodes payload without padding too`() {
        val blob = "eyJ2IjoxLCJob3N0IjoiMTkyLjE2OC4xLjUwIiwicG9ydCI6ODc2NSwibmFtZSI6Ind5YXR0LW1hYyIsInBhaXJfdG9rZW4iOiJ0b2sxMjNhYmMiLCJjZXJ0X3NoYTI1NiI6ImFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWEifQ=="
        assertTrue(DesktopBridgeProtocol.decodePairingPayload(blob.trimEnd('=')).isSuccess)
    }

    @Test
    fun `rejects empty and non-base64 payloads`() {
        assertTrue(DesktopBridgeProtocol.decodePairingPayload("").isFailure)
        assertTrue(DesktopBridgeProtocol.decodePairingPayload("   ").isFailure)
        assertTrue(DesktopBridgeProtocol.decodePairingPayload("!!! not base64 !!!").isFailure)
    }

    @Test
    fun `rejects non-json and wrong-schema payloads`() {
        val notJson = DesktopBridgeProtocol.b64EncodeStd("hello there".toByteArray())
        assertTrue(DesktopBridgeProtocol.decodePairingPayload(notJson).isFailure)

        val wrongVersion = DesktopBridgeProtocol.b64EncodeStd(
            """{"v":2,"host":"h","port":1,"pair_token":"t","cert_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}""".toByteArray()
        )
        val result = DesktopBridgeProtocol.decodePairingPayload(wrongVersion)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("version"))
    }

    @Test
    fun `rejects malformed fingerprint and bad port`() {
        val badFp = DesktopBridgeProtocol.b64EncodeStd(
            """{"v":1,"host":"h","port":8765,"pair_token":"t","cert_sha256":"xyz"}""".toByteArray()
        )
        assertTrue(DesktopBridgeProtocol.decodePairingPayload(badFp).isFailure)

        val badPort = DesktopBridgeProtocol.b64EncodeStd(
            """{"v":1,"host":"h","port":0,"pair_token":"t","cert_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}""".toByteArray()
        )
        assertTrue(DesktopBridgeProtocol.decodePairingPayload(badPort).isFailure)
    }

    // --------------------------------------------------------------
    // Signing (Python interop vectors)
    // --------------------------------------------------------------

    @Test
    fun `canonical message matches daemon format`() {
        val body = """{"method":"ping"}""".toByteArray()
        val bodyHash = DesktopBridgeProtocol.sha256Hex(body)
        assertEquals("2eb16e31c0182a5bdf2800b4beffb2acf509bde11604bd087bb313702ecddcc9", bodyHash)
        val canon = DesktopBridgeProtocol.canonicalMessage("POST", "/rpc", "1753000000", bodyHash)
        assertEquals(
            "POST\n/rpc\n1753000000\n2eb16e31c0182a5bdf2800b4beffb2acf509bde11604bd087bb313702ecddcc9",
            canon
        )
    }

    @Test
    fun `hmac signature matches CPython hmac output`() {
        val canon = "POST\n/rpc\n1753000000\n2eb16e31c0182a5bdf2800b4beffb2acf509bde11604bd087bb313702ecddcc9"
        val sig = DesktopBridgeProtocol.signHmacSha256Base64(secret, canon)
        // Python: base64.b64encode(hmac.new(bytes(range(32)), canon.encode(), sha256).digest())
        assertEquals("kRqkoZg70P5dJnTuxzJmNDLrmOk1gmjsx1HMaL55q7A=", sig)
    }

    // --------------------------------------------------------------
    // Base64 (Python interop vectors)
    // --------------------------------------------------------------

    @Test
    fun `sha256 known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DesktopBridgeProtocol.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun `base64 standard encoding matches Python b64encode`() {
        assertEquals("YWJj", DesktopBridgeProtocol.b64EncodeStd("abc".toByteArray()))
        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            DesktopBridgeProtocol.b64EncodeStd(secret)
        )
        // 1- and 2-byte remainders exercise the padding branches
        assertEquals("YQ==", DesktopBridgeProtocol.b64EncodeStd("a".toByteArray()))
        assertEquals("YWI=", DesktopBridgeProtocol.b64EncodeStd("ab".toByteArray()))
    }

    @Test
    fun `base64 decode round-trips and accepts both alphabets`() {
        val data = ByteArray(32) { (it * 7 + 1).toByte() }
        val std = DesktopBridgeProtocol.b64EncodeStd(data)
        assertArrayEquals(data, DesktopBridgeProtocol.b64Decode(std))
        assertArrayEquals(data, DesktopBridgeProtocol.b64Decode(std.trimEnd('=')))
        // url-safe alphabet equivalents of std characters
        val url = std.replace('+', '-').replace('/', '_')
        assertArrayEquals(data, DesktopBridgeProtocol.b64Decode(url))
    }

    @Test
    fun `base64 decode rejects invalid input`() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopBridgeProtocol.b64Decode("A") // length % 4 == 1 is impossible
        }
        assertThrows(IllegalArgumentException::class.java) {
            DesktopBridgeProtocol.b64Decode("ab\$%")
        }
    }

    // --------------------------------------------------------------
    // Discovery packets
    // --------------------------------------------------------------

    @Test
    fun `parses a real discovery packet`() {
        val fp = "b".repeat(64)
        val pkt = """{"v":1,"service":"meowmba-bridge","name":"wyatt-mac","port":8765,"cert_sha256":"$fp","version":"0.5.0","pairing_open":true}"""
        val d = DesktopBridgeProtocol.parseDiscoveryPacket(pkt, "192.168.1.50")!!
        assertEquals("192.168.1.50", d.host)
        assertEquals(8765, d.port)
        assertEquals("wyatt-mac", d.name)
        assertEquals(fp, d.certSha256)
        assertEquals("0.5.0", d.version)
        assertTrue(d.pairingOpen)
    }

    @Test
    fun `rejects non-bridge packets and malformed fingerprints`() {
        val fp = "b".repeat(64)
        assertEquals(null, DesktopBridgeProtocol.parseDiscoveryPacket("""{"service":"other"}""", "1.2.3.4"))
        assertEquals(null, DesktopBridgeProtocol.parseDiscoveryPacket("not json", "1.2.3.4"))
        assertEquals(null, DesktopBridgeProtocol.parseDiscoveryPacket(
            """{"service":"meowmba-bridge","port":8765,"cert_sha256":"xyz"}""", "1.2.3.4"))
        assertEquals(null, DesktopBridgeProtocol.parseDiscoveryPacket(
            """{"service":"meowmba-bridge","cert_sha256":"$fp"}""", "1.2.3.4")) // no port
    }

    @Test
    fun `host always comes from the network not the packet`() {
        // A packet can't claim to be another machine — sender IP wins
        val fp = "b".repeat(64)
        val pkt = """{"service":"meowmba-bridge","name":"x","port":8765,"cert_sha256":"$fp","host":"8.8.8.8"}"""
        assertEquals("192.168.1.99", DesktopBridgeProtocol.parseDiscoveryPacket(pkt, "192.168.1.99")!!.host)
    }
}
