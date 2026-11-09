package com.manroid.neonrebels.model

/**
 * Platform-neutral intent. `left`, `right` and `attackHeld` are held states; the rest are edges
 * that persist until exactly one fixed step consumes them, so one press never repeats.
 */
class Controls {
    var left = false
    var right = false
    /** Attack/fire button is down (Vy fires while held). */
    var attackHeld = false
    var jump = false
    /** Attack pressed this frame (An queues one combo request per press). */
    var attack = false
    var dodge = false
    var skill = false
    var swap = false
    var interact = false

    fun clearEdges() {
        jump = false
        attack = false
        dodge = false
        skill = false
        swap = false
        interact = false
    }

    fun clear() {
        left = false
        right = false
        attackHeld = false
        clearEdges()
    }
}
