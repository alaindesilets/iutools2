package org.iutools.linguisticdata

import org.apache.logging.log4j.LogManager
import org.iutools.lib.Debug
import org.iutools.linguisticdata.dataCSV.LinguisticDataCSV
import org.iutools.phonology.Dialect
import org.iutools.script.Orthography
import org.iutools.script.Roman
import java.util.Hashtable
import java.util.Vector

class LinguisticData {

    // For bases:
    // We keep info as both Vector<Base> and Vector<Morpheme>
    // because for some unknown reason, we cannot cast from Vector<Base> to Vector<Morpheme>
    // and some clients expect to receive the info in the later type.
    // SURFACE FORMS TO OBJECTS
    var basesForCanonicalForm: MutableMap<String, Vector<Base>> = HashMap()
    var morphemesForCanonicalForm: Hashtable<String, Vector<Morpheme>> = Hashtable()
    var surfaceFormsOfAffixes: Hashtable<String, Vector<SurfaceFormOfAffix>> = Hashtable()
    // MORPHEME IDS TO MORPHEMES
    @JvmField var idToBaseTable: Hashtable<String, Base> = Hashtable()
    @JvmField var idToAffixTable: Hashtable<String, Affix> = Hashtable()
    // OTHERS
    @JvmField var words: Hashtable<String, VerbWord> = Hashtable()
    var sources: Hashtable<String, Source> = Hashtable()
    @JvmField var groupsOfConsonants: Hashtable<Char, Vector<String>> = Hashtable()

    init {
        reinitializeData()
    }

    /*
     * Read the data stored in the CSV files into linguistic objects and register them in the singleton (this).
     */
    @Throws(LinguisticDataException::class)
    fun readLinguisticDataCSV() {
        val ldcsv = LinguisticDataCSV()
        ldcsv.readAndRegisterLinguisticDataCSV(this)
    }

    @Throws(LinguisticDataException::class)
    fun add2basesForCanonicalForm(canonicalForm: String, base: Base) {
        if (!basesForCanonicalForm.containsKey(canonicalForm)) {
            basesForCanonicalForm[canonicalForm] = Vector()
            morphemesForCanonicalForm[canonicalForm] = Vector()
        }
        basesForCanonicalForm[canonicalForm]!!.add(base)
        morphemesForCanonicalForm[canonicalForm]!!.add(base as Morpheme)
    }

    /*
     * These methods create SurfaceFormOfAffix objects that represent forms and actions
     * of the affixes: suffixes and endings. Those objects are registered in a hash table.
     */
    @Throws(LinguisticDataException::class)
    fun addToForms(ending: DemonstrativeEnding, key: String) {
        addToForms1(
            arrayOf(ending.morpheme!!),
            key,
            ending.type,
            ending.id!!,
            null,
            arrayOf(Action.makeAction("neutre")),
            arrayOf(Action.makeAction(null))
        )
    }

    @Throws(LinguisticDataException::class)
    fun addToForms(affix: Affix, key: String?) {
        addToForms1(affix.vform, key, affix.type, affix.id!!, "V", affix.vaction1, affix.vaction2)
        addToForms1(affix.tform, key, affix.type, affix.id!!, "t", affix.taction1, affix.taction2)
        addToForms1(affix.kform, key, affix.type, affix.id!!, "k", affix.kaction1, affix.kaction2)
        addToForms1(affix.qform, key, affix.type, affix.id!!, "q", affix.qaction1, affix.qaction2)
    }

    /*
     * Si l'une des surfaceFormsOfAffixes ou des actions est inconnue, on ne
     * place pas de forme dans la table de hachage des surfaceFormsOfAffixes.
     */
    @Throws(LinguisticDataException::class)
    private fun addToForms1(
        altForms: Array<String>?,
        key: String?,
        type: String?,
        id: String,
        context: String?,
        actions1: Array<Action?>?,
        actions2: Array<Action?>?
    ) {
        if (altForms != null) {
            for (i in altForms.indices) {
                if (altForms[i] != "?" &&
                    actions1!![i]!!.type != Action.UNKNOWN &&
                    actions2!![i]!!.type != Action.UNKNOWN
                ) {
                    var form = if (altForms[i] == "*") key!! else altForms[i]
                    // Simplification of the form (ng > N ; nng > NN).
                    // Because the method looking for an affix is passed a simplified inuktitut text.
                    form = Orthography.simplifiedOrthographyLat(form)
                    var form1 = actions1[i]!!.surfaceForm(form)
                    if (form1 != null) {
                        form1 = Orthography.simplifiedOrthographyLat(form1)
                        val newForm = SurfaceFormOfAffix(form1, key, id, type, context, actions1[i], actions2[i])
                        addToSurfaceFormsOfAffixes(form1, newForm)
                    }

                    /*
                     * Certain action2 may also produce a special form, like self-decapitation for example.
                     */
                    var form2 = actions2[i]!!.surfaceForm(form)
                    if (form2 != null) {
                        form2 = Orthography.simplifiedOrthographyLat(form2)
                        val otherForm = SurfaceFormOfAffix(form2, key, id, type, context, actions1[i], actions2[i])
                        addToSurfaceFormsOfAffixes(form2, otherForm)
                    }
                }
            }
        }
    }

    fun addEntryToIdToBaseTable(baseId: String, baseObject: Base) {
        idToBaseTable[baseId] = baseObject
    }

    fun getBasesForCanonicalForm(canonicalForm: String): Vector<Morpheme>? = morphemesForCanonicalForm[canonicalForm]

    fun getCanonicalFormsForAllBases(): Array<String> = basesForCanonicalForm.keys.toTypedArray()

    fun getBasesForAllCanonicalForms_hashtable(): Hashtable<String, Vector<Morpheme>> = morphemesForCanonicalForm

    /*
     * Look for the morpheme identified by its id.
     * First check in the roots; if not found, check in the affixes.
     */
    fun getMorpheme(morphIdIn: String): Morpheme? {
        val morphId = Morpheme.removeIDBraces(morphIdIn)
        var morph: Morpheme? = getBaseWithId(morphId)
        if (morph == null) {
            morph = getAffixWithId(morphId)
        }
        return morph
    }

    fun getBaseWithId(morphId: String): Base? = idToBaseTable[morphId]

    fun getBaseFromMorphemeIdObject(morphId: Morpheme.Id): Base? = idToBaseTable[morphId.id]

    fun getIdToBaseTable(): Hashtable<String, Base> = idToBaseTable

    fun getAllBasesIds(): Array<String> = idToBaseTable.keys.toTypedArray()

    fun getIdToRootTable(): Hashtable<String, Morpheme> {
        val table = Hashtable<String, Morpheme>()
        val clazz = Base::class.java.name
        for (rootId in idToBaseTable.keys) {
            val obj = idToBaseTable[rootId]!!
            if (obj.javaClass.name == clazz) {
                table[rootId] = idToBaseTable[rootId]!!
            }
        }
        return table
    }

    fun addEntryToIdToAffixTable(affixId: String, affixObject: Affix) {
        idToAffixTable[affixId] = affixObject
    }

    fun getAffixWithId(uniqueId: String): Affix? = idToAffixTable[uniqueId]

    fun getSuffixWithId(uniqueId: String): Suffix? = idToAffixTable[uniqueId] as Suffix?

    fun getIdToAffixTable(): Hashtable<String, Affix> = idToAffixTable

    fun getAllAffixesIds(): Array<String> = idToAffixTable.keys.toTypedArray()

    fun addToSurfaceFormsOfAffixes(str: String, form: SurfaceFormOfAffix) {
        val simplifiedForm = Orthography.simplifiedOrthographyLat(str)
        val v = surfaceFormsOfAffixes[simplifiedForm] ?: Vector()
        v.add(form)
        surfaceFormsOfAffixes[simplifiedForm] = v
    }

    fun getFormToSurfaceFormsOfAffixesTable(): Hashtable<String, Vector<SurfaceFormOfAffix>> = surfaceFormsOfAffixes

    fun getSurfaceForms(form: String): Vector<SurfaceFormOfAffix>? {
        val simplifiedForm = Orthography.simplifiedOrthographyLat(form)
        return surfaceFormsOfAffixes[simplifiedForm]
    }

    fun getForm(morph: String): SurfaceFormOfAffix = getSurfaceForms(morph)!!.elementAt(0)

    fun getAllAffixesSurfaceFormsKeys(): Array<String> = surfaceFormsOfAffixes.keys.toTypedArray()

    fun getId2SuffixTable(): Hashtable<String, Morpheme> {
        val table = Hashtable<String, Morpheme>()
        for (affixId in idToAffixTable.keys) {
            val aff = idToAffixTable[affixId]!!
            if (aff.javaClass.name.endsWith("Suffix")) {
                table[affixId] = aff
            }
        }
        return table
    }

    fun getIdToDemonstrativeTable(): Hashtable<String, Demonstrative> {
        val table = Hashtable<String, Demonstrative>()
        val clazz = Demonstrative::class.java.name
        for (demonstrativeId in idToBaseTable.keys) {
            val obj = idToBaseTable[demonstrativeId]!!
            if (obj.javaClass.name == clazz) {
                table[demonstrativeId] = idToBaseTable[demonstrativeId] as Demonstrative
            }
        }
        return table
    }

    fun getIdToGiVerbsTable(): Hashtable<String, Base> {
        val giverbsHash = Hashtable<String, Base>()
        val bases = getIdToRootTable()
        for (key in bases.keys) {
            val base = bases[key] as Base
            if (base.isGiVerb()) {
                giverbsHash[key] = base
            }
        }
        return giverbsHash
    }

    fun addVerbWord(verbWordForm: String, wordObject: VerbWord) {
        words[verbWordForm] = wordObject
    }

    fun getWords(): Hashtable<String, VerbWord> = words

    fun getAllVerbWordsForms(): Array<String> = words.keys.toTypedArray()

    fun getVerbWord(term: String): VerbWord? = words[term]

    fun addSource(sourceId: String, sourceObject: Source) {
        sources[sourceId] = sourceObject
    }

    fun getIdToSourceTable(): Hashtable<String, Source> = sources

    fun getAllSourceIds(): Array<String> = sources.keys.toTypedArray()

    fun getSource(sourceId: String): Source? = sources[sourceId]

    fun getGroupsOfConsonants(): Hashtable<Char, Vector<String>> = groupsOfConsonants

    fun addToGroupsOfConsonants(str: String) {
        val chars = str.toCharArray()
        for (i in 0 until chars.size - 1) {
            if (Roman.isConsonant(chars[i]) && Roman.isConsonant(chars[i + 1])) {
                val charac = chars[i + 1]
                val grCons = groupsOfConsonants.getOrPut(charac) { Vector() }
                val newGr = String(charArrayOf(chars[i], chars[i + 1]))
                if (!grCons.contains(newGr)) {
                    grCons.add(newGr)
                }
            }
        }
    }

    fun reinitializeData() {
        surfaceFormsOfAffixes = Hashtable()
        basesForCanonicalForm = Hashtable()
        morphemesForCanonicalForm = Hashtable()
        idToBaseTable = Hashtable()
        idToAffixTable = Hashtable()
        words = Hashtable()
        sources = Hashtable()
        groupsOfConsonants = Hashtable()
    }

    private fun makeGroupsOfConsonants() {
        val keys = getCanonicalFormsForAllBases()
        for (key in keys) {
            addToGroupsOfConsonants(Orthography.simplifiedOrthographyLat(key))
        }
        val forms = getAllAffixesSurfaceFormsKeys()
        for (form in forms) {
            addToGroupsOfConsonants(form)
        }
        for (key in Dialect.getKeys()) {
            addToGroupsOfConsonants(key)
        }
    }

    //----- MAKE OBJECTS FOR THE MORPHEMES ------------------------------

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterLinguisticObject(linguisticDataMap: HashMap<String, String>) {
        val morphemeTypeInLinguisticData = linguisticDataMap["type"]
        val classOfMorpheme = type2class[morphemeTypeInLinguisticData]
        when (classOfMorpheme) {
            "Base" -> makeAndRegisterBase(linguisticDataMap)
            "Suffix" -> makeAndRegisterSuffix(linguisticDataMap)
            "NounEnding" -> makeAndRegisterNounEnding(linguisticDataMap)
            "VerbEnding" -> makeAndRegisterVerbEnding(linguisticDataMap)
            "Demonstrative" -> makeAndRegisterDemonstrative(linguisticDataMap)
            "DemonstrativeEnding" -> makeAndRegisterDemonstrativeEnding(linguisticDataMap)
            "Pronoun" -> makeAndRegisterPronoun(linguisticDataMap)
            "VerbWord" -> makeAndRegisterVerbWord(linguisticDataMap)
            "Source" -> makeAndRegisterSource(linguisticDataMap)
        }
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterBase(linguisticData: HashMap<String, String>) {
        val bases = LinguisticObjectFactory.makeBase(linguisticData)
        for (base in bases) {
            if (base.originalMorpheme == null && getIdToBaseTable().containsKey(base.id)) {
                val callStack = Debug.printCallStack()
                throw RuntimeException(
                    "Bases ID already contains a key ${base.id}. This one is defined in ${base.tableName}. " +
                        "Check your .csv files in the linguistics data\n\nCall stack was:\n$callStack"
                )
            } else {
                add2basesForCanonicalForm(base.morpheme!!, base)
                if (base.subtype == null) { // not equal to "nc" (noun composite)
                    addEntryToIdToBaseTable(base.id!!, base)
                }
            }
        }
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterSuffix(linguisticData: HashMap<String, String>) {
        val suffix = LinguisticObjectFactory.makeSuffix(linguisticData)
        if (getIdToAffixTable().containsKey(suffix.id)) {
            throw RuntimeException("Key '${suffix.id}' already exists in linguistic data hash")
        }
        addEntryToIdToAffixTable(suffix.id!!, suffix)
        addToForms(suffix, suffix.morpheme)
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterNounEnding(linguisticData: HashMap<String, String>) {
        val ending = LinguisticObjectFactory.makeNounEnding(linguisticData)
        if (getIdToAffixTable().containsKey(ending.id)) {
            throw RuntimeException("Key '${ending.id}' already exists in linguistic data hash")
        }
        addEntryToIdToAffixTable(ending.id!!, ending)
        addToForms(ending, ending.morpheme)
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterVerbEnding(linguisticData: HashMap<String, String>) {
        val ending = LinguisticObjectFactory.makeVerbEnding(linguisticData)
        // This test with a throw is commented out (in the original) to allow the execution
        // of scripts using the linguistic database.
        addEntryToIdToAffixTable(ending.id!!, ending)
        addToForms(ending, ending.morpheme)
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterDemonstrative(linguisticData: HashMap<String, String>) {
        val demonstratives = LinguisticObjectFactory.makeDemonstrative(linguisticData)
        for (demonstrative in demonstratives) {
            if (getIdToBaseTable().containsKey(demonstrative.id)) {
                throw RuntimeException("Key '${demonstrative.id}' already exists in linguistic data hash")
            }
            add2basesForCanonicalForm(demonstrative.morpheme!!, demonstrative)
            addEntryToIdToBaseTable(demonstrative.id!!, demonstrative)
        }
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterDemonstrativeEnding(linguisticData: HashMap<String, String>) {
        val ending = LinguisticObjectFactory.makeDemonstrativeEnding(linguisticData)
        if (getIdToAffixTable().containsKey(ending.id)) {
            throw RuntimeException("Key '${ending.id}' already exists in linguistic data hash")
        }
        addEntryToIdToAffixTable(ending.id!!, ending)
        addToForms(ending, ending.morpheme!!)
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterPronoun(linguisticData: HashMap<String, String>) {
        val pronouns = LinguisticObjectFactory.makePronoun(linguisticData)
        for (pronoun in pronouns) {
            if (getIdToBaseTable().containsKey(pronoun.id)) {
                throw RuntimeException("Key '${pronoun.id}' already exists in linguistic data hash")
            }
            add2basesForCanonicalForm(pronoun.morpheme!!, pronoun)
            addEntryToIdToBaseTable(pronoun.id!!, pronoun)
        }
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterVerbWord(linguisticData: HashMap<String, String>) {
        val verbWord = LinguisticObjectFactory.makeVerbWord(linguisticData)
        addVerbWord(verbWord.verb!!, verbWord)
    }

    @Throws(LinguisticDataException::class)
    fun makeAndRegisterSource(linguisticData: HashMap<String, String>) {
        val source = LinguisticObjectFactory.makeSource(linguisticData)
        addSource(source.id!!, source)
    }

    fun allMorphemeIDs(): Array<String> {
        val idsList = mutableListOf<String>()
        idsList.addAll(getAllAffixesIds())
        idsList.addAll(getAllBasesIds())
        return idsList.toTypedArray()
    }

    companion object {
        @Volatile
        private var singleton: LinguisticData? = null

        @JvmField
        val type2class: MutableMap<String, String> = HashMap<String, String>().apply {
            put("n", "Base")
            put("v", "Base")
            put("a", "Base")
            put("c", "Base")
            put("e", "Base")
            put("sv", "Suffix")
            put("sn", "Suffix")
            put("q", "Suffix")
            put("tn", "NounEnding")
            put("tv", "VerbEnding")
            put("ad", "Demonstrative")
            put("pd", "Demonstrative")
            put("tad", "DemonstrativeEnding")
            put("tpd", "DemonstrativeEnding")
            put("p", "Pronoun")
            // TODO: only 3 occurrences in RootsSpalding.csv; must be changed to "p"
            put("pr", "Pronoun")
            put("rp", "Pronoun")
            put("rpr", "Pronoun")
            put("vw", "VerbWord")
            put("src", "Source")
        }

        @JvmStatic
        fun init() {
            singleton = null
        }

        @Synchronized
        @JvmStatic
        fun getInstance(): LinguisticData {
            val logger = LogManager.getLogger("LinguisticData.getInstance")
            logger.debug("singleton == null ? ${singleton == null}")
            if (singleton == null) {
                val instance = LinguisticData()
                try {
                    instance.readLinguisticDataCSV()
                    instance.makeGroupsOfConsonants() // used in Dialect.java
                } catch (e: LinguisticDataException) {
                    e.printStackTrace()
                    kotlin.system.exitProcess(1)
                }
                singleton = instance
            }
            return singleton!!
        }
    }
}
