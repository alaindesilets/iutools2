package org.iutools.morph

import java.io.File

/**
 * Reads data/grammar/gold-standard/gold-standard.csv and builds the same
 * AnalyzerCase instances that the hand-written MorphAnalGoldStandard_*.kt
 * files build via addCase(AnalyzerCase(...)) -- used by
 * MorphAnalGoldStandard_Hansard_FromCsv / _WordsThatFailedBefore_FromCsv to
 * verify the CSV export is a faithful copy (see GoldStandardCsvMatchesKotlinTest).
 */
object GoldStandardCsvReader {

    private data class CsvRow(
        val source: String,
        val word: String,
        val decomp: String,
        val hasNoCorrectDecomp: Boolean,
        val allCorrectDecomps: List<String>,
        val isMisspelled: Boolean,
        val isPossiblyMisspelled: Boolean,
        val isBorrowed: Boolean,
        val decompUnknown: Boolean,
        val isProperName: Boolean,
        val comments: String,
    )

    private val SURFACE_FORM_RE = Regex("""\{([^:}]+):[^}]*\}""")

    /**
     * '{amit:amit/1v}{tuq:juq/1vn}' -> 'amittuq' -- the part of each
     * {surface:canonical/id} component BEFORE the colon is the surface
     * substring matched in the word; concatenating them in order must
     * reconstruct the original word.
     */
    fun concatenatedSurfaceForms(decomp: String): String =
        SURFACE_FORM_RE.findAll(decomp).joinToString("") { it.groupValues[1] }

    fun casesFor(source: String): List<AnalyzerCase> {
        val rowsByWord = LinkedHashMap<String, MutableList<CsvRow>>()
        for (row in readAllRows().filter { it.source == source }) {
            rowsByWord.getOrPut(row.word) { mutableListOf() }.add(row)
        }
        return rowsByWord.values.map { toAnalyzerCase(it) }
    }

    /** word -> every decomposition R2L produces for it (empty list if the
     * CSV's all_correct_decomps column was blank for that word, e.g. a
     * flagged word or a "words_that_failed_before" entry). */
    fun allCorrectDecomps(source: String): Map<String, List<String>> {
        val rowsByWord = LinkedHashMap<String, MutableList<CsvRow>>()
        for (row in readAllRows().filter { it.source == source }) {
            rowsByWord.getOrPut(row.word) { mutableListOf() }.add(row)
        }
        return rowsByWord.mapValues { (_, rows) -> rows.first().allCorrectDecomps }
    }

    private fun toAnalyzerCase(wordRows: List<CsvRow>): AnalyzerCase {
        val first = wordRows.first()
        val correctDecomps = if (first.hasNoCorrectDecomp) null else wordRows.map { it.decomp }.toTypedArray()
        var case = AnalyzerCase(first.word, correctDecomps)
        if (first.isMisspelled) case = case.isMisspelled()
        if (first.isPossiblyMisspelled) case = case.possiblyMisspelledWord()
        if (first.isBorrowed) case = case.isBorrowedWord()
        if (first.decompUnknown) case = case.correctDecompUnknown()
        if (first.isProperName) case = case.isProperName()
        if (first.comments.isNotEmpty()) case = case.comment(first.comments)
        return case
    }

    private fun readAllRows(): List<CsvRow> {
        val lines = goldStandardCsvFile().readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        val header = parseCsvLine(lines.first())
        val columnIndex = header.withIndex().associate { (i, name) -> name to i }
        fun fields(line: String) = parseCsvLine(line)
        return lines.drop(1).map { line ->
            val f = fields(line)
            fun field(name: String) = f[columnIndex.getValue(name)]
            CsvRow(
                source = field("source"),
                word = field("word"),
                decomp = field("decomp_as_found_in_source"),
                hasNoCorrectDecomp = field("has_no_correct_decomp").toGoldBoolean(),
                allCorrectDecomps = field("all_correct_decomps").let { if (it.isEmpty()) emptyList() else it.split(";") },
                isMisspelled = field("is_misspelled").toGoldBoolean(),
                isPossiblyMisspelled = field("is_possibly_misspelled").toGoldBoolean(),
                isBorrowed = field("is_borrowed").toGoldBoolean(),
                decompUnknown = field("decomp_unknown").toGoldBoolean(),
                isProperName = field("is_proper_name").toGoldBoolean(),
                comments = field("comments"),
            )
        }
    }

    private fun String.toGoldBoolean(): Boolean = this.equals("true", ignoreCase = true)

    // Minimal RFC4180 line parser: quoted fields, "" as an escaped quote inside
    // one. Gold-standard rows never embed a newline inside a field, so reading
    // line-by-line (rather than the whole file as one RFC4180 record stream) is
    // safe here.
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun goldStandardCsvFile(): File {
        val relative = "data/grammar/gold-standard/gold-standard.csv"
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return File(relative).absoluteFile
    }
}
