package com.example.myfoodtracker.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes an export file to disk and exposes it as a shareable `content://` URI.
 *
 * Lives in the data layer because it needs a [Context]; the ViewModel never
 * holds one — the Fragment drives this writer (AD-1, project rule).
 *
 * The exported file lives in the app cache directory, so it is excluded from
 * cloud backup by default. No storage permission is required for either the
 * Sharesheet (FileProvider grant) or the document picker (SAF grant).
 */
class LogExportFileWriter(
    private val context: Context
) {

    /**
     * Writes [content] as UTF-8 into `cacheDir/exports/` and returns the file.
     * The directory is created when it does not exist yet.
     */
    fun writeToCache(fileName: String, content: String): File {
        val directory = File(context.cacheDir, EXPORTS_DIR)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create export directory")
        }
        val file = File(directory, fileName)
        file.writeBytes(content.toByteArray(Charsets.UTF_8))
        return file
    }

    /**
     * Exposes [file] as a `content://` URI through the app's [FileProvider].
     * Never returns a `file://` URI — that would throw `FileUriExposedException`
     * on API 24+.
     */
    fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, AUTHORITY, file)

    /**
     * Writes [content] as UTF-8 into a user-picked document URI.
     * Opens with mode `"wt"` so existing file content is truncated when overwriting.
     * Throws on failure — the caller catches and reports it.
     */
    fun writeToUri(uri: Uri, content: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Could not open the selected document for writing")
        stream.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    companion object {
        const val AUTHORITY = "com.example.myfoodtracker.fileprovider"
        private const val EXPORTS_DIR = "exports"
    }
}
