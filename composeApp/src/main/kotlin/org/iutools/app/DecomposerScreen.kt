package org.iutools.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.iutools.morph.MorphologicalAnalyzerException
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import java.util.concurrent.TimeoutException

private sealed interface DecomposeState {
    data object Idle : DecomposeState
    data object Loading : DecomposeState
    data class Success(val word: String, val decompositions: List<String>) : DecomposeState
    data class Failure(val message: String) : DecomposeState
}

@Composable
fun DecomposerScreen() {
    val analyzer = remember { MorphologicalAnalyzer_R2L() }
    val scope = rememberCoroutineScope()

    var word by remember { mutableStateOf("") }
    var lenient by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<DecomposeState>(DecomposeState.Idle) }

    fun decompose() {
        val wordToAnalyze = word.trim()
        if (wordToAnalyze.isEmpty()) return
        state = DecomposeState.Loading
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    val decomps = analyzer.decomposeWord(wordToAnalyze, lenient)
                    DecomposeState.Success(wordToAnalyze, decomps.map { it.toString() })
                } catch (e: TimeoutException) {
                    DecomposeState.Failure("La commande a expiré (timeout).")
                } catch (e: MorphologicalAnalyzerException) {
                    DecomposeState.Failure("Erreur d'analyse : ${e.message}")
                }
            }
            state = result
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
                        Text("${s.decompositions.size} décomposition(s) pour « ${s.word} » :")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(s.decompositions) { decomp ->
                                Text(
                                    text = decomp,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
