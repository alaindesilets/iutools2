#!/usr/bin/env bash
# Download the published analyzed-lexicon S1 .jsonl for the version pinned in
# datapackage.json, and verify its checksum. See README.md.
set -euo pipefail
cd "$(dirname "$0")"

read -r URL EXPECTED_SHA PUBLISHED < <(python3 - <<'PY'
import json
d = json.load(open("datapackage.json"))
r = d["resources"][0]
# "published" is false while datapackage.json still carries its DRAFT _status
# or a TODO placeholder in the release-time fields (path / hash).
draft = str(d.get("_status", "")).startswith("DRAFT") or "TODO" in (r["path"] + r["hash"])
print(r["path"], r["_uncompressed_sha256"].removeprefix("sha256:"), "no" if draft else "yes")
PY
)

if [ "$PUBLISHED" != "yes" ]; then
  echo "This version is not published yet — datapackage.json is still a DRAFT." >&2
  echo "See README.md section 'Publishing a new version'." >&2
  exit 1
fi

echo "Downloading $URL"
curl -fL --retry 3 -o hansard-top100k-decomps.jsonl.gz "$URL"

echo "Decompressing ..."
gunzip -kf hansard-top100k-decomps.jsonl.gz

GOT=$(sha256sum hansard-top100k-decomps.jsonl | cut -d' ' -f1)
if [ "$GOT" != "$EXPECTED_SHA" ]; then
  echo "CHECKSUM MISMATCH" >&2
  echo "  expected $EXPECTED_SHA" >&2
  echo "  got      $GOT" >&2
  exit 1
fi
echo "OK — hansard-top100k-decomps.jsonl ($(wc -l < hansard-top100k-decomps.jsonl) records), sha256 verified."
