package org.iutools.script

/*
 * What's reachable from decomposeWord() was ported first: `containsInuktitut`
 * (checked once, at the very start of MorphologicalAnalyzer_R2L.decomposeWord,
 * to decide whether to transliterate syllabic input) and `transcodeToRoman`
 * (the actual transliteration). Everything downstream of that point always
 * runs in Roman orthography (USE_SYLLABICS=false is hardcoded in the
 * analyzer), so the AIPA/ITAI conversion helpers, `syllabicsToRomanICI`
 * (only used by the excluded SpellChecker), and the digit/char accessor
 * methods (`getSyl`, `getCharacter`) were dropped.
 *
 * `allInuktitut` and `syllabicCharsRatio` were added later, for the app's
 * user-facing display-script setting (Roman/Syllabic/as-entered) -- they're
 * used by TransCoder.kt, not by decomposeWord() itself.
 */
object Syllabics {

    private val unicodeXsyl = HashMap<Char, String>()
    private val allSylChars = HashSet<Char>()

    private object Short {
        val unicodeXsyl: Map<Char, String> = mapOf(
            'ᐃ' to "i", 'ᐱ' to "pi", 'ᑎ' to "ti", 'ᑭ' to "ki",
            'ᒋ' to "gi", 'ᒥ' to "mi", 'ᓂ' to "ni", 'ᓯ' to "si",
            'ᓕ' to "li", 'ᔨ' to "ji", 'ᕕ' to "vi", 'ᕆ' to "ri",
            'ᕿ' to "qi", 'ᖏ' to "Ni", 'ᙱ' to "Xi", 'ᖠ' to "&i",
            'ᐅ' to "u", 'ᐳ' to "pu", 'ᑐ' to "tu", 'ᑯ' to "ku",
            'ᒍ' to "gu", 'ᒧ' to "mu", 'ᓄ' to "nu", 'ᓱ' to "su",
            'ᓗ' to "lu", 'ᔪ' to "ju", 'ᕗ' to "vu", 'ᕈ' to "ru",
            'ᖁ' to "qu", 'ᖑ' to "Nu", 'ᙳ' to "Xu", 'ᖢ' to "&u",
            'ᐊ' to "a", 'ᐸ' to "pa", 'ᑕ' to "ta", 'ᑲ' to "ka",
            'ᒐ' to "ga", 'ᒪ' to "ma", 'ᓇ' to "na", 'ᓴ' to "sa",
            'ᓚ' to "la", 'ᔭ' to "ja", 'ᕙ' to "va", 'ᕋ' to "ra",
            'ᖃ' to "qa", 'ᖓ' to "Na", 'ᙵ' to "Xa", 'ᖤ' to "&a"
        )
    }

    private object Long {
        val unicodeXsyl: Map<Char, String> = mapOf(
            'ᐄ' to "ii", 'ᐲ' to "pii", 'ᑏ' to "tii", 'ᑮ' to "kii",
            'ᒌ' to "gii", 'ᒦ' to "mii", 'ᓃ' to "nii", 'ᓰ' to "sii",
            'ᓖ' to "lii", 'ᔩ' to "jii", 'ᕖ' to "vii", 'ᕇ' to "rii",
            'ᖀ' to "qii", 'ᖐ' to "Nii", 'ᙲ' to "Xii", 'ᖡ' to "&ii",
            'ᐆ' to "uu", 'ᐴ' to "puu", 'ᑑ' to "tuu", 'ᑰ' to "kuu",
            'ᒎ' to "guu", 'ᒨ' to "muu", 'ᓅ' to "nuu", 'ᓲ' to "suu",
            'ᓘ' to "luu", 'ᔫ' to "juu", 'ᕘ' to "vuu", 'ᕉ' to "ruu",
            'ᖂ' to "quu", 'ᖒ' to "Nuu", 'ᙴ' to "Xuu", 'ᖣ' to "&uu",
            'ᐋ' to "aa", 'ᐹ' to "paa", 'ᑖ' to "taa", 'ᑳ' to "kaa",
            'ᒑ' to "gaa", 'ᒫ' to "maa", 'ᓈ' to "naa", 'ᓵ' to "saa",
            'ᓛ' to "laa", 'ᔮ' to "jaa", 'ᕚ' to "vaa", 'ᕌ' to "raa",
            'ᖄ' to "qaa", 'ᖔ' to "Naa", 'ᙶ' to "Xaa", 'ᖥ' to "&aa"
        )
    }

    private object Final {
        val unicodeXsyl: Map<Char, String> = mapOf(
            'ᑉ' to "p", 'ᑦ' to "t", 'ᒃ' to "k", 'ᒡ' to "g",
            'ᒻ' to "m", 'ᓐ' to "n", 'ᔅ' to "s", 'ᓪ' to "l",
            'ᔾ' to "j", 'ᕝ' to "v", 'ᕐ' to "r", 'ᖅ' to "q",
            'ᖕ' to "N", 'ᖖ' to "X", 'ᖦ' to "&", 'ᕼ' to "h",
            'ᖯ' to "b"
        )
    }

    private val syllabicsToRomanAIPAITAI = arrayOf(
        arrayOf("ᐁ", "ai"), arrayOf("ᐂ", "aai"),
        arrayOf("ᐯ", "pai"), arrayOf("ᐰ", "paai"),
        arrayOf("ᑌ", "tai"), arrayOf("ᑍ", "tai"),
        arrayOf("ᑫ", "kai"), arrayOf("ᑬ", "kaai"),
        arrayOf("ᒉ", "gai"), arrayOf("ᒊ", "gaai"),
        arrayOf("ᒣ", "mai"), arrayOf("ᒤ", "maai"),
        arrayOf("ᓀ", "nai"), arrayOf("ᓁ", "naai"),
        arrayOf("ᓭ", "sai"), arrayOf("ᓮ", "saai"),
        arrayOf("ᓓ", "lai"), arrayOf("ᓔ", "laai"),
        arrayOf("ᔦ", "jai"), arrayOf("ᔧ", "jaai"),
        arrayOf("ᕓ", "vai"), arrayOf("ᕔ", "vaai"),
        arrayOf("ᕃ", "rai"), arrayOf("ᕅ", "raai"),
        arrayOf("ᙯ", "qai"), arrayOf("ᕾ", "qaai"),
        arrayOf("ᙰ", "ngai"), arrayOf("ᖎ", "ngaai")
    )

    init {
        unicodeXsyl.putAll(Short.unicodeXsyl)
        unicodeXsyl.putAll(Long.unicodeXsyl)
        unicodeXsyl.putAll(Final.unicodeXsyl)
        allSylChars.addAll(unicodeXsyl.keys)
        for (pair in syllabicsToRomanAIPAITAI) {
            allSylChars.add(pair[0][0])
        }
    }

    @JvmStatic
    fun isInuktitutCharacter(character: Char): Boolean = allSylChars.contains(character)

    @JvmStatic
    fun containsInuktitut(word: String): Boolean {
        for (c in word) {
            if (isInuktitutCharacter(c)) return true
        }
        return false
    }

    @JvmStatic
    fun allInuktitut(word: String): Boolean {
        for (c in word) {
            if (!isInuktitutCharacter(c)) return false
        }
        return true
    }

    @JvmStatic
    fun syllabicCharsRatio(text: String): Double {
        var totalChars = 0
        var iuChars = 0
        for (c in text) {
            if (c.isWhitespace()) continue
            totalChars++
            if (isInuktitutCharacter(c)) iuChars++
        }
        return iuChars.toDouble() / totalChars
    }

    @JvmStatic
    fun transcodeToRoman(s: String): String {
        var i = 0
        val l = s.length
        val sb = StringBuilder()
        while (i < l) {
            val c = s[i]
            var d: String
            when (c) {
                'ᐃ' -> d = "i"
                'ᐱ' -> d = "pi"
                'ᑎ' -> d = "ti"
                'ᑭ' -> d = "ki"
                'ᒋ' -> d = "gi"
                'ᒥ' -> d = "mi"
                'ᓂ' -> d = "ni"
                'ᓯ' -> d = "si"
                'ᓕ' -> d = "li"
                'ᔨ' -> d = "ji"
                'ᕕ' -> d = "vi"
                'ᕆ' -> d = "ri"
                'ᕿ' -> d = "qi"
                'ᖏ' -> d = "ngi"
                'ᙱ' -> d = "nngi"
                'ᖠ' -> d = "&i"
                'ᐅ' -> d = "u"
                'ᐳ' -> d = "pu"
                'ᑐ' -> d = "tu"
                'ᑯ' -> d = "ku"
                'ᒍ' -> d = "gu"
                'ᒧ' -> d = "mu"
                'ᓄ' -> d = "nu"
                'ᓱ' -> d = "su"
                'ᓗ' -> d = "lu"
                'ᔪ' -> d = "ju"
                'ᕗ' -> d = "vu"
                'ᕈ' -> d = "ru"
                'ᖁ' -> d = "qu"
                'ᖑ' -> d = "ngu"
                'ᙳ' -> d = "nngu"
                'ᖢ' -> d = "&u"
                'ᐊ' -> d = "a"
                'ᐸ' -> d = "pa"
                'ᑕ' -> d = "ta"
                'ᑲ' -> d = "ka"
                'ᒐ' -> d = "ga"
                'ᒪ' -> d = "ma"
                'ᓇ' -> d = "na"
                'ᓴ' -> d = "sa"
                'ᓚ' -> d = "la"
                'ᔭ' -> d = "ja"
                'ᕙ' -> d = "va"
                'ᕋ' -> d = "ra"
                'ᖃ' -> d = "qa"
                'ᖓ' -> d = "nga"
                'ᙵ' -> d = "nnga"
                'ᖤ' -> d = "&a"
                'ᐄ' -> d = "ii"
                'ᐲ' -> d = "pii"
                'ᑏ' -> d = "tii"
                'ᑮ' -> d = "kii"
                'ᒌ' -> d = "gii"
                'ᒦ' -> d = "mii"
                'ᓃ' -> d = "nii"
                'ᓰ' -> d = "sii"
                'ᓖ' -> d = "lii"
                'ᔩ' -> d = "jii"
                'ᕖ' -> d = "vii"
                'ᕇ' -> d = "rii"
                'ᖀ' -> d = "qii"
                'ᖐ' -> d = "ngii"
                'ᙲ' -> d = "nngii"
                'ᖡ' -> d = "&ii"
                'ᐆ' -> d = "uu"
                'ᐴ' -> d = "puu"
                'ᑑ' -> d = "tuu"
                'ᑰ' -> d = "kuu"
                'ᒎ' -> d = "guu"
                'ᒨ' -> d = "muu"
                'ᓅ' -> d = "nuu"
                'ᓲ' -> d = "suu"
                'ᓘ' -> d = "luu"
                'ᔫ' -> d = "juu"
                'ᕘ' -> d = "vuu"
                'ᕉ' -> d = "ruu"
                'ᖂ' -> d = "quu"
                'ᖒ' -> d = "nguu"
                'ᙴ' -> d = "nnguu"
                'ᖣ' -> d = "&uu"
                'ᐋ' -> d = "aa"
                'ᐹ' -> d = "paa"
                'ᑖ' -> d = "taa"
                'ᑳ' -> d = "kaa"
                'ᒑ' -> d = "gaa"
                'ᒫ' -> d = "maa"
                'ᓈ' -> d = "naa"
                'ᓵ' -> d = "saa"
                'ᓛ' -> d = "laa"
                'ᔮ' -> d = "jaa"
                'ᕚ' -> d = "vaa"
                'ᕌ' -> d = "raa"
                'ᖄ' -> d = "qaa"
                'ᖔ' -> d = "ngaa"
                'ᙶ' -> d = "nngaa"
                'ᖥ' -> d = "&aa"
                'ᑉ' -> d = "p"
                'ᑦ' -> d = "t"
                'ᒃ' -> d = "k"
                'ᒡ' -> d = "g"
                'ᒻ' -> d = "m"
                'ᓐ' -> d = "n"
                'ᔅ' -> d = "s"
                'ᓪ' -> d = "l"
                'ᔾ' -> d = "j"
                'ᕝ' -> d = "v"
                'ᕐ' -> { // r
                    i++
                    d = if (i < l) {
                        val e = s[i]
                        when (e) { // r+k. > q.
                            'ᑭ' -> "qi"
                            'ᑯ' -> "qu"
                            'ᑲ' -> "qa"
                            'ᑮ' -> "qii"
                            'ᑰ' -> "quu"
                            'ᑳ' -> "qaa"
                            'ᑫ' -> "qai"
                            else -> { i--; "r" }
                        }
                    } else {
                        i--
                        "r"
                    }
                }
                'ᖅ' -> { // q
                    i++
                    d = if (i < l) {
                        val e = s[i]
                        when (e) { // q+k. > qq.
                            'ᑭ' -> "qqi"
                            'ᑯ' -> "qqu"
                            'ᑲ' -> "qqa"
                            'ᑮ' -> "qqii"
                            'ᑰ' -> "qquu"
                            'ᑳ' -> "qqaa"
                            'ᑫ' -> "qqai"
                            else -> { i--; "q" }
                        }
                    } else {
                        i--
                        "q"
                    }
                }
                'ᖕ' -> { // ng
                    i++
                    d = if (i < l) {
                        val e = s[i]
                        when (e) { // ng+g. > ng.
                            'ᒋ' -> "ngi"
                            'ᒍ' -> "ngu"
                            'ᒐ' -> "nga"
                            'ᒌ' -> "ngii"
                            'ᒎ' -> "nguu"
                            'ᒑ' -> "ngaa"
                            'ᒉ' -> "ngai"
                            else -> { i--; "ng" }
                        }
                    } else {
                        i--
                        "ng"
                    }
                }
                'ᖖ' -> {
                    i++
                    d = if (i < l) {
                        val e = s[i]
                        when (e) { // nng+g. > nng.
                            'ᒋ' -> "nngi"
                            'ᒍ' -> "nngu"
                            'ᒐ' -> "nnga"
                            'ᒌ' -> "nngii"
                            'ᒎ' -> "nnguu"
                            'ᒑ' -> "nngaa"
                            'ᒉ' -> "nngai"
                            else -> { i--; "nng" }
                        }
                    } else {
                        i--
                        "nng"
                    }
                }
                'ᖦ' -> d = "&"
                'ᕼ' -> d = "H" // Nunavut H
                'ᕴ' -> d = "hai" // Nunavik Hai
                'ᕵ' -> d = "hi" // Nunavik Hi
                'ᕶ' -> d = "hii" // Nunavik Hii
                'ᕷ' -> d = "hu" // Nunavik Hu
                'ᕸ' -> d = "huu" // Nunavik Huu
                'ᕹ' -> d = "ha" // Nunavik Ha
                'ᕺ' -> d = "haa" // Nunavik Haa
                'ᖯ' -> d = "b" // Aivilik B
                'ᐁ' -> d = "ai" // ai
                'ᐯ' -> d = "pai" // pai
                'ᑌ' -> d = "tai" // tai
                'ᑫ' -> d = "kai" // kai
                'ᒉ' -> d = "gai" // gai
                'ᒣ' -> d = "mai" // mai
                'ᓀ' -> d = "nai" // nai
                'ᓭ' -> d = "sai" // sai
                'ᓓ' -> d = "lai" // lai
                'ᔦ' -> d = "jai" // jai
                'ᕓ' -> d = "vai" // vai
                'ᕃ' -> d = "rai" // rai
                'ᕂ' -> d = "rai" // rai
                'ᙯ' -> d = "qai" // qai
                'ᙰ' -> d = "ngai" // ngai
                else -> d = c.toString()
            }
            i++
            sb.append(d)
        }
        return sb.toString()
    }
}
