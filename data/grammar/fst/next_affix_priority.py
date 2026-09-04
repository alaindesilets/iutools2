"""
Recomputes affix priority against the ACTUAL current implementation state,
not the static frequency ranking affix_frequency.py produces once at the
start. That original ranking's "cumulative fully-covered words" column
assumes affixes get added in that exact order -- it doesn't reflect the
real, messier order this project has actually followed (skips,
abandonments, chain dependencies), so by now it's a poor guide to "which
unimplemented affix would unlock the most words next."

This script instead asks the real question directly: for every word in the
gold standard that ISN'T fully decomposable yet, which of its affixes are
still missing? If a word is missing exactly ONE affix (everything else in
its decomposition is already implemented), adding that one affix would
unlock that word immediately -- credit it. Rank unimplemented affixes by
that marginal unlock count.

"Already implemented" is derived from data/grammar/fst/histogram.py's own
GOLD_CASES rather than hand-maintained separately: every affix key that
appears anywhere in that curated list has a real, hand-verified working
example backing it (that's the whole point of histogram.py), so the set
self-updates every time a new affix's example word gets added there --
no separate bookkeeping to keep in sync.

Usage (from data/grammar/fst/):
    python3 next_affix_priority.py
"""
from collections import Counter, defaultdict

from affix_frequency import load_words, affix_key
from histogram import GOLD_CASES, parse_gold

OUTPUT_FILE = None  # printed only; rerun before each batch, not tracked as a file


def implemented_affix_keys() -> set[str]:
    keys = set()
    for _, gold_strings in GOLD_CASES:
        for gold_string in gold_strings:
            morphemes = parse_gold(gold_string)
            for canonical, morph_id in morphemes[1:]:  # [0] is always the root
                keys.add(affix_key(canonical, morph_id))
    return keys


def main():
    implemented = implemented_affix_keys()
    print(f"{len(implemented)} affix keys confirmed implemented (from histogram.py's GOLD_CASES)\n")

    grouped = defaultdict(list)
    for word, morphemes in load_words():
        grouped[word].append(list(morphemes))

    unlock_count = Counter()
    unlockable_words = defaultdict(list)
    already_done = 0
    multi_missing = 0

    for word, gold_parses in grouped.items():
        # A word may have several accepted gold parses (homograph roots
        # etc.) -- use whichever parse has the fewest missing affixes,
        # same "any valid parse counts" spirit as histogram.py/
        # full_corpus_check.py's own categorize().
        best_missing = None
        for morphemes in gold_parses:
            affix_keys = [affix_key(c, i) for c, i in morphemes[1:]]
            missing = [k for k in affix_keys if k not in implemented]
            if best_missing is None or len(missing) < len(best_missing):
                best_missing = missing

        if not best_missing:
            already_done += 1
        elif len(best_missing) == 1:
            unlock_count[best_missing[0]] += 1
            unlockable_words[best_missing[0]].append(word)
        else:
            multi_missing += 1

    print(f"{already_done} words already fully decomposable with current affixes")
    print(f"{multi_missing} words need 2+ more affixes (not creditable to a single next pick)")
    print(f"{sum(unlock_count.values())} words are exactly ONE affix away\n")

    print("| rank | affix | words unlocked (this affix alone) | example |")
    print("|---|---|---|---|")
    for i, (key, count) in enumerate(unlock_count.most_common(120), start=1):
        example = unlockable_words[key][0]
        print(f"| {i} | `{key}` | {count} | {example} |")


if __name__ == "__main__":
    main()
