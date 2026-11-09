package com.manroid.neonrebels.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * PATROL inside [minX]..[maxX] → CHASE when the player is seen (in range, same height band, no
 * wall between) → WINDUP (stops; strike direction locked) → ATTACK (short active hitbox in front;
 * lands only if the player is in it now) → RECOVERY. Never walks off a ledge. Hits cause a short
 * HURT stagger that cancels a pending strike.
 */
class PatrolRobot(id: Int, x: Float, private val minX: Float, private val maxX: Float) :
    Enemy(id, x, Level.STREET, 1f, 1.5f, GameConfig.ROBOT_HP) {
    enum class Mode { PATROL, CHASE, WINDUP, ATTACK, RECOVERY, HURT, DEAD }

    var mode = Mode.PATROL
        private set
    var strikeDir = -1
        private set
    private var struck = false

    override val dropsParts: Int get() = GameConfig.DROP_ROBOT

    /** Time left before the strike becomes active (0 when not winding up). */
    val windup: Float get() = if (mode == Mode.WINDUP) timer else 0f

    /** The strike hitbox (front of the robot), meaningful in WINDUP/ATTACK. */
    fun strikeX(): Float = if (strikeDir > 0) x + w else x - GameConfig.ROBOT_ATTACK_RANGE

    override fun hit(damage: Int, direction: Int, knockback: Float): Boolean {
        if (!super.hit(damage, direction, knockback)) return false
        mode = if (hp == 0) Mode.DEAD else Mode.HURT
        return true
    }

    private fun sees(world: World): Boolean {
        val p = world.player
        if (p.dead()) return false
        val dx = p.centerX() - centerX()
        if (abs(dx) > GameConfig.ROBOT_SIGHT_RANGE || abs(p.y - y) > 2f) return false
        val from = min(centerX(), p.centerX())
        return !world.level.blocked(from, y + .6f, abs(dx), .3f)
    }

    private fun inReach(world: World): Boolean {
        val p = world.player
        val gap = if (p.centerX() >= centerX()) p.x - (x + w) else x - (p.x + p.w)
        return gap <= GameConfig.ROBOT_ATTACK_RANGE && abs(p.y - y) < 1.2f
    }

    /** Next step forward is floor, inside the patrol zone and not a wall. */
    private fun canStep(world: World, dir: Int): Boolean {
        val probe = if (dir > 0) x + w + .1f else x - .1f
        if (probe < minX || probe > maxX + w) return false
        if (world.level.blocked(if (dir > 0) x + w else x - .1f, y + .1f, .1f, h - .2f)) return false
        return world.level.groundAt(probe, y)
    }

    override fun act(world: World, dt: Float) {
        if (hurtTime > 0) {
            hurtTime = max(0f, hurtTime - dt)
            state = State.HURT
            fall(world, dt)
            vx *= .85f
            x = max(minX, min(maxX, x))
            if (hurtTime == 0f) setMode(Mode.CHASE, 0f)
            return
        }
        val p = world.player
        when (mode) {
            Mode.PATROL -> {
                if (sees(world)) {
                    setMode(Mode.CHASE, 0f)
                    vx = 0f
                } else {
                    if (!canStep(world, facing)) facing = -facing
                    vx = if (canStep(world, facing)) facing * GameConfig.ROBOT_SPEED else 0f
                }
            }
            Mode.HURT, Mode.DEAD -> setMode(Mode.CHASE, 0f)
            Mode.CHASE -> {
                if (!sees(world)) {
                    setMode(Mode.PATROL, 0f)
                    vx = 0f
                } else {
                    facing = if (p.centerX() >= centerX()) 1 else -1
                    if (inReach(world)) {
                        vx = 0f
                        strikeDir = facing
                        struck = false
                        setMode(Mode.WINDUP, GameConfig.ROBOT_WINDUP)
                    } else {
                        vx = if (canStep(world, facing)) facing * GameConfig.ROBOT_SPEED else 0f
                    }
                }
            }
            Mode.WINDUP -> {
                vx = 0f
                if (timer == 0f) setMode(Mode.ATTACK, GameConfig.ROBOT_ACTIVE)
            }
            Mode.ATTACK -> {
                vx = 0f
                if (!struck && p.overlaps(strikeX(), y, GameConfig.ROBOT_ATTACK_RANGE, h)) {
                    struck = true
                    world.queuePlayerHit(GameConfig.ROBOT_DAMAGE, strikeDir)
                }
                if (timer == 0f) setMode(Mode.RECOVERY, GameConfig.ROBOT_RECOVERY)
            }
            Mode.RECOVERY -> {
                vx = 0f
                if (timer == 0f) setMode(if (sees(world)) Mode.CHASE else Mode.PATROL, 0f)
            }
        }
        state = when (mode) {
            Mode.WINDUP, Mode.ATTACK -> State.ATTACK
            Mode.PATROL, Mode.CHASE -> if (vx != 0f) State.MOVE else State.IDLE
            else -> State.IDLE
        }
        fall(world, dt)
        x = max(minX, min(maxX, x))
    }

    private fun setMode(next: Mode, time: Float) {
        mode = next
        timer = time
    }
}
