package org.iutools.app

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Regression test for Alain's report: "Guess Meaning" then the back arrow
 * landed on an empty search screen instead of the word's card as it was.
 * Root cause was MainActivity.kt's `when (screen) { ... }` destroying and
 * recreating WordLookupScreen's whole composition on every switch -- any
 * plain `remember`-backed state inside it (the analyzed word, its
 * decomposition, dictionary results) was lost and rebuilt from scratch.
 * Fixed by hoisting that state into WordLookupScreenState, created by the
 * caller (MainActivity) instead of WordLookupScreen itself -- see that
 * class's header comment in WordLookupScreen.kt.
 *
 * This test reproduces the composition teardown/recreation directly, the
 * same way MainActivity.kt's `when (screen) { ... }` does it -- a
 * mutableStateOf switch, read within a single setContent (Compose test
 * rules only allow calling setContent once per test), between
 * WordLookupScreen and unrelated stand-in content sharing one
 * WordLookupScreenState instance -- rather than needing MainActivity or
 * real navigation.
 *
 * Built on the Spalding lookup, not the morphological analyzer's
 * decomposition table, for the same reason as DisplayScriptSwitchUiTest.kt:
 * Spalding lookups are synchronous, so there's no background coroutine that
 * could get stuck resuming under this project's Robolectric/Compose-test
 * setup (see LanguageSwitchUiTest.kt's header comment).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordLookupScreenStateUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sharedState_survivesComposableTeardownAndRecreation() {
        val screenState = WordLookupScreenState()
        val showWordLookup = mutableStateOf(true)
        composeTestRule.setContent {
            if (showWordLookup.value) {
                WordLookupScreen(screenState = screenState)
            } else {
                Text("Guess Meaning screen stand-in")
            }
        }

        composeTestRule.onNodeWithTag("word_input").performTextInput("igalaaq")
        composeTestRule.onNodeWithTag("find_word_button").performClick()
        composeTestRule.onNodeWithTag("dictionary_result_word_0").assertTextEquals("igalaaq")

        // Simulates navigating to Guess Meaning: WordLookupScreen leaves the
        // composition entirely, which is what disposes its state.
        showWordLookup.value = false
        composeTestRule.waitForIdle()

        // Simulates navigating back: a fresh WordLookupScreen composable,
        // but the same screenState instance. dictionary_result_word_0 is
        // driven by screenState.dictionaryResults (not by re-reading the
        // text field), so this alone confirms the hoisted state, not just
        // the text field's content, survived.
        showWordLookup.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dictionary_result_word_0").assertTextEquals("igalaaq")
    }
}
