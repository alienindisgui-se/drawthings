package com.example.drawthings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object EditorConstants {
    const val MAX_COLLAGE_ITEMS = 10
    const val MAX_EXPORT_BYTES = 3 * 1024 * 1024
    const val MAX_COLLAGE_BYTES = 10 * 1024 * 1024
    const val MAX_DOWNSCALE_DIMENSION = 2560
    const val DEFAULT_STROKE_WIDTH = 5f
    const val DEFAULT_TEXT_SIZE = 80f
    const val BORDER_STROKE_WIDTH = 20f
    const val ARROW_LENGTH = 50f
    const val OVERLAY_TEXT_PADDING_FACTOR = 0.25f
    const val OVERLAY_POSITION_X = 30f
    const val OVERLAY_POSITION_Y = 100f
    val CIRCLE_PRESETS = listOf(10f, 25f, 40f, 60f, 80f, 100f, 130f, 170f, 220f, 300f)
    val EXPORT_QUALITIES = listOf(95, 85, 75, 65, 55, 45, 35, 25, 15)
    val COLOR_PICKER_COLORS = listOf(Color.Red, Color.Blue, Color.Green, Color.White, Color.Black)
    val COLOR_PICKER_ITEM_SIZE = 36.dp
    val COLOR_PICKER_ITEM_PADDING = 4.dp
    const val STROKE_WIDTH_MIN = 5f
    const val STROKE_WIDTH_MAX = 10f
    const val TEXT_SIZE_MIN = 20f
    const val TEXT_SIZE_MAX = 200f
}
