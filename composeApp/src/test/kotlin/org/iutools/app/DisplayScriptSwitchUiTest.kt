package org.iutools.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Rendered-UI check that the display-script setting (Roman/Syllabic/As entered)
 * actually reaches a dictionary result's headword -- per the TODO in
 * doc/spike-llm-local-iutools-mobile.md's Phase 3 Spalding section.
 *
 * Deliberately built on the Spalding lookup, not the morphological analyzer's
 * decomposition table: Spalding lookups are synchronous (a local Map read, see
 * SpaldingDictionary.kt), so unlike the dropped test documented in
 * LanguageSwitchUiTest.kt's header comment, there's no background coroutine to
 * get stuck -- the result is available the instant the button click returns, no
 * waitUntil needed.
 *
 * Doesn't assert the exact syllabic string (that's TransCoder's job, already
 * tested elsewhere in :cli) -- just that the displayed word actually changes
 * when the setting changes, same "compare before/after, don't hardcode a guess"
 * approach as LinguisticDataMeaningTest.kt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayScriptSwitchUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dictionaryResultWord_switchesToSyllabic_whenSyllabicIsChosen() {
        composeTestRule.setContent { DecomposerScreen() }

        composeTestRule.onNodeWithTag("word_input").performTextInput("igalaaq")
        composeTestRule.onNodeWithTag("find_word_button").performClick()

        composeTestRule.onNodeWithTag("dictionary_result_word").assertTextEquals("igalaaq")

        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.onNodeWithText("Syllabic").performClick()
        // A testTag, not text matching, for the same reason noted in
        // LanguageSwitchUiTest.kt: querying text still inside the open dialog that
        // just changed proved unreliable under this Robolectric/Compose-test setup.
        composeTestRule.onNodeWithTag("settings_dialog_close_button").performClick()

        val syllabicText = composeTestRule.onNodeWithTag("dictionary_result_word")
            .fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(separator = "") { it.text }
            .orEmpty()

        assertTrue("expected non-blank syllabic text, got: '$syllabicText'", syllabicText.isNotBlank())
        assertNotEquals("igalaaq", syllabicText)
    }
}
