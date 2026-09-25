package com.ganim.killersudoku

import android.app.Application
import com.ganim.killersudoku.ui.AppContainer

/** Holds the dependency graph for the process, so a rotation does not rebuild the pack index. */
class KillerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
