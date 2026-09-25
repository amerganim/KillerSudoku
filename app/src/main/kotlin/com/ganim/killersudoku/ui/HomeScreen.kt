package com.ganim.killersudoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.game.label

@Composable
fun HomeScreen(canContinue: Boolean, onContinue: () -> Unit, onNew: (Difficulty) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Killer Sudoku", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text("Every puzzle solvable by logic alone", fontSize = 14.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        if (canContinue) {
            HomeButton("Continue", primary = true, onClick = onContinue)
            Spacer(Modifier.height(20.dp))
        }
        Difficulty.entries.forEach { d ->
            HomeButton("New ${d.label()}", primary = false) { onNew(d) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HomeButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        color = if (primary) colors.onPrimary else colors.onSurfaceVariant,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
