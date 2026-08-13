package org.iutools.app

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Rendered-UI check that the in-app language toggle actually reaches what's
 * on screen -- not just that the underlying string resources resolve
 * correctly per locale in isolation (see GuessMeaningStringsTest and
 * UiStringLocalizationTest for that, much cheaper, layer).
 *
 * Only one scenario here (static Kotlin-defined labels, i.e. stringResource()
 * calls) -- a second scenario for text sourced from the linguistic-data CSVs
 * was attempted (typing a word, running the real analyzer, checking the
 * rendered meaning switches language) but dropped: it needs the real
 * background analysis coroutine (rememberCoroutineScope().launch { ... }
 * withContext(Dispatchers.Default) { ... }) to resume back onto the main
 * composition, which never completed under this project's Robolectric+
 * Compose-test setup -- confirmed genuinely stuck, not just slow, via a
 * throwaway diagnostic (a real 8s Thread.sleep + waitForIdle() still found
 * nothing). Root cause is almost certainly Dispatchers.Main not being wired
 * to something Robolectric can pump correctly under the test's own
 * TestDispatcher; fixing it properly would mean making the analyzer/
 * dispatcher injectable into DecomposerScreen, a bigger production-code
 * change than this test coverage pass warranted. See
 * LinguisticDataMeaningTest.kt instead: same real risk (CSV-derived
 * bilingual text), covered directly and synchronously, no UI/coroutines
 * involved.
 *
 * Real Claude API replies are deliberately NOT covered here, or anywhere in
 * this test suite: verifying "did the AI actually answer in French" needs a
 * live network call with a real API key, which costs real money per request
 * -- that's a manual check, not something a JVM unit test can or should
 * automate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanguageSwitchUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsDialog_switchesStaticKotlinLabels_whenFrenchIsChosen() {
        composeTestRule.setContent { DecomposerScreen() }

        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.onNodeWithText("Français").performClick()

        // Checked via the main screen's own settings_button label (a Kotlin
        // stringResource() call, same mechanism as every other static label)
        // rather than text still inside the open AlertDialog: querying live
        // Dialog content under Robolectric's Compose test harness proved
        // unreliable here even though the underlying state update is correct
        // (confirmed via a throwaway diagnostic assertion during development)
        // -- a harness quirk, not an app bug; this still exercises the same
        // language-switch mechanism without depending on it.
        composeTestRule.onNodeWithTag("settings_button").assertTextContains("Réglages", substring = true)
    }
}
