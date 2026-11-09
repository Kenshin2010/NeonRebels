package com.manroid.neonrebels.progress

import kotlin.math.roundToInt

class CharacterStats(val maxHealth: Int, val damage: Int, val skillCooldown: Float)

/** The single stat formula shared by gameplay, the squad screen and the workshop. */
object StatCalculator {
    fun health(character: CharacterDefinition, level: Int): Int =
        (character.baseHealth * (1f + GameData.upgrade(UpgradeType.HEALTH).perLevel * level)).roundToInt()

    fun damage(character: CharacterDefinition, level: Int): Int =
        (character.baseDamage * (1f + GameData.upgrade(UpgradeType.DAMAGE).perLevel * level)).roundToInt()

    fun cooldown(character: CharacterDefinition, level: Int): Float =
        character.baseCooldown * (1f - GameData.upgrade(UpgradeType.COOLDOWN).perLevel * level)

    /** Numeric value of one upgrade track at [level], for previews. */
    fun value(character: CharacterDefinition, type: UpgradeType, level: Int): Float = when (type) {
        UpgradeType.HEALTH -> health(character, level).toFloat()
        UpgradeType.DAMAGE -> damage(character, level).toFloat()
        UpgradeType.COOLDOWN -> cooldown(character, level)
    }

    fun stats(character: CharacterDefinition, state: GameState): CharacterStats = CharacterStats(
        health(character, state.upgradeLevel(character.id, UpgradeType.HEALTH)),
        damage(character, state.upgradeLevel(character.id, UpgradeType.DAMAGE)),
        cooldown(character, state.upgradeLevel(character.id, UpgradeType.COOLDOWN)),
    )

    /** Cooldown text with at most two decimals, e.g. "6s", "5.7s", "5.42s". Display only. */
    fun formatSeconds(seconds: Float): String {
        val hundredths = (seconds * 100).roundToInt()
        val whole = hundredths / 100
        val fraction = hundredths % 100
        return when {
            fraction == 0 -> "${whole}s"
            fraction % 10 == 0 -> "$whole.${fraction / 10}s"
            else -> "$whole.${if (fraction < 10) "0" else ""}${fraction}s"
        }
    }
}
