#!/usr/bin/env python3
"""Mine R2L decompositions for the N most frequent Nunavut Hansard word forms.

Stage S1 of the analyzed-lexicon dataset (doc/dev/plans/analyzed-lexicon-dataset.md).
R2L only, no re-ranking; `--lenient-decomps` ON; every decomposition R2L
produces per word, each tagged strict vs lenient (guessed dropped final
consonant) from the CLI's `decompositionsLenient` array.

Output (JSONL, one record per word, most frequent first):
  {"rank", "count", "word_syl", "word_rom",
   "decomps": [...], "decomps_lenient": [bool,...],
   "n_decomps", "r2l_timed_out", "r2l_error"}

R2L is single-threaded and slow (~0.5-2 s/word past the top few thousand),
so the work is split into parallel shards (one `cli --pipeline` process
each). Each shard is resumable; a shard crash (R2L OOM etc.) is isolated to
the crashing word.

Run from the repo root:
    python3 data/lexicon/decompositions/mine_decompositions.py [--top N] [--shards K]
Prerequisite: cli/build/install/cli/bin/cli  (./gradlew :cli:installDist)

Usually driven via regenerate.sh, which pins the build commit and verifies
the corpus first. Writes the .jsonl plus run-manifest.json (a raw run log);
the dataset descriptor is the hand-curated datapackage.json. See README.md.
"""
import argparse
import collections
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
CORPUS = REPO / "tools/hansard-corpus/Nunavut-Hansard-Inuktitut-English-Parallel-Corpus-3.0/NunavutHansard.iu"
CLI = REPO / "cli/build/install/cli/bin/cli"
CLI_LIB = REPO / "cli/build/install/cli/lib"
OUTDIR = Path(__file__).resolve().parent
WORK = OUTDIR / "_work"
SYL = re.compile(r"[᐀-ᙿᢰ-᣿]+")

CHUNK = 150
PER_WORD_TIMEOUT_SECS = 20
# hard wall on any single `cli --pipeline` call: R2L's own --timeout-secs
# does not interrupt every code path, so a pathological word can hang the
# process indefinitely. When this fires the chunk is bisected and the
# offending word ends up isolated + recorded as no_output.
PIPELINE_HARD_CAP_SECS = 180
DEFAULT_SHARDS = 4
# cap each parallel cli JVM so N shards fit in RAM; a genuinely pathological
# word then OOMs its chunk fast (isolated + recorded) instead of thrashing.
CLI_JVM_HEAP = "900m"


# ----------------------------------------------------------------- corpus prep
def frequency_list():
    c = collections.Counter()
    total = 0
    with CORPUS.open(encoding="utf-8") as fh:
        for line in fh:
            for tok in SYL.findall(line):
                c[tok] += 1
                total += 1
    return c.most_common(), total


def transcode_to_roman(words):
    out = subprocess.run(
        ["java", "-cp", f"{CLI_LIB}/*", "org.iutools.morph.cli.TranscodeToolKt"],
        input="\n".join(words) + "\n", capture_output=True, text=True, check=True,
    )
    rom = out.stdout.splitlines()
    if len(rom) != len(words):
        sys.exit(f"transcode count mismatch: {len(rom)} != {len(words)}")
    return rom


# ----------------------------------------------------------------- R2L calls
def run_pipeline(words):
    timeout = min(60 + len(words) * (PER_WORD_TIMEOUT_SECS + 2), PIPELINE_HARD_CAP_SECS)
    res = {}
    env = {**os.environ, "CLI_OPTS": f"-Xmx{CLI_JVM_HEAP}"}
    try:
        p = subprocess.run(
            [str(CLI), "--pipeline", "--lenient-decomps", "--timeout-secs", str(PER_WORD_TIMEOUT_SECS)],
            input="\n".join(words) + "\n", capture_output=True, text=True, timeout=timeout, env=env,
        )
        stream = p.stdout
    except subprocess.TimeoutExpired as e:
        stream = e.stdout.decode("utf-8", "replace") if e.stdout else ""
    for line in stream.splitlines():
        try:
            r = json.loads(line)
        except ValueError:
            continue
        res[r["word"]] = r
    return res


def _payload(decomps=None, lenient=None, timed_out=False, error=None):
    decomps = decomps or []
    outcome = "crashed" if error else ("timed_out" if timed_out else "completed")
    return {
        # the analyzer itself is file-level (manifest.analyzer_used), not per record
        "analyzer_outcome": outcome,  # completed | timed_out | crashed
        "decomps": decomps,
        "decomps_lenient": lenient or [],
        "n_decomps": len(decomps),
        "r2l_timed_out": timed_out,   # older equivalents, kept
        "r2l_error": error,
    }


def decompose_chunk(words, emit, depth=0):
    got = run_pipeline(words)
    for w in words:
        if w in got:
            r = got[w]
            emit(w, _payload(r.get("decompositions"), r.get("decompositionsLenient"),
                             bool(r.get("timedOut")), r.get("exception")))
    missing = [w for w in words if w not in got]
    if not missing:
        return
    if len(missing) == len(words):
        if len(words) == 1 or depth > 12:
            for w in words:
                emit(w, _payload(error="no_output"))
            return
        mid = len(words) // 2
        decompose_chunk(words[:mid], emit, depth + 1)
        decompose_chunk(words[mid:], emit, depth + 1)
        return
    crasher = missing[0]
    emit(crasher, _payload(error="crashed_pipeline"))
    rest = [w for w in missing if w != crasher]
    if rest:
        decompose_chunk(rest, emit, depth + 1)


# ----------------------------------------------------------------- shard worker
def read_jsonl(path):
    recs = []
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            try:
                recs.append(json.loads(line))
            except ValueError:
                break  # truncated trailing line from a killed run
    return recs


def run_worker(shard, shards):
    """Process words[shard::shards] from _work/words.tsv into _work/shard_<k>.jsonl."""
    rows = [l.split("\t") for l in (WORK / "words.tsv").read_text(encoding="utf-8").splitlines()]
    mine = rows[shard::shards]
    shard_path = WORK / f"shard_{shard}.jsonl"

    done_recs = read_jsonl(shard_path)
    done = {r["word_syl"] for r in done_recs}
    with shard_path.open("w", encoding="utf-8") as fh:  # rewrite (drops any truncated tail)
        for r in done_recs:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    todo = [(int(rank), int(cnt), syl, rom) for rank, cnt, syl, rom in mine if syl not in done]
    meta = {syl: (rank, cnt, rom) for rank, cnt, syl, rom in todo}
    fh = shard_path.open("a", encoding="utf-8")
    n = 0
    t0 = time.time()

    def emit(syl, payload):
        nonlocal n
        rank, cnt, rom = meta[syl]
        rec = {"rank": rank, "count": cnt, "word_syl": syl, "word_rom": rom, **payload}
        fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
        n += 1

    for i in range(0, len(todo), CHUNK):
        decompose_chunk([syl for _, _, syl, _ in todo[i:i + CHUNK]], emit)
        fh.flush()
        rate = n / max(time.time() - t0, 1e-9)
        print(f"[shard {shard}] {n}/{len(todo)}  {rate:.2f} w/s", flush=True)
    fh.close()
    print(f"[shard {shard}] done ({len(done)+n} total)", flush=True)


# ----------------------------------------------------------------- coordinator
def sha256(p):
    h = hashlib.sha256()
    h.update(Path(p).read_bytes())
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--top", type=int, default=100_000)
    ap.add_argument("--shards", type=int, default=DEFAULT_SHARDS)
    ap.add_argument("--shard-worker", type=int, default=None)
    args = ap.parse_args()

    if args.shard_worker is not None:
        run_worker(args.shard_worker, args.shards)
        return

    N = args.top
    WORK.mkdir(exist_ok=True)
    out_path = OUTDIR / f"hansard-top{N//1000}k-decomps.jsonl"
    freq_path = OUTDIR / f"hansard-freq-top{N//1000}k.tsv"
    run_manifest_path = OUTDIR / "run-manifest.json"

    print(f"[{time.strftime('%H:%M:%S')}] counting corpus frequencies ...", flush=True)
    ranked, total_tokens = frequency_list()
    top = ranked[:N]
    freq_path.write_text("".join(f"{i}\t{c}\t{w}\n" for i, (w, c) in enumerate(top, 1)), encoding="utf-8")
    print(f"  {total_tokens:,} tokens, {len(ranked):,} types", flush=True)

    # words already decomposed by any earlier (single-thread or sharded) run
    prior = {r["word_syl"]: r for r in read_jsonl(out_path)}
    for k in range(args.shards):
        for r in read_jsonl(WORK / f"shard_{k}.jsonl"):
            prior[r["word_syl"]] = r
    print(f"  {len(prior):,} words already done from earlier runs", flush=True)

    syls = [w for w, _ in top]
    need = [w for w in syls if w not in prior]
    print(f"[{time.strftime('%H:%M:%S')}] transcoding {len(need):,} remaining words to Roman ...", flush=True)
    roms = transcode_to_roman(need) if need else []
    rom_of = dict(zip(need, roms))
    rank_of = {w: i + 1 for i, w in enumerate(syls)}
    count_of = dict(top)
    (WORK / "words.tsv").write_text(
        "".join(f"{rank_of[w]}\t{count_of[w]}\t{w}\t{rom_of[w]}\n" for w in need), encoding="utf-8")

    if need:
        print(f"[{time.strftime('%H:%M:%S')}] launching {args.shards} shard workers ...", flush=True)
        procs = [
            subprocess.Popen(
                [sys.executable, __file__, "--top", str(N), "--shards", str(args.shards),
                 "--shard-worker", str(k)],
                stdout=open(WORK / f"shard_{k}.log", "w"), stderr=subprocess.STDOUT)
            for k in range(args.shards)
        ]
        t0 = time.time()
        while any(p.poll() is None for p in procs):
            time.sleep(60)
            done_now = sum(len(read_jsonl(WORK / f"shard_{k}.jsonl")) for k in range(args.shards))
            total = len(prior) + done_now
            rate = done_now / max(time.time() - t0, 1e-9)
            eta = (len(need) - done_now) / max(rate, 1e-9)
            print(f"[{time.strftime('%H:%M:%S')}] {total:,}/{N:,}  ({rate:.1f} w/s, "
                  f"ETA {eta/60:.0f} min)", flush=True)
        for p in procs:
            if p.returncode != 0:
                print(f"  WARNING: a shard worker exited {p.returncode}", flush=True)

    # merge everything in rank order
    merged = dict(prior)
    for k in range(args.shards):
        for r in read_jsonl(WORK / f"shard_{k}.jsonl"):
            merged[r["word_syl"]] = r
    ordered = sorted(merged.values(), key=lambda r: r["rank"])
    with out_path.open("w", encoding="utf-8") as fh:
        for r in ordered:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    stats = collections.Counter(total=len(ordered))
    for r in ordered:
        stats[r["analyzer_outcome"]] += 1                     # completed | timed_out | crashed
        if r["analyzer_outcome"] == "completed":
            stats["completed_with_decomps" if r["n_decomps"] else "completed_empty"] += 1

    # Raw record of THIS run. Not the dataset descriptor -- that is the
    # hand-curated datapackage.json (tracked). This file is gitignored and
    # rewritten every run; the publish checklist in README.md copies the
    # values below into a new datapackage.json version.
    #   The git HEAD recorded here is the commit the CLI was built from *only
    # if* the build happened on that checkout -- regenerate.sh enforces that.
    run_manifest = {
        "_comment": "One local mining run. See datapackage.json (curated) and README.md.",
        "finished": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "iutools2_commit": subprocess.run(["git", "-C", str(REPO), "rev-parse", "HEAD"],
                                          capture_output=True, text=True).stdout.strip(),
        "command": f"python3 data/lexicon/decompositions/mine_decompositions.py --top {N} --shards {args.shards}",
        "analyzer": "MorphologicalAnalyzer_R2L (Uqailaut / Benoit Farley), no re-ranking, "
                    f"--lenient-decomps on, --timeout-secs {PER_WORD_TIMEOUT_SECS}",
        "input_corpus": {
            "file": str(CORPUS.relative_to(REPO)),
            "sha256": sha256(CORPUS),
        },
        "tokenization_regex": SYL.pattern,
        "total_corpus_tokens": total_tokens,
        "word_types_kept": N,
        "output_file": out_path.name,
        "output_sha256": sha256(out_path),
        "output_bytes": out_path.stat().st_size,
        "record_counts": {
            "records": stats["total"],
            "with_at_least_one_decomposition": stats["completed_with_decomps"],
            "no_parse_found": stats["completed_empty"],
            "analyzer_timed_out": stats["timed_out"],
            "analyzer_crashed": stats["crashed"],
        },
    }
    run_manifest_path.write_text(json.dumps(run_manifest, indent=2, ensure_ascii=False))
    print(f"[{time.strftime('%H:%M:%S')}] DONE {dict(stats)}\n  {out_path.name}\n  {run_manifest_path.name}", flush=True)


if __name__ == "__main__":
    main()
