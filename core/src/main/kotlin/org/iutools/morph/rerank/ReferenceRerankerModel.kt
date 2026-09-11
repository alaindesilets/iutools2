package org.iutools.morph.rerank

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/*
 * The frozen R2L reference@1 re-ranker model: a small gradient-boosted
 * ensemble of decision trees, loaded once from the
 * data/grammar/reference-reranker/reranker_model.json classpath resource
 * (see that directory's README for how it was trained and exported).
 *
 * Trees are already expressed in raw feature-value thresholds (not the
 * quantile-binned form the Python training code used internally), so
 * scoring here is a plain "smaller than the threshold? go left" walk --
 * see the README's "Why trees with raw thresholds" for why that
 * conversion is safe.
 */

private sealed interface TreeNode {
    data class Leaf(val value: Double) : TreeNode
    data class Split(val featureIndex: Int, val threshold: Double, val left: TreeNode, val right: TreeNode) : TreeNode
}

private fun TreeNode.predict(x: DoubleArray): Double = when (this) {
    is TreeNode.Leaf -> value
    is TreeNode.Split -> (if (x[featureIndex] < threshold) left else right).predict(x)
}

class ReferenceRerankerModel private constructor(
    val featureNames: List<String>,
    private val learningRate: Double,
    private val trees: List<TreeNode>,
) {
    /** F(x) = learningRate * sum(tree(x) for tree in trees) -- higher is a
     *  better guess for the word's Hansard-attested reading. */
    fun score(x: DoubleArray): Double = learningRate * trees.sumOf { it.predict(x) }

    companion object {
        private const val RESOURCE_PATH = "/reranker_model.json"

        val instance: ReferenceRerankerModel by lazy { load() }

        private fun load(): ReferenceRerankerModel {
            val stream = ReferenceRerankerModel::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: error("Missing classpath resource $RESOURCE_PATH -- is data/grammar/reference-reranker wired into :core's resources.srcDir?")
            val root = stream.use { jacksonObjectMapper().readTree(it) }

            val features = root["features"].map { it.asText() }
            check(features == ReferenceRerankerFeatureNames.ORDER) {
                "reranker_model.json's feature list doesn't match " +
                    "ReferenceRerankerFeatureNames.ORDER -- the model and the Kotlin " +
                    "feature computation have drifted apart. Regenerate the model " +
                    "with data/grammar/fst/export_kotlin_reranker.py, or update ORDER " +
                    "to match, and re-check with ReferenceRerankerParityTest."
            }
            val learningRate = root["lr"].asDouble()
            val trees = root["trees"].map { parseNode(it) }
            return ReferenceRerankerModel(features, learningRate, trees)
        }

        private fun parseNode(node: JsonNode): TreeNode =
            if (node.has("leaf")) {
                TreeNode.Leaf(node["leaf"].asDouble())
            } else {
                TreeNode.Split(
                    featureIndex = node["feat"].asInt(),
                    threshold = node["thr"].asDouble(),
                    left = parseNode(node["left"]),
                    right = parseNode(node["right"]),
                )
            }
    }
}
