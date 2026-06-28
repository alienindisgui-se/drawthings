package com.example.drawthings

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Editor : Screen("editor")
    object Collage : Screen("collage")
}
