package com.badiadam.aserver // İŞTE EKSİK OLAN KİMLİK KARTI BUYDU!

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AServer",
        icon = painterResource("icon.png")
    ) {
        App()
    }
}