package org.iutools.csv

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Port of org.iutools.csv.CSVReaderTest (Java). test_contructor (sic, typo
 * in the original) isn't ported: it asserts directly on `fieldNames`, which
 * is a package-private field in Java but `private` in Kotlin's CSVReader --
 * not visible outside the class at all, let alone from a different module's
 * test. Its coverage isn't actually lost though: readNext() below builds its
 * result map by looking values up under those same parsed field names, so a
 * field-parsing bug would fail this test too.
 */
class CSVReaderTest {

    @Test
    fun readNext() {
        val tempFile = File.createTempFile("temp", null)
        tempFile.deleteOnExit()
        val fw = FileWriter(tempFile)
        fw.write("morpheme,type,meaning\n")
        fw.write("inuk,n,\"some text, with coma\"")
        fw.close()
        val br = BufferedReader(FileReader(tempFile))
        val csvReader = CSVReader(br)
        val gotData = csvReader.readNext()!!
        assertEquals("inuk", gotData["morpheme"], "Field no 1 is incorrect.")
        assertEquals("n", gotData["type"], "Field no 2 is incorrect.")
        assertEquals("some text, with coma", gotData["meaning"], "Field no 3 is incorrect.")
    }
}
