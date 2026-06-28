package com.example.drawthings

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    when (currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                onEditImage = { currentScreen = Screen.Editor },
                onCreateCollage = { currentScreen = Screen.Collage }
            )
        }
        is Screen.Editor -> {
            if (selectedImageUri != null) {
                ImageEditorScreen(
                    initialImageUri = selectedImageUri,
                    onBack = { currentScreen = Screen.Home }
                )
            } else {
                ImageEditorScreen(
                    onBack = { currentScreen = Screen.Home }
                )
            }
        }
        is Screen.Collage -> {
            CollageScreen(
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}
