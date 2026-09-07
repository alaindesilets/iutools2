package org.iutools.corpus

import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Plain JVM test: decompressTo() only touches java.io / java.util.zip, no
 * Android and no network. The rest of HansardIndexDownloader is a real
 * ~156 MiB network download, normally Alain's to exercise manually (see
 * AGENTS.md's "Division of labor") -- same split as TusaalangaFetcherTest.
 */
class HansardIndexDownloaderTest {

    @Test
    fun decompressTo_gzipFixture_producesOriginalBytes() {
        val original = "test content, some accented chars: éàçᐃᒡᓗ".toByteArray(Charsets.UTF_8)
        val compressed = File.createTempFile("fixture", ".gz").apply { deleteOnExit() }
        GZIPOutputStream(compressed.outputStream()).use { it.write(original) }
        val destination = File.createTempFile("decompressed", ".db").apply { deleteOnExit() }

        HansardIndexDownloader.decompressTo(compressed, destination)

        assertEquals(String(original, Charsets.UTF_8), destination.readText(Charsets.UTF_8))
    }
}
