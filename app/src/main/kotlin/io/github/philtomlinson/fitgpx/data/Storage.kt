/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.github.philtomlinson.fitgpx.BuildConfig
import io.github.philtomlinson.fitgpx.core.FitToGpx
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Storage Access Framework helpers. FitGPX never needs a storage permission. */
object Storage {
    const val GPX_MIME = "application/gpx+xml"

    private val SUPPORTED_SUFFIXES = listOf(".fit", ".fit.gz", ".gz", ".zip")

    fun isSupportedName(name: String): Boolean = name.lowercase().let { n -> SUPPORTED_SUFFIXES.any { n.endsWith(it) } }

    fun displayName(resolver: ContentResolver, uri: Uri): String {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "activity.fit"
    }

    /** Every supported file below [treeUri], recursively (bounded depth). */
    fun listTree(resolver: ContentResolver, treeUri: Uri, maxDepth: Int = 8): List<Pair<Uri, String>> {
        val out = mutableListOf<Pair<Uri, String>>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val dirs = mutableListOf<String>()
            resolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirs += id
                    } else if (isSupportedName(name)) {
                        out += DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to name
                    }
                }
            }
            if (depth < maxDepth) dirs.forEach { walk(it, depth + 1) }
        }
        walk(DocumentsContract.getTreeDocumentId(treeUri), 0)
        return out.sortedBy { it.second.lowercase() }
    }

    fun treeName(resolver: ContentResolver, treeUri: Uri): String {
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return displayName(resolver, doc)
    }

    fun hasPersistedWrite(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }

    fun persist(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

/** Where a batch of GPX files goes. */
sealed interface Destination {
    data class Folder(val treeUri: Uri) : Destination
    data class Zip(val uri: Uri) : Destination
    data object Share : Destination
}

/** Receives the GPX files of one batch. */
internal interface OutputSink : AutoCloseable {
    /** Opens a new output for [desiredName]; returns the final name actually used. */
    fun open(desiredName: String): Pair<String, OutputStream>

    /** Content URIs of what was written, for sharing. */
    val sharedUris: List<Uri> get() = emptyList()

    companion object {
        fun create(context: Context, destination: Destination, policy: ConflictPolicy): OutputSink = when (destination) {
            is Destination.Folder -> FolderSink(context.contentResolver, destination.treeUri, policy)
            is Destination.Zip -> ZipSink(context.contentResolver, destination.uri)
            Destination.Share -> ShareSink(context)
        }
    }
}

private class FolderSink(
    private val resolver: ContentResolver,
    treeUri: Uri,
    private val policy: ConflictPolicy,
) : OutputSink {
    private val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    private val existing: MutableMap<String, Uri> = HashMap()
    private val writtenThisBatch = HashSet<String>()

    init {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                existing[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            }
        }
    }

    override fun open(desiredName: String): Pair<String, OutputStream> {
        val name = when (policy) {
            ConflictPolicy.KEEP_BOTH -> FitToGpx.uniqueName(desiredName, existing.keys + writtenThisBatch)
            // Never overwrite something written moments ago in this same batch.
            ConflictPolicy.OVERWRITE -> FitToGpx.uniqueName(desiredName, writtenThisBatch)
        }
        writtenThisBatch += name
        val target = existing[name]
        val stream = if (target != null && policy == ConflictPolicy.OVERWRITE) {
            resolver.openOutputStream(target, "wt")
        } else {
            val doc = DocumentsContract.createDocument(resolver, parent, Storage.GPX_MIME, name)
                ?: throw IOException("Could not create $name")
            resolver.openOutputStream(doc, "w")
        } ?: throw IOException("Could not write $name")
        return name to stream
    }

    override fun close() = Unit
}

private class ZipSink(resolver: ContentResolver, uri: Uri) : OutputSink {
    private val zip = ZipOutputStream(resolver.openOutputStream(uri, "w") ?: throw IOException("Could not write archive"))
    private val names = HashSet<String>()

    override fun open(desiredName: String): Pair<String, OutputStream> {
        val name = FitToGpx.uniqueName(desiredName, names)
        names += name
        zip.putNextEntry(ZipEntry(name))
        return name to object : FilterOutputStream(zip) {
            override fun write(b: ByteArray, off: Int, len: Int) = zip.write(b, off, len)
            override fun close() {
                flush()
                zip.closeEntry()
            }
        }
    }

    override fun close() = zip.close()
}

private class ShareSink(private val context: Context) : OutputSink {
    private val dir = File(context.cacheDir, "exports").apply {
        deleteRecursively()
        mkdirs()
    }
    private val names = HashSet<String>()
    private val files = mutableListOf<File>()

    override fun open(desiredName: String): Pair<String, OutputStream> {
        val name = FitToGpx.uniqueName(desiredName, names)
        names += name
        val f = File(dir, name)
        files += f
        return name to f.outputStream()
    }

    override val sharedUris: List<Uri>
        get() = files.map { FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", it) }

    override fun close() = Unit
}
