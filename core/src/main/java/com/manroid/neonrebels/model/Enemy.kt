package com.manroid.neonrebels.model

import kotlin.math.max

/**
 * Base for every hostile. [state] is a coarse render state; subclasses keep their own AI mode.
 * Damage never lands here directly: the world queues hits and resolves them once per step.
 */
abstract class Enemy(val id: Int, x: Float, y: Float, w: Float, h: Float, val maxHp: Int) : Body(x, y, w, h) {
    enum class State { IDLE, MOVE, ATTACK, HURT, DEAD }

    var hp = maxHp
    var facing = -1
    var state = State.IDLE
    var timer = 0f
    var hurtTime = 0f
    var deathTime = 0f
    /** Set once the world has collected this enemy's component drop. */
    var looted = false

    open val dropsParts: Int get() = 0

    fun alive(): Boolean = hp > 0

    /** Applies resolved damage. Returns true if it landed. */
    open fun hit(damage: Int, direction: Int, knockback: Float): Boolean {
        if (hp <= 0) return false
        hp = max(0, hp - damage)
        hurtTime = GameConfig.ENEMY_HIT_STUN
        vx = direction * knockback
        state = if (hp == 0) State.DEAD else State.HURT
        return true
    }

    fun update(world: World, dt: Float) {
        if (hp <= 0) {
            state = State.DEAD
            deathTime += dt
            return
        }
        timer = max(0f, timer - dt)
        act(world, dt)
    }

    protected abstract fun act(world: World, dt: Float)

    protected fun fall(world: World, dt: Float) {
        vy = max(-GameConfig.MAX_FALL_SPEED, vy + GameConfig.GRAVITY * dt)
        CollisionSystem.move(this, world.level, dt)
    }
}

/**
 * Tutorial target in the safe opening area: takes hits, never dies, never attacks. It only exists
 * while the tutorial still needs it; [remove] takes it out of the world for good (not hittable,
 * not drawn, no longer blocks shots).
 */
class TrainingDummy(id: Int, x: Float) : Enemy(id, x, Level.STREET, .9f, 1.6f, 1) {
    var hitsTaken = 0
        private set

    fun remove() {
        hp = 0
        looted = true
        state = State.DEAD
    }

    override fun hit(damage: Int, direction: Int, knockback: Float): Boolean {
        if (hp <= 0) return false
        hitsTaken++
        hurtTime = .15f
        state = State.HURT
        return true
    }

    override fun act(world: World, dt: Float) {
        hurtTime = max(0f, hurtTime - dt)
        state = if (hurtTime > 0) State.HURT else State.IDLE
    }
}
