package org.iutools.morph.rerank

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Cross-language parity check against data/grammar/reference-reranker/
 * reranker_golden_fixture.json -- a sample of words with their full
 * candidate lists, the Python re-ranker's own computed feature vectors,
 * and its model scores. This is the guard against the very kind of
 * Python/Kotlin drift this port exists to eliminate: if
 * ReferenceRerankerFeatureNames.ORDER or the feature formulas in
 * ReferenceRerankerFeatures.kt ever diverge from
 * data/grammar/fst/build_reranker_table.py's features()/add_relative_features(),
 * this test catches it -- feature-by-feature, not just "the final score
 * happened to come out close."
 */
class ReferenceRerankerParityTest {

    private data class FixtureRow(val word: String, val parts: List<MorphemePart>, val x: DoubleArray, val score: Double)

    private fun loadFixture(): List<FixtureRow> {
        val stream = javaClass.getResourceAsStream("/reranker_golden_fixture.json")
            ?: error("Missing test resource /reranker_golden_fixture.json -- see apps/cli/build.gradle.kts")
        val root: JsonNode = stream.use { jacksonObjectMapper().readTree(it) }
        return root.map { row ->
            val parts = row["parts"].map { p -> MorphemePart(p[0].asText(), p[1].asText()) }
            val x = DoubleArray(row["x"].size()) { i -> row["x"][i].asDouble() }
            FixtureRow(row["word"].asText(), parts, x, row["score"].asDouble())
        }
    }

    @Test
    fun computeFeatureVectors_matchesThePythonModelsOwnFeatureVectors_exactly() {
        val fixture = loadFixture()
        val byWord = fixture.groupBy { it.word }
        assertTrue(byWord.isNotEmpty(), "fixture should not be empty")

        var checked = 0
        for ((word, rows) in byWord) {
            val vectors = computeFeatureVectors(word, rows.map { it.parts })
            assertEquals(rows.size, vectors.size, "word=$word")
            for (i in rows.indices) {
                assertEquals(
                    rows[i].x.toList(), vectors[i].toList(),
                    "word=$word candidate=$i parts=${rows[i].parts}",
                )
                checked++
            }
        }
        assertTrue(checked >= 100, "expected a substantial fixture, only checked $checked rows")
    }

    @Test
    fun modelScore_matchesThePythonModelsOwnScore_withinFloatingPointTolerance() {
        val fixture = loadFixture()
        val byWord = fixture.groupBy { it.word }
        val model = ReferenceRerankerModel.instance

        for ((word, rows) in byWord) {
            val vectors = computeFeatureVectors(word, rows.map { it.parts })
            for (i in rows.indices) {
                val kotlinScore = model.score(vectors[i])
                assertTrue(
                    kotlin.math.abs(kotlinScore - rows[i].score) < 1e-6,
                    "word=$word candidate=$i: python=${rows[i].score} kotlin=$kotlinScore",
                )
            }
        }
    }
}
