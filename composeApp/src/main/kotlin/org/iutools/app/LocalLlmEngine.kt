package org.iutools.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/*
 * local-llm-spike branch, Phase 6 (see doc/spike-llm-local-iutools-mobile.md):
 * on-device inference for Guess Meaning via LiteRT-LM, as an alternative to
 * the Claude API call in GuessMeaningScreen.kt. Not MediaPipe's tasks-genai --
 * that API is now maintenance-only; LiteRT-LM is Google's own current
 * recommendation, with a native Kotlin API (Engine/Conversation) and
 * Flow-based streaming, so this wrapper is thinner than a MediaPipe one would
 * have been.
 *
 * Model file handling, deliberately minimal for this spike: whether an
 * on-device model even fits in memory at all is still an open, blocking
 * question (see the Phase 6 "Test bloquant" in the plan doc) -- building a
 * full in-app downloader (with Hugging Face auth for gated models like
 * Gemma) only makes sense once that question is answered. For now, getting
 * the model file onto the device is a two-step, human-driven process: (1)
 * download the .litertlm file in the device's own browser (lands in the
 * ordinary Downloads folder, no special access needed), then (2) either
 * `adb push` it to <externalFilesDir>/models/ directly, or -- per Alain's
 * preference, no USB debugging toggle required -- use the "Choisir le
 * fichier modèle" picker button in GuessMeaningScreen.kt, which calls
 * [importModel] below to copy it into that same directory via a content://
 * Uri from the system file picker (Storage Access Framework -- needs no
 * storage permission on modern Android either way).
 *
 * One Engine per call, not a persistent instance reused across calls: engine
 * initialization takes several seconds (loading the whole model into memory),
 * so a persistent engine would answer faster on a second question -- but nothing
 * in this Phase 6 spike depends on that; keeping the lifecycle scoped to a
 * single call is simpler to reason about and to tear down cleanly, and the
 * question this phase asks (does a small local model produce decent guesses,
 * and does it fit in memory at all) doesn't need repeat-call latency to be
 * separately optimized yet.
 */
object LocalLlmEngine {

    private const val MODEL_SUBDIRECTORY = "models"
    private const val MODEL_FILE_EXTENSION = ".litertlm"

    /**
     * Directory this spike expects the model file under: the app's external
     * files directory (no special storage permission needed on API 26+, and
     * reachable with a plain `adb push` -- no `run-as`/root required, unlike
     * the internal files dir).
     */
    fun modelDirectory(context: Context): File = File(context.getExternalFilesDir(null), MODEL_SUBDIRECTORY)

    /**
     * The first `.litertlm` file found in [modelDirectory], or `null` if
     * there isn't one yet. Doesn't assume a specific filename -- which
     * exact model (Gemma variant, quantization) this phase settles on is
     * still an open question (see the plan doc's Phase 6 "Test bloquant"),
     * so whichever single file the human has pushed to the device is used
     * as-is, no renaming required on their end.
     */
    fun findModelFile(context: Context): File? =
        modelDirectory(context).listFiles { file -> file.isFile && file.name.endsWith(MODEL_FILE_EXTENSION) }
            ?.firstOrNull()

    /**
     * Copies the file behind [sourceUri] (as handed back by the system file
     * picker -- see GuessMeaningScreen.kt) into [modelDirectory], replacing
     * any `.litertlm` file already there (findModelFile() only ever expects
     * one). Kept under its original display name (queried from the picker's
     * content resolver, e.g. "Qwen2_0.5B_Instruct.litertlm") rather than a
     * fixed name -- once per-model performance stats mattered (see
     * ModelStats.kt), the filename became the only practical way to tell
     * which model a set of stats belongs to, so it's worth preserving. Falls
     * back to a generic name if the source doesn't report one, or if the
     * reported name is unsafe to use as a bare filename.
     *
     * Blocking (synchronous file I/O) -- callers should run this off the
     * main thread.
     *
     * @throws IOException if [sourceUri] can't be opened for reading.
     */
    fun importModel(context: Context, sourceUri: Uri) {
        val directory = modelDirectory(context)
        directory.mkdirs()
        findModelFile(context)?.delete()

        val destination = File(directory, destinationFileName(context, sourceUri))
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Could not open the selected file: $sourceUri")
        input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
    }

    private fun destinationFileName(context: Context, sourceUri: Uri): String {
        val displayName = context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        // Guard against a name that isn't a plain filename (path separators,
        // empty, or missing entirely) -- this becomes a File() path segment
        // below, so anything odd falls back rather than being trusted as-is.
        val safeName = displayName?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') }
        return when {
            safeName == null -> "model$MODEL_FILE_EXTENSION"
            safeName.endsWith(MODEL_FILE_EXTENSION) -> safeName
            else -> "$safeName$MODEL_FILE_EXTENSION"
        }
    }

    /**
     * Runs [userMessage] against the on-device model, with [systemPrompt] as
     * the conversation's system instruction -- the same prompt already
     * validated against Claude in earlier phases (see
     * GuessMeaningScreen.kt's chat_system_prompt), reused as-is per the plan
     * doc's Phase 6 step 28, for a direct comparison rather than a
     * differently-tuned prompt.
     *
     * Streams the response as a [Flow] of [LocalGenerationEvent] -- a
     * [LocalGenerationEvent.Chunk] per incremental text delta (not the
     * cumulative text-so-far -- confirmed against LiteRT-LM's own Kotlin
     * example, which prints each chunk as it arrives without clearing
     * previous output), so the caller can show progress while the model is
     * still generating (local inference can be markedly slower than the
     * Claude network call), followed by exactly one
     * [LocalGenerationEvent.Done] carrying prefill/decode token counts for
     * this turn -- see ModelStats.kt, which these feed into for the
     * input/output-token comparison against Claude that Alain asked for.
     *
     * Loads and tears down a fresh [Engine] for this single call (see the
     * file header comment for why). [modelPath] must already exist --
     * callers should check [findModelFile] first and show a clear message
     * if it's missing, rather than let this throw.
     */
    fun generate(
        modelPath: String,
        systemPrompt: String,
        userMessage: String,
    ): Flow<LocalGenerationEvent> = flow {
        // Benchmark info (see below) is only populated when this is enabled
        // at Engine-creation time -- a global flag on the SDK's own
        // ExperimentalFlags object, not per-instance, but harmless here since
        // this app only ever has one Engine in flight at a time (see the file
        // header comment on why each call gets its own fresh Engine).
        @OptIn(ExperimentalApi::class)
        ExperimentalFlags.enableBenchmark = true
        val engine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
        try {
            // Documented as potentially taking ~10s -- must not run on the
            // main thread. flowOn(Dispatchers.IO) below covers emit(), but
            // this blocking call needs its own withContext since it isn't
            // reached via emit.
            withContext(Dispatchers.IO) { engine.initialize() }

            val conversation = engine.createConversation(
                ConversationConfig(systemInstruction = Contents.of(systemPrompt)),
            )
            try {
                conversation.sendMessageAsync(userMessage).collect { chunk ->
                    emit(LocalGenerationEvent.Chunk(chunk.toString()))
                }
                // lastPrefillTokenCount covers the whole prefill for this
                // turn (system instruction + userMessage, since each call
                // starts a brand-new Conversation -- see the file header
                // comment), i.e. the closest available equivalent to
                // Claude's response.usage().inputTokens(). Likewise
                // lastDecodeTokenCount for outputTokens.
                @OptIn(ExperimentalApi::class)
                val benchmark = conversation.getBenchmarkInfo()
                emit(LocalGenerationEvent.Done(benchmark.lastPrefillTokenCount, benchmark.lastDecodeTokenCount))
            } finally {
                conversation.close()
            }
        } finally {
            engine.close()
        }
    }.flowOn(Dispatchers.IO)
}

sealed interface LocalGenerationEvent {
    data class Chunk(val text: String) : LocalGenerationEvent
    data class Done(val prefillTokens: Int, val decodeTokens: Int) : LocalGenerationEvent
}
