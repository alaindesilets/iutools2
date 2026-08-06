package org.iutools.linguisticdata

class Inchoative : Suffix() {

    init {
        morpheme = morph
        makeFormsAndActions("V", morpheme!!, morpheme, "n", "n")
        nb = "1"
        type = "sv"
        function = "vv"
        englishMeaning = "to start to; to begin to"
        frenchMeaning = "commencer à"
        setAttrs()
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("[Suffix\$Inchoative:\n")
        sb.append("morpheme= ").append(morpheme).append("\n")
        sb.append("nb: ").append(nb).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("function= ").append(function).append("\n")
        sb.append("position= ").append(position).append("\n")
        sb.append("englishMeaning= ").append(englishMeaning).append("\n")
        sb.append("]\n")
        return sb.toString()
    }

    companion object {
        private const val morph = "#incho#"

        @JvmStatic
        fun getMorph(): String = morph
    }
}
