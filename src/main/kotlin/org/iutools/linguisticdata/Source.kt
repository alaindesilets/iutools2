package org.iutools.linguisticdata

import java.util.Hashtable

class Source(v: HashMap<String, String>) {
    var id: String? = v["id"]
    var authorSurName: String? = v["authorSurName"]
    var authorMidName: String? = v["authorMidName"]
    var authorFirstName: String? = v["authorFirstName"]
    var title: String? = v["title"]
    var subtitle: String? = v["subtitle"]
    var publisher: String? = v["publisher"]
    var publisherMisc: String? = v["publisherMisc"]
    var location: String? = v["city/country"]
    var year: String? = v["year"]

    companion object {
        @JvmField
        val hash = Hashtable<String, Source>()

        @JvmStatic
        fun addToHash(key: String, obj: Any) {
            hash[key] = obj as Source
        }
    }
}
