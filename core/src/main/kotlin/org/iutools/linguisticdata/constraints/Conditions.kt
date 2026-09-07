package org.iutools.linguisticdata.constraints

import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme

interface Conditions {
    @Throws(LinguisticDataException::class)
    fun isMetBy(m: Morpheme): Boolean

    @Throws(LinguisticDataException::class)
    fun isMetByFullMorphem(m: Morpheme): Boolean

    fun toText(lang: String): String?
}
