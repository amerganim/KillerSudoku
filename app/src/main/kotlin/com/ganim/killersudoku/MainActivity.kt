package com.ganim.killersudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ganim.killersudoku.ui.KillerApp

/** The single activity: the Compose entry point, and the foreground hook for billing. */
class MainActivity : ComponentActivity() {
    private val container get() = (application as KillerApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KillerApp(container) }
    }

    override fun onStart() {
        super.onStart()
        // Query and restore purchases on every launch and return to the app.
        container.onAppForegrounded()
    }
}
