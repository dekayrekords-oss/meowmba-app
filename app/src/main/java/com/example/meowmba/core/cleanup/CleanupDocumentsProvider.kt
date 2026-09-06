package com.example.meowmba.core.cleanup

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.FileNotFoundException

/** Read-only review collection. Never grants write access or accepts arbitrary URIs. */
class CleanupDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true
    private fun items() = PhoneCleanup.pendingFresh()?.items.orEmpty().filter { it.uri != null }
    private fun item(id: String) = items().firstOrNull { it.uri == id }
        ?: throw FileNotFoundException("Cleanup review expired. Scan again in Memba.")

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES)
        return MatrixCursor(columns).apply {
            val values = mapOf(Root.COLUMN_ROOT_ID to "suggestions", Root.COLUMN_DOCUMENT_ID to "suggestions",
                Root.COLUMN_TITLE to "Cleanup suggestions", Root.COLUMN_FLAGS to Root.FLAG_LOCAL_ONLY,
                Root.COLUMN_MIME_TYPES to "*/*")
            addRow(columns.map { values[it] })
        }
    }

    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_FLAGS, Document.COLUMN_SUMMARY))

    private fun add(cursor: MatrixCursor, id: String) {
        val file = if (id == "suggestions") null else item(id)
        val mime = if (file == null) Document.MIME_TYPE_DIR else
            runCatching { context!!.contentResolver.getType(Uri.parse(file.uri)) }.getOrNull()
                ?: "application/octet-stream"
        val values = mapOf(Document.COLUMN_DOCUMENT_ID to id,
            Document.COLUMN_DISPLAY_NAME to (file?.label ?: "Cleanup suggestions"),
            Document.COLUMN_MIME_TYPE to mime, Document.COLUMN_SIZE to file?.bytes,
            Document.COLUMN_FLAGS to 0, Document.COLUMN_SUMMARY to (file?.why ?: "Review only · return to Memba to confirm cleanup"))
        cursor.addRow(cursor.columnNames.map { values[it] })
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?) =
        cursor(projection).apply { add(this, documentId) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        cursor(projection).apply {
            if (parentDocumentId != "suggestions") throw FileNotFoundException(parentDocumentId)
            items().forEach { add(this, it.uri!!) }
        }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Cleanup review is read-only")
        return context!!.contentResolver.openFileDescriptor(Uri.parse(item(documentId).uri), "r", signal)
            ?: throw FileNotFoundException(documentId)
    }
}
