package org.iutools.morph.fst

import net.sf.hfst.NoTokenizationException
import net.sf.hfst.Transducer
import net.sf.hfst.TransducerAlphabet
import net.sf.hfst.TransducerHeader
import net.sf.hfst.UnweightedTransducer
import net.sf.hfst.WeightedTransducer
import org.iutools.morph.Decomposition
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.RankedDecomposition
import org.iutools.script.Syllabics
import org.iutools.utilities1.Util
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

/*
 * Runs the HFST-based finite-state analyzer (tools/fst/) from Kotlin, as an
 * alternative to MorphologicalAnalyzer_R2L (Benoit Farley's ported R2L
 * analyzer).
 *
 * It reads the compiled transducer tools/fst/lexicon-analyser.hfstol
 * directly, in-process, via the vendored pure-Java optimized-lookup reader
 * (core/src/jvmMain/java/net/sf/hfst/ -- see its VENDORED.md). No subprocess,
 * no native `hfst-lookup` binary needed. That reader is pure Java over an
 * InputStream, so the same approach can move to an Android source set later;
 * for now this lives in jvmMain and is exercised by
 * MorphologicalAnalyzer_FST__AccuracyTest, which pins its coverage to what
 * the native `hfst-lookup` path produces for the same transducer.
 *
 * The decompositions it returns carry only the canonical morpheme + tag id
 * ("atuaq/1v"), NOT the surface substring each morpheme matched -- HFST
 * optimized-lookup exposes whole-string input/output pairs, not a
 * per-morpheme alignment. So Decomposition.toString() here is
 * "{atuaq/1v}{gaq/1vn}", not "{atua:atuaq/1v}{gaq:gaq/1vn}".
 *
 * Duplicates are removed (the Java reader can emit the same path more than
 * once), first occurrence kept. What survives is then ordered by the shared
 * MorphologicalAnalyzer.sortDecompositions -- the very ranking
 * MorphologicalAnalyzer_R2L uses -- so "which decomposition comes first" is
 * comparable between the two analyzers. The Java reader's own raw traversal
 * order is arbitrary and is not relied on.
 *
 * The transducer path can be overridden with the system property
 * `iutools.fst.transducer` or the env var IUTOOLS_FST_TRANSDUCER; by default
 * it is found by walking up from the working directory looking for
 * tools/fst/lexicon-analyser.hfstol.
 */
class MorphologicalAnalyzer_FST(
    private val transducer: File = defaultTransducerFile(),
) : MorphologicalAnalyzer() {

    // Loaded once, on first use, and reused. The Java reader's analyze() is
    // NOT reentrant (mutable instance state), so every call is serialized --
    // fine for this analyzer's usage (one word at a time).
    private var engine: Engine? = null

    private class Engine(val transducer: Transducer, val stream: FileInputStream)

    // The .lexc lexicon is written in lowercase Roman, so the input has to be
    // brought to that form first -- the same normalization
    // MorphologicalAnalyzer_R2L does before its own search
    // (decomposeUntilTimeoutOrCompletion): syllabics -> Roman, lowercase
    // (keeping a capital H, the Roman-Inuktitut convention), and undo a known
    // "qk" transliteration error. Without this, a word typed in syllabics or
    // capitalized simply gets no analysis.
    private fun normalizeInput(word: String): String {
        var w = word
        if (Syllabics.containsInuktitut(w)) {
            w = Syllabics.transcodeToRoman(w)
        }
        w = Util.enMinuscule(w)
        w = w.replace(Regex("([iua])qk([iua])"), "$1qq$2")
        return w
    }

    override fun doDecompose(word: String, lenient: Boolean?): Array<Decomposition> {
        val cleaned = normalizeInput(word.trim())
        if (cleaned.isEmpty()) return emptyArray()

        // tools/fst/phonology.xfscript's LENIENT rule gives the "a final
        // k/p/q/t was dropped after a vowel" paths weight 1.0 and everything
        // else weight 0.0 -- the FST equivalent of R2L's extended/lenient
        // analysis. Base decomposeWord() passes lenient=true by default.
        val weightCutoff = if (lenient ?: true) 1.0f else 0.0f

        val rawResults = synchronized(this) {
            try {
                ensureLoaded().transducer.analyze(cleaned)
            } catch (e: NoTokenizationException) {
                emptyList<String>()
            }
        }

        return rankRawResults(rawResults, weightCutoff)
    }

    /**
     * The dedup + ranking half of [doDecompose], split out so a cross-
     * language sync test (SortSyncTest / tools/fst/sort_decomps.py) can drive
     * exactly this logic with a frozen bag of raw transducer lines and check
     * the order matches the Python ranking. [rawResults] are the reader's own
     * "<analysis>\t<weight>" strings (a bare "<analysis>" means weight 0);
     * entries above [weightCutoff] are dropped.
     *
     * The transducer reaches one analysis string by several paths, and
     * phonology.xfscript's LENIENT rule means the SAME string often comes out
     * both at weight 0 (a strict parse) and weight 1 (that parse also
     * reachable by assuming a dropped final consonant). Keep each string
     * once, at its LOWEST weight -- a parse is strict if any path yields it
     * strictly. Keeping "whichever the reader emitted first" instead would
     * make the weight sort key depend on the reader's arbitrary path-
     * enumeration order (net.sf.hfst and native hfst-lookup differ).
     */
    fun rankRawResults(rawResults: Collection<String>, weightCutoff: Float = 1.0f): Array<Decomposition> {
        val minWeightByAnalysis = LinkedHashMap<String, Float>()
        for (result in rawResults) {
            // Weighted transducer: "canonical+id++...\t<weight>". Unweighted:
            // just "canonical+id++...".
            val tab = result.indexOf('\t')
            val analysis = if (tab < 0) result else result.substring(0, tab)
            val weight = if (tab < 0) 0.0f else result.substring(tab + 1).trim().toFloatOrNull() ?: 0.0f
            if (weight > weightCutoff) continue
            val prev = minWeightByAnalysis[analysis]
            if (prev == null || weight < prev) minWeightByAnalysis[analysis] = weight
        }

        val ranked = ArrayList<RankedDecomposition>(minWeightByAnalysis.size)
        for ((analysis, weight) in minWeightByAnalysis) {
            val specs = hfstAnalysisToDecompSpecs(analysis)
            // The HFST tag's first morpheme's canonical text IS the root's
            // citation form -- this project's lexicon puts canonical + id on
            // the upper side ("atuaq+1v"), with no surface/variant split like
            // R2L's -- so its length is the root-canonical-length sort key
            // directly.
            val rootCanonicalLength = specs.substringBefore(' ').substringBefore('/').length
            ranked.add(RankedDecomposition(Decomposition(specs), rootCanonicalLength, weight))
        }
        return sortDecompositions(ranked, breakTiesByMorphemeFrequency = true)
    }

    private fun ensureLoaded(): Engine {
        engine?.let { return it }

        require(transducer.isFile) {
            "FST transducer not found at ${transducer.absolutePath} -- build it first (see tools/fst/phonology.xfscript's header), " +
                "or set -Diutools.fst.transducer=/path/to/lexicon-analyser.hfstol"
        }

        // Same read sequence as net.sf.hfst.HfstOptimizedLookup.main: header,
        // then alphabet off a DataInputStream over the same stream, then the
        // index/transition tables.
        val stream = FileInputStream(transducer)
        val header = TransducerHeader(stream)
        val alphabet = TransducerAlphabet(DataInputStream(stream), header.symbolCount)
        val t: Transducer =
            if (header.isWeighted()) WeightedTransducer(stream, header, alphabet)
            else UnweightedTransducer(stream, header, alphabet)
        return Engine(t, stream).also { engine = it }
    }

    override fun close() {
        synchronized(this) {
            try {
                engine?.stream?.close()
            } catch (_: Exception) {
            }
            engine = null
        }
    }

    companion object {
        /**
         * "atuaq+1v++gaq+1vn" -> "atuaq/1v gaq/1vn", the space-separated
         * `canonical/tagId` component form Decomposition() parses (its
         * toString() then renders "{atuaq/1v}{gaq/1vn}"). "++" separates
         * morphemes; the first "+" within a morpheme separates canonical
         * from tag id -- same split tools/fst/histogram.py's
         * parse_hfst_analysis does.
         */
        internal fun hfstAnalysisToDecompSpecs(analysis: String): String =
            analysis.split("++").joinToString(" ") { morpheme ->
                val plus = morpheme.indexOf('+')
                if (plus < 0) morpheme else "${morpheme.substring(0, plus)}/${morpheme.substring(plus + 1)}"
            }

        /** True when the transducer file is present and can be loaded. */
        fun isAvailable(transducer: File = defaultTransducerFile()): Boolean {
            if (!transducer.isFile) return false
            return try {
                FileInputStream(transducer).use { stream ->
                    val header = TransducerHeader(stream)
                    TransducerAlphabet(DataInputStream(stream), header.symbolCount)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun defaultTransducerFile(): File {
            System.getProperty("iutools.fst.transducer")?.let { return File(it) }
            System.getenv("IUTOOLS_FST_TRANSDUCER")?.let { return File(it) }

            val relative = "tools/fst/lexicon-analyser.hfstol"
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                val candidate = File(dir, relative)
                if (candidate.isFile) return candidate
                dir = dir.parentFile
            }
            return File(relative).absoluteFile
        }
    }
}
