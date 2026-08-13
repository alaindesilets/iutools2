package org.iutools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Two screens, no navigation library: a bare enum + mutableStateOf is
// enough for this app's size, and avoids pulling in Navigation Compose for
// what's currently a single back-and-forth.
private enum class Screen { WordLookup, GuessMeaning }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.WordLookup) }
                // Created here, not inside WordLookupScreen itself, so it survives
                // switching to Screen.GuessMeaning and back -- see
                // WordLookupScreenState's header comment for why that switch would
                // otherwise wipe the word's card (reported by Alain: "Guess Meaning"
                // then back landed on an empty search screen).
                val wordLookupScreenState = remember { WordLookupScreenState() }
                var guessMeaningSeed by remember { mutableStateOf("") }
                var guessMeaningCacheKey by remember { mutableStateOf<GuessMeaningCacheKey?>(null) }
                // In-memory only (lost on process death), keyed by word+lenient: lets
                // re-opening Guess Meaning for an already-analyzed word replay the past
                // conversation instead of re-calling Claude (and re-billing) for it --
                // see the cache note in GuessMeaningScreen.kt's header comment.
                val guessMeaningConversations = remember { mutableStateMapOf<GuessMeaningCacheKey, List<ChatMessage>>() }
                // The on-screen "<-" button in GuessMeaningScreen only covers a tap;
                // without this, the hardware/gesture back action falls through past
                // our own screen switching and closes the whole app instead.
                BackHandler(enabled = screen == Screen.GuessMeaning) {
                    screen = Screen.WordLookup
                }
                when (screen) {
                    Screen.WordLookup -> WordLookupScreen(
                        screenState = wordLookupScreenState,
                        onOpenGuessMeaning = { cacheKey, seed ->
                            guessMeaningCacheKey = cacheKey
                            guessMeaningSeed = seed
                            screen = Screen.GuessMeaning
                        },
                    )
                    Screen.GuessMeaning -> {
                        val cacheKey = guessMeaningCacheKey
                        val cachedMessages = cacheKey?.let { guessMeaningConversations[it] } ?: emptyList()
                        GuessMeaningScreen(
                            onBack = { screen = Screen.WordLookup },
                            // A cached conversation already carries the original seed as its
                            // first turn -- only pre-fill the input field from scratch when
                            // there's nothing cached yet for this word.
                            initialInput = if (cachedMessages.isEmpty()) guessMeaningSeed else "",
                            initialMessages = cachedMessages,
                            onMessagesChanged = { updated -> cacheKey?.let { guessMeaningConversations[it] = updated } },
                        )
                    }
                }
            }
        }
    }
}
