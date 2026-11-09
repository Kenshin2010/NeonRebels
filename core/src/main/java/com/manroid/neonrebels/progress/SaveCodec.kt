package com.manroid.neonrebels.progress

import java.util.zip.CRC32

/**
 * Text snapshot of [GameState]: one `key=value` per line, a `saveVersion`, and a trailing
 * `crc` over everything before it so truncated or edited data is detected instead of loaded.
 *
 * Decoding is tolerant where it is safe: unknown keys are ignored (newer optional fields),
 * unknown ids are dropped, numbers are clamped. It refuses data with a bad checksum, a missing
 * version or a version newer than this build understands.
 */
object SaveCodec {
    const val VERSION = 1

    sealed class Decoded {
        class Ok(val state: GameState) : Decoded()
        class Invalid(val reason: String) : Decoded()
    }

    fun encode(s: GameState): String {
        val lines = ArrayList<String>()
        lines += "saveVersion=$VERSION"
        lines += "campaignStarted=${s.campaignStarted}"
        lines += "components=${s.components}"
        lines += "unlockedCharacters=${s.unlockedCharacters.joinToString(",")}"
        lines += "team=${s.team.joinToString(",")}"
        lines += "selectedLevel=${s.selectedLevelId ?: ""}"
        lines += "lastPlayedLevel=${s.lastPlayedLevelId ?: ""}"
        lines += "completedLevels=${s.completedLevels.joinToString(",")}"
        for ((level, seconds) in s.bestTimes) lines += "best.$level=$seconds"
        for ((id, levels) in s.upgrades) {
            for (type in UpgradeType.values()) lines += "upgrade.$id.${type.name}=${levels[type.ordinal]}"
        }
        for ((step, value) in s.tutorial) lines += "tutorial.$step=$value"
        lines += "nextRunId=${s.nextRunId}"
        lines += "lastSettledRunId=${s.lastSettledRunId}"
        lines += "lastSettledWon=${s.lastSettledWon}"
        lines += "lastSettledReward=${s.lastSettledReward}"
        val body = lines.joinToString("\n")
        return "$body\ncrc=${crc(body)}"
    }

    fun decode(text: String?): Decoded {
        if (text.isNullOrBlank()) return Decoded.Invalid("empty")
        val crcAt = text.lastIndexOf("\ncrc=")
        if (crcAt < 0) return Decoded.Invalid("missing checksum")
        val body = text.substring(0, crcAt)
        if (text.substring(crcAt + 5).trim() != crc(body)) return Decoded.Invalid("checksum mismatch")
        val values = HashMap<String, String>()
        for (line in body.split('\n')) {
            val eq = line.indexOf('=')
            if (eq > 0) values[line.substring(0, eq)] = line.substring(eq + 1)
        }
        val version = values["saveVersion"]?.toIntOrNull() ?: return Decoded.Invalid("missing version")
        if (version > VERSION) return Decoded.Invalid("save version $version is newer than $VERSION")
        migrate(values, version)
        return Decoded.Ok(fromValues(values))
    }

    /** Upgrades older snapshot layouts in place, one version at a time. Version 1 is current. */
    private fun migrate(values: MutableMap<String, String>, from: Int) {
        var version = from
        while (version < VERSION) {
            // when (version) { 1 -> ...rename or fill keys for version 2... }
            version++
        }
        values["saveVersion"] = VERSION.toString()
    }

    private fun fromValues(v: Map<String, String>): GameState {
        val s = GameState()
        s.campaignStarted = v["campaignStarted"] == "true"
        s.components = (v["components"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        for (id in list(v["unlockedCharacters"])) if (GameData.character(id) != null) s.unlockedCharacters += id
        for (id in list(v["completedLevels"])) if (GameData.campaign.level(id) != null) s.completedLevels += id
        s.selectedLevelId = v["selectedLevel"]?.takeIf { GameData.campaign.level(it) != null }
        s.lastPlayedLevelId = v["lastPlayedLevel"]?.takeIf { GameData.campaign.level(it) != null }
        for ((key, value) in v) {
            if (key.startsWith("best.")) {
                val level = key.removePrefix("best.")
                val seconds = value.toFloatOrNull()
                if (GameData.campaign.level(level) != null && seconds != null && seconds > 0) s.bestTimes[level] = seconds
            } else if (key.startsWith("tutorial.")) {
                val step = key.removePrefix("tutorial.")
                if (step in TUTORIAL_STEPS && (value == "done" || value == "skipped")) s.tutorial[step] = value
            } else if (key.startsWith("upgrade.")) {
                val parts = key.split('.')
                val character = GameData.character(parts.getOrNull(1))
                val type = UpgradeType.values().firstOrNull { it.name == parts.getOrNull(2) }
                val level = value.toIntOrNull()
                if (character != null && type != null && level != null) {
                    s.setUpgradeLevel(character.id, type, level.coerceIn(0, GameData.upgrade(type).maxLevel))
                }
            }
        }
        s.nextRunId = (v["nextRunId"]?.toLongOrNull() ?: 1L).coerceAtLeast(1L)
        s.lastSettledRunId = (v["lastSettledRunId"]?.toLongOrNull() ?: 0L).coerceIn(0L, s.nextRunId - 1)
        s.lastSettledWon = v["lastSettledWon"] == "true"
        s.lastSettledReward = (v["lastSettledReward"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        if (s.campaignStarted) {
            for (c in GameData.characters) if (c.unlockedAtStart) s.unlockedCharacters += c.id
            if (s.selectedLevelId == null) s.selectedLevelId = GameData.DEFAULT_LEVEL
        }
        val team = list(v["team"]).distinct().filter { id ->
            val c = GameData.character(id)
            c != null && c.implemented && id in s.unlockedCharacters
        }
        if (team.size == 2) s.team += team
        else if (s.campaignStarted) s.team += listOf(GameData.AN.id, GameData.VY.id)
        return s
    }

    /** Known tutorial step ids (see model.TutorialController.Step); others are dropped on load. */
    private val TUTORIAL_STEPS = setOf("move", "jump", "attack", "dodge", "skill", "swap")

    /** Keys written by builds before the versioned snapshot existed. */
    val LEGACY_KEYS = listOf("level1Complete", "bestSeconds", "parts") +
        (0..1).flatMap { slot -> listOf("HP", "DAMAGE", "COOLDOWN").map { "level$slot$it" } } +
        listOf("team0", "team1")

    /**
     * Converts the pre-snapshot keys (level complete flag, best time, parts and 1-based
     * upgrade levels) into a version-1 state. Returns null when none of them exist.
     */
    fun fromLegacy(values: Map<String, String>): GameState? {
        if (LEGACY_KEYS.none { values.containsKey(it) }) return null
        val s = GameState.newCampaign()
        s.components = (values["parts"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        if (values["level1Complete"] == "true") s.completedLevels += GameData.DEFAULT_LEVEL
        values["bestSeconds"]?.toFloatOrNull()?.takeIf { it > 0 }?.let { s.bestTimes[GameData.DEFAULT_LEVEL] = it }
        val legacyStats = listOf("HP" to UpgradeType.HEALTH, "DAMAGE" to UpgradeType.DAMAGE, "COOLDOWN" to UpgradeType.COOLDOWN)
        for ((slot, character) in listOf(GameData.AN, GameData.VY).withIndex()) {
            for ((name, type) in legacyStats) {
                val old = values["level$slot$name"]?.toIntOrNull() ?: continue
                s.setUpgradeLevel(character.id, type, (old - 1).coerceIn(0, GameData.upgrade(type).maxLevel))
            }
        }
        return s
    }

    private fun list(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun crc(body: String): String {
        val crc = CRC32()
        crc.update(body.toByteArray(Charsets.UTF_8))
        return java.lang.Long.toHexString(crc.value)
    }
}
