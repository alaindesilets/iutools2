package org.iutools.utilities1

object Util {
    /**
     * In Roman-alphabet Inuktitut, the letter 'H' is always uppercase.
     * Hence this special method instead of plain lowercase().
     */
    @JvmStatic
    fun enMinuscule(chaine: String): String {
        return chaine.lowercase().replace('h', 'H')
    }

    @JvmStatic
    fun isVowel(c: Char): Boolean {
        return "aeiouAEIOUàâéèêëîïôùûü".indexOf(c) >= 0
    }
}
