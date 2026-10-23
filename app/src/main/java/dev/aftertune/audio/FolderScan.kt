package dev.aftertune.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Finds playable audio inside a folder the user picked with the system folder picker. */
object FolderScan {
    class Found(val uri: Uri, val title: String)

    private class Row(val id: String, val name: String, val mime: String?)

    const val MAX_FILES = 2000
    private const val MAX_DEPTH = 8
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "mka", "amr", "3gp")

    /** Audio by MIME type, or by extension when the provider reports a generic type. */
    fun isAudio(mime: String?, name: String): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        val generic = mime == null || mime == "application/octet-stream"
        return generic && name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
    }

    fun titleOf(name: String): String = name.substringBeforeLast('.', name).ifBlank { name }

    /** Every audio file below [tree] (subfolders included), in folder / file-name order. */
    fun audioFiles(context: Context, tree: Uri): List<Found> {
        val out = ArrayList<Found>()
        try {
            walk(context, tree, DocumentsContract.getTreeDocumentId(tree), 0, out)
        } catch (_: Exception) {
            // Unreadable folder: return what was found so far.
        }
        return out
    }

    private fun walk(context: Context, tree: Uri, parentId: String, depth: Int, out: MutableList<Found>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val rows = ArrayList<Row>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) rows += Row(c.getString(0), c.getString(1).orEmpty(), c.getString(2))
        }
        rows.sortBy { it.name.lowercase() }
        for (r in rows) {
            if (out.size >= MAX_FILES) return
            if (r.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (depth < MAX_DEPTH) walk(context, tree, r.id, depth + 1, out)
            } else if (isAudio(r.mime, r.name)) {
                out += Found(DocumentsContract.buildDocumentUriUsingTree(tree, r.id), titleOf(r.name))
            }
        }
    }
}
