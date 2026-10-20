package dev.hd450btnc.companion.eq

/** Minimal persistence seam. Android uses SharedPreferences; tests use [InMemoryStore]. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}
