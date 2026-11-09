package com.manroid.neonrebels.progress

import com.manroid.neonrebels.model.Loadout

/** Squad being edited on the squad screen; only saved when a run actually starts. */
class DraftTeam(initial: List<String>) {
    val slots = arrayOfNulls<String>(2)

    init {
        for (i in 0 until minOf(2, initial.size)) slots[i] = initial[i]
    }

    fun count(): Int = slots.count { it != null }

    operator fun contains(id: String): Boolean = slots[0] == id || slots[1] == id
}

/** Everything a run needs, copied from progress when the run starts so later edits cannot leak in. */
class RunSetup(val runId: Long, val levelId: String, val team: List<String>, val stats: Map<String, CharacterStats>) {
    fun loadout(): Loadout {
        val first = GameData.character(team[0])!!.type!!
        val an = stats.getValue(GameData.AN.id)
        val vy = stats.getValue(GameData.VY.id)
        return Loadout(first, intArrayOf(an.maxHealth, vy.maxHealth), intArrayOf(an.damage, vy.damage),
            floatArrayOf(an.skillCooldown, vy.skillCooldown))
    }

    init {
        require(team.size == 2 && team[0] != team[1])
        require(team.all { GameData.character(it)?.type != null })
    }
}

class TeamService(private val repo: ProgressRepository, private val campaign: CampaignService) {
    enum class Change { OK, NOT_AVAILABLE, TEAM_FULL, NOT_IN_TEAM }
    enum class StartError { LEVEL_NOT_PLAYABLE, INVALID_TEAM, SAVE_FAILED }

    fun draft(): DraftTeam = DraftTeam(repo.state.team)

    /** Unlocked in this campaign and backed by real gameplay. */
    fun selectable(id: String): Boolean {
        val c = GameData.character(id) ?: return false
        return c.implemented && id in repo.state.unlockedCharacters
    }

    fun add(draft: DraftTeam, id: String): Change {
        if (!selectable(id)) return Change.NOT_AVAILABLE
        if (id in draft) return Change.OK
        val free = draft.slots.indexOfFirst { it == null }
        if (free < 0) return Change.TEAM_FULL
        draft.slots[free] = id
        return Change.OK
    }

    fun remove(draft: DraftTeam, id: String): Change {
        val index = draft.slots.indexOf(id)
        if (index < 0) return Change.NOT_IN_TEAM
        draft.slots[index] = null
        // Keep the lead slot filled so "slot 1 deploys first" stays meaningful.
        if (index == 0 && draft.slots[1] != null) {
            draft.slots[0] = draft.slots[1]
            draft.slots[1] = null
        }
        return Change.OK
    }

    fun swap(draft: DraftTeam) {
        val first = draft.slots[0]
        draft.slots[0] = draft.slots[1]
        draft.slots[1] = first
    }

    fun valid(draft: DraftTeam): Boolean {
        val a = draft.slots[0] ?: return false
        val b = draft.slots[1] ?: return false
        return a != b && selectable(a) && selectable(b)
    }

    fun stats(id: String): CharacterStats? = GameData.character(id)?.let { StatCalculator.stats(it, repo.state) }

    /**
     * Re-checks level and squad, then saves the squad, last played level and a fresh run id in
     * one write. Returns the immutable run setup, or the reason it could not start.
     */
    fun start(draft: DraftTeam, levelId: String): Pair<RunSetup?, StartError?> {
        if (!campaign.canPlay(levelId)) return null to StartError.LEVEL_NOT_PLAYABLE
        if (!valid(draft)) return null to StartError.INVALID_TEAM
        val team = listOf(draft.slots[0]!!, draft.slots[1]!!)
        val runId = repo.state.nextRunId
        val saved = repo.update { s ->
            s.team.clear(); s.team += team
            s.lastPlayedLevelId = levelId
            s.selectedLevelId = levelId
            s.nextRunId = runId + 1
        }
        if (!saved) return null to StartError.SAVE_FAILED
        val stats = team.associateWith { stats(it)!! }
        return RunSetup(runId, levelId, team, stats) to null
    }
}
