package com.example.meowmba

import com.example.meowmba.core.browser.WebViewBridge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the fix for "saved web page is a black/garbage canvas": evaluateJavascript
 * returns a JSON-encoded string, and we must decode the escapes (not just trim the
 * quotes) before writing the page to disk.
 */
class WebViewBridgeTest {
    // bs = a single backslash, q = a double quote (as Kotlin string values).
    val bs = "\\"
    val q = "\""

    @Test
    fun `decodes a JSON-encoded html string with escapes`() {
        val raw = q + "<html>" + bs + "n<body class=" + bs + q + "x" + bs + q + ">hi</body></html>" + q
        val expected = "<html>\n<body class=" + q + "x" + q + ">hi</body></html>"
        assertEquals(expected, WebViewBridge.decodeJsValue(raw))
    }

    @Test
    fun `decodes unicode escapes`() {
        assertEquals("cat", WebViewBridge.decodeJsValue(q + bs + "u0063at" + q))
    }

    @Test
    fun `non-string values pass through`() {
        assertEquals("42", WebViewBridge.decodeJsValue("42"))
        assertEquals("null", WebViewBridge.decodeJsValue("null"))
        assertEquals("null", WebViewBridge.decodeJsValue(null))
    }

    @Test
    fun `plain trimmed string without quotes is untouched`() {
        assertEquals("clicked: a", WebViewBridge.decodeJsValue("clicked: a"))
    }
}
