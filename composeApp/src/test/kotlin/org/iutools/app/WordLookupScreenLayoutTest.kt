package org.iutools.app

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Regression test for a layout bug introduced while fixing the
 * SelectionContainer/TextField crash (see RealAndroidContext's header
 * comment in WordLookupScreen.kt): splitting the screen's single
 * SelectionContainer around word_input left the second half wrapping
 * several sibling composables (the lenient switch Row, the find-word
 * Button, dictionary/decomposition/Hansard sections...) directly, with no
 * Column arranging them -- SelectionContainer doesn't stack multiple
 * children vertically the way a Column does, so everything below
 * word_input rendered on top of itself at the same position (confirmed
 * visually by Alain on the emulator: overlapping, unreadable text). Fixed
 * by wrapping that segment's children in their own Column, same as the
 * first segment (inside SettingsDialog's own split) already had.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordLookupScreenLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // WordLookupScreen loads the API key eagerly (see its apiKey comment),
    // which needs a real Android Keystore that Robolectric can't simulate --
    // see AppSettings.securePrefsFactory and AppSettingsTest.kt's own header
    // comment for the full reasoning.
    @Before
    fun useFakeSecurePrefs() {
        AppSettings.securePrefsFactory = { context -> context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE) }
    }

    @After
    fun restoreRealSecurePrefsFactory() {
        AppSettings.resetSecurePrefsFactoryToDefault()
    }

    @Test
    fun sectionsBelowWordInput_stackVertically_ratherThanOverlapping() {
        composeTestRule.setContent { WordLookupScreen() }

        val wordInputBounds = composeTestRule.onNodeWithTag("word_input").fetchSemanticsNode().boundsInRoot
        val findButtonBounds = composeTestRule.onNodeWithTag("find_word_button").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "expected find_word_button (top=${findButtonBounds.top}) to render below " +
                "word_input (bottom=${wordInputBounds.bottom}), not stacked on top of it",
            findButtonBounds.top >= wordInputBounds.bottom,
        )
    }
}
