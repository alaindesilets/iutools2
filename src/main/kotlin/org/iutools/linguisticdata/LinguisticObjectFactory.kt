package org.iutools.linguisticdata

import java.util.StringTokenizer

object LinguisticObjectFactory {

    /**
     * Make an object of class Base with the passed attribute-value pairs and
     * add an entry to a mapped set of canonical forms.
     */
    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeBase(linguisticDataMap: HashMap<String, String>): List<Base> {
        val objects = mutableListOf<Base>()
        val base = Base(linguisticDataMap)
        objects.add(base)
        // If the root has variant forms, create a root object for each one and link it to the original root.
        if (base.getVariant() != null) {
            val variants = tokenize(base.getVariant()!!)
            for (variant in variants) {
                val baseVariant = _makeBaseVariant(base, linguisticDataMap, variant)
                objects.add(baseVariant)
            }
        }
        // If the root has a special root for composition, create such a composition object and link it to the original root.
        if (base.getCompositionRoot() != null) {
            val baseComp = _makeBaseCompositionRoot(base, linguisticDataMap, base.getCompositionRoot()!!)
            objects.add(baseComp)
        }

        return objects
    }

    @Throws(LinguisticDataException::class)
    private fun _makeBaseCompositionRoot(base: Base, linguisticDataMap: HashMap<String, String>, compositionRoot: String): Base {
        @Suppress("UNCHECKED_CAST")
        val clone = linguisticDataMap.clone() as HashMap<String, String>
        clone["morpheme"] = compositionRoot
        clone.remove("variant")
        clone["originalMorpheme"] = base.id!!
        clone.remove("compositionRoot")
        clone["subtype"] = "nc"
        clone["nb"] = base.nb!!
        return Base(clone)
    }

    @Throws(LinguisticDataException::class)
    private fun _makeBaseVariant(base: Base, linguisticDataMap: HashMap<String, String>, variant: String): Base {
        @Suppress("UNCHECKED_CAST")
        val clone = linguisticDataMap.clone() as HashMap<String, String>
        clone["morpheme"] = variant
        clone.remove("variant")
        clone["originalMorpheme"] = base.id!!
        return Base(clone)
    }

    /*
     * Demonstratives actually produce 2 roots: the first one is used alone, as is;
     * the second one is used with demonstrative endings. These 2 roots are defined in the CSV file
     * in 2 fields:
     * 1st root (stand alone): field "morpheme"
     * 2nd root: field "racine" (French for 'root')
     * The field 'racine' may actually contain more than 1 value.
     */
    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeDemonstrative(v: HashMap<String, String>): List<Demonstrative> {
        val demonstratives = mutableListOf<Demonstrative>()
        // 1st form
        val x = Demonstrative(v)
        demonstratives.add(x)

        // 2nd form: create a new object for each form of the root
        val roots = x.getRoot()!!.split(" ")
        for (root in roots) {
            @Suppress("UNCHECKED_CAST")
            val v2 = v.clone() as HashMap<String, String>
            v2["morpheme"] = root
            v2["root"] = root
            val x2 = Demonstrative(v2, "r")
            demonstratives.add(x2)
        }

        return demonstratives
    }

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makePronoun(v: HashMap<String, String>): List<Pronoun> {
        val pronouns = mutableListOf<Pronoun>()
        val x = Pronoun(v)
        pronouns.add(x)
        if (x.getVariant() != null) {
            val st = StringTokenizer(x.getVariant())
            while (st.hasMoreTokens()) {
                @Suppress("UNCHECKED_CAST")
                val v2 = v.clone() as HashMap<String, String>
                v2["morpheme"] = st.nextToken()
                v2.remove("variant")
                v2["nb"] = x.nb!!
                v2["originalMorpheme"] = x.id!!
                val x2 = Pronoun(v2)
                pronouns.add(x2)
            }
        }

        return pronouns
    }

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeSuffix(v: HashMap<String, String>): Suffix = Suffix(v)

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeNounEnding(v: HashMap<String, String>): NounEnding = NounEnding(v)

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeVerbEnding(v: HashMap<String, String>): VerbEnding = VerbEnding(v)

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeDemonstrativeEnding(v: HashMap<String, String>): DemonstrativeEnding = DemonstrativeEnding(v)

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeVerbWord(v: HashMap<String, String>): VerbWord = VerbWord(v)

    @Synchronized
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun makeSource(v: HashMap<String, String>): Source = Source(v)

    private fun tokenize(string: String): List<String> {
        val tokens = mutableListOf<String>()
        val st = StringTokenizer(string)
        while (st.hasMoreTokens()) {
            tokens.add(st.nextToken())
        }
        return tokens
    }
}
