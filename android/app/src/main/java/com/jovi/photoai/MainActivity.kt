package com.jovi.photoai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jovi.photoai.ui.PhotographyDirectorApp
import com.jovi.photoai.ui.design.PhotoDirectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoDirectorTheme {
                PhotographyDirectorApp()
            }
        }
    }
}
