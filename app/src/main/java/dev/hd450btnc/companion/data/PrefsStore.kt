package dev.hd450btnc.companion.data

import android.content.Context
import dev.hd450btnc.companion.eq.KeyValueStore

/** SharedPreferences-backed store. Writes are asynchronous (apply) so they never block the UI. */
class PrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
