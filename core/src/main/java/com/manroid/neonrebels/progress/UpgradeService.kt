package com.manroid.neonrebels.progress

class UpgradePreview(
    val level: Int,
    val maxLevel: Int,
    val current: Float,
    /** Null at the maximum level. */
    val next: Float?,
    val cost: Long?,
    val missing: Long,
) {
    val maxed: Boolean get() = next == null
    val affordable: Boolean get() = !maxed && missing == 0L
}

class UpgradeService(private val repo: ProgressRepository) {
    enum class Purchase { OK, LOCKED, MAXED, INSUFFICIENT, BUSY, SAVE_FAILED }

    private var busy = false

    val processing: Boolean get() = busy

    fun canUpgrade(characterId: String): Boolean {
        val c = GameData.character(characterId) ?: return false
        return c.implemented && characterId in repo.state.unlockedCharacters
    }

    fun preview(characterId: String, type: UpgradeType): UpgradePreview {
        val c = GameData.character(characterId)!!
        val def = GameData.upgrade(type)
        val level = repo.state.upgradeLevel(characterId, type)
        val maxed = level >= def.maxLevel
        val cost = if (maxed) null else def.costs[level].toLong()
        val missing = if (cost == null) 0L else (cost - repo.state.components).coerceAtLeast(0L)
        return UpgradePreview(level, def.maxLevel, StatCalculator.value(c, type, level),
            if (maxed) null else StatCalculator.value(c, type, level + 1), cost, missing)
    }

    /** Buys exactly one level. Balance and level change in the same save, or not at all. */
    fun purchase(characterId: String, type: UpgradeType): Purchase {
        if (busy) return Purchase.BUSY
        busy = true
        try {
            if (!canUpgrade(characterId)) return Purchase.LOCKED
            val def = GameData.upgrade(type)
            val level = repo.state.upgradeLevel(characterId, type)
            if (level >= def.maxLevel) return Purchase.MAXED
            val cost = def.costs[level].toLong()
            if (repo.state.components < cost) return Purchase.INSUFFICIENT
            val saved = repo.update { s ->
                s.components -= cost
                s.setUpgradeLevel(characterId, type, level + 1)
            }
            return if (saved) Purchase.OK else Purchase.SAVE_FAILED
        } finally {
            busy = false
        }
    }
}
