package org.iutools.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.Morpheme
import org.iutools.morph.Decomposition
import org.iutools.morph.MorphologicalAnalyzerException
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import java.util.concurrent.TimeoutException

private const val PREVIEW_LIMIT = 3

private data class MorphemeRow(
    val surfaceForm: String,
    val morphemeId: String,
    val meaning: String,
    val fullRecord: Morpheme?,
)

private sealed interface DecomposeState {
    data object Idle : DecomposeState
    data object Loading : DecomposeState
    data class Success(
        val word: String,
        val lenient: Boolean,
        val decompositions: List<List<MorphemeRow>>,
        val hasMore: Boolean,
    ) : DecomposeState
    data class Failure(val message: String) : DecomposeState
}

private fun Decomposition.toMorphemeRows(): List<MorphemeRow> {
    val linguisticData = LinguisticData.getInstance()
    val surfaceForms = surfaceForms()
    val morphemeIds = getMorphemes()
    return surfaceForms.indices.map { i ->
        val morphemeId = morphemeIds[i]
        val morpheme = linguisticData.getMorpheme(morphemeId)
        val meaning = morpheme?.frenchMeaning?.takeIf { it.isNotBlank() }
            ?: morpheme?.englishMeaning?.takeIf { it.isNotBlank() }
            ?: "(sens inconnu)"
        MorphemeRow(
            surfaceForm = surfaceForms[i],
            morphemeId = morphemeId,
            meaning = meaning,
            fullRecord = morpheme,
        )
    }
}

/**
 * Runs the analysis on a background thread. When [expandAll] is false, the
 * analyzer is told to stop as soon as it has found one more decomposition
 * than we display (PREVIEW_LIMIT + 1) -- enough to know whether a "More"
 * button is warranted, without paying for an exhaustive search up front.
 */
private suspend fun analyze(
    analyzer: MorphologicalAnalyzer_R2L,
    word: String,
    lenient: Boolean,
    expandAll: Boolean,
): DecomposeState = withContext(Dispatchers.Default) {
    try {
        val fetchLimit = if (expandAll) Int.MAX_VALUE else PREVIEW_LIMIT + 1
        val decomps = analyzer.stopAfterN(fetchLimit).decomposeWord(word, lenient)
        val hasMore = !expandAll && decomps.size > PREVIEW_LIMIT
        val displayed = if (hasMore) decomps.take(PREVIEW_LIMIT) else decomps.toList()
        DecomposeState.Success(
            word = word,
            lenient = lenient,
            decompositions = displayed.map { it.toMorphemeRows() },
            hasMore = hasMore,
        )
    } catch (e: TimeoutException) {
        DecomposeState.Failure("La commande a expiré (timeout).")
    } catch (e: MorphologicalAnalyzerException) {
        DecomposeState.Failure("Erreur d'analyse : ${e.message}")
    }
}

@Composable
fun DecomposerScreen() {
    val analyzer = remember { MorphologicalAnalyzer_R2L() }
    val scope = rememberCoroutineScope()

    var word by remember { mutableStateOf("") }
    var lenient by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<DecomposeState>(DecomposeState.Idle) }
    val listState = rememberLazyListState()
    var scrollToIndexOnExpand by remember { mutableStateOf<Int?>(null) }

    fun decompose() {
        val wordToAnalyze = word.trim()
        if (wordToAnalyze.isEmpty()) return
        val lenientAtSearch = lenient
        state = DecomposeState.Loading
        scope.launch {
            state = analyze(analyzer, wordToAnalyze, lenientAtSearch, expandAll = false)
        }
    }

    fun loadMore(previous: DecomposeState.Success) {
        // Once the expanded list renders, jump to the first decomposition
        // that wasn't already visible (index PREVIEW_LIMIT, i.e. the 4th)
        // instead of leaving the user scrolled to the top of what they've
        // already seen.
        scrollToIndexOnExpand = PREVIEW_LIMIT
        state = DecomposeState.Loading
        scope.launch {
            state = analyze(analyzer, previous.word, previous.lenient, expandAll = true)
        }
    }

    LaunchedEffect(state) {
        val index = scrollToIndexOnExpand
        if (index != null && state is DecomposeState.Success) {
            scrollToIndexOnExpand = null
            listState.animateScrollToItem(index)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Décomposeur morphologique inuktitut",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = word,
                onValueChange = { word = it },
                label = { Text("Mot à analyser") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { decompose() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Analyse tolérante (lenient-decomps)")
                Switch(checked = lenient, onCheckedChange = { lenient = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { decompose() },
                enabled = word.isNotBlank() && state != DecomposeState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Décomposer")
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val s = state) {
                is DecomposeState.Idle -> {}
                is DecomposeState.Loading -> CircularProgressIndicator()
                is DecomposeState.Failure -> Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                )
                is DecomposeState.Success -> {
                    if (s.decompositions.isEmpty()) {
                        Text("Aucune décomposition trouvée pour « ${s.word} ».")
                    } else {
                        Text(
                            if (s.hasMore) {
                                "Décompositions pour « ${s.word} » (${PREVIEW_LIMIT} premières affichées) :"
                            } else {
                                "${s.decompositions.size} décomposition(s) pour « ${s.word} » :"
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(state = listState) {
                            itemsIndexed(s.decompositions) { index, rows ->
                                if (s.decompositions.size > 1) {
                                    Text(
                                        text = "Décomposition ${index + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                MorphemeTable(rows)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (s.hasMore) {
                                item {
                                    TextButton(
                                        onClick = { loadMore(s) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Plus")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MorphemeTable(rows: List<MorphemeRow>) {
    var selectedRow by remember { mutableStateOf<MorphemeRow?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = "Morphème",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Sens",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { selectedRow = row })
                    .padding(vertical = 8.dp),
            ) {
                Text(text = row.surfaceForm, modifier = Modifier.weight(1f))
                Text(text = row.meaning, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
        }
    }

    selectedRow?.let { row ->
        MorphemeDetailDialog(row = row, onDismiss = { selectedRow = null })
    }
}

@Composable
private fun MorphemeDetailDialog(row: MorphemeRow, onDismiss: () -> Unit) {
    val morpheme = row.fullRecord
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text(row.surfaceForm) },
        text = {
            Column {
                DetailField("Identifiant", row.morphemeId)
                DetailField("Forme canonique", morpheme?.morpheme)
                DetailField("Sens (français)", morpheme?.frenchMeaning)
                DetailField("Sens (anglais)", morpheme?.englishMeaning)
                DetailField("Dialecte", morpheme?.dialect)
            }
        },
    )
}

@Composable
private fun DetailField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(text = value)
    }
}
