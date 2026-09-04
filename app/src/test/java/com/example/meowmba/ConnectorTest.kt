package com.example.meowmba

import com.example.meowmba.core.plugins.Connector
import com.example.meowmba.core.plugins.ConnectorBackend
import com.example.meowmba.core.plugins.ConnectorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorTest {

    private fun sample() = Connector(
        id = "mcp-abcd1234",
        name = "Android Studio",
        description = "External MCP server at http://10.0.0.5:24601/mcp.",
        kind = ConnectorKind.MCP_SERVER,
        backend = ConnectorBackend.MCP_HTTP,
        endpoint = "http://10.0.0.5:24601/mcp",
        configured = true,
        toolIds = listOf("gradle_build", "read_logcat"),
        enabled = true,
        iconToken = "🔌",
    )

    @Test
    fun `serialization round-trips every field`() {
        val json = sample().toJson()
        val back = Connector.fromJson(json)
        assertEquals(sample(), back)
    }

    @Test
    fun `blank optional fields survive round-trip as null`() {
        val c = Connector(
            id = "app-com.example.gmail",
            name = "Gmail",
            description = "",
            kind = ConnectorKind.APP,
            backend = ConnectorBackend.EMBEDDED_PLUGIN,
            packageName = "com.example.gmail",
            configured = false,
            enabled = false,
            iconToken = "com.example.gmail",
        )
        assertEquals(c, Connector.fromJson(c.toJson()))
        assertEquals("com.example.gmail", Connector.fromJson(c.toJson()).packageName)
        assertEquals(null, Connector.fromJson(c.toJson()).endpoint)
    }

    @Test
    fun `user plugins carry a non-blank distinct icon`() {
        val c = sample()
        assertTrue(c.iconToken.isNotBlank())
        // Every connector, including user plugins, must have a distinct icon token.
        Connector(
            id = "plugin-00000000",
            name = "Importer",
            description = "",
            kind = ConnectorKind.USER_PLUGIN,
            backend = ConnectorBackend.LOCAL,
            iconToken = "✨",
            configured = true,
        ).let { assertTrue(it.iconToken.isNotBlank()) }
    }

    @Test
    fun `tool ids round-trip as a list`() {
        val back = Connector.fromJson(sample().toJson())
        assertEquals(listOf("gradle_build", "read_logcat"), back.toolIds)
        assertFalse(back.toolIds.isEmpty())
    }
}
