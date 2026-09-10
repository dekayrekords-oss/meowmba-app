package com.example.meowmba

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.meowmba.core.access.MeowAccessibilityService
import com.example.meowmba.core.agent.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneControlDispatchTest {
    private fun node(label: String, edit: Boolean = false): AccessibilityNodeInfo = mock(AccessibilityNodeInfo::class.java).also {
        `when`(it.isVisibleToUser).thenReturn(true); `when`(it.isEnabled).thenReturn(true)
        `when`(it.isEditable).thenReturn(edit); `when`(it.isClickable).thenReturn(!edit)
        `when`(it.text).thenReturn(if (edit) "" else label)
        `when`(it.hintText).thenReturn(if (edit) label else null)
        `when`(it.viewIdResourceName).thenReturn(if (edit) "com.search:id/query" else null)
        `when`(it.refresh()).thenReturn(true)
    }
    private fun service(nodes: List<AccessibilityNodeInfo>): MeowAccessibilityService {
        val svc = mock(MeowAccessibilityService::class.java, CALLS_REAL_METHODS)
        val root = mock(AccessibilityNodeInfo::class.java)
        doReturn(root).`when`(svc).rootInActiveWindow
        `when`(root.packageName).thenReturn("com.search"); `when`(root.childCount).thenReturn(nodes.size)
        nodes.forEachIndexed { i, n -> `when`(root.getChild(i)).thenReturn(n) }
        val pm = mock(PackageManager::class.java); doReturn(pm).`when`(svc).packageManager
        `when`(pm.getPackageInfo("com.search", 0)).thenReturn(PackageInfo().apply { setLongVersionCode(1) })
        return svc
    }
    @Test fun moreThanFiftyControlsDoNotProduceAnIncompleteIdentityMatch() {
        val nodes = (1..60).map { node(if (it in setOf(1, 60)) "Search" else "Control $it") }
        val snapshot = service(nodes).observeForAgent()!!
        assertEquals(60, snapshot.controls.size)
        assertNull(snapshot.find(PhoneSelector(label = "Search")))
    }
    @Test fun snapshotsBeyondTheControlBudgetAreRejectedInsteadOfTruncated() {
        assertNull(service((1..201).map { node("Control $it") }).observeForAgent())
    }
    @Test fun changingHintsChangesTheFingerprintEvenWhenVisibleValueIsUnchanged() {
        val edit = node("Search", true); val svc = service(listOf(edit))
        val before = svc.observeForAgent()!!
        `when`(edit.hintText).thenReturn("Message")
        assertFalse(before.sameControls(svc.observeForAgent()!!))
    }
    @Test fun dispatchUsesTheObservedNodeAndChecksItsRefreshedSemantics() = runTest {
        val edit = node("Search", true); val svc = service(listOf(edit))
        val before = svc.observeForAgent()!!
        `when`(edit.refresh()).thenAnswer { `when`(edit.hintText).thenReturn("Message"); true }
        val result = svc.performAgentControl(PhoneAction("type", index = 1, value = "birds"), before)
        assertTrue(result.startsWith("Control changed"))
        verify(edit, never()).performAction(anyInt(), any(Bundle::class.java))
    }
    @Test fun trustedNativeTypingExecutesExactlyOnceOnTheMatchingNode() = runTest {
        val edit = node("Search", true); val svc = service(listOf(edit))
        `when`(edit.performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any(Bundle::class.java))).thenReturn(true)
        val result = svc.performAgentControl(PhoneAction("type", selector = PhoneSelector(viewId = "com.search:id/query", role = "edit"), value = "birds"), svc.observeForAgent()!!)
        assertTrue(result.startsWith("Typed into"))
        verify(edit, times(1)).performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any(Bundle::class.java))
    }
    @Test fun actualSendLabelRequiresHandoffBeforeAnyClick() = runTest {
        val send = node("Send"); val svc = service(listOf(send))
        val result = svc.performAgentControl(PhoneAction("tap_index", index = 1), svc.observeForAgent()!!)
        assertTrue(result.startsWith("Handoff required:"))
        verify(send, never()).performAction(anyInt())
    }
    @Test fun searchWordsInsideDraftContentDoNotAuthorizeOverwritingTheDraft() {
        val note = node("", true)
        `when`(note.hintText).thenReturn(null); `when`(note.viewIdResourceName).thenReturn("com.search:id/draft")
        `when`(note.text).thenReturn("Search birds later")
        val frame = service(listOf(note)).observeForAgent()!!
        assertNull(PhoneVerification.ground(PhoneAction("type", index = 1, value = "overwrite"), frame))
    }
}
