package com.manroid.neonrebels.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The squad: An and Vy share one collider and position; only the active one exists in the world.
 * Each keeps its own health and skill cooldown. Exclusive actions (melee strike, dash slash,
 * dodge) are a small state machine in [action]; hit-stun and hit invulnerability are separate
 * timers. Hit invulnerability is shared by the squad, so swapping neither adds nor clears it.
 */
class Player(val loadout: Loadout = Loadout()) : Body(3f, Level.STREET, .85f, 1.6f) {
    enum class Action { NONE, STRIKE, DASH, DODGE }
    enum class StrikePhase { STARTUP, ACTIVE, RECOVERY }

    val maxHp: IntArray = loadout.maxHp.copyOf()
    val hp: IntArray = maxHp.copyOf()
    val skillCooldown = FloatArray(2)
    var type = loadout.first
    var facing = 1
    var switchCooldown = 0f
    /** Shared hit invulnerability (after a hit or a respawn). */
    var invulnerable = 0f
    /** Hit-stun: no movement input or actions. */
    var hurtTime = 0f
    var coyote = 0f
    var jumpBuffer = 0f
    var safeX = 3f
    var safeY = Level.STREET

    var action = Action.NONE
        private set
    /** Seconds spent in the current action (or strike phase). */
    var actionTime = 0f
        private set

    /** Monotonic presentation events; never used to decide hits or cooldowns. */
    var actionSerial = 0L
        private set
    var shotSerial = 0L
        private set
    var lastShotPiercing = false
        private set

    fun recordShot(piercing: Boolean) {
        shotSerial++
        lastShotPiercing = piercing
        shotFlash = 0f
    }

    // An: melee combo.
    /** Current strike 1..3, or 0. */
    var comboStep = 0
        private set
    var strikePhase = StrikePhase.STARTUP
        private set
    /** One buffered "next strike" request; extra presses are dropped. */
    var comboQueued = false
        private set
    var airStrike = false
        private set
    /** Direction locked when the strike or dash started. */
    var attackDir = 1
        private set
    /** Targets already hit by the current strike or dash (bit per Enemy.id). */
    var swordHits = 0L

    // Dodge.
    var dodgeCooldown = 0f
    private var dodgeDir = 1

    // Vy: rifle.
    var shotCooldown = 0f
    /** After a swap the fire button must be released before Vy shoots. */
    var fireLatch = false
    /** Decorative: time since the last shot, for the muzzle flash. */
    var shotFlash = 0f

    init {
        // Spawns standing on the street.
        grounded = true
    }

    fun slot(): Int = type.ordinal
    fun dead(): Boolean = hp[0] <= 0 && hp[1] <= 0
    fun activeAlive(): Boolean = hp[slot()] > 0
    fun reserveAlive(): Boolean = hp[1 - slot()] > 0
    fun skillCooldownMax(slot: Int): Float = loadout.skillCooldown[slot]

    fun striking(): Boolean = action == Action.STRIKE
    fun dashing(): Boolean = action == Action.DASH
    fun dodging(): Boolean = action == Action.DODGE
    fun swordActive(): Boolean = striking() && strikePhase == StrikePhase.ACTIVE
    fun dodgeInvulnerable(): Boolean = dodging() && actionTime < GameConfig.DODGE_INVULNERABLE
    /** Free to start a new action (not stunned, not mid-action). */
    fun free(): Boolean = action == Action.NONE && hurtTime == 0f && activeAlive()

    /** Voluntary swap is refused mid-action, in hit-stun, on cooldown or with a downed reserve. */
    fun canSwap(): Boolean = !dead() && reserveAlive() && switchCooldown == 0f && free()

    /**
     * Switches to the reserve, keeping position, velocity and facing. A forced swap (the active
     * rebel fell) ignores the cooldown and action lock. Clears any action and held fire.
     */
    fun swap(forced: Boolean): Boolean {
        if (!reserveAlive() || (!forced && !canSwap())) return false
        type = if (slot() == 0) CharacterType.VY else CharacterType.AN
        switchCooldown = GameConfig.SWITCH_COOLDOWN
        endAction()
        hurtTime = 0f
        fireLatch = true
        return true
    }

    /**
     * Applies a hit to the active rebel. Returns false if it was ignored (down, invulnerable or
     * inside the dodge window). [ignoreInvulnerability] is for falls. Invulnerability is set to a
     * fixed value, never stacked.
     */
    fun hurt(damage: Int, direction: Float, ignoreInvulnerability: Boolean = false): Boolean {
        if (dead() || !activeAlive()) return false
        if (!ignoreInvulnerability && (invulnerable > 0 || dodgeInvulnerable())) return false
        hp[slot()] = max(0, hp[slot()] - max(0, damage))
        invulnerable = max(invulnerable, GameConfig.HIT_INVULNERABLE)
        endAction()
        if (!ignoreInvulnerability) {
            hurtTime = GameConfig.HIT_STUN
            vx = direction * GameConfig.PLAYER_KNOCKBACK
            vy = GameConfig.PLAYER_KNOCKBACK_UP
        }
        if (hp[slot()] == 0) swap(true)
        return true
    }

    fun fallDamage(): Int = ceil(maxHp[slot()] * GameConfig.FALL_DAMAGE_FRACTION).toInt()

    // ------------------------------------------------------------- melee

    /** Starts strike 1 (or a single air strike). */
    fun startStrike(step: Int) {
        actionSerial++
        action = Action.STRIKE
        comboStep = step
        strikePhase = StrikePhase.STARTUP
        actionTime = 0f
        comboQueued = false
        airStrike = !grounded
        attackDir = facing
        swordHits = 0L
    }

    /** One buffered request while the current strike is active or recovering, on the ground only. */
    fun queueStrike() {
        if (striking() && !airStrike && comboStep < 3 && strikePhase != StrikePhase.STARTUP) comboQueued = true
    }

    fun swordX(): Float = if (attackDir > 0) x + w else x - swordWidth()
    fun swordWidth(): Float = if (dashing()) w + GameConfig.DASH_RANGE else GameConfig.SWORD_RANGE

    fun swordDamage(): Int {
        val base = loadout.damage[0]
        return if (dashing()) (base * GameConfig.DASH_MULTIPLIER).roundToInt()
        else (base * GameConfig.COMBO_DAMAGE[comboStep - 1]).roundToInt()
    }

    fun swordKnockback(): Float = if (dashing()) GameConfig.COMBO_KNOCKBACK[2] else GameConfig.COMBO_KNOCKBACK[comboStep - 1]

    fun startDash() {
        actionSerial++
        action = Action.DASH
        actionTime = 0f
        attackDir = facing
        swordHits = 0L
        comboStep = 0
        comboQueued = false
    }

    // ------------------------------------------------------------- dodge

    fun startDodge(direction: Int) {
        actionSerial++
        action = Action.DODGE
        actionTime = 0f
        dodgeDir = direction
        facing = direction
        dodgeCooldown = GameConfig.DODGE_COOLDOWN
        comboStep = 0
        comboQueued = false
    }

    fun dodgeDirection(): Int = dodgeDir

    /** Dodge may cancel strike recovery only. */
    fun dodgeCancelsAction(): Boolean = action == Action.NONE || (striking() && strikePhase == StrikePhase.RECOVERY)

    // ------------------------------------------------------------- vy

    fun shotDamage(piercing: Boolean): Int {
        val base = loadout.damage[1]
        return if (piercing) (base * GameConfig.PIERCE_MULTIPLIER).roundToInt() else base
    }

    fun muzzleX(): Float = if (facing > 0) x + w else x - .4f
    fun muzzleY(): Float = y + .85f

    // ------------------------------------------------------------- timing

    /**
     * Advances the current action by [dt]. Returns the next strike step to start if a buffered
     * combo request fires at the end of a recovery, or 0.
     */
    fun advanceAction(dt: Float): Int {
        if (action == Action.NONE) return 0
        actionTime += dt
        when (action) {
            Action.STRIKE -> {
                val i = comboStep - 1
                val limit = when (strikePhase) {
                    StrikePhase.STARTUP -> GameConfig.COMBO_STARTUP[i]
                    StrikePhase.ACTIVE -> GameConfig.COMBO_ACTIVE[i]
                    StrikePhase.RECOVERY -> GameConfig.COMBO_RECOVERY[i]
                }
                if (actionTime >= limit) {
                    actionTime -= limit
                    when (strikePhase) {
                        StrikePhase.STARTUP -> strikePhase = StrikePhase.ACTIVE
                        StrikePhase.ACTIVE -> strikePhase = StrikePhase.RECOVERY
                        StrikePhase.RECOVERY -> {
                            val next = if (comboQueued && grounded && comboStep < 3) comboStep + 1 else 0
                            endAction()
                            return next
                        }
                    }
                }
            }
            Action.DASH -> if (actionTime >= GameConfig.DASH_TIME) endAction()
            Action.DODGE -> if (actionTime >= GameConfig.DODGE_TIME) endAction()
            Action.NONE -> Unit
        }
        return 0
    }

    /** Ends any action and clears the combo. */
    fun endAction() {
        action = Action.NONE
        actionTime = 0f
        comboStep = 0
        comboQueued = false
        airStrike = false
    }
}
