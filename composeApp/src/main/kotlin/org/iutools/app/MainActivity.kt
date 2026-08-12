package org.iutools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Two screens, no navigation library: a bare enum + mutableStateOf is
// enough for this app's size, and avoids pulling in Navigation Compose for
// what's currently a single back-and-forth.
private enum class Screen { Decomposer, GuessMeaning }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.Decomposer) }
                when (screen) {
                    Screen.Decomposer -> DecomposerScreen(onOpenGuessMeaning = { screen = Screen.GuessMeaning })
                    Screen.GuessMeaning -> GuessMeaningScreen(onBack = { screen = Screen.Decomposer })
                }
            }
        }
    }
}
