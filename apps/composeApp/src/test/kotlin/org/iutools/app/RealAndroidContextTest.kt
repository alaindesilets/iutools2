package org.iutools.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Regression test for Alain's report: long-pressing to paste into the API
 * key field crashed every time on his Samsung phone. Root cause: that field
 * (and WordLookupScreen's own word-search field) rendered under
 * LocalizedContent's synthetic, locale-only Context -- fine for
 * stringResource()-based labels, but OutlinedTextField's own platform text
 * editing (the floating copy/paste toolbar) needs LocalContext.current to
 * resolve back to a real Activity, which that synthetic Context can't do.
 * Fixed with RealAndroidContext, which restores the real context around
 * such widgets (see WordLookupScreen.kt's own header comments on both).
 *
 * This test locks in the follow-on requirement the fix depends on: a label
 * resolved with localizedStringResource() *before* entering
 * RealAndroidContext must still reflect the chosen uiLanguage, even though
 * the RealAndroidContext-wrapped widget it's shown in no longer sees the
 * synthetic locale Context at all. Pre-resolving into a captured String
 * value (as every RealAndroidContext call site in this app does) is what
 * makes that work -- a live stringResource() call made *inside*
 * RealAndroidContext would NOT (it would silently fall back to the device's
 * own locale), which is exactly the mistake this test guards against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RealAndroidContextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun labelResolvedBeforeEnteringRealAndroidContext_staysInTheChosenLanguage() {
        composeTestRule.setContent {
            LocalizedContent(AppLanguage.FRENCH) {
                val label = localizedStringResource(AppLanguage.FRENCH, R.string.close_button)
                RealAndroidContext {
                    Text(label)
                }
            }
        }

        // close_button is "Fermer" in French, "Close" in English -- Robolectric's
        // default test locale is English, so this only passes if the label was
        // genuinely resolved in French despite RealAndroidContext restoring the
        // real (unlocalized) context around where it's displayed.
        composeTestRule.onNodeWithText("Fermer").assertExists()
    }
}
