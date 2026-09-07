package org.iutools.app

import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.iutools.i18n.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Regression test for Alain's report: SettingsDialog and "Inspecter le
 * prompt" both showed English text even with the app's in-app language set
 * to French. Root cause: AlertDialog (like Dialog/Popup generally) renders
 * into its own separate window with its own AndroidComposeView root, which
 * re-establishes LocalContext/LocalConfiguration from *that window's own*
 * unlocalized context -- a LocalizedContent wrap around the call site (in
 * WordLookupScreen/ExplanationScreen) never reaches inside the dialog's own
 * title/text/button slots, even though it's a plain CompositionLocalProvider
 * and those normally do propagate. Fixed by introducing LocalizedAlertDialog
 * (see WordLookupScreen.kt), which re-wraps each slot individually, placed
 * *inside* the dialog's own composition root where it can actually take
 * effect -- every AlertDialog call in this app now goes through it.
 *
 * Composes fresh with uiLanguage = FRENCH from the start, rather than
 * switching language while a dialog is already open -- LanguageSwitchUiTest's
 * header comment documents that querying a dialog's content right after a
 * *live* state change proved unreliable under this project's Robolectric
 * setup. A fresh composition sidesteps that harness quirk entirely, so this
 * test only exercises what it actually needs to: whether the dialog's own
 * slots resolve the app's chosen language when composed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedAlertDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun slotContent_resolvesTheGivenUiLanguage_notTheDeviceDefault() {
        composeTestRule.setContent {
            LocalizedAlertDialog(
                uiLanguage = AppLanguage.FRENCH,
                onDismissRequest = {},
                confirmButton = { Text(stringResource(R.string.close_button)) },
            )
        }

        // close_button is "Fermer" in French, "Close" in English -- Robolectric's
        // default test locale is English, so seeing "Fermer" here only happens
        // if the AppLanguage.FRENCH passed in above actually reached this slot.
        composeTestRule.onNodeWithText("Fermer").assertExists()
    }
}
