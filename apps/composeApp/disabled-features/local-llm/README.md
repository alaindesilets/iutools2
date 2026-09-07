# On-device LLM backend — disabled, kept for later

This directory holds the on-device (local) LLM backend for "Guess Meaning",
moved out of `composeApp/src/main/kotlin/` so it is **not compiled or
bundled into the app**. As of this writing, the app never loads a local
model — Guess Meaning only ever calls the Claude API (see
`GuessMeaningEngine.kt`).

## Why it's here instead of deleted

Alain wants to keep this code for possible future use, but not ship it: it
adds a `~dozen MB` native inference dependency
(`com.google.ai.edge.litertlm:litertlm-android`) to every install for a
feature that's still experimental (see the files' own header comments for
the on-device model's mixed early results, e.g. Qwen2-0.5B producing poor
guesses).

## What's here

- `LocalLlmEngine.kt` — the LiteRT-LM wrapper: model file handling
  (`modelDirectory`/`findModelFile`/`importModel`) and `generate()`, which
  runs a prompt through an on-device model and streams the reply.
- `GuessMeaningScreen.kt` — the "Advanced" debug screen: the only UI that
  ever exposed the local-model toggle and the model-file picker. It was
  already unreachable before this backend was disabled (no button navigated
  to it; `ExplanationScreen.kt` replaced its prompt-tuning role) — moved
  here as a unit with `LocalLlmEngine.kt` since neither is useful without
  the other.

Both files are otherwise unchanged from when they lived under `src/main/`.

## How to reactivate

1. Re-add the dependency in `composeApp/build.gradle.kts` (see the comment
   there marking where it was removed).
2. Move both files in this directory back to
   `composeApp/src/main/kotlin/org/iutools/app/`.
3. In `composeApp/src/main/kotlin/org/iutools/app/MainActivity.kt`, restore
   `Screen.GuessMeaning`, the `useLocalModel` state, and the
   `GuessMeaningScreen(...)` call — see git history for the commit that
   removed them (search the log for this README's filename).
4. In `composeApp/src/main/kotlin/org/iutools/app/GuessMeaningEngine.kt`,
   restore `sendToLocalModel()` and the `if (useLocalModel)` branch in
   `send()` — again, see git history.
5. Restore the local-model string resources removed from `strings.xml` /
   `values-fr/strings.xml` (`local_llm_toggle_label`,
   `local_llm_pick_model_button`, `local_llm_model_imported`,
   `local_llm_model_import_failed`, `local_llm_role_label`), and revert
   `local_llm_disabled_message` back to `local_llm_model_missing`'s original
   wording.
