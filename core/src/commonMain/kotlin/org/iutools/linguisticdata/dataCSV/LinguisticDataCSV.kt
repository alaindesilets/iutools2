package org.iutools.linguisticdata.dataCSV

import org.apache.logging.log4j.LogManager
import org.iutools.csv.CSVReader
import org.iutools.lib.ResourceGetter
import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.LinguisticDataException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class LinguisticDataCSV {

    /*
     * Read all the linguistic data files into linguistic objects and register them in
     * the LinguisticData singleton passed in the argument linguisticDataRegister.
     */
    @Throws(LinguisticDataException::class)
    fun readAndRegisterLinguisticDataCSV(linguisticDataRegister: LinguisticData) {
        for (data in dataTables) {
            _readLinguisticDataCSV(data, linguisticDataRegister)
        }
    }

    @Throws(LinguisticDataException::class)
    private fun _readLinguisticDataCSV(data: Array<String>, linguisticDataRegister: LinguisticData) {
        val logger = LogManager.getLogger("LinguisticDataCSV.readLinguisticDataCSV")
        val dbName = data[1]
        val tableName = data[2]
        val typeOfObject = if (data.size == 4) data[3] else null
        val fileName = "$tableName.csv"
        val tablePath = "org/iutools/linguisticdata/dataCSV/$fileName"
        try {
            val stream = ResourceGetter.getResourceAsStream(tablePath)
                ?: throw IOException("Resource not found: $tablePath")
            readLinguisticDataCSV(stream, dbName, tableName, typeOfObject, linguisticDataRegister)
        } catch (e: IOException) {
            throw LinguisticDataException("Could not read linguistic data file $tablePath")
        }
        logger.trace("Done reading the CSV file")
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class, LinguisticDataException::class)
    fun readLinguisticDataCSV(
        stream: java.io.InputStream,
        dbName: String,
        tableName: String,
        typeOfObject: String?,
        linguisticDataRegister: LinguisticData
    ) {
        val f = BufferedReader(InputStreamReader(stream))
        val csvReader = CSVReader(f)
        var endOfFile = false
        while (!endOfFile) {
            val row = csvReader.readNext()
            if (row == null) {
                endOfFile = true
            } else {
                row["dbName"] = dbName
                row["tableName"] = tableName
                if (typeOfObject != null) {
                    // non-linguistic objects must be added a field "type"
                    row["type"] = typeOfObject
                }
                linguisticDataRegister.makeAndRegisterLinguisticObject(row as HashMap<String, String>)
            }
        }
    }

    companion object {
        // element 0: class of the objects created from data in data file
        // element 1: name of the database
        // element 2: name of the CSV data file (without the extension)
        // element 3: if present, 'type' to be used when creating the objects
        //            only for non-linguistic data
        @JvmField
        val dataTables = arrayOf(
            arrayOf("Base", "Inuktitut", "RootsSpalding"),
            arrayOf("Base", "Inuktitut", "RootsSchneider"),
            arrayOf("Base", "Inuktitut", "WordsRelatedToRoots"),
            arrayOf("Base", "Inuktitut", "UndecomposableCompositeWords"),
            arrayOf("Base", "Inuktitut", "CommonCompositeWords"),
            arrayOf("Base", "Inuktitut", "Locations"),
            arrayOf("Base", "Inuktitut", "LoanWords"),
            arrayOf("Suffix", "Inuktitut", "Suffixes"),
            arrayOf("NounEnding", "Inuktitut", "Endings_noun"),
            arrayOf("VerbEnding", "Inuktitut", "Endings_verb"),
            arrayOf("VerbEnding", "Inuktitut", "Endings_verb_participle"),
            arrayOf("Demonstrative", "Inuktitut", "Demonstratives"),
            arrayOf("DemonstrativeEnding", "Inuktitut", "Endings_demonstrative"),
            arrayOf("Pronoun", "Inuktitut", "Pronouns"),
            // The following files do not describe linguistic objects. They do not contain
            // a field 'type'; it is provided here as the fourth element so that the objects
            // can be created. vw: verb-word objects; src: source objects
            arrayOf("VerbWord", "Inuktitut", "Passives_French", "vw"),
            arrayOf("VerbWord", "Inuktitut", "Passives_English", "vw"),
            arrayOf("Source", "Inuktitut", "Sources", "src"),
            // The next file contains additional morphemes not in the original database
            arrayOf("Suffix", "Inuktitut", "Suffixes_additional")
        )
    }
}
