package dev.hd450btnc.companion.eq

class EqRepository(private val store: KeyValueStore) {
    fun load(): EqState = EqStateCodec.decode(store.getString(KEY))
    fun save(state: EqState) = store.putString(KEY, EqStateCodec.encode(state))
    fun clear() = store.putString(KEY, null)

    companion object {
        const val KEY = "eq_state_v1"
    }
}
