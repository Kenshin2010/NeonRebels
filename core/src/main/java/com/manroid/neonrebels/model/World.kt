package com.manroid.neonrebels.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Owns one run. A replay constructs a fresh World (fresh enemies, projectile pool, triggers).
 *
 * [update] is one fixed step, always in this order:
 *  1. consume input   2. character timers, actions and intent   3. enemy AI
 *  4. player movement and terrain   5. projectiles   6. collect hits (sword, shots, enemy attacks)
 *  7. resolve damage   8. deaths, forced swap, falls, drops   9. triggers, boss, end conditions
 *  10. activate queued spawns.
 * Damage is queued and resolved in a fixed order (player→enemy hits by enemy id, then hits on the
 * player in queue order), so collection iteration order never changes the outcome.
 */
class World(loadout: Loadout = Loadout()) {
    enum class Result { PLAYING, WON, LOST }
    enum class Objective { REACH_SIGNAL, DEFEAT_BOSS, ACTIVATE_STATION }

    val level = Level()
    val player = Player(loadout)
    val boss = Boss(6)
    val dummy = TrainingDummy(7, 8f)
    val enemies: Array<Enemy> = arrayOf(
        PatrolRobot(0, 29f, 26f, 34f), PatrolRobot(1, 39f, 35f, 44f),
        Drone(2, 58f, 3.9f), PatrolRobot(3, 80f, 78f, 87f),
        Drone(4, 91f, 4.2f), PatrolRobot(5, 101f, 98f, 105f), boss, dummy
    )
    val projectiles = Array(GameConfig.PROJECTILE_POOL) { Projectile() }
    var result = Result.PLAYING
    var paused = false
    var healed = false
    var time = 0f
    /** Components picked up this run; the campaign only banks them if the run is won. */
    var collectedParts = 0
    /** Derived from the level state, so it can never disagree with it. */
    val objective: Objective
        get() = when {
            level.bossDefeated -> Objective.ACTIVATE_STATION
            level.bossAwake -> Objective.DEFEAT_BOSS
            else -> Objective.REACH_SIGNAL
        }
    /** True while the player stands at the active radio station (shows the interact button). */
    var canInteract = false
        private set

    // Counters read by the tutorial (real gameplay actions, not UI taps).
    var distanceMoved = 0f
        private set
    var jumps = 0
        private set
    var dodges = 0
        private set
    var skillsUsed = 0
        private set
    var swaps = 0
        private set
    var hitsLanded = 0
        private set

    // Event queue for the session (notices, dialogue triggers).
    private val events = IntArray(16)
    private var eventCount = 0

    // Hit queues, reset every step.
    private val enemyHitTarget = IntArray(32)
    private val enemyHitDamage = IntArray(32)
    private val enemyHitDir = IntArray(32)
    private val enemyHitKnock = FloatArray(32)
    private var enemyHitCount = 0
    private val playerHitDamage = IntArray(16)
    private val playerHitDir = IntArray(16)
    private var playerHitCount = 0
    private val pending = BooleanArray(GameConfig.PROJECTILE_POOL)

    fun update(c: Controls, dt: Float) {
        if (paused || result != Result.PLAYING) {
            c.clear()
            return
        }
        time += dt
        enemyHitCount = 0
        playerHitCount = 0
        val p = player

        // 1-2. Input, timers, actions.
        tickTimers(dt)
        handleIntent(c, dt)

        // 3. Enemy AI (enemy attacks queue hits on the player).
        for (e in enemies) e.update(this, dt)

        // 4. Player movement and terrain.
        val beforeX = p.x
        p.vy = max(-GameConfig.MAX_FALL_SPEED, p.vy + GameConfig.GRAVITY * dt)
        CollisionSystem.move(p, level, dt)
        if (p.grounded && !p.dodging() && !p.dashing()) distanceMoved += abs(p.x - beforeX)
        // A dash or dodge that hits a wall stops there.
        if ((p.dashing() || p.dodging()) && p.vx == 0f) p.endAction()

        // 5-6. Projectiles and hit collection.
        for (i in projectiles.indices) if (projectiles[i].active && !pending[i]) moveProjectile(projectiles[i], dt)
        collectSwordHits()

        // 7. Resolve damage.
        resolveHits()

        // 8. Deaths, falls, drops.
        if (p.y < GameConfig.FALL_Y) fall()
        collectDrops()

        // 9. Triggers and end conditions.
        triggers(c)

        // 10. Spawns queued this step become live.
        pending.fill(false)
        c.clearEdges()
    }

    private fun tickTimers(dt: Float) {
        val p = player
        // Both rebels' skill cooldowns run while playing, including the reserve's.
        for (i in 0 until 2) p.skillCooldown[i] = down(p.skillCooldown[i], dt)
        p.switchCooldown = down(p.switchCooldown, dt)
        p.invulnerable = down(p.invulnerable, dt)
        p.hurtTime = down(p.hurtTime, dt)
        p.dodgeCooldown = down(p.dodgeCooldown, dt)
        p.shotCooldown = down(p.shotCooldown, dt)
        p.shotFlash += dt
        p.coyote = if (p.grounded) GameConfig.COYOTE else down(p.coyote, dt)
    }

    private fun handleIntent(c: Controls, dt: Float) {
        val p = player
        if (!c.attackHeld) p.fireLatch = false
        p.jumpBuffer = if (c.jump) GameConfig.JUMP_BUFFER else down(p.jumpBuffer, dt)
        val held = (if (c.right) 1 else 0) - (if (c.left) 1 else 0)

        // A valid swap goes first and drops this step's combat commands.
        var combatAllowed = true
        if (c.swap && p.swap(false)) {
            swaps++
            combatAllowed = false
        }

        // Advance the current action; a buffered combo request starts the next strike.
        val next = p.advanceAction(dt)
        if (next > 0) p.startStrike(next)

        if (combatAllowed && p.activeAlive() && p.hurtTime == 0f) {
            if (c.dodge && tryDodge(if (held != 0) held else p.facing)) {
                // Dodge wins this step.
            } else if (c.skill && trySkill()) {
                // Skill wins over attack.
            } else if (p.type == CharacterType.AN) {
                if (c.attack) {
                    if (p.striking()) p.queueStrike()
                    else if (p.action == Player.Action.NONE) p.startStrike(1)
                }
            } else if (c.attackHeld && !p.fireLatch && p.shotCooldown == 0f && !p.dodging()) {
                shoot(false)
                p.shotCooldown = GameConfig.SHOT_INTERVAL
            }
        }

        // Horizontal movement.
        when {
            p.hurtTime > 0 -> Unit // keep knockback
            p.dodging() -> p.vx = p.dodgeDirection() * GameConfig.DODGE_SPEED
            p.dashing() -> p.vx = p.attackDir * GameConfig.DASH_SPEED
            p.striking() && !p.airStrike -> {
                p.vx = 0f
                p.facing = p.attackDir
            }
            else -> {
                p.vx = held * GameConfig.SPEED
                if (held != 0 && !p.striking()) p.facing = held
            }
        }

        // Jump: grounded or coyote time; one jump consumes both the buffer and the coyote window.
        if (p.jumpBuffer > 0 && p.coyote > 0 && p.hurtTime == 0f && p.activeAlive() &&
            (p.action == Player.Action.NONE || (p.striking() && p.strikePhase == Player.StrikePhase.RECOVERY))
        ) {
            if (p.striking()) p.endAction()
            p.vy = GameConfig.JUMP
            p.coyote = 0f
            p.jumpBuffer = 0f
            p.grounded = false
            jumps++
        }
    }

    /** Free space for a dash/dodge start; refusing does not spend the cooldown. */
    private fun clear(dir: Int): Boolean {
        val p = player
        val x = if (dir > 0) p.x + p.w else p.x - GameConfig.MIN_CLEARANCE
        return !level.blocked(x, p.y + .05f, GameConfig.MIN_CLEARANCE, p.h - .1f)
    }

    private fun tryDodge(dir: Int): Boolean {
        val p = player
        if (!p.grounded || p.dodgeCooldown > 0 || !p.dodgeCancelsAction() || !clear(dir)) return false
        p.endAction()
        p.startDodge(dir)
        dodges++
        return true
    }

    private fun trySkill(): Boolean {
        val p = player
        if (p.skillCooldown[p.slot()] > 0 || p.action != Player.Action.NONE) return false
        if (p.type == CharacterType.AN) {
            if (!p.grounded || !clear(p.facing)) return false
            p.skillCooldown[0] = p.skillCooldownMax(0)
            p.startDash()
        } else {
            p.skillCooldown[1] = p.skillCooldownMax(1)
            shoot(true)
            p.shotCooldown = max(p.shotCooldown, GameConfig.PIERCE_FIRE_LOCK)
        }
        skillsUsed++
        return true
    }

    private fun shoot(piercing: Boolean) {
        val p = player
        p.recordShot(piercing)
        fire(p.muzzleX(), p.muzzleY(), p.facing * (if (piercing) GameConfig.PIERCE_SPEED else GameConfig.SHOT_SPEED), 0f,
            friendly = true, piercing = piercing, damage = p.shotDamage(piercing),
            life = if (piercing) GameConfig.PIERCE_LIFE else GameConfig.SHOT_LIFE)
    }

    /** Queues a shot; it becomes live at the end of this step. Dropped if the pool is full. */
    fun fire(x: Float, y: Float, vx: Float, vy: Float, friendly: Boolean, piercing: Boolean, damage: Int, life: Float = 2.5f) {
        for (i in projectiles.indices) {
            val shot = projectiles[i]
            if (!shot.active) {
                shot.launch(x, y, vx, vy, friendly, piercing, damage, life)
                pending[i] = true
                return
            }
        }
    }

    /** Enemy attacks call this; resolved after all attacks this step are known. */
    fun queuePlayerHit(damage: Int, direction: Int) {
        if (playerHitCount < playerHitDamage.size) {
            playerHitDamage[playerHitCount] = damage
            playerHitDir[playerHitCount] = direction
            playerHitCount++
        }
    }

    private fun queueEnemyHit(e: Enemy, damage: Int, direction: Int, knockback: Float) {
        if (enemyHitCount < enemyHitTarget.size) {
            enemyHitTarget[enemyHitCount] = e.id
            enemyHitDamage[enemyHitCount] = damage
            enemyHitDir[enemyHitCount] = direction
            enemyHitKnock[enemyHitCount] = knockback
            enemyHitCount++
        }
    }

    private fun collectSwordHits() {
        val p = player
        if (p.type != CharacterType.AN || !p.activeAlive() || !(p.swordActive() || p.dashing())) return
        for (e in enemies) {
            val mask = 1L shl e.id
            if (e.alive() && (p.swordHits and mask) == 0L && e.overlaps(p.swordX(), p.y, p.swordWidth(), GameConfig.SWORD_HEIGHT)) {
                p.swordHits = p.swordHits or mask
                queueEnemyHit(e, p.swordDamage(), p.attackDir, p.swordKnockback())
            }
        }
    }

    private fun resolveHits() {
        // Player→enemy hits in ascending enemy id, then queue order.
        for (id in 0 until 64) {
            for (k in 0 until enemyHitCount) {
                if (enemyHitTarget[k] != id) continue
                val e = enemies.firstOrNull { it.id == id } ?: continue
                if (e.hit(enemyHitDamage[k], enemyHitDir[k], enemyHitKnock[k])) hitsLanded++
            }
        }
        if (boss.hp <= 0 && !level.bossDefeated) onBossDefeated()
        // Hits on the player: the first that lands makes the rest bounce off invulnerability.
        for (k in 0 until playerHitCount) {
            player.hurt(playerHitDamage[k], playerHitDir[k].toFloat())
        }
    }

    private fun onBossDefeated() {
        level.bossDefeated = true
        // Pending attacks die with the boss; clear enemy shots still flying.
        for (s in projectiles) if (!s.friendly) s.active = false
        event(EVENT_BOSS_DEFEATED)
    }

    private fun triggers(c: Controls) {
        val p = player
        if (p.dead()) {
            result = Result.LOST
            canInteract = false
            return
        }
        recordSafePoint()
        if (!healed && p.x > Level.HEAL_LEFT && p.x < Level.HEAL_RIGHT) {
            // Heals the living, never revives; skill cooldowns of the living reset.
            for (i in 0 until 2) {
                if (p.hp[i] > 0) {
                    p.hp[i] = p.maxHp[i]
                    p.skillCooldown[i] = 0f
                }
            }
            healed = true
            p.safeX = Level.HEAL_SAFE_X
            p.safeY = Level.STREET
            event(EVENT_HEALED)
        }
        // Lock the arena only once the player is fully past the door collider.
        if (!level.bossAwake && !level.bossDefeated && p.x > Level.ARENA_TRIGGER) {
            level.bossAwake = true
            boss.wake()
            p.safeX = Level.ARENA_SAFE_X
            p.safeY = Level.STREET
            event(EVENT_ARENA_LOCKED)
        }
        canInteract = level.bossDefeated && p.overlaps(Level.STATION_X, Level.STREET, Level.STATION_W, 4f)
        if (canInteract && c.interact && !p.dead()) result = Result.WON
    }

    /** Broad floor before the arena, away from living enemies. */
    private fun recordSafePoint() {
        val p = player
        if (!p.grounded || p.x >= Level.SAFE_LIMIT || p.action != Player.Action.NONE) return
        for (e in enemies) {
            if (e.alive() && e !is TrainingDummy && abs(e.centerX() - p.centerX()) < 3f) return
        }
        for (floor in level.platforms) {
            if (floor.h == 2f && abs(p.y - floor.y - floor.h) < .02f && p.x > floor.x + 1 && p.x + p.w < floor.x + floor.w - 1) {
                p.safeX = p.x
                p.safeY = floor.y + floor.h
            }
        }
    }

    private fun moveProjectile(s: Projectile, dt: Float) {
        s.life -= dt
        if (s.life <= 0 || abs(s.x - player.x) > 38 || s.x < 0 || s.x > GameConfig.LEVEL_WIDTH || s.y < -5 || s.y > 25) {
            s.active = false
            return
        }
        // Swept in small sub-steps so fast shots cannot skip a thin target or wall.
        val steps = max(1, ceil(max(abs(s.vx * dt), abs(s.vy * dt)) / .15f).toInt())
        var step = 0
        while (step < steps && s.active) {
            s.x += s.vx * dt / steps
            s.y += s.vy * dt / steps
            if (level.blocked(s.x, s.y, s.w, s.h)) {
                s.active = false
                break
            }
            if (s.friendly) {
                for (e in enemies) {
                    val mask = 1L shl e.id
                    if (e.alive() && (s.hits and mask) == 0L && s.overlaps(e)) {
                        s.hits = s.hits or mask
                        queueEnemyHit(e, s.damage, if (s.vx > 0) 1 else -1, GameConfig.SHOT_KNOCKBACK)
                        if (!s.piercing) {
                            s.active = false
                            break
                        }
                    }
                }
            } else if (player.activeAlive() && s.overlaps(player)) {
                queuePlayerHit(s.damage, if (s.vx > 0) 1 else -1)
                s.active = false
            }
            step++
        }
    }

    private fun collectDrops() {
        for (e in enemies) {
            if (e.alive() || e.looted) continue
            e.looted = true
            collectedParts += e.dropsParts
        }
    }

    /**
     * One fall = one damage event of 15% max health (rounded up) that ignores invulnerability.
     * After death/forced swap, the survivor returns to the safe point with a short protection.
     */
    private fun fall() {
        val p = player
        p.endAction()
        p.hurt(p.fallDamage(), 0f, ignoreInvulnerability = true)
        p.x = p.safeX
        p.y = p.safeY
        p.vx = 0f
        p.vy = 0f
        p.coyote = 0f
        p.jumpBuffer = 0f
        p.hurtTime = 0f
        if (!p.dead()) p.invulnerable = max(p.invulnerable, GameConfig.RESPAWN_PROTECTION)
        event(EVENT_FELL)
    }

    private fun event(id: Int) {
        if (eventCount < events.size) events[eventCount++] = id
    }

    /** Events since the last [clearEvents]: [eventCount] entries, read with [eventAt]. */
    fun eventCount(): Int = eventCount
    fun eventAt(i: Int): Int = events[i]
    fun clearEvents() {
        eventCount = 0
    }

    /** Living enemies (excluding the training target), for the debug overlay. */
    fun livingEnemies(): Int = enemies.count { it.alive() && it !is TrainingDummy }

    fun activeProjectiles(): Int = projectiles.count { it.active }

    private fun down(value: Float, dt: Float): Float = max(0f, value - dt)

    companion object {
        const val EVENT_HEALED = 1
        const val EVENT_ARENA_LOCKED = 2
        const val EVENT_BOSS_DEFEATED = 3
        const val EVENT_FELL = 4
    }
}
