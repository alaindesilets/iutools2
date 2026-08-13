"""
One-off script for Phase 3 of the Guess Meaning spike (see
doc/spike-llm-local-iutools-mobile.md): parses the Spalding dictionary's
static HTML page into a flat word -> meaning JSON, embedded in the app as
composeApp/src/main/res/raw/spalding.json. Not part of the app build itself
-- run manually, output reviewed, then copied in by hand. Re-run this (and
re-review the diff) if the source page is ever updated.

Source: https://www.inuktitutcomputing.ca/Spalding/index.php?lang=en
(a single long static page -- no per-word search -- confirmed by Alain and
by inspecting the fetched page directly; see Phase 3 of the plan doc for
why that makes it the simplest dictionary to start with).

Usage: fetch the page (this project's sandbox can't reach the open
internet, so this step needs to happen somewhere that can, e.g.
    curl -sS "https://www.inuktitutcomputing.ca/Spalding/index.php?lang=en" -o /tmp/spalding_raw.html
), then:
    python3 tools/parse_spalding_dictionary.py /tmp/spalding_raw.html composeApp/src/main/res/raw/spalding.json

Structure of the source page (confirmed by inspection, not guessed): each
dictionary entry is one <div class=entry> ... </div> block containing one
or more headwords marked as <a name='WORD'>WORD</a>, followed by prose
(definition, dialect notes, cross-references to other headwords via
javascript:go2('word') links). A single block often bundles a primary word
with several related derived/variant forms sharing the same block.

Approach: one output row per headword, all headwords in the same block
sharing that block's full plain-text content as "meaning" -- deliberately
not trying to slice the prose apart per-headword (it's free-flowing
scholarly prose with inline cross-references, not cleanly delimited per
word), so a lookup on any variant surfaces the whole entry's context,
which is more useful for a "guess meaning" tool anyway. A literal "-" as a
headword (a print-dictionary convention meaning "repeat the preceding
stem") is skipped -- not a real independent word.
"""
import html
import json
import re
import sys

ENTRY_RE = re.compile(r"<div class=entry>(.*?)</div>", re.DOTALL)
HEADWORD_RE = re.compile(r"<a name='([^']*)'>")
TAG_RE = re.compile(r"<[^>]+>")
WHITESPACE_RE = re.compile(r"\s+")


def strip_html(fragment: str) -> str:
    text = TAG_RE.sub(" ", fragment)
    text = html.unescape(text)
    text = WHITESPACE_RE.sub(" ", text).strip()
    return text


def parse(html_content: str) -> list[dict]:
    blocks = ENTRY_RE.findall(html_content)
    rows = []
    for block in blocks:
        headwords = HEADWORD_RE.findall(block)
        real_headwords = [w for w in headwords if w.strip() and w.strip() != "-"]
        if not real_headwords:
            continue
        meaning = strip_html(block)
        for word in real_headwords:
            rows.append({"word": html.unescape(word), "meaning": meaning})
    return rows


def main():
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <source.html> <output.json>", file=sys.stderr)
        sys.exit(1)
    src_path, out_path = sys.argv[1], sys.argv[2]

    with open(src_path, "r", encoding="utf-8") as f:
        content = f.read()

    rows = parse(content)
    print(f"headword rows: {len(rows)}")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, separators=(",", ":"))

    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
