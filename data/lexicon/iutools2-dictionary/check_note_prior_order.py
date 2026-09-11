"""
One-off experiment (Q3): for the 7 words that went from "attested" (cap=5,
native order) to "robustly 0 attested" (cap=10, re-ranked order, 4/4
observations) with the SAME candidate set (total_decomps <= 10 for 6 of
the 7 -- only the ORDER changed), does telling the LLM the list is already
sorted by likelihood (notePriorOrder=true) recover the attested verdict?

3 reps each, notePriorOrder=false and notePriorOrder=true, same Hansard
examples across all 6 calls per word (fetched once).

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
N_REPS = 3
MAX_DECOMPS = 10
MAX_HANSARD_EXAMPLES = 5

WORDS = [
    "kamagijauningit", "pilirijittinnik", "inulirijiunirmik", "qausuittu",
    "taimaiqunaqtuugaluaq", "ulluksanganik", "iluunnatalu",
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
    proc = subprocess.Popen(
        [str(CLI), "--pipeline"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1, encoding="utf-8",
    )

    def call(word, examples, note_prior_order):
        request = {
            "cmd": "guess_meaning", "word": word, "lenient": True,
            "maxDecomps": MAX_DECOMPS, "hansardExamples": examples,
            "notePriorOrder": note_prior_order,
        }
        proc.stdin.write(json.dumps(request, ensure_ascii=False) + "\n")
        proc.stdin.flush()
        line = proc.stdout.readline()
        if not line:
            sys.exit(f"cli died. stderr:\n{proc.stderr.read()}")
        reply = json.loads(line)
        if reply.get("exception"):
            print(f"  {word} FAILED: {reply['exception']}", file=sys.stderr)
            return None
        return sum(1 for a in reply["decompAttestations"] if a["attested"])

    results = {}
    for word in WORDS:
        examples = hansard_examples(word)
        off = [call(word, examples, False) for _ in range(N_REPS)]
        on = [call(word, examples, True) for _ in range(N_REPS)]
        results[word] = {"off": off, "on": on}
        print(f"{word}: notePriorOrder=false -> {off}   notePriorOrder=true -> {on}", flush=True)

    proc.stdin.close()
    proc.wait(timeout=30)

    off_zero = sum(1 for r in results.values() if all((x or 0) == 0 for x in r["off"]))
    on_zero = sum(1 for r in results.values() if all((x or 0) == 0 for x in r["on"]))
    print(f"\nwords robustly 0-attested: off={off_zero}/{len(WORDS)}  on={on_zero}/{len(WORDS)}")


if __name__ == "__main__":
    main()
