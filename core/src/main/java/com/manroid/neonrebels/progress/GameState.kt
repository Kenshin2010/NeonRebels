package com.manroid.neonrebels.progress

/**
 * Persistent campaign progress (settings live elsewhere). Never holds per-run state such as
 * current health, projectiles or touches; unlocks are derived from [completedLevels].
 */
class GameState {
    var campaignStarted = false
    var components = 0L
    val unlockedCharacters = LinkedHashSet<String>()
    /** Ordered squad: index 0 deploys first. */
    val team = ArrayList<String>()
    var selectedLevelId: String? = null
    var lastPlayedLevelId: String? = null
    val completedLevels = LinkedHashSet<String>()
    val bestTimes = LinkedHashMap<String, Float>()
    /** characterId -> level per [UpgradeType.ordinal]. */
    val upgrades = LinkedHashMap<String, IntArray>()
    /** Action tutorial: step id -> "done" or "skipped". Reset by a new campaign. */
    val tutorial = LinkedHashMap<String, String>()
    /** Next run id to hand out; ids only grow, so one settled marker is enough to reject replays. */
    var nextRunId = 1L
    var lastSettledRunId = 0L
    var lastSettledWon = false
    var lastSettledReward = 0L

    fun upgradeLevel(characterId: String, type: UpgradeType): Int = upgrades[characterId]?.get(type.ordinal) ?: 0

    fun setUpgradeLevel(characterId: String, type: UpgradeType, level: Int) {
        upgrades.getOrPut(characterId) { IntArray(UpgradeType.values().size) }[type.ordinal] = level
    }

    fun copy(): GameState {
        val c = GameState()
        c.copyFrom(this)
        return c
    }

    fun copyFrom(o: GameState) {
        campaignStarted = o.campaignStarted
        components = o.components
        unlockedCharacters.clear(); unlockedCharacters.addAll(o.unlockedCharacters)
        team.clear(); team.addAll(o.team)
        selectedLevelId = o.selectedLevelId
        lastPlayedLevelId = o.lastPlayedLevelId
        completedLevels.clear(); completedLevels.addAll(o.completedLevels)
        bestTimes.clear(); bestTimes.putAll(o.bestTimes)
        upgrades.clear()
        for ((id, levels) in o.upgrades) upgrades[id] = levels.copyOf()
        tutorial.clear(); tutorial.putAll(o.tutorial)
        nextRunId = o.nextRunId
        lastSettledRunId = o.lastSettledRunId
        lastSettledWon = o.lastSettledWon
        lastSettledReward = o.lastSettledReward
    }

    companion object {
        /** A brand-new campaign: An and Vy unlocked, squad [an, vy], first level selected. */
        fun newCampaign(): GameState {
            val s = GameState()
            s.campaignStarted = true
            for (c in GameData.characters) if (c.unlockedAtStart) s.unlockedCharacters.add(c.id)
            s.team.add(GameData.AN.id)
            s.team.add(GameData.VY.id)
            s.selectedLevelId = GameData.DEFAULT_LEVEL
            return s
        }
    }
}
