package org.iutools.morph.cli

import org.iutools.script.Syllabics
import java.util.Scanner

/*
 * Throwaway dev-time tool: reads one syllabics word per line from stdin,
 * prints its Roman transliteration to stdout in the same order -- reuses
 * the real Syllabics.transcodeToRoman rather than reimplementing the
 * Unicode mapping table elsewhere. Not part of the CLI's own --word/
 * --interactive/--pipeline argument surface (see Main.kt's own header
 * comment on why that surface deliberately mirrors the original iutools
 * CLI); this is a separate entry point used only to prepare input for the
 * data/grammar/fst/ prototype's own Hansard-corpus experiments.
 */
fun main() {
    val input = Scanner(System.`in`)
    while (input.hasNextLine()) {
        println(Syllabics.transcodeToRoman(input.nextLine()))
    }
}
