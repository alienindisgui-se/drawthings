package com.example.drawthings

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CollageScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isExporting by remember { mutableStateOf(false) }

    val pickMultipleMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
        onResult = { uris ->
            selectedUris = uris
        }
    )

    LaunchedEffect(Unit) {
        pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color.White) }
            Text("Create Collage", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedUris.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select up to 4 images", color = Color.Gray)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedUris) { uri ->
                Image(
                    bitmap = loadBitmap(context, uri).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            if (selectedUris.size < 4) {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add image",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isExporting = true
                val bitmaps = selectedUris.map { loadBitmap(context, it) }
                val collage = createCollage(bitmaps)
                saveCollageToGallery(context, collage)
                isExporting = false
            },
            enabled = !isExporting && selectedUris.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isExporting) "Exporting..." else "Export Collage")
        }
    }
}

private suspend fun loadBitmap(context: Context, uri: Uri): Bitmap {
    return withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)
        } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

private fun createCollage(bitmaps: List<Bitmap>): Bitmap {
    val count = bitmaps.size.coerceAtMost(4)
    val cols = 2
    val rows = 2
    val cellW = bitmaps.firstOrNull()?.width ?: 1080
    val cellH = bitmaps.firstOrNull()?.height ?: 1080

    val result = Bitmap.createBitmap(cellW * cols, cellH * rows, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), paint)

    for (i in 0 until count) {
        val col = i % cols
        val row = i / cols
        val bmp = bitmaps[i]
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, cellW, cellH, true)
        canvas.drawBitmap(scaled, col * cellW.toFloat(), row * cellH.toFloat(), null)
        if (scaled != bmp) scaled.recycle()
    }

    return result
}

private fun saveCollageToGallery(context: Context, bitmap: Bitmap) {
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val relativePath = "${Environment.DIRECTORY_PICTURES}/drawthings_outputs/$todayStr/"

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

    val filename = "collage_$nextIndex.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let { outUri ->
        context.contentResolver.openOutputStream(outUri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
        }
        Toast.makeText(context, "Collage saved!", Toast.LENGTH_SHORT).show()
    }
}
