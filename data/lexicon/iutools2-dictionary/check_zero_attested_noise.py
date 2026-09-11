"""
One-off: for each of the words that came out "0 attested decomps" in the
Phase 2 run (cap=10, re-ranked), re-run guess_meaning 3 times with the SAME
inputs (same word, same fetched Hansard examples across the 3 repeats) and
see whether the "0 attested" outcome is stable -- i.e. is it noise from an
unpinned-temperature LLM call, or a real, repeatable gap?

Run from data/lexicon/iutools2-dictionary/.
"""
import json
import subprocess
import sys
import uuid
import urllib.request
from pathlib import Path

CLI = Path("../../../apps/cli/build/install/cli/bin/cli")
HANSARD_SEARCH_URL = "https://www.inuktitutcomputing.ca/NunavutHansard/__searchHansard.php"
HANSARD_ALIGN_URL = "https://www.inuktitutcomputing.ca/NunavutHansard/__getAlignmentsForWord.php"
N_REPEATS = 3
MAX_DECOMPS = 10
MAX_HANSARD_EXAMPLES = 5

ZERO_ATTESTED_WORDS = [
    "kamagijauningit", "kiinaujaliriniq", "qaujimajuinnauvugut", "pilirijittinnik",
    "naalauqtinnagit", "inulirijiunirmik", "tukitaaqsimavisi", "qausuittu",
    "piuniqsauniarmat", "uqaqattaqullugit", "katimaniqarniaratta", "uqalimaarumavara",
    "niurrutiqarlutik", "nunalinnuuqattarluta", "tuniuqqaqtauqattaqtut",
    "taimaiqunaqtuugaluaq", "ulluksanganik", "iluunnatalu", "tikinnasuarninganik",
]


def multipart_post(url, fields, timeout=20):
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


def hansard_examples(word, cap=MAX_HANSARD_EXAMPLES):
    try:
        search = json.loads(multipart_post(HANSARD_SEARCH_URL, {
            "query_inuktitut": word, "query_english": "", "show_latin": "on", "lang": "en",
        }))
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


def main():
    if not CLI.exists():
        sys.exit(f"error: {CLI} not found")

    proc = subprocess.Popen(
        [str(CLI), "--pipeline"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1, encoding="utf-8",
    )

    results = {}
    for word in ZERO_ATTESTED_WORDS:
        examples = hansard_examples(word)
        counts = []
        for rep in range(N_REPEATS):
            request = {
                "cmd": "guess_meaning", "word": word, "lenient": True,
                "maxDecomps": MAX_DECOMPS, "hansardExamples": examples,
            }
            proc.stdin.write(json.dumps(request, ensure_ascii=False) + "\n")
            proc.stdin.flush()
            line = proc.stdout.readline()
            if not line:
                sys.exit(f"cli died. stderr:\n{proc.stderr.read()}")
            reply = json.loads(line)
            if reply.get("exception"):
                counts.append(None)
                print(f"{word} rep{rep}: FAILED {reply['exception']}", flush=True)
                continue
            n_att = sum(1 for a in reply["decompAttestations"] if a["attested"])
            counts.append(n_att)
        results[word] = counts
        print(f"{word}: {len(examples)} ex, attested-count per rep = {counts}", flush=True)

    proc.stdin.close()
    proc.wait(timeout=30)

    n_always_zero = sum(1 for c in results.values() if all(x == 0 for x in c if x is not None))
    n_ever_nonzero = sum(1 for c in results.values() if any((x or 0) > 0 for x in c))
    n_all_nonzero = sum(1 for c in results.values() if all((x or 0) > 0 for x in c))
    print(f"\n{len(results)} words checked")
    print(f"always 0 attested across all {N_REPEATS} reps: {n_always_zero}")
    print(f"at least one rep with >0 attested: {n_ever_nonzero}")
    print(f"all {N_REPEATS} reps with >0 attested: {n_all_nonzero}")

    with open("scratchpad_zero_attested_noise.json", "w", encoding="utf-8") as fh:
        json.dump(results, fh, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
