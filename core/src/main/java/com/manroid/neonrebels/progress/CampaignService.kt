package com.manroid.neonrebels.progress

/** Lock state and implementation state are separate: a level can be unlocked yet "coming soon". */
class LevelStatus(val level: LevelDefinition, val unlocked: Boolean, val completed: Boolean) {
    val implemented: Boolean get() = level.implemented
    val playable: Boolean get() = unlocked && implemented
}

/** Why a level is locked: the previous level, or the previous chapter's boss, is not cleared. */
class LockReason(val requiredLevel: LevelDefinition, val bossOfPreviousChapter: Boolean)

/** Outcome of a finished run, sent by gameplay. */
class RunResult(val runId: Long, val levelId: String, val won: Boolean, val seconds: Float, val collected: Int)

class RewardOutcome(
    val accepted: Boolean,
    /** True when this run was already settled earlier; nothing was added again. */
    val duplicate: Boolean,
    val reward: Long,
    val firstClear: Boolean,
    val newRecord: Boolean,
    /** Next level the player can actually play, or null (e.g. all current content cleared). */
    val nextPlayable: String?,
)

class CampaignService(private val repo: ProgressRepository, private val campaign: CampaignDefinition = GameData.campaign) {
    private val state: GameState get() = repo.state

    fun started(): Boolean = repo.loaded && state.campaignStarted

    fun chapterUnlocked(chapter: ChapterDefinition): Boolean {
        if (!started()) return false
        if (chapter.number == 1) return true
        val previous = campaign.chapters.firstOrNull { it.number == chapter.number - 1 } ?: return false
        return previous.bossLevel.id in state.completedLevels
    }

    fun status(levelId: String): LevelStatus? {
        val level = campaign.level(levelId) ?: return null
        val chapter = campaign.chapterOf(level)
        val unlocked = chapterUnlocked(chapter) &&
            (level.number == 1 || chapter.levels[level.number - 2].id in state.completedLevels)
        return LevelStatus(level, unlocked, level.id in state.completedLevels)
    }

    fun canPlay(levelId: String?): Boolean = levelId != null && status(levelId)?.playable == true

    fun lockReason(levelId: String): LockReason? {
        val s = status(levelId) ?: return null
        if (s.unlocked || !started()) return null
        val chapter = campaign.chapterOf(s.level)
        if (!chapterUnlocked(chapter)) {
            val previous = campaign.chapters.first { it.number == chapter.number - 1 }
            return LockReason(previous.bossLevel, bossOfPreviousChapter = true)
        }
        return LockReason(chapter.levels[s.level.number - 2], bossOfPreviousChapter = false)
    }

    fun bestTime(levelId: String): Float? = state.bestTimes[levelId]

    fun tutorialRecord(): Map<String, String> = HashMap(state.tutorial)

    /** Persists one tutorial step result ("done"/"skipped"); unchanged values are not rewritten. */
    fun recordTutorial(step: String, value: String): Boolean {
        if (state.tutorial[step] == value) return true
        return repo.update { it.tutorial[step] = value }
    }

    /** Base reward and the one-time first-clear bonus still available for [levelId]. */
    fun rewardPreview(levelId: String): Pair<Long, Long> {
        val level = campaign.level(levelId) ?: return 0L to 0L
        val base = if (level.boss) GameData.REWARD_BOSS else GameData.REWARD_NORMAL
        return base to if (level.id in state.completedLevels) 0L else GameData.REWARD_FIRST_CLEAR
    }

    fun selectedLevel(): LevelDefinition = campaign.level(state.selectedLevelId) ?: campaign.levels.first()

    /** Stores the level the player is looking at; any existing level may be selected for viewing. */
    fun select(levelId: String): Boolean {
        if (campaign.level(levelId) == null) return false
        if (state.selectedLevelId == levelId) return true
        return repo.update { it.selectedLevelId = levelId }
    }

    /** Level to focus when a chapter is picked: remembered → first uncleared → first. */
    fun levelForChapter(chapter: ChapterDefinition): LevelDefinition {
        campaign.level(state.selectedLevelId)?.let { if (it.chapterId == chapter.id) return it }
        return chapter.levels.firstOrNull { it.id !in state.completedLevels } ?: chapter.levels.first()
    }

    /** Level to show on CONTINUE: last played if playable → first uncleared playable → last playable. */
    fun continueLevel(): LevelDefinition {
        campaign.level(state.lastPlayedLevelId)?.let { if (canPlay(it.id)) return it }
        val playable = campaign.levels.filter { canPlay(it.id) }
        return playable.firstOrNull { it.id !in state.completedLevels } ?: playable.lastOrNull() ?: campaign.levels.first()
    }

    fun allContentCleared(): Boolean = campaign.levels.filter { it.implemented }.all { it.id in state.completedLevels }

    /** Resets progress, components and upgrades. Run ids keep growing so stale results stay rejected. */
    fun newCampaign(): Boolean {
        val fresh = GameState.newCampaign()
        if (repo.status == ProgressRepository.LoadStatus.CORRUPT) return repo.replaceCorrupt(fresh)
        return repo.update { s ->
            val nextRun = s.nextRunId
            val settled = s.lastSettledRunId
            s.copyFrom(fresh)
            s.nextRunId = nextRun
            s.lastSettledRunId = settled
        }
    }

    /**
     * Settles a run exactly once: marks completion, best time and components in one save.
     * Losses settle the run with no reward. Results for unknown, already settled or older runs
     * are not applied again.
     */
    fun applyResult(result: RunResult): RewardOutcome {
        val s = state
        if (result.runId == s.lastSettledRunId) {
            return RewardOutcome(true, true, s.lastSettledReward, false, false, nextAfter(result.levelId))
        }
        val level = campaign.level(result.levelId)
        if (level == null || result.runId < s.lastSettledRunId || result.runId >= s.nextRunId) {
            return RewardOutcome(false, false, 0L, false, false, null)
        }
        val firstClear = result.won && level.id !in s.completedLevels
        val previousBest = s.bestTimes[level.id]
        val newRecord = result.won && (previousBest == null || result.seconds < previousBest)
        val reward = if (!result.won) 0L else {
            val (base, bonus) = rewardPreview(level.id)
            base + bonus + result.collected.coerceAtLeast(0)
        }
        val saved = repo.update { next ->
            next.lastSettledRunId = result.runId
            next.lastSettledWon = result.won
            next.lastSettledReward = reward
            if (result.won) {
                next.completedLevels += level.id
                if (newRecord) next.bestTimes[level.id] = result.seconds
                next.components += reward
            }
        }
        if (!saved) return RewardOutcome(false, false, 0L, false, false, null)
        return RewardOutcome(true, false, reward, firstClear, newRecord, nextAfter(level.id))
    }

    private fun nextAfter(levelId: String): String? {
        val index = campaign.levels.indexOfFirst { it.id == levelId }
        return campaign.levels.drop(index + 1).firstOrNull { canPlay(it.id) && it.id !in state.completedLevels }?.id
    }
}
