package com.daygle.aicamera.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream

class FileDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    suspend fun downloadFile(url: String, fileName: String, mimeType: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Server returned ${response.code}")
            
            val body = response.body
            
            val resolver = context.contentResolver
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

            val uri = resolver.insert(collection, contentValues) ?: throw Exception("Could not create MediaStore entry")

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
}
