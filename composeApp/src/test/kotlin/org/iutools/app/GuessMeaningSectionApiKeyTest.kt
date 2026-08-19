package org.iutools.app

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.anthropic.models.messages.MessageParam
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Regression test for Alain's report: after entering and saving a new API
 * key in Settings, tapping "Deviner le sens" again on WordLookupScreen still
 * redirected back to Settings. Root cause: GuessMeaningSection read the key
 * itself via a keyless `remember { AppSettings.loadApiKey(context) }`, which
 * captured the empty pre-save value once and never re-read it -- Settings is
 * a sibling composable within the same still-mounted WordLookupScreen, so it
 * never tore GuessMeaningSection down and rebuilt it, the only thing that
 * would have made that `remember` recompute. Fixed by turning apiKey into a
 * parameter GuessMeaningSection receives fresh on every recomposition,
 * sourced from WordLookupScreen's own live state (see that file's
 * GuessMeaningSection call site).
 *
 * Exercises GuessMeaningSection directly rather than the full WordLookupScreen
 * + Settings flow: reaching the "no dictionary result, Guess Meaning button
 * shown" state through the real UI needs the analyzer/dictionary background
 * coroutines to resume, which don't reliably complete under this project's
 * Robolectric/Compose-test setup (see LanguageSwitchUiTest.kt's header
 * comment) -- GuessMeaningSection's own apiKey handling is independently
 * testable without any of that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuessMeaningSectionApiKeyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingButton_afterApiKeyIsSetOnARecomposition_noLongerRedirectsToSettings() {
        val wordCacheKey = GuessMeaningCacheKey("qanuippit", lenient = false)
        val conversations = mutableStateMapOf<GuessMeaningConversationKey, List<ChatMessage>>()
        val modelStats = mutableStateMapOf<String, AggregatedBackendStats>()
        var needApiKeyCallCount = 0
        val apiKeyState = mutableStateOf("")

        composeTestRule.setContent {
            GuessMeaningSection(
                wordCacheKey = wordCacheKey,
                seed = "seed text",
                uiLanguage = AppLanguage.FRENCH,
                conversations = conversations,
                useLocalModel = false,
                modelStats = modelStats,
                onExplain = {},
                onNeedApiKey = { needApiKeyCallCount++ },
                apiKey = apiKeyState.value,
            )
        }

        composeTestRule.onNodeWithTag("guess_meaning_button").performClick()
        assertEquals(1, needApiKeyCallCount)

        // Simulates what saving a new key via Settings does in production --
        // WordLookupScreen's own apiKey state changes, which recomposes this
        // still-mounted GuessMeaningSection with the new value.
        apiKeyState.value = "sk-ant-test-key"
        composeTestRule.waitForIdle()

        // Pre-seeds a cached reply for the attempt this click would look up,
        // so it passes the apiKey check (what this test is about) but then
        // finds a non-empty conversation and skips send() -- no real network
        // call, keeping this test hermetic while still exercising the exact
        // branch the bug lived in.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val systemPrompt = context.getString(R.string.chat_system_prompt)
        val key = GuessMeaningConversationKey(wordCacheKey, false, AppLanguage.FRENCH, systemPrompt, "seed text")
        conversations[key] = listOf(
            ChatMessage(role = MessageParam.Role.ASSISTANT, text = "Candidate meanings:\n- foo"),
        )

        composeTestRule.onNodeWithTag("guess_meaning_button").performClick()
        assertEquals(1, needApiKeyCallCount)
    }
}
