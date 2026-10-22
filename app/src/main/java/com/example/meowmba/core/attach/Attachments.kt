package com.example.meowmba.core.attach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Photos, camera shots, documents and pasted files the user hands Meow (Flow/Desk "+", chat "+",
 * paste). Read on the phone: text via ML Kit OCR (images, PDF pages), Word/text files directly.
 * A copy is kept in app storage so Meow can refer back to it. Nothing is uploaded by this class.
 */
object Attachments {
    enum class Kind { PHOTO, IMAGE, FILE }

    data class Read(
        val name: String,
        val kind: Kind,
        val mime: String,
        /** Text found in the file (OCR or document text), trimmed to a prompt-sized budget. */
        val text: String,
        val tracking: List<Tracking>,
        val saved: File?,
    )

    data class Tracking(val carrier: String, val number: String) {
        val url: String get() = when (carrier) {
            "UPS" -> "https://www.ups.com/track?tracknum=$number"
            "USPS" -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=$number"
            "FedEx" -> "https://www.fedex.com/fedextrack/?trknbr=$number"
            "DHL" -> "https://www.dhl.com/us-en/home/tracking.html?tracking-id=$number"
            "Amazon" -> "https://www.amazon.com/progress-tracker/package?trackingId=$number"
            else -> "https://www.google.com/search?q=${Uri.encode("track package $number")}"
        }
    }

    private const val MAX_TEXT = 6_000

    suspend fun read(context: Context, uri: Uri, kind: Kind): Read = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: guessMime(uri.lastPathSegment.orEmpty())
        val name = displayName(context, uri) ?: (if (kind == Kind.PHOTO) "photo.jpg" else "file")
        val saved = runCatching { keepCopy(context, uri, name) }.getOrNull()
        val source = saved?.let { Uri.fromFile(it) } ?: uri
        val text = runCatching {
            when {
                mime.startsWith("image/") -> ocr(context, InputImage.fromFilePath(context, source))
                mime == "application/pdf" -> pdfText(context, saved)
                mime.contains("wordprocessingml") || name.endsWith(".docx", true) -> docxText(context, source)
                mime.startsWith("text/") || mime.contains("json") || mime.contains("csv") || mime.contains("xml") ->
                    cr.openInputStream(source)?.bufferedReader()?.use { it.readText() }.orEmpty()
                else -> ""
            }
        }.getOrDefault("").replace(Regex("[ \\t]+"), " ").trim().take(MAX_TEXT)
        Read(name, kind, mime, text, findTracking(text), saved)
    }

    /** What Meow is told about the attachment, alongside anything the user typed. */
    fun describe(r: Read, note: String?): String = buildString {
        note?.takeIf { it.isNotBlank() }?.let { append(it.trim()).append("\n\n") }
        append(when (r.kind) { Kind.PHOTO -> "[I took a photo"; Kind.IMAGE -> "[I shared a picture"; Kind.FILE -> "[I shared a file" })
        append(": ").append(r.name).append("]")
        if (r.tracking.isNotEmpty()) append("\nTracking number: ").append(r.tracking.joinToString { "${it.number} (${it.carrier})" })
        if (r.text.isNotBlank()) append("\nText in it:\n").append(r.text)
        else if (r.kind != Kind.FILE) append("\n(No readable text found in the image.)")
    }

    // ---- tracking numbers -------------------------------------------------------------------

    private val PATTERNS = listOf(
        "UPS" to Regex("\\b1Z[0-9A-Z]{16}\\b"),
        "USPS" to Regex("\\b(9[1-5]\\d{2}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{2}|(?:EA|EC|CP|RA|RR|LZ|LX)\\d{9}US)\\b"),
        "Amazon" to Regex("\\bTBA\\d{12}\\b"),
        "FedEx" to Regex("\\b(\\d{12}|\\d{15}|\\d{20})\\b"),
        "DHL" to Regex("\\b(JD\\d{18}|\\d{10})\\b"),
    )

    /** Tracking numbers in [text], most specific carrier first; each number reported once. */
    fun findTracking(text: String): List<Tracking> {
        val upper = text.uppercase()
        val seen = HashSet<String>()
        val out = ArrayList<Tracking>()
        for ((carrier, re) in PATTERNS) for (m in re.findAll(upper)) {
            val n = m.value.replace(" ", "")
            // A 10–20 digit run inside a longer number (or a phone number with context) is not a tracking number.
            if (carrier == "DHL" && !Regex("DHL|WAYBILL|AIRWAYBILL").containsMatchIn(upper)) continue
            if (carrier == "FedEx" && !Regex("FEDEX|FED EX|TRACKING").containsMatchIn(upper)) continue
            if (seen.none { it.contains(n) || n.contains(it) } && seen.add(n)) out += Tracking(carrier, n)
        }
        return out
    }

    // ---- readers -----------------------------------------------------------------------------

    private fun ocr(context: Context, image: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try { Tasks.await(recognizer.process(image), 30, TimeUnit.SECONDS).text } finally { recognizer.close() }
    }

    /** First three pages rendered and read by OCR (PDF text layers are often images anyway). */
    private fun pdfText(context: Context, file: File?): String {
        file ?: return ""
        val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd).use { r ->
            (0 until minOf(3, r.pageCount)).joinToString("\n") { i ->
                r.openPage(i).use { page ->
                    val scale = 1600f / maxOf(page.width, page.height)
                    val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ocr(context, InputImage.fromBitmap(bmp, 0)).also { bmp.recycle() }
                }
            }
        }
    }

    /** Word text without a library: `word/document.xml` with the tags removed. */
    private fun docxText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" } ?: return@use ""
                zip.bufferedReader().readText()
                    .replace(Regex("</w:p>"), "\n").replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            }
        }.orEmpty()

    private fun keepCopy(context: Context, uri: Uri, name: String): File {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
        val dest = File(dir, "${System.currentTimeMillis()}-$safe")
        context.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
        return dest
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "heic" -> "image/heic"
        "pdf" -> "application/pdf"; "txt", "md" -> "text/plain"; "csv" -> "text/csv"; "json" -> "application/json"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    /** Where a camera shot is written before reading (cache/, shared through the app's FileProvider). */
    fun newCameraFile(context: Context): File = File(context.cacheDir, "camera").apply { mkdirs() }
        .let { File(it, "photo-${System.currentTimeMillis()}.jpg") }
}
