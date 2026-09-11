"""
Phase 2 of the iutools2-dictionary spike (see README.md and
build_spike_dico.py's own header, which describes Phase 1): the LLM pass.

For each of the 100 original seed words (the same random sample
build_spike_dico.py drew -- same SEED, recomputed here rather than stored,
since Phase 1 didn't tag which of the 1883 records in words.jsonl were an
original seed vs. a stem-sibling pulled in structurally), this script:

1. Fetches real bilingual Hansard sentence pairs for the word from
   inuktitutcomputing.ca's Nunavut Hansard search (see the
   reference_nunavut_hansard_search_api memory for how that API works).
2. Asks the Kotlin :cli's `guess_meaning` subcommand, in STAGED mode
   (`"staged": true` -- see GuessMeaningStaged.kt in :core) -- over one
   long-running `cli --pipeline` process, per doc/dev/dev-guidelines.md's
   "(b) Port + expose via :cli --pipeline": start with just the top-2
   (ReferenceReranker-sorted) decompositions, and only grow to the top-5
   then top-10 if nothing was attested AND some Hansard example still
   isn't explained. Verdicts from an earlier stage are frozen, never
   re-asked. Chosen over one single top-10 call after a live investigation
   (2026-09-11, doc/dev/plans/offline-dictionary-generation.md,
   "Post-validation") found the single-call form's zero-attested rate is
   order-sensitive and staging fixes most of it: 18/19 previously-zero
   words got an attestation in at least one repeat, at lower cost too
   (smaller prompts).
3. Writes the answer back into the matching record in words.jsonl,
   REPLACING decomps with just the capped, re-ranked, evaluated set (see
   README.md's schema comment on why -- some words have hundreds of raw
   candidates) plus total_decomps (how many there really were),
   llm_meanings, several_decomps_are_llm_attested, stems (derived from the
   attested decomp(s), same stem_pairs() rule as build_spike_dico.py --
   root through the last derivational suffix), and
   hansard_examples_not_matching_any_decomp -- NOTE: this last field's
   accuracy is NOT yet validated; a quick manual spot-check found several
   likely false positives (an example flagged as unmatched that actually
   read as consistent with an already-attested sense). Treat it as a lead
   to investigate, not as ground truth, until that's looked into.

Deliberately scoped to the 100 seeds, not all 1883 records in words.jsonl:
the Hansard search API is an undocumented internal endpoint, ~4s/word for
the two calls it takes -- fine for a one-time spike-sized sample, not
something to run at 1883-word volume without deciding that's worth the load
on someone else's site first. `dictionary_found_in` (Spalding/Tusaalanga
lookup) is a separate step (see doc/dev/plans/offline-dictionary-generation.md,
S3 step 3) and stays null here -- out of scope for this pass.

Prerequisite: `./gradlew :cli:installDist`, and an Anthropic API key
reachable by the CLI (env var or ~/.iutools2/local.secrets.properties --
see apps/cli/src/main/kotlin/org/iutools/morph/cli/ApiKey.kt).

CHECKPOINTING (Alain, 2026-09-11): words.jsonl is rewritten after EVERY
word, not just at the end, and each processed record is stamped with
PHASE2_ALGO_VERSION. So an interrupted run (out of API credits -- see
below -- Ctrl-C, a crash) loses at most the one word in flight, not the
whole batch:
  - Plain re-run (no flag): words already stamped with the CURRENT
    PHASE2_ALGO_VERSION are skipped; anything else in the requested word
    set (never processed, or processed under an OLDER version) is
    (re)run. This is "resume" by default.
  - If some requested words were processed under a DIFFERENT version than
    this run's, the script refuses to guess what you want and exits
    asking for `--resume` (keep those older results, only fill in what's
    missing) or `--restart` (reprocess everything, overwriting them) --
    e.g. after editing the guess_meaning prompt/approach mid-batch.
  - `--restart` reprocesses the whole requested word set regardless of
    what's already there.

If a reply comes back with `"insufficientCredits": true` (the account's
prepaid balance ran out -- see ApiKey.kt / LlmClient_Anthropic.kt), the
run stops immediately (progress already saved incrementally) rather than
churning through the rest of the word list on calls doomed to fail the
same way.

Run from data/lexicon/iutools2-dictionary/:
    python3 run_phase2_llm_pass.py [--limit N] [--resume | --restart]
"""
from __future__ import annotations

import argparse
import json
import random
import re
import subprocess
import sys
import time
import urllib.request
import uuid
from pathlib import Path

LEXICON = "../decompositions/hansard-top100k-decomps.jsonl"
WORDS_FILE = "words.jsonl"
SEED = 20260911
N_SEEDS = 100
MAX_HANSARD_EXAMPLES = 5
CLI = Path("../../../apps/cli/build/install/cli/bin/cli")

# Bump this whenever a change to the guess_meaning prompt/approach would
# make an already-processed record's fields stale/inconsistent with a
# fresh one (e.g. switching from a single call to staged, or -- as
# happened same-day -- separating the unmatched-examples check out into
# its own final call). Stamped onto every record apply_reply() touches;
# see the module docstring's CHECKPOINTING section.
PHASE2_ALGO_VERSION = "staged-v2-separate-unmatched-check"

HANSARD_SEARCH_URL = "https://www.inuktitutcomputing.ca/NunavutHansard/__searchHansard.php"
HANSARD_ALIGN_URL = "https://www.inuktitutcomputing.ca/NunavutHansard/__getAlignmentsForWord.php"

ENDING_RE = re.compile(r"^t[nvd]-|^t[ap]d-")
ENCL_RE = re.compile(r"^\d+q$")


def multipart_post(url: str, fields: dict, timeout: int = 20) -> str:
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n')
    parts.append(f"--{boundary}--\r\n")
    body = "".join(parts).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8")


def hansard_examples(word: str, cap: int = MAX_HANSARD_EXAMPLES) -> list[dict]:
    try:
        search = json.loads(multipart_post(HANSARD_SEARCH_URL, {
            "query_inuktitut": word, "query_english": "", "show_latin": "on", "lang": "en",
        }))
        # PHP serializes an empty associative array as a JSON array `[]`,
        # not `{}` -- "no matches" comes back as "distributions": [].
        distributions = search.get("distributions")
        if not isinstance(distributions, dict):
            return []
        dist = distributions.get(word)
        if not dist or not dist.get("pointers"):
            return []
        pointers = list(dict.fromkeys(dist["pointers"]))[:cap]
        align = json.loads(multipart_post(HANSARD_ALIGN_URL, {
            "word": word, "pointers": ",".join(pointers),
        }))
        return [
            {"inuktitut": a["inuktitut"], "english": a["english"], "date": a.get("date", "")}
            for a in align.get("alignments", [])[:cap]
        ]
    except Exception as e:
        print(f"  [hansard warning] {word}: {e}", file=sys.stderr)
        return []


def stem_pairs_from_decomp(decomp_strs: list[str]) -> list[str]:
    """Same rule as build_spike_dico.py's stem_pairs(): root through the
    last derivational suffix -- drop trailing enclitics, then cut before
    the first inflectional ending."""
    ids = [s.split("/", 1)[1] for s in decomp_strs]
    e = len(ids)
    while e and ENCL_RE.match(ids[e - 1]):
        e -= 1
    cut = e
    for k in range(e):
        if ENDING_RE.match(ids[k]):
            cut = k
            break
    return decomp_strs[:cut]


def seed_words() -> list[str]:
    lexicon_words = set()
    with open(LEXICON, encoding="utf-8") as fh:
        for line in fh:
            rec = json.loads(line)
            if rec["analyzer_outcome"] == "completed" and rec.get("decomps"):
                lexicon_words.add(rec["word_rom"])
    return sorted(random.Random(SEED).sample(sorted(lexicon_words), N_SEEDS))


def load_words() -> list[dict]:
    with open(WORDS_FILE, encoding="utf-8") as fh:
        fh.readline()  # header _comment
        return [json.loads(line) for line in fh]


def apply_reply(record: dict, examples: list[dict], reply: dict) -> None:
    # REPLACES record["decomps"] with just the capped, re-ranked set the
    # CLI actually evaluated (see README.md's schema comment) -- discards
    # the untruncated Phase-1 list, which for some words runs into the
    # hundreds and was never itself re-ranked.
    new_decomps = [
        {"decomp": d["decomp"], "lenient": d["lenient"], "llm_attested": None}
        for d in reply["decompositions"]
    ]
    attested_decomps = []
    for att in reply["decompAttestations"]:
        entry = new_decomps[att["decompIndex"]]
        entry["llm_attested"] = att["attested"]
        if att["attested"]:
            attested_decomps.append(entry["decomp"])
    record["decomps"] = new_decomps
    record["total_decomps"] = reply["totalDecomps"]
    record["decomps_evaluated"] = reply["decompsEvaluated"]
    if new_decomps:
        record["top_decomp"] = new_decomps[0]["decomp"]
        record["top_decomp_is_lenient"] = new_decomps[0]["lenient"]
        record["some_lenient_decomps_included"] = any(d["lenient"] for d in new_decomps)

    record["llm_meanings"] = reply["senses"]
    record["several_decomps_are_llm_attested"] = sum(1 for d in new_decomps if d["llm_attested"]) > 1
    record["llm_evidence_count"] = len(examples)
    record["hansard_examples_not_matching_any_decomp"] = [
        examples[i] for i in reply["unmatchedExampleIndices"]
    ]
    stems: list[list[str]] = []
    for d in attested_decomps:
        s = stem_pairs_from_decomp(d)
        if s and s not in stems:
            stems.append(s)
    record["stems"] = stems
    record["phase2_algo_version"] = PHASE2_ALGO_VERSION


def save_words(records: list[dict], n_done: int, n_total: int) -> None:
    """Rewrites the whole file -- called after EVERY word, not just at the
    end, so an interrupted run (Ctrl-C, a crash, out of API credits) loses
    at most the one word in flight. See the module docstring's
    CHECKPOINTING section."""
    with open(WORDS_FILE, "w", encoding="utf-8") as fh:
        fh.write(json.dumps({
            "_comment": (
                f"SPIKE DICTIONARY -- Phase 2, checkpointed (run_phase2_llm_pass.py, "
                f"algo version {PHASE2_ALGO_VERSION}): {n_done}/{n_total} requested "
                f"words processed so far this run. A record with "
                f"phase2_algo_version == \"{PHASE2_ALGO_VERSION}\" has decomps "
                f"REPLACED by the capped/re-ranked/evaluated set (see total_decomps "
                f"for how many there really were) plus llm_meanings/stems/"
                f"hansard_examples_not_matching_any_decomp filled in; everything else "
                f"is still Phase-1-only (untruncated decomps, those other fields "
                f"null/absent) or processed under an older algo version. NOT the "
                f"real dataset. See README.md, Phase 1/Phase 2."
            ),
        }, ensure_ascii=False) + "\n")
        for r in sorted(records, key=lambda r: r["rank"]):
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--limit", type=int, default=None, help="process only the first N seed words (for a quick test)")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--resume", action="store_true",
                       help="keep results from an older algo version, only (re)process what's missing/current-version-missing")
    mode.add_argument("--restart", action="store_true",
                       help="reprocess the whole requested word set, overwriting any existing results")
    args = parser.parse_args()

    if not CLI.exists():
        sys.exit(f"error: {CLI} not found -- run `./gradlew :cli:installDist` from the repo root first.")

    todo_words = seed_words()
    if args.limit:
        todo_words = todo_words[: args.limit]

    records = load_words()
    by_word = {r["word_roman"]: r for r in records}
    missing = [w for w in todo_words if w not in by_word]
    if missing:
        print(f"warning: {len(missing)} seed word(s) not found in {WORDS_FILE}: {missing[:5]}", file=sys.stderr)
    todo_words = [w for w in todo_words if w in by_word]

    current_version_done = {w for w in todo_words if by_word[w].get("phase2_algo_version") == PHASE2_ALGO_VERSION}
    other_versions = {by_word[w]["phase2_algo_version"] for w in todo_words
                       if by_word[w].get("phase2_algo_version") not in (None, PHASE2_ALGO_VERSION)}
    stale_done = {w for w in todo_words if by_word[w].get("phase2_algo_version") in other_versions}

    if stale_done and not args.resume and not args.restart:
        sys.exit(
            f"{len(stale_done)} of the {len(todo_words)} requested word(s) were already "
            f"processed under a DIFFERENT algo version ({sorted(other_versions)}) than "
            f"this script's current version ({PHASE2_ALGO_VERSION!r}).\n"
            f"Pass --resume to keep those results and only (re)process what's missing "
            f"under the current version, or --restart to reprocess the whole requested "
            f"word set, overwriting them."
        )

    words_to_run = todo_words if args.restart else [w for w in todo_words if w not in current_version_done]
    n_already_done = len(todo_words) - len(words_to_run)

    print(
        f"{len(todo_words)} seed word(s) requested, {n_already_done} already done "
        f"(algo version {PHASE2_ALGO_VERSION!r}), {len(words_to_run)} to process now",
        flush=True,
    )
    if not words_to_run:
        return

    proc = subprocess.Popen(
        [str(CLI), "--pipeline"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1, encoding="utf-8",
    )

    total_cost = 0.0
    n_ok = 0
    n_failed = 0
    started = time.time()
    for i, word in enumerate(words_to_run, 1):
        examples = hansard_examples(word)
        request = {
            "cmd": "guess_meaning", "word": word, "lenient": True,
            "hansardExamples": examples, "staged": True,
        }
        proc.stdin.write(json.dumps(request, ensure_ascii=False) + "\n")
        proc.stdin.flush()
        reply_line = proc.stdout.readline()
        if not reply_line:
            err = proc.stderr.read()
            sys.exit(f"cli --pipeline exited unexpectedly. stderr:\n{err}")
        reply = json.loads(reply_line)

        if reply.get("insufficientCredits"):
            print(
                f"\nSTOPPING at [{i}/{len(words_to_run)}] {word}: out of API credits -- "
                f"{reply['exception']}\n{n_ok} word(s) saved this run. Top up credits, "
                f"then re-run (no flag needed -- already-done words are skipped by default).",
                file=sys.stderr, flush=True,
            )
            break

        record = by_word[word]
        if reply.get("exception"):
            n_failed += 1
            print(f"[{i}/{len(words_to_run)}] {word}: FAILED -- {reply['exception']}", flush=True)
            continue

        apply_reply(record, examples, reply)
        n_attested = sum(1 for a in reply["decompAttestations"] if a["attested"])
        cost = reply.get("estimatedCostUsd") or 0.0
        total_cost += cost
        n_ok += 1
        save_words(records, n_already_done + n_ok, len(todo_words))
        print(
            f"[{i}/{len(words_to_run)}] {word}: {len(examples)} hansard ex., "
            f"{len(reply['senses'])} senses, {n_attested} attested decomp(s), "
            f"{reply['stagesRun']} stage(s)/{reply['decompsEvaluated']} decomps evaluated, ${cost:.5f}",
            flush=True,
        )

    proc.stdin.close()
    proc.wait(timeout=30)

    elapsed = time.time() - started
    print(
        f"\ndone: {n_ok} ok, {n_failed} failed, total estimated LLM cost "
        f"${total_cost:.4f}, {elapsed:.0f}s elapsed",
        flush=True,
    )


if __name__ == "__main__":
    main()
