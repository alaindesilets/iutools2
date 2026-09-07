package org.iutools.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * findByLongestPrefix() is a pure function, no platform dependency. Lives
 * in :cli's test source set because :core has none of its own (see
 * AGENTS.md) -- moved here from :composeApp when PrefixFallback moved into
 * :core.
 */
class PrefixFallbackTest {

    @Test
    fun findByLongestPrefix_exactWordNotTried_onlyShorterPrefixes() {
        val calls = mutableListOf<String>()
        val result = findByLongestPrefix("igloo") { candidate ->
            calls.add(candidate)
            if (candidate == "igloo") "should never match" else null
        }

        assertEquals(null, result)
        // "igloo" itself (length 5) is never a candidate -- only 4, 3.
        assertEquals(listOf("iglo", "igl"), calls)
    }

    @Test
    fun findByLongestPrefix_returnsLongestMatchingPrefix() {
        val known = setOf("igl", "iglo")

        val result = findByLongestPrefix("igloo") { candidate -> known.firstOrNull { it == candidate } }

        assertEquals("iglo" to "iglo", result)
    }

    @Test
    fun findByLongestPrefix_minLengthCandidateItselfIsTried() {
        // Only the length-3 candidate ("igl") matches -- confirms
        // PREFIX_FALLBACK_MIN_LENGTH is an inclusive lower bound, not an
        // exclusive one that would skip it.
        val result = findByLongestPrefix("igloo") { candidate -> candidate.takeIf { it == "igl" } }

        assertEquals("igl" to "igl", result)
    }

    @Test
    fun findByLongestPrefix_wordAtOrBelowMinLength_triesNothing() {
        val calls = mutableListOf<String>()

        val result = findByLongestPrefix("ig") { candidate ->
            calls.add(candidate)
            candidate
        }

        assertNull(result)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun findByLongestPrefix_noMatch_returnsNull() {
        assertNull(findByLongestPrefix("igloo") { null })
    }
}
