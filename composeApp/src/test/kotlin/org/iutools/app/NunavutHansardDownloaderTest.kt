package org.iutools.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/*
 * Plain JUnit, not Robolectric: decompressTo() only touches java.io/
 * java.util.zip, no Android framework class -- unlike the rest of this
 * fetcher, which needs a real network download and is normally Alain's to
 * exercise manually (see AGENTS.md's "Division of labor"), same reasoning
 * as TusaalangaFetcherTest.kt's dedicated real-network test.
 */
class NunavutHansardDownloaderTest {

    @Test
    fun decompressTo_gzipFixture_producesOriginalBytes() {
        val original = "test content, some accented chars: éàçᐃᒡᓗ".toByteArray(Charsets.UTF_8)
        val compressed = File.createTempFile("fixture", ".gz").apply { deleteOnExit() }
        GZIPOutputStream(compressed.outputStream()).use { it.write(original) }
        val destination = File.createTempFile("decompressed", ".db").apply { deleteOnExit() }

        NunavutHansardDownloader.decompressTo(compressed, destination)

        assertEquals(String(original, Charsets.UTF_8), destination.readText(Charsets.UTF_8))
    }
}
