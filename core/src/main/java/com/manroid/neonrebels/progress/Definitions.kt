package com.manroid.neonrebels.progress

import com.manroid.neonrebels.model.CharacterType
import com.manroid.neonrebels.model.GameConfig

/** Static game data. Save files refer to these by [id], never by display name. */
class CharacterDefinition(
    val id: String,
    /** Gameplay slot; null while the character has no gameplay yet. */
    val type: CharacterType?,
    val baseHealth: Int,
    val baseDamage: Int,
    val baseCooldown: Float,
    val unlockedAtStart: Boolean,
) {
    val implemented: Boolean get() = type != null
}

class LevelDefinition(
    val id: String,
    val chapterId: String,
    /** 1-based position inside the chapter; the last level of a chapter is its boss. */
    val number: Int,
    val boss: Boolean,
    /** True only when real gameplay exists for this level. */
    val implemented: Boolean,
)

class ChapterDefinition(val id: String, val number: Int, val levels: List<LevelDefinition>) {
    val bossLevel: LevelDefinition get() = levels.last()
}

class CampaignDefinition(val chapters: List<ChapterDefinition>) {
    val levels: List<LevelDefinition> = chapters.flatMap { it.levels }

    fun level(id: String?): LevelDefinition? = levels.firstOrNull { it.id == id }

    fun chapter(id: String?): ChapterDefinition? = chapters.firstOrNull { it.id == id }

    fun chapterOf(level: LevelDefinition): ChapterDefinition = chapter(level.chapterId)!!
}

enum class UpgradeType { HEALTH, DAMAGE, COOLDOWN }

/** [costs][i] buys level i + 1; the number of costs is the maximum level. */
class UpgradeDefinition(val type: UpgradeType, val perLevel: Float, val costs: IntArray) {
    val maxLevel: Int get() = costs.size
}

object GameData {
    const val DEFAULT_LEVEL = "streets_01"
    const val REWARD_NORMAL = 100L
    const val REWARD_BOSS = 250L
    const val REWARD_FIRST_CLEAR = 100L

    val AN = CharacterDefinition("an", CharacterType.AN, GameConfig.AN_HP, GameConfig.AN_DAMAGE,
        GameConfig.AN_SKILL_COOLDOWN, unlockedAtStart = true)
    val VY = CharacterDefinition("vy", CharacterType.VY, GameConfig.VY_HP, GameConfig.VY_DAMAGE,
        GameConfig.VY_SKILL_COOLDOWN, unlockedAtStart = true)
    /** Preview only: planned unlock via the rescue mission in level 2-2, once that exists. */
    val KHANG = CharacterDefinition("khang", null, 1500, 120, 10f, unlockedAtStart = false)
    val characters = listOf(AN, VY, KHANG)

    val upgrades = listOf(
        UpgradeDefinition(UpgradeType.HEALTH, .10f, intArrayOf(100, 200, 350, 550, 800)),
        UpgradeDefinition(UpgradeType.DAMAGE, .08f, intArrayOf(150, 300, 500, 750, 1100)),
        UpgradeDefinition(UpgradeType.COOLDOWN, .05f, intArrayOf(150, 300, 500, 750, 1100)),
    )

    val campaign = CampaignDefinition(listOf(
        chapter("neon_streets", 1, "streets", implemented = 1),
        chapter("steel_factory", 2, "factory"),
        chapter("flooded_tunnels", 3, "tunnels"),
        chapter("sky_garden", 4, "garden"),
        chapter("core_tower", 5, "tower"),
    ))

    fun character(id: String?): CharacterDefinition? = characters.firstOrNull { it.id == id }

    fun character(type: CharacterType): CharacterDefinition = characters.first { it.type == type }

    fun upgrade(type: UpgradeType): UpgradeDefinition = upgrades[type.ordinal]

    /** Levels 1..[implemented] of the chapter have gameplay; the rest show "coming soon". */
    private fun chapter(id: String, number: Int, prefix: String, implemented: Int = 0) = ChapterDefinition(id, number,
        (1..3).map { LevelDefinition("${prefix}_0$it", id, it, boss = it == 3, implemented = it <= implemented) })
}
