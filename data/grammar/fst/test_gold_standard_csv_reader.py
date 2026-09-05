"""Verifies the all_correct_decomps column of
data/grammar/gold-standard/gold-standard.csv (every decomposition R2L
produces for a "fair" Hansard word, `;`-separated -- see
add_all_correct_decomps.py) parses correctly via gold_standard_csv_reader.py.
Mirrors cli/src/test/kotlin/org/iutools/morph/GoldStandardCsvAllCorrectDecompsTest.kt.

Usage (from data/grammar/fst/):
    python3 test_gold_standard_csv_reader.py
"""

import unittest

from gold_standard_csv_reader import concatenated_surface_forms, load_gold_standard

KNOWN_GOLD_NOT_IN_R2L_OUTPUT = {"imaimmat", "taaksumunga"}


def _is_flagged(case) -> bool:
    return (
        case.is_misspelled
        or case.is_possibly_misspelled
        or case.is_borrowed
        or case.is_proper_name
        or case.decomp_unknown
    )


class GoldStandardCsvAllCorrectDecompsTest(unittest.TestCase):
    def setUp(self):
        self.hansard = load_gold_standard("hansard")
        self.words_that_failed_before = load_gold_standard("words_that_failed_before")

    def test_fair_hansard_words_all_have_a_non_empty_all_correct_decomps_list(self):
        fair_words = [word for word, case in self.hansard.items() if not _is_flagged(case)]
        self.assertTrue(fair_words)
        for word in fair_words:
            decomps = self.hansard[word].all_correct_decomps
            self.assertTrue(decomps, f"Expected a non-empty all_correct_decomps list for fair word '{word}'")

    def test_flagged_words_have_an_empty_all_correct_decomps_list(self):
        flagged_words = [word for word, case in self.hansard.items() if _is_flagged(case)]
        self.assertTrue(flagged_words)
        for word in flagged_words:
            self.assertEqual(
                [], self.hansard[word].all_correct_decomps, f"Expected no all_correct_decomps for flagged word '{word}'"
            )

    def test_fair_words_that_failed_before_words_also_have_a_non_empty_all_correct_decomps_list(self):
        self.assertTrue(self.words_that_failed_before)
        for word, case in self.words_that_failed_before.items():
            self.assertTrue(
                case.all_correct_decomps,
                f"Expected a non-empty all_correct_decomps list for '{word}' (words_that_failed_before source)",
            )

    def test_every_decomps_concatenated_surface_forms_reconstruct_the_word(self):
        checked = 0
        for word, case in self.hansard.items():
            for decomp in case.all_correct_decomps:
                self.assertEqual(
                    word, concatenated_surface_forms(decomp),
                    f"Concatenated surface forms of {decomp!r} should reconstruct {word!r}",
                )
                checked += 1
        self.assertGreater(checked, 0, "Expected to have actually checked at least one decomp")

    def test_hansard_attested_decomp_is_among_r2l_own_decomps_except_two_known_gaps(self):
        checked = 0
        for word, case in self.hansard.items():
            if _is_flagged(case) or word in KNOWN_GOLD_NOT_IN_R2L_OUTPUT:
                continue
            gold_decomps = case.correct_decomps
            if not gold_decomps:
                continue
            self.assertTrue(
                any(g in case.all_correct_decomps for g in gold_decomps),
                f"Expected at least one of the Hansard-attested decomps for '{word}' to be in R2L's own output",
            )
            checked += 1
        self.assertGreater(checked, 0)

        # The two known gaps really are gaps -- not present -- so a future
        # fix that makes R2L find them should update this test, not silently
        # pass either way.
        for word in KNOWN_GOLD_NOT_IN_R2L_OUTPUT:
            case = self.hansard[word]
            gold_decomps = case.correct_decomps
            if not gold_decomps:
                continue
            self.assertTrue(
                all(g not in case.all_correct_decomps for g in gold_decomps),
                f"'{word}' was expected to still be a known gap (gold decomp not found by R2L) -- "
                "if this now fails, R2L started finding it: remove it from KNOWN_GOLD_NOT_IN_R2L_OUTPUT",
            )


if __name__ == "__main__":
    unittest.main()
