package org.iutools.linguisticdata

import java.util.Hashtable

class VerbWord(v: HashMap<String, String>) {
    var verb: String? = v["verb"]
    var passive: String? = v["passive"]

    companion object {
        @JvmField
        val hash = Hashtable<String, VerbWord>()

        @JvmStatic
        fun addToHash(key: String, obj: Any) {
            hash[key] = obj as VerbWord
        }
    }
}
