package com.manroid.neonrebels.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PATROL (drifts around its post at a fixed altitude) → AIM (locks the player's position at the
 * start, shown as a warning) → FIRE (one shot at the locked point) → REPOSITION (moves to the
 * other side of its post while the shot cools down). Altitude is chosen so Vy's level shots reach
 * it with a jump; it is never knocked back.
 */
class Drone(id: Int, x: Float, y: Float) : Enemy(id, x, y, 1.2f, .8f, GameConfig.DRONE_HP) {
    enum class Mode { PATROL, AIM, FIRE, REPOSITION, DEAD }

    private val originX = x
    private val originY = y
    private var bob = 0f
    private var targetX = x + GameConfig.DRONE_PATROL
    var mode = Mode.PATROL
        private set
    var aimX = 0f
        private set
    var aimY = 0f
        private set

    override val dropsParts: Int get() = GameConfig.DROP_DRONE

    init {
        timer = .7f
    }

    override fun hit(damage: Int, direction: Int, knockback: Float): Boolean {
        if (!super.hit(damage, direction, 0f)) return false
        hurtTime = .1f
        if (hp == 0) mode = Mode.DEAD
        return true
    }

    override fun act(world: World, dt: Float) {
        hurtTime = max(0f, hurtTime - dt)
        bob += dt
        y = originY + sin(bob * 2f) * .3f
        val p = world.player
        val dx = p.centerX() - centerX()
        val inRange = !p.dead() && abs(dx) < GameConfig.DRONE_RANGE && abs(p.centerY() - centerY()) < 8f
        when (mode) {
            Mode.PATROL -> {
                drift(dt)
                if (inRange && timer == 0f) {
                    aimX = p.centerX()
                    aimY = p.centerY()
                    mode = Mode.AIM
                    timer = GameConfig.DRONE_AIM
                }
            }
            Mode.AIM -> if (timer == 0f) mode = Mode.FIRE
            Mode.FIRE -> {
                val ax = aimX - centerX()
                val ay = aimY - centerY()
                val length = max(.01f, sqrt(ax * ax + ay * ay))
                world.fire(centerX() - .19f, centerY() - .11f, ax / length * GameConfig.DRONE_SHOT_SPEED,
                    ay / length * GameConfig.DRONE_SHOT_SPEED, friendly = false, piercing = false,
                    damage = GameConfig.DRONE_DAMAGE, life = GameConfig.DRONE_SHOT_LIFE)
                targetX = if (centerX() > originX) originX - GameConfig.DRONE_PATROL else originX + GameConfig.DRONE_PATROL
                mode = Mode.REPOSITION
                timer = GameConfig.DRONE_REST
            }
            Mode.REPOSITION -> {
                drift(dt)
                if (timer == 0f) mode = Mode.PATROL
            }
            Mode.DEAD -> Unit
        }
        facing = if (dx >= 0) 1 else -1
        state = when {
            hurtTime > 0 -> State.HURT
            mode == Mode.AIM -> State.ATTACK
            else -> State.MOVE
        }
    }

    private fun drift(dt: Float) {
        val dx = targetX - x
        if (abs(dx) < .05f) {
            targetX = if (x > originX) originX - GameConfig.DRONE_PATROL else originX + GameConfig.DRONE_PATROL
        } else {
            x += (if (dx > 0) 1 else -1) * minOf(abs(dx), GameConfig.DRONE_SPEED * dt)
        }
    }
}
