package com.jovi.photoai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jovi.photoai.ui.PhotographyDirectorApp
import com.jovi.photoai.ui.design.PhotoDirectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoDirectorTheme {
                PhotographyDirectorApp()
            }
        }
    }
}
