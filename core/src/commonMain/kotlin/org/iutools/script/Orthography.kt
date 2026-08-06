package org.iutools.script

/*
 * The morphological analyzer always runs with USE_SYLLABICS=false
 * (MorphologicalAnalyzer_R2L hardcodes it), so the isSyllabic=true branch of
 * simplifiedOrthography()/orthographyICI() is unreachable in practice; any
 * syllabic input is transliterated to Roman orthography once up front via
 * Syllabics.transcodeToRoman() before reaching these. The *Syl() variants
 * (and the large Unicode conversion tables they'd need from Roman.java) were
 * therefore not ported — they throw if ever actually reached.
 */
object Orthography {

    @JvmStatic
    fun simplifiedOrthography(term: String, isSyllabic: Boolean): String {
        return if (isSyllabic) simplifiedOrthographySyl(term) else simplifiedOrthographyLat(term)
    }

    // Remplacement de: nng par NN; ng par N
    @JvmStatic
    fun simplifiedOrthographyLat(term: String): String {
        val sb = StringBuilder()
        val lengthTerm = term.length
        var i = 0
        while (i < lengthTerm) {
            if (term[i] == 'n') {
                if (i < lengthTerm - 1) {
                    if (term[i + 1] == 'n') {
                        if (i < lengthTerm - 2 && term[i + 2] == 'g') {
                            sb.append("NN")
                            i += 2
                        } else {
                            sb.append("nn")
                            i += 1
                        }
                    } else if (term[i + 1] == 'g') {
                        sb.append("N")
                        i += 1
                    } else {
                        sb.append(term.substring(i, i + 1))
                    }
                } else {
                    sb.append(term.substring(i, i + 1))
                }
            } else {
                sb.append(term.substring(i, i + 1))
            }
            i++
        }
        return sb.toString()
    }

    private fun simplifiedOrthographySyl(term: String): String {
        throw UnsupportedOperationException(
            "Syllabic-orthography simplification is unreachable in the ported code path " +
                "(the analyzer always runs with USE_SYLLABICS=false and transliterates syllabic " +
                "input to Roman up front); not ported."
        )
    }

    //---------------------------- ICI --------------------------------------
    // Orthographe selon le standard du "Inuit Cultural Institute" à partir d'un
    // texte à orthographe simplifiée.
    @JvmStatic
    fun orthographyICI(term: String, isSyllabic: Boolean): String {
        return if (isSyllabic) orthographyICISyl(term) else orthographyICILat(term)
    }

    // Remplacement de: NN par nng; N par ng
    @JvmStatic
    fun orthographyICILat(term: String): String {
        val lengthTerm = term.length
        val sb = StringBuilder()
        var i = 0
        while (i < lengthTerm) {
            if (term[i] == 'X') {
                sb.append("nng")
            } else if (term[i] == 'N') {
                if (i < lengthTerm - 1 && term[i + 1] == 'N') {
                    sb.append("nng")
                    i += 1
                } else {
                    sb.append("ng")
                }
            } else {
                sb.append(term.substring(i, i + 1))
            }
            i++
        }
        return sb.toString()
    }

    private fun orthographyICISyl(term: String): String {
        throw UnsupportedOperationException(
            "Syllabic-orthography ICI rendering is unreachable in the ported code path " +
                "(see simplifiedOrthographySyl); not ported."
        )
    }
}
