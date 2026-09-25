package com.ganim.killersudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.game.GameScreen
import com.ganim.killersudoku.game.GameViewModel
import com.ganim.killersudoku.ui.HomeScreen
import com.ganim.killersudoku.ui.theme.KillerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KillerTheme {
                val vm: GameViewModel = viewModel()
                var inGame by rememberSaveable { mutableStateOf(false) }
                var lastDifficulty by rememberSaveable { mutableStateOf(Difficulty.EASY) }

                // After process death the view model is new: reload the saved game.
                if (inGame && vm.ui == null && !vm.resume()) inGame = false

                if (inGame) {
                    BackHandler { inGame = false }
                    GameScreen(
                        vm,
                        onHome = { inGame = false },
                        onNewGame = { vm.newGame(lastDifficulty) },
                    )
                } else {
                    HomeScreen(
                        canContinue = vm.hasSavedGame,
                        onContinue = { if (vm.resume()) inGame = true },
                        onNew = { d ->
                            lastDifficulty = d
                            vm.newGame(d)
                            inGame = true
                        },
                    )
                }
            }
        }
    }
}
