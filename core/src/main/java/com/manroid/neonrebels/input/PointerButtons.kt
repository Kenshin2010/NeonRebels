package com.manroid.neonrebels.input

/** No GDX dependency: supports multiple fingers and releases when dragged outside. */
class PointerButtons {
    private val owners = IntArray(20)
    private val counts = IntArray(ButtonLayout.CX.size)

    init {
        clear()
    }

    fun move(pointer: Int, button: Int): Boolean {
        if (pointer < 0 || pointer >= owners.size) return false
        val old = owners[pointer]
        if (old == button) return false
        if (old >= 0) counts[old]--
        owners[pointer] = button
        if (button >= 0) counts[button]++
        return button >= 0 && counts[button] == 1
    }

    fun held(button: Int): Boolean = counts[button] > 0

    fun clear() {
        owners.fill(-1)
        counts.fill(0)
    }
}
