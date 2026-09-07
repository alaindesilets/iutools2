package org.iutools.text.segmentation

class MyStringTokenizer(s: String, del: Char, carChaine: Char) {
    private val tokens = mutableListOf<String>()
    private val nTokens: Int
    private var compteur = 0

    init {
        val ls = s.length
        var deb = 0
        var dansChaine = false
        for (k in 0 until ls) {
            val c = s[k]
            if (c == del && !dansChaine) {
                if (s[deb] == carChaine && s[k - 1] == carChaine) {
                    tokens.add(s.substring(deb + 1, k - 1))
                } else {
                    tokens.add(s.substring(deb, k))
                }
                deb = k + 1
            } else if (c == carChaine) {
                dansChaine = !dansChaine
            }
        }
        if (s[deb] == carChaine && s[ls - 1] == carChaine) {
            tokens.add(s.substring(deb + 1, ls - 1))
        } else {
            tokens.add(s.substring(deb, ls))
        }
        nTokens = tokens.size
    }

    fun countTokens(): Int = nTokens

    fun hasMoreTokens(): Boolean = compteur != nTokens

    fun nextToken(): String = tokens[compteur++]

    fun reset() {
        compteur = 0
    }
}
