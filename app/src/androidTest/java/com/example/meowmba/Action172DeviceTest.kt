package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.screen.ScreenHelp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Exercises the packaged barcode and OCR models, not mocked recognition responses. */
class Action172DeviceTest {
    @Test fun sharedTextReachesTheRetainedModelAfterActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun share(text: String) = android.content.Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, text).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        // MainActivity consumes ACTION_SEND by clearing its action. ActivityScenario
        // matches lifecycle callbacks with Intent.filterEquals, so launching the
        // scenario with ACTION_SEND itself would lose those callbacks in onCreate.
        val initial = android.content.Intent(context, MainActivity::class.java)
            .setType("text/plain")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(initial).use { scenario ->
            val modelField = MainActivity::class.java.getDeclaredField("companionViewModel").apply { isAccessible = true }
            var originalModel: Any? = null
            repeat(200) {
                if (originalModel == null) {
                    scenario.onActivity { originalModel = modelField.get(it) }
                    if (originalModel == null) android.os.SystemClock.sleep(50)
                }
            }
            assertNotNull("The real Compose factory must initialize the model", originalModel)
            fun awaitText(expected: String) {
                var actual: String? = null
                repeat(200) {
                    scenario.onActivity { activity ->
                        // Observe without creating a default VM before Compose's real factory runs.
                        actual = (modelField.get(activity) as? com.example.meowmba.ui.companion.CompanionViewModel)?.screenHelp?.value?.text
                    }
                    if (actual == expected) return
                    android.os.SystemClock.sleep(50)
                }
                assertEquals(expected, actual)
            }
            scenario.recreate()
            scenario.onActivity { assertSame("The recreated Activity must bind its retained model", originalModel, modelField.get(it)) }
            context.startActivity(share("After recreation: notifications enabled"))
            awaitText("After recreation: notifications enabled")
        }
    }

    @Test fun bundledQrDecoderReadsKnownValue() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val bitmap = testContext.assets.open("qr-172.png").use(BitmapFactory::decodeStream)
        val scanner = BarcodeScanning.getClient()
        try {
            val codes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
            assertEquals("meow-qr-acceptance-172", codes.single().rawValue)
        } finally { scanner.close(); bitmap.recycle() }
    }

    @Test fun blankImageDoesNotInventQrValue() {
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val scanner = BarcodeScanning.getClient()
        try {
            assertTrue(Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).isEmpty())
        } finally { scanner.close(); bitmap.recycle() }
    }

    @Test fun sharedScreenUsesRealOcrAndConsumesOnlyItsTemporaryCapture() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "screen_help").apply { mkdirs() }
        val file = File.createTempFile("screen-", ".jpg", directory)
        val unrelated = File(directory, "keep-this.txt").apply { writeText("preserve") }
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawText("Settings: Notifications enabled", 30f, 120f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val result = ScreenHelp.read(context, "", uri, System.currentTimeMillis())
            assertTrue(result.text, result.text.contains("Notifications", true))
            assertNotNull(result.image)
            assertFalse("A consumed screen must not linger in cache", file.exists())
            assertTrue(unrelated.exists())
        } finally { file.delete(); unrelated.delete() }
    }
}
