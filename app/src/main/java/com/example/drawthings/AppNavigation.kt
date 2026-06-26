package com.example.drawthings

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    when (currentScreen) {
        "home" -> HomeScreen(
            onEditImage = { currentScreen = "editor" },
            onCreateCollage = { currentScreen = "collage" }
        )
        "editor" -> {
            if (selectedImageUri != null) {
                ImageEditorScreen(
                    initialImageUri = selectedImageUri,
                    onBack = { currentScreen = "home" }
                )
            } else {
                ImageEditorScreen(
                    onBack = { currentScreen = "home" }
                )
            }
        }
        "collage" -> CollageScreen(
            onBack = { currentScreen = "home" }
        )
    }
}
