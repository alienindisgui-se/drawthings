package com.example.drawthings

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun todayOutputPath(): String {
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "${Environment.DIRECTORY_PICTURES}/drawthings_outputs/$todayStr/"
}

fun nextMediaStoreIndex(context: Context, relativePath: String): Int {
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
    val selection = buildString {
        append("${MediaStore.Images.Media.RELATIVE_PATH}=?")
        append(" AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%.%'")
    }
    val selectionArgs = arrayOf(relativePath)

    var nextIndex = 1
    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val dotIdx = name.indexOf('.')
                if (dotIdx <= 0) continue
                val numPart = name.substring(0, dotIdx)
                val n = numPart.toIntOrNull() ?: continue
                if (n >= nextIndex) nextIndex = n + 1
            }
        }
    } catch (_: Exception) {
        nextIndex = 1
    }
    return nextIndex
}

fun insertImageToMediaStore(
    context: Context,
    filename: String,
    mimeType: String,
    relativePath: String,
    bytes: ByteArray
) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let { outUri ->
        context.contentResolver.openOutputStream(outUri)?.use { out ->
            out.write(bytes)
            out.flush()
        }
    }
}

fun showSavedToast(context: Context, relativePath: String, filename: String) {
    Toast.makeText(context, "Saved to: $relativePath$filename", Toast.LENGTH_SHORT).show()
}

fun compressJpegUnderLimit(
    bitmap: Bitmap,
    maxBytes: Int,
    startQuality: Int = 90,
    qualityStep: Int = 8,
    maxAttempts: Int = 12,
    scaleDownAfterAttempt: Int = 3
): ByteArray {
    var quality = startQuality
    var currentBitmap = bitmap
    val baos = ByteArrayOutputStream()

    repeat(maxAttempts) { attempt ->
        baos.reset()
        val ok = currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        if (!ok) return@repeat
        val encoded = baos.toByteArray()

        if (encoded.size <= maxBytes || quality <= 5) {
            return encoded
        }

        quality -= qualityStep
        if (attempt == scaleDownAfterAttempt) {
            val scale = (maxBytes.toFloat() / encoded.size.toFloat()).coerceIn(0.1f, 1f)
            val newW = (currentBitmap.width * scale).toInt().coerceAtLeast(1)
            val newH = (currentBitmap.height * scale).toInt().coerceAtLeast(1)
            currentBitmap = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
        }
    }
    return baos.toByteArray()
}
