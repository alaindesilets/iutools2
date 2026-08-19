package org.iutools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// Three screens, no navigation library: a bare enum + mutableStateOf is
// enough for this app's size, and avoids pulling in Navigation Compose.
// GuessMeaning ("Advanced", the old manual chat/prompt-tuning screen) has no
// button leading to it anymore, per Alain's request -- it's currently
// unreachable, kept rather than deleted since it's still the only place with
// a local-model backend toggle. Explanation ("Explications") replaced it as
// the debug prompt-tuning entry point (see ExplanationScreen.kt's
// "Inspecter le prompt").
private enum class Screen { WordLookup, GuessMeaning, Explanation }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Established once, here, before any screen has a chance to
                // swap LocalContext.current for a locale override (see
                // WordLookupScreen.kt's LocalizedContent/RealAndroidContext)
                // -- LocalContext.current is guaranteed to still be the real
                // Activity context at this exact point, since nothing above
                // this line touches it.
                CompositionLocalProvider(LocalRealAndroidContext provides LocalContext.current) {
                var screen by remember { mutableStateOf(Screen.WordLookup) }
                // Created here, not inside WordLookupScreen itself, so it survives
                // switching away and back -- see WordLookupScreenState's header
                // comment for why that switch would otherwise wipe the word's card
                // (reported by Alain: "Guess Meaning" then back landed on an empty
                // search screen).
                val wordLookupScreenState = remember { WordLookupScreenState() }
                var guessMeaningSeed by remember { mutableStateOf("") }
                var guessMeaningCacheKey by remember { mutableStateOf<GuessMeaningCacheKey?>(null) }
                // The attempt ExplanationScreen was opened for -- set by
                // WordLookupScreen's onOpenExplanation (see GuessMeaningSection's
                // onExplain) right before switching to Screen.Explanation.
                var explanationKey by remember { mutableStateOf<GuessMeaningConversationKey?>(null) }
                var explanationWordInfo by remember { mutableStateOf<WordInfoSnapshot?>(null) }
                // local-llm-spike branch: which backend Guess Meaning uses, hoisted up
                // to here (not local state inside GuessMeaningScreen) so it's "sticky"
                // across words instead of resetting to Claude every time the screen is
                // re-entered -- what you want while going back and forth comparing
                // backends on several words in a row.
                var useLocalModel by remember { mutableStateOf(false) }
                // In-memory only (lost on process death). Keyed by GuessMeaningConversationKey
                // (word+lenient+backend+system prompt+seed, not just word+lenient) so
                // that re-opening a word replays the matching past attempt instead of
                // re-calling that backend for it, and so that trying a different backend
                // or a tweaked prompt for the *same* word creates its own entry rather
                // than colliding with or replaying a previous attempt -- see
                // GuessMeaningEngine.kt's header comment. Owned here, shared by
                // WordLookupScreen (the normal inline flow, see GuessMeaningSection in
                // GuessMeaningInline.kt), ExplanationScreen (its debug-only prompt
                // resubmission), and GuessMeaningScreen, so any of them picks up an
                // attempt another one started rather than starting over or diverging.
                val guessMeaningConversations = remember { mutableStateMapOf<GuessMeaningConversationKey, List<ChatMessage>>() }
                // In-memory only, per Alain's request to track average latency/tokens
                // per backend while comparing Claude and the local model -- see
                // ModelStats.kt. Owned here so stats accumulate across every word
                // tried in a session, not just the current one.
                val guessMeaningModelStats = remember { mutableStateMapOf<String, AggregatedBackendStats>() }
                // The on-screen "<-" button on Explanation/GuessMeaning only covers a
                // tap; without this, the hardware/gesture back action falls through
                // past our own screen switching and closes the whole app instead.
                BackHandler(enabled = screen != Screen.WordLookup) {
                    screen = Screen.WordLookup
                }
                when (screen) {
                    Screen.WordLookup -> WordLookupScreen(
                        screenState = wordLookupScreenState,
                        onOpenExplanation = { key, wordInfo ->
                            explanationKey = key
                            explanationWordInfo = wordInfo
                            screen = Screen.Explanation
                        },
                        guessMeaningConversations = guessMeaningConversations,
                        useLocalModel = useLocalModel,
                        guessMeaningModelStats = guessMeaningModelStats,
                    )
                    Screen.GuessMeaning -> GuessMeaningScreen(
                        onBack = { screen = Screen.WordLookup },
                        wordCacheKey = guessMeaningCacheKey,
                        seed = guessMeaningSeed,
                        conversations = guessMeaningConversations,
                        useLocalModel = useLocalModel,
                        onUseLocalModelChanged = { useLocalModel = it },
                        modelStats = guessMeaningModelStats,
                    )
                    Screen.Explanation -> {
                        // Both are always set together right before switching here
                        // (see onOpenExplanation above) -- null-checked instead of
                        // forced with !! purely so an unexpected direct entry (e.g.
                        // process restore mid-navigation) falls back to WordLookup
                        // instead of crashing.
                        val key = explanationKey
                        val wordInfo = explanationWordInfo
                        if (key != null && wordInfo != null) {
                            ExplanationScreen(
                                conversationKey = key,
                                wordInfo = wordInfo,
                                conversations = guessMeaningConversations,
                                modelStats = guessMeaningModelStats,
                                onBack = { screen = Screen.WordLookup },
                            )
                        } else {
                            screen = Screen.WordLookup
                        }
                    }
                }
                }
            }
        }
    }
}
