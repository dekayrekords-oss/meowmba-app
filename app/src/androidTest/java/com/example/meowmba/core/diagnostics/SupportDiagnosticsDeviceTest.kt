package com.example.meowmba.core.diagnostics

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SupportDiagnosticsDeviceTest {
    @Test fun generatedReportIsReadableThroughGrantedFileProviderPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = SupportDiagnostics.capture(context, false, false, false, false)
        val file = SupportDiagnostics.write(context, snapshot)
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            assertEquals("content", uri.scheme)
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val report = Json.parseToJsonElement(text).jsonObject
            assertEquals(com.example.meowmba.BuildConfig.VERSION_CODE.toLong(), report["versionCode"]!!.jsonPrimitive.long)
            assertTrue(text.toByteArray().size < 8192)
            assertEquals(SupportDiagnostics.render(snapshot), text)
            assertTrue(report["recentProcessExits"]!!.jsonArray.size <= 5)
        } finally { file.delete() }
    }
}
