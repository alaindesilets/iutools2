"""
Bulk-generates lexc entries for the demonstrative pronoun/adverb system --
an entire root+ending category, not an extension of an already-partially-
implemented affix/ending.

Source data: core/.../dataCSV/Demonstratives.csv (65 rows: "ad" =
adverbial demonstratives like "right here"/"over there", "pd" = pronoun
demonstratives like "this one"/"that one") and Endings_demonstrative.csv
(17 rows: "tad" = case endings for adverbial roots, "tpd" = case endings
for pronoun roots). Confirmed a genuine lever, not a repeat of the "large
CSV block, near-zero corpus attestation" trap found elsewhere in this
project's history (e.g. the imperative mood, see lexicon.lexc's comment
on "suk"/1vv): a full sweep of the
985-word gold standard found 64 distinct words touching a tad-/tpd-/(r)ad-
/(r)pd- tagged morpheme, BEFORE any of this was implemented.

Two forms per Demonstratives.csv row (confirmed by reverse-engineering the
tag shapes MorphAnalGoldStandard_Hansard.kt's own gold ids actually use,
e.g. "taakkuninga" = {taakku:taakku/rpd-sc-p}{ninga:ninga/tpd-acc-p}
alongside "tamannali" = {tamanna:tamanna/pd-ml-s}{li:li/1q} -- the SAME
CSV row backs both):
  - a STANDALONE complete word (the "morpheme" column, tag = "{type}-
    {objectType}[-{number}]", e.g. "pd-ml-s") -- a demonstrative can stand
    alone as a full pronoun/adverb, taking only a discourse particle
    afterward (gold-attested: "tamannali"/"tamannalu"/"taimali").
  - a ROOT that takes a case ending from Endings_demonstrative.csv (the
    "root" column, tag = "r{type}-{objectType}[-{number}]", e.g.
    "rpd-sc-p") -- e.g. "taakkuninga" = root "taakku" + ending "ninga".
  A few rows (4, all "manna"/"tamanna"/"kanna"/"takanna") give TWO
  space-separated root variants (e.g. "tamatu tamaksu"). Unlike a normal
  "multiple candidates, no disambiguator" ambiguity elsewhere in this
  project, the two variants here don't compete for the same surface
  string -- each spells a genuinely distinct surface form -- so BOTH are
  wired as independent root-taking-an-ending entries (gold attests both
  "tamatuminga" and "tamaksuminga" for the same underlying word,
  confirming this isn't guesswork).

No phonological alternation is modeled at all: neither CSV file has any
action column (unlike every other CSV this project has bulk-generated
from), so every entry here is PLAIN concatenation, verified correct for
8 of the round's 12 target words by direct string concatenation (root +
ending letter-for-letter matches the gold surface). The other 4 target
words (tavvanngat/tavvuuna, sharing an unexplained root-internal "tagv"->
"tavv" alternation, and taassuma/taassuminga, sharing an unexplained
"taapsu"+"m..."->"tass"+"m..." alternation) are NOT covered by this
generator and remain unimplemented -- same "don't guess at an unexplained
alternation" policy as "iglu"->"illu" elsewhere in this project.

Output is a SEPARATE file (demonstratives-generated.lexc), never
hand-edited, mirroring generate_roots.py's own convention.

Usage (from tools/fst/):
    python3 generate_demonstratives.py
"""
import csv
from pathlib import Path

from generate_roots import dialect_variants  # Dialect.groups cluster-spelling variants

REPO_ROOT = Path(__file__).parent.parent.parent
DATA_DIR = REPO_ROOT / "core/src/commonMain/resources/org/iutools/linguisticdata/dataCSV"
OUTPUT_FILE = Path(__file__).parent / "demonstratives-generated.lexc"

# type -> (standalone continuation, root-form ending lexicon)
TYPE_INFO = {
    "ad": ("DemonstrativeAdverbsGenerated", "TadEndings"),
    "pd": ("DemonstrativePronounsGenerated", "TpdEndings"),
}


def standalone_tag(row: dict) -> str:
    tag = f"{row['type']}-{row['objectType']}"
    if row["number"]:
        tag += f"-{row['number']}"
    return tag


def root_tag(row: dict) -> str:
    return f"r{standalone_tag(row)}"


def main():
    tags_used = set()
    standalone_entries = {"ad": [], "pd": []}
    root_entries = {"ad": [], "pd": []}

    with (DATA_DIR / "Demonstratives.csv").open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            row_type = row["type"]
            if row_type not in TYPE_INFO:
                continue

            morpheme = row["morpheme"]
            s_tag = standalone_tag(row)
            tags_used.add(s_tag)
            for surf in [morpheme] + dialect_variants(morpheme):
                standalone_entries[row_type].append(f"{morpheme}+{s_tag}:{surf} # ;")
                standalone_entries[row_type].append(f"{morpheme}+{s_tag}:{surf} QParticles ;")

            root = row["root"]
            r_tag = root_tag(row)
            tags_used.add(r_tag)
            _, endings_lexicon = TYPE_INFO[row_type]
            # A few rows give TWO space-separated dialectal root variants
            # (see module docstring) -- unlike a normal "multiple
            # candidates, no disambiguator" ambiguity elsewhere in this
            # project, these don't compete for the SAME surface string:
            # each variant spells a distinct surface form (e.g. "tamatu"
            # vs "tamaksu"), so both are wired as independent entries
            # rather than skipped -- same "wire all attested outcomes in
            # parallel" precedent as mut/tn-dat-s's own default branch,
            # see lexicon.lexc's comment on "mut"/tn-dat-s in NounEndings.
            for variant in root.split(" "):
                for surf in [variant] + dialect_variants(variant):
                    root_entries[row_type].append(
                        f"{variant}+{r_tag}:{surf} {endings_lexicon} ;"
                    )

    # Endings_demonstrative.csv has no "variant" column at all (unlike
    # the root-source CSVs generate_roots.py reads), but gold attests a
    # "double n" spelling for exactly these three endings alongside the
    # canonical one -- confirmed genuine free variation, not a
    # conditioned alternation, by finding gold rows for BOTH spellings
    # of the IDENTICAL root+ending pair (e.g. "tamakkuninga"/
    # "tamakkuninnga", "tamatuminga"/"tamatuminnga", both root
    # "tamakku"/"tamatu" + this same ending). Hard-coded here rather
    # than guessed at as a general rule, same as the tagv/taapsu root
    # spelling variants hand-added in lexicon.lexc (see that file's
    # Adverbs/Pronouns blocks) for the sibling roots this exact family
    # of endings attaches to.
    EXTRA_ENDING_VARIANTS = {
        "minga": ["minnga"],
        "munga": ["munnga"],
        "ninga": ["ninnga"],
    }

    ending_entries = {"tad": [], "tpd": []}
    with (DATA_DIR / "Endings_demonstrative.csv").open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            row_type = row["type"]
            if row_type not in ending_entries:
                continue
            morpheme = row["morpheme"]
            tag = f"{row_type}-{row['case']}"
            if row["number"]:
                tag += f"-{row['number']}"
            tags_used.add(tag)
            for spelling in [morpheme] + EXTRA_ENDING_VARIANTS.get(morpheme, []):
                ending_entries[row_type].append(f"++{morpheme}+{tag}:{spelling} # ;")

    lines = [
        "! Generated by tools/fst/generate_demonstratives.py -- DO NOT HAND-EDIT.\n"
        "! Rerun that script to regenerate after the source CSVs change.\n"
        "! See its module docstring for exactly which rows are included/excluded.\n",
        "Multichar_Symbols " + " ".join(sorted(tags_used)),
    ]
    lines.append("\nLEXICON DemonstrativeAdverbsGenerated")
    lines.extend(standalone_entries["ad"])
    lines.append("\nLEXICON DemonstrativePronounsGenerated")
    lines.extend(standalone_entries["pd"])
    lines.append("\nLEXICON DemonstrativeAdverbRootsGenerated")
    lines.extend(root_entries["ad"])
    lines.append("\nLEXICON DemonstrativePronounRootsGenerated")
    lines.extend(root_entries["pd"])
    lines.append("\nLEXICON TadEndings")
    lines.extend(ending_entries["tad"])
    lines.append("\nLEXICON TpdEndings")
    lines.extend(ending_entries["tpd"])

    OUTPUT_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")

    total = sum(len(v) for v in standalone_entries.values()) + \
        sum(len(v) for v in root_entries.values()) + \
        sum(len(v) for v in ending_entries.values())
    print(f"Wrote {total} entries ({len(tags_used)} distinct tags) to {OUTPUT_FILE}")
    for label, d in [("standalone", standalone_entries), ("root", root_entries), ("ending", ending_entries)]:
        for k, v in d.items():
            print(f"  {label} {k}: {len(v)}")


if __name__ == "__main__":
    main()
