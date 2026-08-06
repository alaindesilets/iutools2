package org.iutools.script

/*
 * Only the handful of methods actually reachable from decomposeWord() were
 * ported: isConsonant/typeOfLetterLat (stem-context classification),
 * nasalOfOcclusiveUnvoicedLat/voicedOfOcclusiveUnvoicedLat (consonant
 * mutation lookups). The syllabics-conversion tables and other helpers in
 * the original 1400-line Roman.java are unused by the R2L engine.
 */
object Roman {
    const val V = 0 // verbe; voyelle
    const val C = 2 // consonne

    private val consonants = charArrayOf(
        'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', '&', 'N', 'X', 'H'
    ).sortedArray()
    private val vowels = charArrayOf('a', 'i', 'u').sortedArray()

    @JvmStatic
    fun typeOfLetterLat(letter: Char): Int {
        return if (consonants.binarySearch(letter) >= 0) C
        else if (vowels.binarySearch(letter) >= 0) V
        else -1
    }

    @JvmStatic
    fun isConsonant(charac: Char): Boolean = typeOfLetterLat(charac) == C

    @JvmStatic
    fun nasalOfOcclusiveUnvoicedLat(n: Char): Char {
        return when (n) {
            'p' -> 'm'
            't' -> 'n'
            'k' -> 'N'
            'q' -> 'r'
            else -> (-1).toChar()
        }
    }

    @JvmStatic
    fun voicedOfOcclusiveUnvoicedLat(n: Char): Char {
        return when (n) {
            'p' -> 'v'
            't' -> 'l'
            'k' -> 'g'
            'q' -> 'r'
            else -> (-1).toChar()
        }
    }
}
