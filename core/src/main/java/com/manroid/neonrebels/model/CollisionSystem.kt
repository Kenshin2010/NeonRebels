package com.manroid.neonrebels.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Axis-separated AABB with bounded micro-steps, including during dashes. */
object CollisionSystem {
    private const val MAX_SUBSTEP = .15f

    fun move(b: Body, level: Level, dt: Float) {
        var dx = b.vx * dt
        var dy = b.vy * dt
        val steps = max(1, ceil(max(abs(dx), abs(dy)) / MAX_SUBSTEP).toInt())
        dx /= steps
        dy /= steps
        b.grounded = false
        for (step in 0 until steps) {
            b.x += dx
            for (i in 0 until level.obstacleCount()) {
                if (!level.solid(i)) continue
                val p = level.obstacle(i)
                if (b.overlaps(p)) {
                    if (dx > 0) b.x = p.x - b.w
                    if (dx < 0) b.x = p.x + p.w
                    b.vx = 0f
                    dx = 0f
                }
            }
            b.y += dy
            for (i in 0 until level.obstacleCount()) {
                if (!level.solid(i)) continue
                val p = level.obstacle(i)
                if (b.overlaps(p)) {
                    if (dy < 0) {
                        b.y = p.y + p.h
                        b.grounded = true
                    }
                    if (dy > 0) b.y = p.y - b.h
                    b.vy = 0f
                    dy = 0f
                }
            }
        }
        b.x = max(0f, min(GameConfig.LEVEL_WIDTH - b.w, b.x))
    }
}
