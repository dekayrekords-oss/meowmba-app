package com.example.meowmba

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import com.example.meowmba.core.access.MeowAccessibilityService
import com.example.meowmba.core.agent.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneSemanticSnapshotTest {
    @Test fun realSnapshotRetainsSemanticIdentityAndRedactsProtectedFields() {
        val svc = mock(MeowAccessibilityService::class.java, CALLS_REAL_METHODS)
        val root = mock(AccessibilityNodeInfo::class.java)
        val edit = mock(AccessibilityNodeInfo::class.java)
        val password = mock(AccessibilityNodeInfo::class.java)
        val result = mock(AccessibilityNodeInfo::class.java)
        doReturn(root).`when`(svc).rootInActiveWindow
        `when`(root.packageName).thenReturn("com.search")
        `when`(root.childCount).thenReturn(3)
        `when`(root.getChild(0)).thenReturn(edit); `when`(root.getChild(1)).thenReturn(password); `when`(root.getChild(2)).thenReturn(result)
        for (node in listOf(edit, password, result)) { `when`(node.isVisibleToUser).thenReturn(true); `when`(node.isEnabled).thenReturn(true) }
        `when`(edit.isEditable).thenReturn(true); `when`(edit.hintText).thenReturn("Search")
        `when`(edit.viewIdResourceName).thenReturn("com.search:id/query"); `when`(edit.text).thenReturn("birds")
        `when`(password.isEditable).thenReturn(true); `when`(password.isPassword).thenReturn(true); `when`(password.text).thenReturn("secret123")
        `when`(result.text).thenReturn("Search results")
        val pm = mock(PackageManager::class.java)
        doReturn(pm).`when`(svc).packageManager
        val info = PackageInfo().apply { setLongVersionCode(42) }
        `when`(pm.getPackageInfo("com.search", 0)).thenReturn(info)
        val frame = svc.observeForAgent()!!
        assertEquals(42L, frame.version)
        assertEquals("birds", frame.find(PhoneSelector(viewId = "com.search:id/query", role = "edit"))?.value)
        assertEquals(listOf("Search results"), frame.visibleText)
        assertFalse(frame.prompt.contains("secret123"))
        assertFalse(frame.controls.any { it.value == "secret123" || it.label == "secret123" })
        assertNull(frame.find(PhoneSelector(role = "edit", label = "secret123")))
    }
}
