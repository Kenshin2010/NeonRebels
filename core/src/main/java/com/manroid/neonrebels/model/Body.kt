package com.manroid.neonrebels.model

open class Body(var x: Float, var y: Float, var w: Float, var h: Float) {
    var vx = 0f
    var vy = 0f
    var grounded = false

    fun overlaps(bx: Float, by: Float, bw: Float, bh: Float): Boolean =
        x < bx + bw && x + w > bx && y < by + bh && y + h > by

    fun overlaps(b: Body): Boolean = overlaps(b.x, b.y, b.w, b.h)
    fun centerX(): Float = x + w / 2
    fun centerY(): Float = y + h / 2
}
