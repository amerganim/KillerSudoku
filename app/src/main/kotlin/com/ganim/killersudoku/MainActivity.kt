package com.ganim.killersudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ganim.killersudoku.ui.KillerApp

/** The single activity: the Compose entry point, nothing else. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KillerApplication).container
        setContent { KillerApp(container) }
    }
}
