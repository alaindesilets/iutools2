package org.iutools.llm

/*
 * Substitutes a value into a pre-translated sentence that has a slot for it.
 *
 * The app's French/English string resources mark that slot as "%1$s" (or
 * "%1$d" for a number) -- e.g. "Meanings for %1$s?" with "iglu" gives
 * "Meanings for iglu?". On Android that substitution is normally done by
 * String.format, which isn't available in the shared (commonMain) code this
 * lives in. Every sentence passed through here (see GuessMeaningSeedLabels,
 * GuessMeaningErrorLabels) has exactly one such slot, so a plain string
 * replace is enough.
 */
fun fillPlaceholder(template: String, value: Any): String =
    template.replace("%1\$s", value.toString())
        .replace("%1\$d", value.toString())
