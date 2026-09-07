#!/usr/bin/env bash
# Rebuild hansard-top100k-decomps.jsonl from the corpus, in one command.
# This is for cutting a NEW version, not everyday use (~2 h of compute, and
# the result is non-deterministic at ~0.2 % — see README.md). To just get the
# published dataset, run fetch.sh instead.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
cd "$REPO"

read -r PIN_COMMIT CORPUS_SHA < <(python3 - "$HERE/datapackage.json" <<'PY'
import json, re, sys
d = json.load(open(sys.argv[1]))
assoc = d["provenance"]["prov:wasGeneratedBy"]["prov:wasAssociatedWith"]
commit = re.search(r"@ ([0-9a-f]{40})", assoc).group(1)
corpus_sha = d["sources"][0]["hash"].removeprefix("sha256:")
print(commit, corpus_sha)
PY
)

HEAD_COMMIT="$(git rev-parse HEAD)"
if [ "$HEAD_COMMIT" != "$PIN_COMMIT" ]; then
  echo "HEAD is $HEAD_COMMIT" >&2
  echo "datapackage.json pins the build to $PIN_COMMIT" >&2
  echo "Check out that commit first (or set FORCE=1 to build anyway and update the pin afterwards)." >&2
  [ "${FORCE:-0}" = "1" ] || exit 1
fi

CORPUS="tools/hansard-corpus/Nunavut-Hansard-Inuktitut-English-Parallel-Corpus-3.0/NunavutHansard.iu"
if [ ! -f "$CORPUS" ]; then
  echo "Corpus not found: $CORPUS" >&2
  echo "Download the Nunavut Hansard Parallel Corpus 3.0.1 and unpack it there — see README.md." >&2
  exit 1
fi
GOT_CORPUS_SHA="$(sha256sum "$CORPUS" | cut -d' ' -f1)"
if [ "$GOT_CORPUS_SHA" != "$CORPUS_SHA" ]; then
  echo "Corpus checksum mismatch — expected $CORPUS_SHA, got $GOT_CORPUS_SHA" >&2
  exit 1
fi

echo "Building the CLI ..."
./gradlew :cli:installDist

echo "Mining decompositions (this takes ~2 h) ..."
python3 data/lexicon/decompositions/mine_decompositions.py --top "${TOP:-100000}" --shards "${SHARDS:-4}"

echo
echo "Fresh run vs. the counts committed in datapackage.json:"
python3 - "$HERE/datapackage.json" "$HERE/run-manifest.json" <<'PY'
import json, sys
old = json.load(open(sys.argv[1]))["record_counts"]
new = json.load(open(sys.argv[2]))["record_counts"]
w = max(len(k) for k in old)
for k in old:
    o, n = old[k], new.get(k, "?")
    delta = f"  ({n-o:+d})" if isinstance(n, int) else ""
    print(f"  {k:<{w}}  committed {o:>7}   fresh {n:>7}{delta}")
print("\nSHA-256 will differ from any previous run — that is expected (README.md).")
PY
