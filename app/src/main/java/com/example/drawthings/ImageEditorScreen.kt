package com.example.drawthings

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// --- DATA MODELS ---

enum class Tool { DRAW, CIRCLE, TEXT }

sealed class DrawAction {
    data class DrawPath(
        val path: Path,
        val color: Color,
        val strokeWidth: Float,
        val isSmooth: Boolean,
        val hasArrow: Boolean,
        val points: List<Offset>
    ) : DrawAction()

    data class HollowCircle(
        val center: Offset,
        val radius: Float,
        val color: Color,
        val strokeWidth: Float
    ) : DrawAction()

    data class DrawText(
        val text: String,
        val position: Offset,
        val color: Color,
        val bgColor: Color,
        val textSize: Float
    ) : DrawAction()
}

// --- MAIN UI COMPOSABLE ---

@Composable
fun ImageEditorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Core Image State
    var nativeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

// Action State
    var actions by remember { mutableStateOf(listOf<DrawAction>()) }
    var selectedTool by remember { mutableStateOf(Tool.DRAW) }

    // Shared stroke width (pen + circles + line)
    var strokeWidth by remember { mutableFloatStateOf(10f) }

    // Tool Configurations
    var drawColor by remember { mutableStateOf(Color.Red) }
    var drawArrow by remember { mutableStateOf(false) }
    var drawLineMode by remember { mutableStateOf(false) }
    var drawSmooth by remember { mutableStateOf(true) }
    var circleColor by remember { mutableStateOf(Color.Red) }

    // Text Tool Configurations
    var toolTextString by remember { mutableStateOf("Sample Text") }
    var toolTextColor by remember { mutableStateOf(Color.White) }
    var toolTextBgColor by remember { mutableStateOf(Color.Black) }
    var toolTextSize by remember { mutableFloatStateOf(80f) }

    // Overlay Configurations
    var overlayText by remember { mutableStateOf("") }
    var overlayColor by remember { mutableStateOf(Color.Red) }
    var overlayBgColor by remember { mutableStateOf(Color.Black) }
    var borderEnabled by remember { mutableStateOf(false) }
    var borderColor by remember { mutableStateOf(Color.Red) }

    // Live Gestures State
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) } // freehand
    var currentLineStart by remember { mutableStateOf<Offset?>(null) }
    var currentLineEnd by remember { mutableStateOf<Offset?>(null) }
    var currentCircleCenter by remember { mutableStateOf<Offset?>(null) }
    var currentCircleRadius by remember { mutableFloatStateOf(0f) }
    var currentTextPosition by remember { mutableStateOf<Offset?>(null) }

    // Tab Navigation State
    val tabs = listOf("Draw", "Circle", "Text", "Overlay")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch(Dispatchers.IO) {
                    val rotation = try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            val exif = android.media.ExifInterface(stream)
                            when (exif.getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION,
                                android.media.ExifInterface.ORIENTATION_NORMAL
                            )) {
                                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                        } ?: 0f
                    } catch (_: Exception) {
                        0f
                    }

                    val bmp = context.contentResolver.openInputStream(it)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }

                    if (bmp != null) {
                        val finalBmp = if (rotation != 0f) {
                            val matrix = Matrix().apply { postRotate(rotation) }
                            android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                        } else bmp

                        withContext(Dispatchers.Main) {
                            nativeBitmap = finalBmp
                            imageBitmap = finalBmp.asImageBitmap()
                            actions = emptyList()
                        }
                    }
                }
            }
        }
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP AREA: CANVAS (65% Height) ---
            Box(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxWidth()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    val ratio = imageBitmap!!.width.toFloat() / imageBitmap!!.height.toFloat()
                    Box(
                        modifier = Modifier
                            .aspectRatio(ratio)
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = imageBitmap!!,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(selectedTool) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            when (selectedTool) {
                                                Tool.DRAW -> {
                                                    if (drawLineMode) {
                                                        currentLineStart = offset
                                                        currentLineEnd = offset
                                                    } else {
                                                        currentPoints = listOf(offset)
                                                    }
                                                }
                                                Tool.CIRCLE -> {
                                                    currentCircleCenter = offset
                                                    currentCircleRadius = 0f
                                                }
                                                Tool.TEXT -> currentTextPosition = offset
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            when (selectedTool) {
                                                Tool.DRAW -> {
                                                    if (drawLineMode) {
                                                        currentLineEnd = change.position
                                                    } else {
                                                        currentPoints = currentPoints + change.position
                                                    }
                                                }
                                                Tool.CIRCLE -> {
                                                    currentCircleCenter?.let {
                                                        currentCircleRadius = (change.position - it).getDistance()
                                                    }
                                                }
                                                Tool.TEXT -> currentTextPosition = change.position
                                            }
                                        },
onDragEnd = {
                                            when (selectedTool) {
                                                Tool.DRAW -> {
                                                    if (drawLineMode) {
                                                        val start = currentLineStart
                                                        val end = currentLineEnd
                                                        if (start != null && end != null && start != end) {
                                                            val path = Path().apply {
                                                                moveTo(start.x, start.y)
                                                                lineTo(end.x, end.y)
                                                            }
                                                            actions = actions + DrawAction.DrawPath(
                                                                path = path,
                                                                color = drawColor,
                                                                strokeWidth = strokeWidth,
                                                                isSmooth = false,
                                                                hasArrow = drawArrow,
                                                                points = listOf(start, end)
                                                            )
                                                        }
                                                        currentLineStart = null
                                                        currentLineEnd = null
                                                    } else {
                                                        if (currentPoints.size > 1) {
                                                            val path = Path().apply {
                                                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                                                for (i in 1 until currentPoints.size) {
                                                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                                                }
                                                            }
                                                            actions = actions + DrawAction.DrawPath(
                                                                path = path,
                                                                color = drawColor,
                                                                strokeWidth = strokeWidth,
                                                                isSmooth = drawSmooth,
                                                                hasArrow = drawArrow,
                                                                points = currentPoints.toList()
                                                            )
                                                        }
                                                        currentPoints = emptyList()
                                                    }
                                                }

                                                Tool.CIRCLE -> {
                                                    if (currentCircleCenter != null) {
                                                        actions = actions + DrawAction.HollowCircle(
                                                            center = currentCircleCenter!!,
                                                            radius = currentCircleRadius,
                                                            color = circleColor,
                                                            strokeWidth = strokeWidth
                                                        )
                                                    }
                                                    currentCircleCenter = null
                                                }

                                                Tool.TEXT -> {
                                                    if (currentTextPosition != null && toolTextString.isNotEmpty()) {
                                                        actions = actions + DrawAction.DrawText(
                                                            text = toolTextString,
                                                            position = currentTextPosition!!,
                                                            color = toolTextColor,
                                                            bgColor = toolTextBgColor,
                                                            textSize = toolTextSize
                                                        )
                                                    }
                                                    currentTextPosition = null
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            // 1. Draw Stored Actions
                            actions.forEach { action ->
                                when (action) {
                                    is DrawAction.DrawPath -> {
                                        drawPath(
                                            path = action.path,
                                            color = action.color,
                                            style = Stroke(
                                                width = action.strokeWidth,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round,
                                                pathEffect = if (action.isSmooth) PathEffect.cornerPathEffect(50f) else null
                                            )
                                        )
                                        if (action.hasArrow) drawArrow(action.points, action.color, action.strokeWidth)
                                    }

                                    is DrawAction.HollowCircle -> {
                                        drawCircle(
                                            color = action.color,
                                            radius = action.radius,
                                            center = action.center,
                                            style = Stroke(width = action.strokeWidth)
                                        )
                                    }

                                    is DrawAction.DrawText -> {
                                        drawTextWithBackground(
                                            text = action.text,
                                            position = action.position,
                                            textColor = action.color,
                                            bgColor = action.bgColor,
                                            textSize = action.textSize
                                        )
                                    }
                                }
                            }

                            // 2. Draw Live Action (while dragging)
                            when (selectedTool) {
                                Tool.DRAW -> {
                                    if (drawLineMode) {
                                        val start = currentLineStart
                                        val end = currentLineEnd
                                        if (start != null && end != null && start != end) {
                                            drawLine(
                                                color = drawColor,
                                                start = start,
                                                end = end,
                                                strokeWidth = strokeWidth,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    } else {
                                        if (currentPoints.size > 1) {
                                            val livePath = Path().apply {
                                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                                for (i in 1 until currentPoints.size) lineTo(currentPoints[i].x, currentPoints[i].y)
                                            }
                                            drawPath(
                                                path = livePath,
                                                color = drawColor,
                                                style = Stroke(
                                                    width = strokeWidth,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round,
                                                    pathEffect = if (drawSmooth) PathEffect.cornerPathEffect(50f) else null
                                                )
                                            )
                                            if (drawArrow) drawArrow(currentPoints, drawColor, strokeWidth)
                                        }
                                    }
                                }

                                Tool.CIRCLE -> {
                                    if (currentCircleCenter != null) {
                                        drawCircle(
                                            color = circleColor,
                                            radius = currentCircleRadius,
                                            center = currentCircleCenter!!,
                                            style = Stroke(width = strokeWidth)
                                        )
                                    }
                                }

                                Tool.TEXT -> {
                                    if (currentTextPosition != null) {
                                        drawTextWithBackground(
                                            text = toolTextString,
                                            position = currentTextPosition!!,
                                            textColor = toolTextColor,
                                            bgColor = toolTextBgColor,
                                            textSize = toolTextSize
                                        )
                                    }
                                }
                            }

                            // 3. Draw Global Overlay & Border
                            if (borderEnabled) {
                                drawRect(color = borderColor, style = Stroke(width = 20f))
                            }
                            if (overlayText.isNotEmpty()) {
                                drawTextWithBackground(
                                    text = overlayText,
                                    position = Offset(30f, 100f),
                                    textColor = overlayColor,
                                    bgColor = overlayBgColor,
                                    textSize = 80f
                                )
                            }
                        }
                    }
                } else {
                    Text("Select an image to start editing", color = Color.White)
                }
            }

            HorizontalDivider()

            // --- BOTTOM AREA: CONTROLS (35% Height Window) ---
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth()
            ) {
                // Top Global Actions Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) { Text("Open") }

                        Button(
                            onClick = {
                                nativeBitmap?.let { bmp ->
                                    exportImage(
                                        context,
                                        bmp,
                                        actions,
                                        canvasSize,
                                        borderEnabled,
                                        borderColor,
                                        overlayText,
                                        overlayColor,
                                        overlayBgColor
                                    )
                                }
                            },
                            enabled = nativeBitmap != null
                        ) { Text("Export") }
                    }

                    Button(
                        onClick = { if (actions.isNotEmpty()) actions = actions.dropLast(1) },
                        enabled = actions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) { Text("Undo") }
                }

                // Tab Navigation
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                selectedTool = when (index) {
                                    0 -> Tool.DRAW
                                    1 -> Tool.CIRCLE
                                    2 -> Tool.TEXT
                                    3 -> selectedTool
                                    else -> selectedTool
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }

                // Tab Content Panel
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (selectedTabIndex) {
                        0 -> { // DRAW
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = drawLineMode, onCheckedChange = { drawLineMode = it })
                                Text("Straight Line")
                            }
                            Spacer(Modifier.height(8.dp))
                            if (!drawLineMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = drawSmooth, onCheckedChange = { drawSmooth = it })
                                    Text("Smooth")
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = drawArrow, onCheckedChange = { drawArrow = it })
                                Text("Add Arrow Head")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Pen Color", style = MaterialTheme.typography.labelMedium)
                            ColorPickerRow(selectedColor = drawColor) { drawColor = it }
                        }

                        1 -> { // CIRCLE
                            Text(
                                "Tip: Drag outward from center to scale",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Circle Color", style = MaterialTheme.typography.labelMedium)
                            ColorPickerRow(selectedColor = circleColor) { circleColor = it }
                        }

                        2 -> { // TEXT
                            OutlinedTextField(
                                value = toolTextString,
                                onValueChange = { toolTextString = it },
                                label = { Text("Text to place") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))

                            Text("Text Size: ${toolTextSize.toInt()}", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = toolTextSize,
                                onValueChange = { toolTextSize = it },
                                valueRange = 20f..200f
                            )

                            Text("Text Color", style = MaterialTheme.typography.labelMedium)
                            ColorPickerRow(selectedColor = toolTextColor) { toolTextColor = it }

                            Text("Background Box Color (50% Opacity)", style = MaterialTheme.typography.labelMedium)
                            ColorPickerRow(selectedColor = toolTextBgColor) { toolTextBgColor = it }
                        }

                        3 -> { // OVERLAY & GLOBALS
                            Text("Top-Left Overlay", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = overlayText,
                                onValueChange = { overlayText = it },
                                label = { Text("Text/Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Overlay Text Color", style = MaterialTheme.typography.labelMedium)
                            ColorPickerRow(selectedColor = overlayColor) { overlayColor = it }

                            Text("Overlay Background Box Color", style = MaterialTheme.typography.labelMedium)
                            ColorPickerRow(selectedColor = overlayBgColor) { overlayBgColor = it }

                            HorizontalDivider(Modifier.padding(vertical = 12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = borderEnabled, onCheckedChange = { borderEnabled = it })
                                Text("Enable Image Border")
                            }
                            if (borderEnabled) {
                                ColorPickerRow(selectedColor = borderColor) { borderColor = it }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Width slider (controls pen + circle + line)
                    // Show it in all tabs except overlay (still OK if shown; but keep tidy)
                    if (selectedTabIndex != 3) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(
                            "Stroke Width: ${strokeWidth.toInt()}",
                            style = MaterialTheme.typography.labelMedium
                        )
Slider(
                                value = strokeWidth,
                                onValueChange = { strokeWidth = it },
                                valueRange = 5f..10f
                            )
                    }
                }
            }
        }
    }
}

// --- UI HELPERS ---

@Composable
fun ColorPickerRow(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.Red,
        Color.Blue,
        Color.Green,
        Color.White,
        Color.Black
    )
    LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(4.dp)
                    .background(color, CircleShape)
                    .border(if (color == selectedColor) 3.dp else 1.dp, Color.Gray, CircleShape)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

fun DrawScope.drawArrow(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val end = points.last()

    var prev = points[maxOf(0, points.size - 2)]
    for (i in points.lastIndex downTo 0) {
        if ((end - points[i]).getDistance() > 12f) {
            prev = points[i]
            break
        }
    }

    val angle = atan2((end.y - prev.y).toDouble(), (end.x - prev.x).toDouble())
    val arrowLen = 50f

    val a1 = angle - Math.PI / 6
    val a2 = angle + Math.PI / 6

    val p1 = Offset(
        end.x - (arrowLen * cos(a1)).toFloat(),
        end.y - (arrowLen * sin(a1)).toFloat()
    )
    val p2 = Offset(
        end.x - (arrowLen * cos(a2)).toFloat(),
        end.y - (arrowLen * sin(a2)).toFloat()
    )

    drawLine(color, end, p1, strokeWidth = width, cap = StrokeCap.Round)
    drawLine(color, end, p2, strokeWidth = width, cap = StrokeCap.Round)
}

fun DrawScope.drawTextWithBackground(
    text: String,
    position: Offset,
    textColor: Color,
    bgColor: Color,
    textSize: Float
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            this.textSize = textSize
            isAntiAlias = true
        }
        val bgPaint = android.graphics.Paint().apply {
            color = bgColor.copy(alpha = 0.5f).toArgb()
            style = android.graphics.Paint.Style.FILL
        }

        val fm = paint.fontMetrics
        val textWidth = paint.measureText(text)
        val padding = textSize * 0.25f

        if (bgColor != Color.Transparent) {
            canvas.nativeCanvas.drawRect(
                position.x - padding,
                position.y + fm.ascent - padding,
                position.x + textWidth + padding,
                position.y + fm.descent + padding,
                bgPaint
            )
        }

        canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
    }
}

// --- NATIVE EXPORT ENGINE ---

fun exportImage(
    context: Context,
    nativeBitmap: android.graphics.Bitmap,
    actions: List<DrawAction>,
    canvasSize: IntSize,
    borderEnabled: Boolean,
    borderColor: Color,
    overlayText: String,
    overlayColor: Color,
    overlayBgColor: Color
) {
    fun android.graphics.Bitmap.copyToConfig(config: android.graphics.Bitmap.Config): android.graphics.Bitmap {
        return if (this.config == config) this else this.copy(config, true)
    }

    fun buildScaledBitmapIfNeeded(bmp: android.graphics.Bitmap, maxDimension: Int): android.graphics.Bitmap {
        val w = bmp.width
        val h = bmp.height
        val max = maxOf(w, h)
        if (max <= maxDimension) return bmp

        val scale = maxDimension.toFloat() / max.toFloat()
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)

        return android.graphics.Bitmap.createScaledBitmap(bmp, newW, newH, true)
    }

    fun encodeToBytes(
        bmp: android.graphics.Bitmap,
        format: android.graphics.Bitmap.CompressFormat,
        quality: Int
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(format, quality, out)
        return out.toByteArray()
    }

data class Encoded(val bytes: ByteArray, val mimeType: String, val extension: String)

    fun encodeBestUnderLimit(source: android.graphics.Bitmap, maxBytes: Int): Encoded {
        val candidates = listOf(
            android.graphics.Bitmap.CompressFormat.JPEG to Triple("image/jpeg", ".jpg", 90),
            android.graphics.Bitmap.CompressFormat.PNG to Triple("image/png", ".png", 70)
        )

        // Quality sweep (kept close to your original approach)
        val qualities = listOf(95, 85, 75, 65, 55, 45, 35, 25, 15)

        var best: Encoded? = null

        for ((format, mimeExt) in candidates) {
            val mimeType = mimeExt.first
            val ext = mimeExt.second
            for (q in qualities) {
                val bytes = encodeToBytes(source, format, q)
                val encoded = Encoded(bytes, mimeType, ext)

                val under = bytes.size <= maxBytes
                if (best == null) {
                    best = encoded
                } else {
                    val bestUnder = best!!.bytes.size <= maxBytes
                    if (under) {
                        if (!bestUnder || bytes.size > best!!.bytes.size) best = encoded
                    } else {
                        if (!bestUnder && bytes.size < best!!.bytes.size) best = encoded
                    }
                }

                if (under) return encoded
            }
        }

        return best ?: Encoded(ByteArray(0), "image/png", ".png")
    }

    // Render overlay/draw actions into a mutable result bitmap.
    val resultBmp = nativeBitmap
        .copyToConfig(android.graphics.Bitmap.Config.ARGB_8888)
        .copy(android.graphics.Bitmap.Config.ARGB_8888, true)

    val canvas = android.graphics.Canvas(resultBmp)

    val scaleX = resultBmp.width.toFloat() / canvasSize.width.toFloat()
    val scaleY = resultBmp.height.toFloat() / canvasSize.height.toFloat()

    if (borderEnabled) {
        val borderPaint = android.graphics.Paint().apply {
            color = borderColor.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 20f * scaleX
        }
        canvas.drawRect(0f, 0f, resultBmp.width.toFloat(), resultBmp.height.toFloat(), borderPaint)
    }

    if (overlayText.isNotEmpty()) {
        val textPaint = android.graphics.Paint().apply {
            color = overlayColor.toArgb()
            textSize = 80f * scaleX
            isAntiAlias = true
        }
        val bgPaint = android.graphics.Paint().apply {
            color = overlayBgColor.copy(alpha = 0.5f).toArgb()
            style = android.graphics.Paint.Style.FILL
        }

        val fm = textPaint.fontMetrics
        val textWidth = textPaint.measureText(overlayText)
        val padding = 80f * scaleX * 0.25f
        val sx = 30f * scaleX
        val sy = 100f * scaleY

        if (overlayBgColor != Color.Transparent) {
            canvas.drawRect(
                sx - padding,
                sy + fm.ascent - padding,
                sx + textWidth + padding,
                sy + fm.descent + padding,
                bgPaint
            )
        }
        canvas.drawText(overlayText, sx, sy, textPaint)
    }

    val matrix = Matrix().apply { setScale(scaleX, scaleY) }

    for (action in actions) {
        when (action) {
            is DrawAction.DrawPath -> {
                val paint = android.graphics.Paint().apply {
                    color = action.color.toArgb()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = action.strokeWidth * scaleX
                    isAntiAlias = true
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    if (action.isSmooth) {
                        pathEffect = android.graphics.CornerPathEffect(50f * scaleX)
                    }
                }

                val androidPath = action.path.asAndroidPath()
                val scaledPath = android.graphics.Path()
                androidPath.transform(matrix, scaledPath)
                canvas.drawPath(scaledPath, paint)

                if (action.hasArrow && action.points.size >= 2) {
                    val end = action.points.last()
                    var prev = action.points[maxOf(0, action.points.size - 2)]
                    for (i in action.points.lastIndex downTo 0) {
                        if ((end - action.points[i]).getDistance() > 12f) {
                            prev = action.points[i]
                            break
                        }
                    }

                    val sxEnd = end.x * scaleX
                    val syEnd = end.y * scaleY
                    val sxPrev = prev.x * scaleX
                    val syPrev = prev.y * scaleY

                    val angle = atan2((syEnd - syPrev).toDouble(), (sxEnd - sxPrev).toDouble())
                    val arrowLen = 50f * scaleX

                    val sx1 = (sxEnd - arrowLen * cos(angle - Math.PI / 6)).toFloat()
                    val sy1 = (syEnd - arrowLen * sin(angle - Math.PI / 6)).toFloat()
                    val sx2 = (sxEnd - arrowLen * cos(angle + Math.PI / 6)).toFloat()
                    val sy2 = (syEnd - arrowLen * sin(angle + Math.PI / 6)).toFloat()

                    canvas.drawLine(sxEnd, syEnd, sx1, sy1, paint)
                    canvas.drawLine(sxEnd, syEnd, sx2, sy2, paint)
                }
            }

            is DrawAction.HollowCircle -> {
                val paint = android.graphics.Paint().apply {
                    color = action.color.toArgb()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = action.strokeWidth * scaleX
                    isAntiAlias = true
                }
                canvas.drawCircle(
                    action.center.x * scaleX,
                    action.center.y * scaleY,
                    action.radius * scaleX,
                    paint
                )
            }

            is DrawAction.DrawText -> {
                val textPaint = android.graphics.Paint().apply {
                    color = action.color.toArgb()
                    textSize = action.textSize * scaleX
                    isAntiAlias = true
                }
                val bgPaint = android.graphics.Paint().apply {
                    color = action.bgColor.copy(alpha = 0.5f).toArgb()
                    style = android.graphics.Paint.Style.FILL
                }

                val fm = textPaint.fontMetrics
                val textWidth = textPaint.measureText(action.text)
                val padding = action.textSize * scaleX * 0.25f

                val sx = action.position.x * scaleX
                val sy = action.position.y * scaleY

                if (action.bgColor != Color.Transparent) {
                    canvas.drawRect(
                        sx - padding,
                        sy + fm.ascent - padding,
                        sx + textWidth + padding,
                        sy + fm.descent + padding,
                        bgPaint
                    )
                }
                canvas.drawText(action.text, sx, sy, textPaint)
            }
        }
    }

    // 2) Downscale and encode with a size budget.
    val maxBytes = 5 * 1024 * 1024 // 5 MiB
    val downscaled = buildScaledBitmapIfNeeded(resultBmp, maxDimension = 2560)
    val encoded = encodeBestUnderLimit(downscaled, maxBytes)

    // 3) Write to MediaStore
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

    val filename = "$nextIndex${encoded.extension}"

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, encoded.mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let { outUri ->
        context.contentResolver.openOutputStream(outUri)?.use { out ->
            out.write(encoded.bytes)
            out.flush()
        }
        Toast.makeText(context, "Saved to: $relativePath$filename", Toast.LENGTH_SHORT).show()
    }
}

