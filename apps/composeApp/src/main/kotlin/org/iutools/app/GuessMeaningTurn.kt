package org.iutools.app

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.iutools.llm.AggregatedBackendStats
import org.iutools.llm.BackendCallStats
import org.iutools.llm.ChatMessage
import org.iutools.llm.GuessMeaningCostLog
import org.iutools.llm.GuessMeaningEngine
import org.iutools.llm.GuessMeaningErrorLabels
import org.iutools.llm.LlmClient_Anthropic
import org.iutools.llm.estimatedCostUsd

/*
 * Connects :core's GuessMeaningEngine to this app for one conversation turn.
 *
 * The engine (build the request, append the reply, branch on errors) and
 * the actual Claude call (LlmClient_Anthropic) both live in :core. This
 * function supplies the client, hands the
 * engine the app's localized error strings, and -- only when a call
 * actually reached the model -- folds the reported cost into the on-screen
 * per-model stats and the persisted spend log.
 *
 * Shared by the inline flow on WordLookupScreen and ExplanationScreen's
 * debug prompt resubmission; both pass their own [onMessagesChanged] to
 * write the message list wherever they keep the conversation.
 */
suspend fun sendGuessMeaningTurn(
    context: Context,
    history: List<ChatMessage>,
    text: String,
    systemPrompt: String,
    useLocalModel: Boolean,
    apiKey: String,
    modelStats: SnapshotStateMap<String, AggregatedBackendStats>,
    unauthorizedErrorMessage: String,
    localModelDisabledMessage: String,
    genericErrorTemplate: String,
    onMessagesChanged: (List<ChatMessage>) -> Unit,
) {
    val engine = GuessMeaningEngine(LlmClient_Anthropic { apiKey })

    val outcome = engine.send(
        history = history,
        userText = text,
        systemPrompt = systemPrompt,
        useLocalModel = useLocalModel,
        labels = GuessMeaningErrorLabels(
            unauthorized = unauthorizedErrorMessage,
            localModelDisabled = localModelDisabledMessage,
            genericTemplate = genericErrorTemplate,
        ),
        onMessagesChanged = onMessagesChanged,
    ) ?: return

    modelStats[outcome.model] = (modelStats[outcome.model] ?: AggregatedBackendStats()) +
        BackendCallStats(
            latencyMs = outcome.latencyMs,
            inputTokens = outcome.inputTokens,
            outputTokens = outcome.outputTokens,
        )

    // Only real (non-cached) online-model calls reach here, same as the
    // stats above -- a cache replay never produces an outcome.
    estimatedCostUsd(outcome.model, outcome.inputTokens, outcome.outputTokens)?.let { costUsd ->
        GuessMeaningCostLog(sharedPreferencesCostLogStore(context)).record(outcome.model, costUsd)
    }
}
