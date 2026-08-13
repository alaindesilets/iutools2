# Nunavut Hansard bilingual examples: building and publishing the index

The "Find bilingual examples of use" feature in `WordLookupScreen` reads
from a local SQLite index built from the [Nunavut Hansard Inuktitut-English
Parallel Corpus 3.0.1](https://nrc-digital-repository.canada.ca/eng/view/object/?id=c7e34fa7-7629-43c2-bd6d-19b32bf64f60)
(NRC Digital Repository, DOI [10.4224/40001819](https://doi.org/10.4224/40001819),
CC BY 4.0). See `NunavutHansardLocalIndex.kt`'s header comment for why this
is a local index rather than a live network fetch.

The resulting database is large (~550 MiB) and is **not** bundled as an app
asset or pushed by any Gradle task -- every `installDebug`/Run would then
push that much data to the device on every redeploy. Instead it lands in
the app's external-files directory (survives ordinary redeploys, only wiped
by uninstalling or clearing app data) one of two ways:

- **End users**: tap "Download bilingual examples" in the app itself
  (`NunavutHansardDownloader.kt`) -- fetches a compressed copy from this
  repo's GitHub Releases and unpacks it. This is the normal path; nothing
  below is needed for it to work once a release has been published (see
  "Publishing a release" below).
- **Devs**, e.g. testing before a release exists, or preferring not to
  exercise the download path repeatedly: build it locally and push it with
  `adb`, see "Local dev setup" below.

## Publishing a release (do this once per corpus/schema version)

1. Download `NUNAVUT-HANSARD-INUKTITUT-ENGLISH-PARALLEL-CORPUS-3.0.1.TGZ`
   (202 MiB) from the NRC Digital Repository page linked above, and extract
   at least `NunavutHansard.en`, `NunavutHansard.iu`, `NunavutHansard.id`
   into `tools/hansard-corpus/` (gitignored -- this repo never tracks the
   corpus itself).
2. Build the index:
   ```
   python3 tools/build_hansard_index.py tools/hansard-corpus/Nunavut-Hansard-Inuktitut-English-Parallel-Corpus-3.0 tools/hansard.db
   ```
   Takes a few minutes; prints progress every 200k pairs processed.
3. Compress it:
   ```
   gzip -9 -c tools/hansard.db > tools/hansard.db.gz
   ```
   (~155 MiB; both files are gitignored -- never committed to the repo.)
4. Publish it as a new, immutable GitHub Release. **Always a new tag,
   never overwrite an existing one's asset** -- same "never force-push"
   discipline as this project's git history, and it's what makes a pinned
   `DOWNLOAD_URL` in the app code trustworthy:
   ```
   gh release create hansard-db-v1 tools/hansard.db.gz \
     --title "Nunavut Hansard bilingual index v1" \
     --notes "SQLite index built from the Nunavut Hansard Inuktitut-English Parallel Corpus 3.0.1 (NRC, CC BY 4.0) for iutools-mobile's bilingual-examples feature. See tools/README-hansard.md."
   ```
   Bump the tag number (`hansard-db-v2`, ...) for the next build, and update
   `NunavutHansardDownloader.DOWNLOAD_URL` to match, in the same commit that
   triggered the rebuild (schema change in `build_hansard_index.py` bumping
   `NunavutHansardLocalIndex.SCHEMA_VERSION`, or a newer corpus release) --
   that commit is the actual code-version-to-data-version link, see
   `NunavutHansardDownloader.kt`'s header comment.

## Local dev setup (skip the download, push directly)

After building `tools/hansard.db` (steps 1-2 above), push it straight to a
running device/emulator instead of downloading it back from GitHub:
```
tools/push_hansard_db.sh
```
Repeat per device/emulator you test on; only needed again if you wipe an
emulator's data or rebuild the db with a different schema.

## License obligations (CC BY 4.0)

The corpus's own `LICENSE` and `README` files are bundled in the app as
`composeApp/src/main/res/raw/hansard_license.txt` and `hansard_readme.txt`
(copied byte-for-byte, not retyped -- see AGENTS.md's data-integrity rule),
and a short attribution/citation line is shown alongside every Hansard
result in the app itself (`R.string.hansard_attribution`). Any change to how
this corpus is used or redistributed should keep both of those, per the
corpus's own terms of use.
