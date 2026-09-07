package org.iutools.app

import android.content.Context
import org.iutools.llm.CostLogStore

/*
 * The Android backing for :core's GuessMeaningCostLog: its raw text blob in
 * a small plain-text SharedPreferences file, same approach as AppSettings
 * for non-secret data.
 */
fun sharedPreferencesCostLogStore(context: Context): CostLogStore {
    val prefs = context.getSharedPreferences("guess_meaning_cost_log", Context.MODE_PRIVATE)
    return object : CostLogStore {
        override fun read(): String = prefs.getString("entries", "") ?: ""
        override fun write(value: String) {
            prefs.edit().putString("entries", value).apply()
        }
    }
}
