package com.manroid.neonrebels.model

/** Level 1-1 geometry and landmark positions (world units). */
class Level {
    val platforms = arrayOf(
        Body(0f, 0f, 48f, 2f), Body(54f, 0f, 16f, 2f),
        Body(77f, 0f, 83f, 2f),
        Body(12f, 3.4f, 4f, .6f), Body(20f, 4.7f, 4f, .6f),
        Body(44f, 3.6f, 4f, .6f), Body(50f, 4.3f, 3f, .6f),
        Body(60f, 3.7f, 4f, .6f), Body(67f, 4.8f, 4f, .6f),
        Body(73f, 4.1f, 4f, .6f), Body(83f, 3.5f, 4f, .6f),
        Body(93f, 4.4f, 4f, .6f)
    )
    val gate = Body(GATE_X, STREET, 1f, 16f)
    val arenaDoor = Body(DOOR_X, STREET, .7f, 16f)
    var bossAwake = false
    var bossDefeated = false

    fun solid(index: Int): Boolean {
        if (index < platforms.size) return true
        return if (index == platforms.size) !bossDefeated else bossAwake && !bossDefeated
    }

    fun obstacle(index: Int): Body {
        if (index < platforms.size) return platforms[index]
        return if (index == platforms.size) gate else arenaDoor
    }

    fun obstacleCount(): Int = platforms.size + 2

    /** True if any solid obstacle overlaps the box. */
    fun blocked(x: Float, y: Float, w: Float, h: Float): Boolean {
        for (i in 0 until obstacleCount()) {
            if (!solid(i)) continue
            val o = obstacle(i)
            if (x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y) return true
        }
        return false
    }

    /** True if there is a floor directly under the point (x, y). */
    fun groundAt(x: Float, y: Float): Boolean = blocked(x - .02f, y - .1f, .04f, .08f)

    companion object {
        const val STREET = 2f
        const val HEAL_LEFT = 107f
        const val HEAL_RIGHT = 112f
        const val HEAL_SAFE_X = 109f
        const val DOOR_X = 113f
        /** Entering past this x wakes the boss (the player is then clear of the door collider). */
        const val ARENA_TRIGGER = 115f
        const val ARENA_SAFE_X = 116f
        const val ARENA_LEFT = DOOR_X + .7f
        const val GATE_X = 143f
        const val ARENA_RIGHT = GATE_X
        const val STATION_X = 153f
        const val STATION_W = 5f
        /** Safe points are only recorded before the arena. */
        const val SAFE_LIMIT = 112f
    }
}
