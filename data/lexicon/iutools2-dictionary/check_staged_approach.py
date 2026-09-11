"""
One-off (Q4 validation): re-run the 19 original zero-attested words through
the staged guess_meaning path (top-2, grow only if undecided + evidence
remains unexplained), 3 reps each, same Hansard examples across reps, to
see whether staging changes the zero-attested rate vs the single-call
form's earlier 8/19-robustly-zero result.

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
MAX_HANSARD_EXAMPLES = 5

WORDS = [
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
    proc = subprocess.Popen(
        [str(CLI), "--pipeline"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1, encoding="utf-8",
    )

    results = {}
    total_cost = 0.0
    for word in WORDS:
        examples = hansard_examples(word)
        counts = []
        stages = []
        for _ in range(N_REPS):
            request = {
                "cmd": "guess_meaning", "word": word, "lenient": True,
                "hansardExamples": examples, "staged": True,
            }
            proc.stdin.write(json.dumps(request, ensure_ascii=False) + "\n")
            proc.stdin.flush()
            line = proc.stdout.readline()
            if not line:
                sys.exit(f"cli died. stderr:\n{proc.stderr.read()}")
            reply = json.loads(line)
            if reply.get("exception"):
                counts.append(None)
                print(f"{word}: FAILED {reply['exception']}", file=sys.stderr)
                continue
            n_att = sum(1 for a in reply["decompAttestations"] if a["attested"])
            counts.append(n_att)
            stages.append(reply["stagesRun"])
            total_cost += reply.get("estimatedCostUsd") or 0.0
        results[word] = counts
        print(f"{word}: {len(examples)} ex, attested per rep = {counts}, stagesRun per rep = {stages}", flush=True)

    proc.stdin.close()
    proc.wait(timeout=30)

    always_zero = sum(1 for c in results.values() if all((x or 0) == 0 for x in c))
    ever_nonzero = sum(1 for c in results.values() if any((x or 0) > 0 for x in c))
    print(f"\n{len(results)} words, total cost ${total_cost:.4f}")
    print(f"always 0 attested across {N_REPS} reps (staged): {always_zero}")
    print(f"at least one rep with >0 attested (staged): {ever_nonzero}")


if __name__ == "__main__":
    main()
