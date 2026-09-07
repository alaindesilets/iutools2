package org.iutools.llm

import org.iutools.morph.Decomposition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * toMorphemeRows() parses the two decomposition-component shapes the
 * analyzers produce. These tests use made-up morpheme ids so the parse is
 * checked without depending on which real morphemes exist.
 */
class MorphemeRowTest {

    @Test
    fun toMorphemeRows_bracedSurfacePrefixedComponents_splitSurfaceFromId() {
        val rows = Decomposition("{atua:zzsurfaceprefixed-root/1v} {gaq:zzsurfaceprefixed-suffix/1vn}").toMorphemeRows()

        assertEquals(listOf("atua", "gaq"), rows.map { it.surfaceForm })
        assertEquals(
            listOf("zzsurfaceprefixed-root/1v", "zzsurfaceprefixed-suffix/1vn"),
            rows.map { it.morphemeId },
        )
    }

    @Test
    fun toMorphemeRows_fstStyleComponentWithNoSurfacePrefix_usesCanonicalFormAsSurface() {
        // The FST analyzer emits only "id/tag", never the matched substring.
        val row = Decomposition("zznosurfaceprefix-atuaq/1v").toMorphemeRows().single()

        assertEquals("zznosurfaceprefix-atuaq", row.surfaceForm)
        assertEquals("zznosurfaceprefix-atuaq/1v", row.morphemeId)
    }

    @Test
    fun toMorphemeRows_unknownMorphemeId_leavesFullRecordNull() {
        assertNull(Decomposition("zznosuchmorpheme/1n").toMorphemeRows().single().fullRecord)
    }
}
