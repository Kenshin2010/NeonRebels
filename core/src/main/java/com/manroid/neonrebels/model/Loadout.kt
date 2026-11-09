package com.manroid.neonrebels.model

/**
 * Per-run character stats, fixed when the run starts. Arrays are indexed by
 * [CharacterType.ordinal]; [first] deploys first, the other character is the reserve.
 * The default is both characters at upgrade level 0.
 */
class Loadout(
    val first: CharacterType = CharacterType.AN,
    val maxHp: IntArray = intArrayOf(GameConfig.AN_HP, GameConfig.VY_HP),
    val damage: IntArray = intArrayOf(GameConfig.AN_DAMAGE, GameConfig.VY_DAMAGE),
    val skillCooldown: FloatArray = floatArrayOf(GameConfig.AN_SKILL_COOLDOWN, GameConfig.VY_SKILL_COOLDOWN),
)
