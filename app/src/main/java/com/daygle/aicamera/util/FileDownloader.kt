package com.daygle.aicamera.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File

class FileDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    suspend fun downloadFile(url: String, fileName: String, mimeType: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Server returned ${response.code}")

                val body = response.body
                val resolver = context.contentResolver

                // Android 9 and below require WRITE_EXTERNAL_STORAGE for MediaStore
                // writes (a runtime permission this app never requests). Fall back
                // to app-specific storage and index the file so gallery apps
                // still show it.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    saveToAppStorage(body, fileName, mimeType)
                    return@use
                }
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val folder = if (mimeType.startsWith("image")) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Daygle")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (mimeType.startsWith("image")) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collection, contentValues)
                    ?: throw Exception("Could not create MediaStore entry")

                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("Could not open output stream")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }
        }.onSuccess {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to gallery: $fileName", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { e ->
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Permission-free save target for Android 9 and below. */
    private fun saveToAppStorage(body: ResponseBody, fileName: String, mimeType: String) {
        val dir = context.getExternalFilesDir(
            if (mimeType.startsWith("image")) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        ) ?: context.filesDir
        val target = File(dir, fileName)
        target.outputStream().use { output ->
            body.byteStream().use { input ->
                input.copyTo(output)
            }
        }
        // Register with MediaStore so the file appears in gallery apps.
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
    }
}
