package com.manroid.neonrebels.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Armored Gatekeeper. SLEEP → (arena entered) INTRO → IDLE → TELEGRAPH → ATTACK → RECOVERY → IDLE,
 * plus PHASE_TRANSITION (once, at <= 40% health, after the current attack finishes) and DEAD.
 *
 * SLAM: the danger zone is locked when the telegraph starts and is exactly the hit area (finite
 * height, so a jump clears it). CHARGE: faces the player during the telegraph, locks direction at
 * its end, then runs straight until time runs out or a wall stops it; one hit per charge. The
 * warning zone during a charge telegraph is the full reachable path. Armor: no knockback or stun.
 */
class Boss(id: Int) : Enemy(id, 132f, Level.STREET, 2.5f, 3.4f, GameConfig.BOSS_HP) {
    enum class Phase { SLEEP, INTRO, IDLE, TELEGRAPH, ATTACK, RECOVERY, PHASE_TRANSITION, DEAD }
    enum class Attack { NONE, SLAM, CHARGE }

    var phase = Phase.SLEEP
        private set
    var attack = Attack.NONE
        private set
    /** Phase 2 (shorter recoveries); set once. */
    var enraged = false
        private set
    var transitionPending = false
        private set
    var lastAttack = Attack.NONE
        private set
    var repeatCount = 0
        private set
    private var zoneX = 0f
    private var zoneW = 0f
    private var chargeDir = -1
    private var struck = false

    override val dropsParts: Int get() = GameConfig.DROP_BOSS

    fun enraged(): Boolean = enraged
    fun telegraphing(): Boolean = phase == Phase.TELEGRAPH
    fun striking(): Boolean = phase == Phase.ATTACK
    /** True while the boss is a threat or about to become one (drives the HUD bar). */
    fun engaged(): Boolean = phase != Phase.SLEEP && phase != Phase.DEAD

    /** Danger zone (x, width) shown during TELEGRAPH and used as the slam hitbox. */
    fun dangerX(): Float = zoneX
    fun dangerWidth(): Float = zoneW

    fun wake() {
        if (phase != Phase.SLEEP || hp <= 0) return
        phase = Phase.INTRO
        timer = GameConfig.BOSS_INTRO
    }

    override fun hit(damage: Int, direction: Int, knockback: Float): Boolean {
        if (phase == Phase.SLEEP || hp <= 0) return false
        hp = max(0, hp - damage)
        // Armor: flashes but never staggers, so telegraphs are never cut short.
        hurtTime = .12f
        if (hp == 0) {
            die()
        } else if (!enraged && !transitionPending && hp <= maxHp * GameConfig.BOSS_RAGE_FRACTION) {
            transitionPending = true
        }
        return true
    }

    private fun die() {
        phase = Phase.DEAD
        attack = Attack.NONE
        state = State.DEAD
        zoneW = 0f
        vx = 0f
    }

    override fun act(world: World, dt: Float) {
        hurtTime = max(0f, hurtTime - dt)
        val p = world.player
        when (phase) {
            Phase.SLEEP, Phase.DEAD -> return
            Phase.INTRO -> if (timer == 0f) enter(Phase.IDLE, GameConfig.BOSS_IDLE)
            Phase.IDLE -> {
                face(p)
                if (timer == 0f) {
                    if (transitionPending) enter(Phase.PHASE_TRANSITION, GameConfig.BOSS_TRANSITION)
                    else startAttack(world)
                }
            }
            Phase.TELEGRAPH -> {
                if (attack == Attack.CHARGE) {
                    face(p)
                    chargeZone(world)
                }
                if (timer == 0f) {
                    struck = false
                    if (attack == Attack.CHARGE) chargeDir = facing
                    enter(Phase.ATTACK, if (attack == Attack.SLAM) GameConfig.SLAM_ACTIVE else GameConfig.CHARGE_TIME)
                }
            }
            Phase.ATTACK -> {
                if (attack == Attack.SLAM) {
                    if (!struck && p.overlaps(zoneX, Level.STREET, zoneW, GameConfig.SLAM_HEIGHT)) {
                        struck = true
                        world.queuePlayerHit(GameConfig.SLAM_DAMAGE, if (p.centerX() > centerX()) 1 else -1)
                    }
                } else {
                    val before = x
                    vx = chargeDir * GameConfig.CHARGE_SPEED
                    vy = 0f
                    CollisionSystem.move(this, world.level, dt)
                    x = max(Level.ARENA_LEFT, min(Level.ARENA_RIGHT - w, x))
                    if (!struck && overlaps(p)) {
                        struck = true
                        world.queuePlayerHit(GameConfig.CHARGE_DAMAGE, chargeDir)
                    }
                    // A wall ends the charge early.
                    if (abs(x - before) < GameConfig.CHARGE_SPEED * dt * .5f) timer = 0f
                }
                if (timer == 0f) {
                    vx = 0f
                    zoneW = 0f
                    val base = if (attack == Attack.SLAM) GameConfig.SLAM_RECOVERY else GameConfig.CHARGE_RECOVERY
                    enter(Phase.RECOVERY, if (enraged) base * GameConfig.BOSS_RAGE_RECOVERY else base)
                }
            }
            Phase.RECOVERY -> if (timer == 0f) {
                attack = Attack.NONE
                if (transitionPending) enter(Phase.PHASE_TRANSITION, GameConfig.BOSS_TRANSITION)
                else enter(Phase.IDLE, GameConfig.BOSS_IDLE)
            }
            Phase.PHASE_TRANSITION -> if (timer == 0f) {
                enraged = true
                transitionPending = false
                enter(Phase.IDLE, GameConfig.BOSS_IDLE)
            }
        }
        state = when {
            hp <= 0 -> State.DEAD
            phase == Phase.TELEGRAPH || phase == Phase.ATTACK -> State.ATTACK
            hurtTime > 0 -> State.HURT
            else -> State.IDLE
        }
    }

    private fun face(p: Player) {
        facing = if (p.centerX() >= centerX()) 1 else -1
    }

    private fun enter(next: Phase, time: Float) {
        phase = next
        timer = time
    }

    /** Free run ahead in [dir] until the arena wall, capped by how far one charge travels. */
    private fun chargeSpace(dir: Int): Float {
        val reach = GameConfig.CHARGE_SPEED * GameConfig.CHARGE_TIME
        val free = if (dir > 0) Level.ARENA_RIGHT - (x + w) else x - Level.ARENA_LEFT
        return max(0f, min(reach, free))
    }

    private fun chargeZone(world: World) {
        val run = chargeSpace(facing)
        zoneX = if (facing > 0) x else x - run
        zoneW = w + run
    }

    /**
     * Near → slam; far with room → charge. Never the same attack three times running, unless the
     * alternative has no room (a charge needs free space; a slam always fits).
     */
    private fun startAttack(world: World) {
        val p = world.player
        face(p)
        val near = abs(p.centerX() - centerX()) < GameConfig.BOSS_NEAR
        val chargeFits = chargeSpace(facing) >= GameConfig.CHARGE_MIN_SPACE
        var choice = if (near || !chargeFits) Attack.SLAM else Attack.CHARGE
        if (choice == lastAttack && repeatCount >= GameConfig.BOSS_MAX_REPEAT) {
            val other = if (choice == Attack.SLAM) Attack.CHARGE else Attack.SLAM
            if (other == Attack.SLAM || chargeFits) choice = other
        }
        repeatCount = if (choice == lastAttack) repeatCount + 1 else 1
        lastAttack = choice
        attack = choice
        if (choice == Attack.SLAM) {
            zoneX = max(Level.ARENA_LEFT, x - GameConfig.SLAM_REACH)
            zoneW = min(Level.ARENA_RIGHT, x + w + GameConfig.SLAM_REACH) - zoneX
            enter(Phase.TELEGRAPH, GameConfig.SLAM_TELEGRAPH)
        } else {
            chargeZone(world)
            enter(Phase.TELEGRAPH, GameConfig.CHARGE_TELEGRAPH)
        }
    }
}
