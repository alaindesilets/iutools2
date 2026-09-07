package org.iutools.i18n

import org.iutools.llm.MeaningLanguage
import java.util.Locale

/*
 * The human language the app talks to its user in. iutools is bilingual:
 * every user-facing string exists in English and in French, and the user
 * can pick which one from the Settings panel, overriding whatever the
 * device's system locale is.
 *
 * This is the app-wide choice. It is a superset of MeaningLanguage (in
 * org.iutools.llm), which is the same English/French pair seen narrowly by
 * the "Guess Meaning" feature -- toMeaningLanguage() below bridges the two.
 *
 * endonym is each language's own name for itself ("English", "Français"),
 * so a language picker can list the options without translating them.
 */
enum class AppLanguage(val locale: Locale, val endonym: String) {
    ENGLISH(Locale.ENGLISH, "English"),
    FRENCH(Locale.FRENCH, "Français"),
}

/*
 * The language to start in the first time the app runs, before the user has
 * made a choice in Settings: French if the device is set to French,
 * otherwise English.
 */
fun defaultAppLanguage(): AppLanguage =
    if (Locale.getDefault().language == "fr") AppLanguage.FRENCH else AppLanguage.ENGLISH

fun AppLanguage.toMeaningLanguage(): MeaningLanguage = when (this) {
    AppLanguage.ENGLISH -> MeaningLanguage.ENGLISH
    AppLanguage.FRENCH -> MeaningLanguage.FRENCH
}
