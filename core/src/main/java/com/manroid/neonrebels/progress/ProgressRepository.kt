package com.manroid.neonrebels.progress

/** Key-value persistence; [write] applies a batch (null removes a key) and throws on failure. */
interface SaveStore {
    fun read(key: String): String?
    fun write(values: Map<String, String?>)
}

/**
 * The only owner of campaign progress. Readers use [state]; every change goes through [update],
 * which works on a copy and replaces [state] only after the store accepted the new snapshot.
 * The previous valid snapshot is kept as a backup; unreadable data is never silently deleted.
 */
class ProgressRepository(private val store: SaveStore) {
    enum class LoadStatus { EMPTY, LOADED, RECOVERED_FROM_BACKUP, MIGRATED_LEGACY, CORRUPT }

    var state = GameState()
        private set
    var status = LoadStatus.EMPTY
        private set
    private var writing = false

    val loaded: Boolean get() = status != LoadStatus.CORRUPT

    fun load(): LoadStatus {
        status = try {
            val primary = store.read(KEY)
            when (val decoded = SaveCodec.decode(primary)) {
                is SaveCodec.Decoded.Ok -> {
                    state = decoded.state
                    LoadStatus.LOADED
                }
                is SaveCodec.Decoded.Invalid -> if (primary == null) loadWithoutPrimary() else recover(primary)
            }
        } catch (e: RuntimeException) {
            // An unreadable store must not crash the game; nothing is written until the player chooses.
            state = GameState()
            LoadStatus.CORRUPT
        }
        return status
    }

    private fun loadWithoutPrimary(): LoadStatus {
        val legacyValues = HashMap<String, String>()
        for (key in SaveCodec.LEGACY_KEYS) store.read(key)?.let { legacyValues[key] = it }
        val legacy = SaveCodec.fromLegacy(legacyValues)
        if (legacy == null) {
            state = GameState()
            return LoadStatus.EMPTY
        }
        state = legacy
        return try {
            val batch = HashMap<String, String?>()
            batch[KEY] = SaveCodec.encode(legacy)
            for (key in SaveCodec.LEGACY_KEYS) batch[key] = null
            store.write(batch)
            LoadStatus.MIGRATED_LEGACY
        } catch (e: Exception) {
            // Keep playing on the migrated state; the legacy keys stay until a write succeeds.
            LoadStatus.MIGRATED_LEGACY
        }
    }

    private fun recover(primary: String): LoadStatus {
        val backup = try {
            SaveCodec.decode(store.read(BACKUP))
        } catch (e: RuntimeException) {
            SaveCodec.Decoded.Invalid("backup unreadable")
        }
        if (backup is SaveCodec.Decoded.Ok) {
            state = backup.state
            try {
                store.write(mapOf(CORRUPT to primary, KEY to SaveCodec.encode(backup.state)))
            } catch (e: Exception) {
                // Recovered in memory; the corrupt primary stays on disk and is retried next save.
            }
            return LoadStatus.RECOVERED_FROM_BACKUP
        }
        state = GameState()
        return LoadStatus.CORRUPT
    }

    /**
     * Applies [change] to a copy and persists it. Returns false, leaving [state] untouched, if the
     * store fails or another write is in progress (re-entrant call).
     */
    fun update(change: (GameState) -> Unit): Boolean {
        if (writing || !loaded) return false
        writing = true
        try {
            val next = state.copy()
            change(next)
            val batch = HashMap<String, String?>()
            batch[KEY] = SaveCodec.encode(next)
            if (status != LoadStatus.EMPTY) batch[BACKUP] = SaveCodec.encode(state)
            store.write(batch)
            state = next
            if (status == LoadStatus.EMPTY) status = LoadStatus.LOADED
            return true
        } catch (e: Exception) {
            return false
        } finally {
            writing = false
        }
    }

    /**
     * After an unrecoverable load the player may explicitly start over. The unreadable data is
     * copied to [CORRUPT] first so it can still be inspected.
     */
    fun replaceCorrupt(fresh: GameState): Boolean {
        if (status != LoadStatus.CORRUPT) return false
        return try {
            val damaged = try {
                store.read(KEY)
            } catch (e: RuntimeException) {
                null
            }
            store.write(mapOf(CORRUPT to damaged, KEY to SaveCodec.encode(fresh), BACKUP to null))
            state = fresh
            status = LoadStatus.LOADED
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val KEY = "campaign"
        const val BACKUP = "campaignBackup"
        const val CORRUPT = "campaignCorrupt"
    }
}
