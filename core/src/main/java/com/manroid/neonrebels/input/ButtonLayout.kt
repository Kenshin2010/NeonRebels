package com.manroid.neonrebels.input

/**
 * HUD coordinates, independent from physical screen pixels; shared by drawing and input.
 * Buttons are circles. Every gameplay button sits inside the control deck below [DECK_TOP];
 * the world camera keeps the street level at [DECK_TOP] so fingers and buttons never cover the action.
 */
object ButtonLayout {
    const val LEFT = 0
    const val RIGHT = 1
    const val JUMP = 2
    const val ATTACK = 3
    const val DODGE = 4
    const val SKILL = 5
    const val SWAP = 6
    const val PAUSE = 7
    /** Shown only at the active radio station; never shares a spot with JUMP or ATTACK. */
    const val INTERACT = 8
    /** Shown only while an action tutorial step is on screen. */
    const val SKIP_TUTORIAL = 9
    /** Buttons drawn over the play area (top of the screen), exempt from the deck rule. */
    val OVERLAY = intArrayOf(PAUSE, SKIP_TUTORIAL)
    const val WIDTH = 1280f
    const val HEIGHT = 720f
    /** HUD height of the control deck; the ground surface is drawn exactly on this line. */
    const val DECK_TOP = 240f
    /** Extra touch radius so a thumb slightly off the ring still registers. */
    const val TOUCH_SLOP = 10f

    // Left thumb: movement pad. Right thumb, as in the battle mock-up: a big ATTACK in the corner,
    // JUMP and SWAP to its left, DODGE and SKILL on a second row above them.
    val CX = floatArrayOf(98f, 266f, 1018f, 1186f, 960f, 1070f, 892f, 1226f, 600f, 900f)
    val CY = floatArrayOf(100f, 100f, 74f, 96f, 192f, 194f, 70f, 672f, 96f, 566f)
    val R = floatArrayOf(72f, 72f, 56f, 80f, 44f, 46f, 46f, 32f, 64f, 26f)

    /** Text bundle keys, indexed like [CX]. */
    val LABEL_KEYS = arrayOf(
        "button.left", "button.right", "button.jump", "button.attack",
        "button.dodge", "button.skill", "button.swap", "button.pause", "button.interact", "button.skipTutorial"
    )

    /** Button under (x, y), or -1. Buttons whose [enabled] entry is false are ignored. */
    fun hit(x: Float, y: Float, enabled: BooleanArray? = null): Int {
        for (i in CX.indices) {
            if (enabled != null && !enabled[i]) continue
            val dx = x - CX[i]
            val dy = y - CY[i]
            val r = R[i] + TOUCH_SLOP
            if (dx * dx + dy * dy <= r * r) return i
        }
        return -1
    }
}
