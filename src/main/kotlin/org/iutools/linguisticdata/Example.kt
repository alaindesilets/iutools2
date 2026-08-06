package org.iutools.linguisticdata

import java.util.Vector

class Example(v: Vector<String>) {
    var term: String = v.elementAt(0)
    var nb: String = v.elementAt(1)
    var termExLat: String = v.elementAt(2)
    var termExSyl: String = v.elementAt(3)
    var exampleLat: String = v.elementAt(4)
    var exampleSyl: String = v.elementAt(5)
    var eng: String = v.elementAt(6)
    var fre: String = v.elementAt(7)
}
