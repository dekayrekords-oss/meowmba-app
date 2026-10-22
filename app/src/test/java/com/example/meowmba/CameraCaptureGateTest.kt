package com.example.meowmba

import com.example.meowmba.core.vision.CameraCaptureGate
import org.junit.Assert.*
import org.junit.Test

class CameraCaptureGateTest {
    @Test fun freshManagerDoesNotResumeWithoutExplicitConsent() {
        val gate = CameraCaptureGate()
        assertNull(gate.captureToken())
        assertNull(gate.nextBinding())
        gate.setEnabled(false)
        assertNull(gate.nextBinding())
    }
    @Test fun offRejectsQueuedBindAndInFlightCaptureEvenAfterReenable() {
        val gate = CameraCaptureGate()
        gate.setEnabled(true)
        val capture = gate.captureToken()!!
        val binding = gate.nextBinding()!!
        gate.setEnabled(false)
        assertFalse(gate.acceptsCapture(capture))
        assertFalse(gate.acceptsBinding(binding))
        gate.setEnabled(true)
        assertFalse(gate.acceptsCapture(capture))
        assertFalse(gate.acceptsBinding(binding))
        assertTrue(gate.acceptsCapture(gate.captureToken()!!))
    }
    @Test fun replacementBindMakesOldCallbackAndOldUnbindStale() {
        val gate = CameraCaptureGate()
        gate.setEnabled(true)
        val first = gate.nextBinding()!!
        val teardown = gate.invalidateBindings()
        assertFalse(gate.acceptsBinding(first))
        assertTrue(gate.currentBinding(teardown))
        val replacement = gate.nextBinding()!!
        assertFalse(gate.currentBinding(teardown))
        assertTrue(gate.acceptsBinding(replacement))
    }
    @Test fun temporaryUnbindPreservesConsentForAuthorizedRetry() {
        val gate = CameraCaptureGate()
        gate.setEnabled(true)
        val capture = gate.captureToken()!!
        gate.invalidateBindings()
        assertTrue(gate.acceptsCapture(capture))
        assertTrue(gate.acceptsBinding(gate.nextBinding()!!))
    }
    @Test fun repeatedPreferenceSyncDoesNotCancelCurrentCapture() {
        val gate = CameraCaptureGate()
        gate.setEnabled(true)
        val capture = gate.captureToken()!!
        val binding = gate.nextBinding()!!
        gate.setEnabled(true)
        assertTrue(gate.acceptsCapture(capture))
        assertTrue(gate.acceptsBinding(binding))
    }
}
