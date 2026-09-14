package com.example.meowmba.neural

import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.bridge.DesktopBridgeProtocol
import com.example.meowmba.core.neural.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MaleCnsServerManagerTest {
    private fun credentials(host: String, certificate: String = "a".repeat(64), name: String = host) =
        DesktopBridgeManager.PairedCredentials(DesktopBridgeManager.PairedBridge(host, 8765, name, certificate),
            DesktopBridgeProtocol.b64EncodeStd(ByteArray(32) { 7 }))

    private fun payload(host: String, certificate: String = "b".repeat(64)) =
        DesktopBridgeProtocol.b64EncodeStd(buildJsonObject {
            put("v", 1); put("host", host); put("port", 8765); put("name", host)
            put("pair_token", "unit-test-code"); put("cert_sha256", certificate)
        }.toString().toByteArray())

    private class Backend(var computer: DesktopBridgeManager.PairedCredentials?) : MaleCnsServerBackend {
        var pairs = 0
        var failPair = false
        var failureMessage = "Pairing rejected"
        var failProbe = false
        var probeFailuresRemaining = 0
        var probeFailureMessage = "Server unavailable"
        var cancelProbe = false
        var responseGraph = MaleCnsGraphProfile.FULL
        val steps = mutableListOf<Pair<String, MaleCnsRequest>>()
        val stops = mutableListOf<Pair<String, String>>()
        override fun pairedComputer() = computer
        override suspend fun pair(payload: DesktopBridgeProtocol.PairingPayload, phoneName: String): Result<DesktopBridgeManager.PairedCredentials> {
            pairs++
            return if (failPair) Result.failure(IllegalStateException(failureMessage)) else Result.success(
                DesktopBridgeManager.PairedCredentials(DesktopBridgeManager.PairedBridge(payload.host,
                    payload.port, payload.name, payload.certSha256), DesktopBridgeProtocol.b64EncodeStd(ByteArray(32) { 5 })))
        }
        override fun transport(credentials: DesktopBridgeManager.PairedCredentials) = object : MaleCnsTransport {
            override val description = credentials.bridge.name
            override suspend fun step(request: MaleCnsRequest): Result<MaleCnsSnapshot> {
                steps += credentials.bridge.host to request
                if (cancelProbe) throw CancellationException("Test cancellation")
                val failed = failProbe || probeFailuresRemaining > 0
                if (probeFailuresRemaining > 0) probeFailuresRemaining--
                return if (failed) Result.failure(IllegalStateException(probeFailureMessage)) else Result.success(
                    MaleCnsSnapshot(50f, 150f, 100, 176322, 900, 1f, 30f,
                        0f, 0f, 0f, 0f, 0f, 0f, 0f, responseGraph, 0f, 0f, 0f, 0f, 0f, 0f))
            }
            override suspend fun stop(session: String) { stops += credentials.bridge.host to session }
        }
    }

    private class Fixture(val backend: Backend) {
        var saved: String? = null
        var rejectSave = false
        val manager = MaleCnsServerManager({ saved }, {
            if (rejectSave) error("Secure storage unavailable")
            saved = it
        }, backend)
    }

    @Test fun defaultUsesExistingPairedComputerWithoutWritingOrPairing() = runBlocking {
        val computer = credentials("meow.example", name = "Meow")
        val f = Fixture(Backend(computer))
        assertEquals(MaleCnsServerSelection(false, "Meow", "meow.example:8765", true), f.manager.selection())
        assertTrue(f.manager.testConnection().isSuccess)
        assertEquals("meow.example", f.backend.steps.single().first)
        assertEquals(MaleCnsGraphProfile.FULL, f.backend.steps.single().second.graph)
        assertEquals(f.backend.steps.single().second.session, f.backend.stops.single().second)
        assertNull(f.saved); assertEquals(0, f.backend.pairs); assertSame(computer, f.backend.computer)
    }

    @Test fun customHostRequiresFullProbeAndPreservesDesktopPairing() = runBlocking {
        val computer = credentials("meow.example")
        val f = Fixture(Backend(computer))
        assertTrue(f.manager.pairServer(payload("fly.home.arpa"), "Phone").isSuccess)
        assertEquals(MaleCnsServerSelection(true, "fly.home.arpa", "fly.home.arpa:8765", true), f.manager.selection())
        assertSame(computer, f.backend.computer)
        val request = f.backend.steps.single().second
        assertTrue(listOf(request.food, request.touch, request.loomLeft, request.loomRight,
            request.motionLeft, request.motionRight).all { it == 0f })
        assertEquals(MaleCnsGraphProfile.FULL, request.graph)
        assertEquals("fly.home.arpa" to request.session, f.backend.stops.single())
        assertFalse(f.manager.selection().toString().contains("unit-test-code"))
        assertFalse(computer.toString().contains(computer.secretB64))
    }

    @Test fun failedProbeNeverReplacesAWorkingSelectionAndAlwaysStops() = runBlocking {
        val f = Fixture(Backend(credentials("meow.example")))
        assertTrue(f.manager.pairServer(payload("first.example"), "Phone").isSuccess)
        val before = f.saved
        f.backend.failProbe = true
        assertTrue(f.manager.pairServer(payload("second.example", "c".repeat(64)), "Phone").isFailure)
        assertEquals(before, f.saved)
        assertEquals("first.example", f.manager.selection().serverName)
        assertEquals("second.example", f.backend.stops.last().first)
    }

    @Test fun retainedGraphAndFailedAuthenticationDoNotSave() = runBlocking {
        val f = Fixture(Backend(null))
        f.backend.failPair = true
        assertTrue(f.manager.pairServer(payload("other.example"), "Phone").isFailure)
        assertTrue(f.backend.steps.isEmpty()); assertNull(f.saved)
        f.backend.failPair = false
        f.backend.responseGraph = MaleCnsGraphProfile.RETAINED
        assertTrue(f.manager.pairServer(payload("other.example"), "Phone").isFailure)
        assertNull(f.saved); assertEquals(1, f.backend.stops.size)
    }

    @Test fun aKnownCertificateReusesPairingEvenWhenServerAddressChanges() = runBlocking {
        val computer = credentials("old.example")
        val f = Fixture(Backend(computer))
        assertTrue(f.manager.pairServer(payload("new.example", computer.bridge.certSha256), "Phone").isSuccess)
        assertEquals(0, f.backend.pairs)
        assertEquals("new.example", f.backend.steps.single().first)
        assertEquals("old.example", f.backend.computer!!.bridge.host)
    }

    @Test fun returningToPairedComputerKeepsSavedCustomCredentials() = runBlocking {
        val f = Fixture(Backend(credentials("meow.example")))
        assertTrue(f.manager.pairServer(payload("custom.example"), "Phone").isSuccess)
        assertTrue(f.manager.usePairedComputer().isSuccess)
        assertFalse(f.manager.selection().useDedicated)
        assertEquals("meow.example", f.manager.selection().serverName)
        assertTrue(f.manager.pairServer(payload("custom.example"), "Phone").isSuccess)
        assertEquals(1, f.backend.pairs)
    }

    @Test fun changingServersCannotRerouteAnOldTransportOrItsStop() = runBlocking {
        val f = Fixture(Backend(credentials("meow.example")))
        val oldTransport = f.manager.createTransport()
        assertTrue(f.manager.pairServer(payload("custom.example"), "Phone").isSuccess)
        val request = MaleCnsRequest("old-session", 1, 0f, 0f, 0f, 0f, 0f, 0f, MaleCnsGraphProfile.FULL)
        oldTransport.step(request); oldTransport.stop(request.session)
        assertEquals("meow.example", f.backend.steps.last().first)
        assertEquals("meow.example" to request.session, f.backend.stops.last())
        assertEquals("custom.example", f.manager.createTransport().description)
    }

    @Test fun corruptDedicatedRecordFailsClosedInsteadOfUsingDesktop() = runBlocking {
        val f = Fixture(Backend(credentials("meow.example")))
        f.saved = "invalid record"
        assertTrue(f.manager.selection().useDedicated); assertFalse(f.manager.selection().paired)
        assertTrue(f.manager.testConnection().isFailure)
        assertTrue(f.manager.createTransport().step(MaleCnsRequest("session", 1, 0f, 0f, 0f, 0f, 0f, 0f)).isFailure)
        assertTrue(f.backend.steps.isEmpty())
    }

    @Test fun unsupportedHostSyntaxFailsBeforeAnyNetworkOperation() = runBlocking {
        val f = Fixture(Backend(null))
        for (host in listOf("https://host.example", "user@host.example", "host.example/path", "host.example?x=1")) {
            assertTrue(f.manager.pairServer(payload(host), "Phone").isFailure)
        }
        assertEquals(0, f.backend.pairs); assertTrue(f.backend.steps.isEmpty())
    }

    @Test fun cancellationStopsProbeWithoutSavingOrConvertingCancellationToFailure() = runBlocking {
        val f = Fixture(Backend(null))
        f.backend.cancelProbe = true
        try {
            f.manager.pairServer(payload("other.example"), "Phone")
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertNull(f.saved); assertEquals(1, f.backend.stops.size)
    }

    @Test fun secureSaveFailureDoesNotClaimSuccess() = runBlocking {
        val f = Fixture(Backend(null)); f.rejectSave = true
        assertTrue(f.manager.pairServer(payload("other.example"), "Phone").isFailure)
        assertNull(f.saved); assertFalse(f.manager.selection().paired)
    }

    @Test fun missingDesktopPairingReturnsSetupGuidanceWithoutNetwork() = runBlocking {
        val f = Fixture(Backend(null))
        assertTrue(f.manager.usePairedComputer().isSuccess)
        assertFalse(f.manager.selection().paired)
        assertTrue(f.manager.testConnection().isFailure)
        assertTrue(f.backend.steps.isEmpty())
    }
    @Test fun rawServerErrorsNeverExposeCredentialsOrPayloadsInUi() = runBlocking {
        val f = Fixture(Backend(null)); f.backend.failPair = true
        f.backend.failureMessage = "Remote echo: unit-test-code secret=private-secret"
        val error = f.manager.pairServer(payload("other.example"), "Phone").exceptionOrNull()!!
        assertFalse(error.message!!.contains("unit-test-code"))
        assertFalse(error.message!!.contains("private-secret"))
        assertNull(error.cause)
    }

    @Test fun corruptDesktopCredentialsAreUnavailableWithoutAClientCrash() = runBlocking {
        val bad = DesktopBridgeManager.PairedCredentials(
            DesktopBridgeManager.PairedBridge("https://invalid.example", 8765, "Old computer", "a".repeat(64)), "broken")
        val f = Fixture(Backend(bad))
        assertFalse(f.manager.selection().paired)
        assertTrue(f.manager.testConnection().isFailure)
        assertTrue(f.backend.steps.isEmpty())
    }

    @Test fun forgottenCustomOnlyPairingCanUseTheFreshExplicitCode() = runBlocking {
        val f = Fixture(Backend(credentials("meow.example")))
        assertTrue(f.manager.pairServer(payload("custom.example"), "Phone").isSuccess)
        f.backend.probeFailuresRemaining = 1
        f.backend.probeFailureMessage = "desktop forgot the pairing — re-pair"
        assertTrue(f.manager.pairServer(payload("custom.example"), "Phone").isSuccess)
        assertEquals(2, f.backend.pairs)
        assertEquals("custom.example", f.manager.selection().serverName)
    }

    @Test fun rejectedDesktopIdentityNeverRotatesItsCredentialsInNeuralSetup() = runBlocking {
        val computer = credentials("meow.example")
        val f = Fixture(Backend(computer))
        f.backend.failProbe = true
        f.backend.probeFailureMessage = "signature rejected — clocks out of sync or secret mismatch"
        assertTrue(f.manager.pairServer(payload("new.example", computer.bridge.certSha256), "Phone").isFailure)
        assertEquals(0, f.backend.pairs); assertSame(computer, f.backend.computer); assertNull(f.saved)
    }

}
