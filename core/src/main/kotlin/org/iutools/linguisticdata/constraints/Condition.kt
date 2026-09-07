package org.iutools.linguisticdata.constraints

import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme

open class Condition {

    var truth: Boolean = true
    var parameters: String? = null

    /*
     * Condition de non-mobilité associée à un infixe.
     */
    class NonMobilityOfInfix(val infixId: String) : Condition(), Conditions {
        override fun isMetBy(m: Morpheme): Boolean = true
        override fun isMetByFullMorphem(m: Morpheme): Boolean = true
        override fun toText(lang: String): String? = null
    }

    /*
     * Condition sur plus d'un morphème.
     */
    class OverSeveralMorphemes(val conds: List<Condition>) : Condition(), Conditions {
        override fun isMetBy(m: Morpheme): Boolean = true
        override fun isMetByFullMorphem(m: Morpheme): Boolean = true

        override fun toString(): String {
            var str = conds[0].toString()
            for (i in 1 until conds.size) {
                str = "$str+${conds[i]}"
            }
            if (!truth) str = "!($str)"
            return str
        }

        override fun toText(lang: String): String {
            var text = if (lang == "e") "Condition on the preceding morpheme:<br>" else "Condition sur le morphème précédent:<br>"
            text += (conds[0] as Conditions).toText(lang)
            for (i in 1 until conds.size) {
                text += if (lang == "e") "Condition on the next preceding morpheme:<br>" else "Condition sur le morphème précédent suivant:<br>"
                text += (conds[i] as Conditions).toText(lang)
            }
            return text
        }
    }

    /*
     * Condition composée de plusieurs sous-conditions qui doivent toutes être
     * respectées. Par exemple, la condition suivante comprend 3 sous-conditions
     * !type:tn,!(type:n,number:d),!(type:n,number:p)
     */
    class And(val conds: List<Condition>) : Condition(), Conditions {

        constructor(c1: Condition, c2: Condition) : this(listOf(c1, c2))

        @Throws(LinguisticDataException::class)
        override fun isMetBy(m: Morpheme): Boolean {
            var res = true
            val lm = m.getLastCombiningMorpheme()
            for (c in conds) {
                if (!(c as Conditions).isMetBy(m)) {
                    res = false
                    break
                }
            }
            /*
             * Several roots are described in the database as inchoative roots, that
             * is, roots that are the result of adding a 'q' at the end and doubling
             * the last internal consonant (eg. ikummaq- < ikuma-). In the database,
             * this is done in the column 'combination': the last morpheme is
             * #incho#/1vv. This morpheme, as last morpheme, is NOT to be checked.
             */
            if (!res && lm != null && lm.id != "#incho#/1vv") {
                res = true
                for (c in conds) {
                    if (!(c as Conditions).isMetBy(lm)) {
                        res = false
                        break
                    }
                }
            }
            return if (truth) res else !res
        }

        @Throws(LinguisticDataException::class)
        override fun isMetByFullMorphem(m: Morpheme): Boolean {
            var res = true
            for (c in conds) {
                if (!(c as Conditions).isMetBy(m)) {
                    res = false
                    break
                }
            }
            return if (truth) res else !res
        }

        override fun toString(): String {
            var str = conds[0].toString()
            for (i in 1 until conds.size) {
                str = "$str,${conds[i]}"
            }
            if (!truth) str = "!($str)"
            return str
        }

        override fun toText(lang: String): String? {
            var str = (conds[0] as Conditions).toText(lang)
            for (i in 1 until conds.size) {
                str += (if (lang == "e") " AND " else " ET ") + (conds[i] as Conditions).toText(lang)
            }
            if (!truth) str = (if (lang == "e") "NOT " else "PAS ") + "($str)"
            return str
        }
    }

    /*
     * Condition composée de plusieurs sous-conditions mutuellement exclusives,
     * dont au moins une doit être respectée. Par exemple, la condition suivante
     * comprend 3 sous-conditions: type:n function:vn function:nn
     */
    class Or(val conds: List<Condition>) : Condition(), Conditions {

        @Throws(LinguisticDataException::class)
        override fun isMetBy(m: Morpheme): Boolean {
            var res = false
            val lm = m.getLastCombiningMorpheme()
            for (c in conds) {
                if ((c as Conditions).isMetBy(m) ||
                    (lm != null && lm.id != "#incho#/1vv" && c.isMetBy(lm))
                ) {
                    res = true
                    break
                }
            }
            return if (truth) res else !res
        }

        @Throws(LinguisticDataException::class)
        override fun isMetByFullMorphem(m: Morpheme): Boolean {
            var res = false
            for (c in conds) {
                if ((c as Conditions).isMetBy(m)) {
                    res = true
                    break
                }
            }
            return if (truth) res else !res
        }

        override fun toString(): String {
            var str = conds[0].toString()
            for (i in 1 until conds.size) {
                str = "$str ${conds[i]}"
            }
            if (!truth) str = "!($str)"
            return str
        }

        override fun toText(lang: String): String? {
            var str = (conds[0] as Conditions).toText(lang)
            for (i in 1 until conds.size) {
                str += (if (lang == "e") " OR " else " OU ") + (conds[i] as Conditions).toText(lang)
            }
            if (!truth) str = (if (lang == "e") "NOT " else "PAS ") + "($str)"
            return str
        }
    }

    /*
     * Condition de la forme !cp(id:<morpheme id>)
     * La condition sur le morphème précédent rattachée au morphème
     * <morpheme id> ne doit pas être respectée.
     * Exemple: !cp(id:it/3nv) rattachée au morphème it/2nv "ne pas avoir de",
     * "manquer de".
     *
     * Ce type de condition est attribué à un morphème d'une forme qui est aussi
     * celle d'autres morphèmes de même type qui ont, eux, des restrictions
     * particulières. On évite ainsi des analyses incorrectes.
     */
    class Cid(val pn: String, morphidRaw: String) : Condition(), Conditions {
        val morphid: String = morphidRaw.split(":")[1]

        @Throws(LinguisticDataException::class)
        override fun isMetBy(m: Morpheme): Boolean {
            val lm = m.getLastCombiningMorpheme()
            val morphWithCond = Morpheme.getMorpheme(morphid)!!
            val cond: Conditions = (if (pn == "cp") morphWithCond.getPrecCond() else morphWithCond.getNextCond())!!
            var res = cond.isMetBy(m)
            if (!res && lm != null && lm.id != "#incho#/1vv") {
                res = cond.isMetBy(lm)
            }
            return if (truth) res else !res
        }

        @Throws(LinguisticDataException::class)
        override fun isMetByFullMorphem(m: Morpheme): Boolean {
            val morphWithCond = Morpheme.getMorpheme(morphid)!!
            val cond: Conditions = (if (pn == "cp") morphWithCond.getPrecCond() else morphWithCond.getNextCond())!!
            val res = cond.isMetBy(m)
            return if (truth) res else !res
        }

        override fun toString(): String {
            var str = "$pn($morphid)"
            if (!truth) str = "!$str"
            return str
        }

        override fun toText(lang: String): String {
            var str: String
            if (pn == "cp") {
                str = if (lang == "en") "the condition of " else "la condition de "
                str += morphid
                str += if (lang == "en") " on the preceding morpheme" else "sur le morphème précédent"
            } else {
                str = "$pn($morphid)"
            }
            if (!truth) str = (if (lang == "en") "NOT " else "PAS ") + str
            return str
        }
    }
}
