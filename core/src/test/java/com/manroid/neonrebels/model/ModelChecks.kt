package com.manroid.neonrebels.model

import com.manroid.neonrebels.input.ButtonLayout
import com.manroid.neonrebels.input.PointerButtons
import kotlin.math.abs
import kotlin.math.ceil

/** Runs with only a Kotlin compiler and JVM; also called by the JUnit suite. */
object ModelChecks {
    private var checks = 0

    @JvmStatic
    fun main(args: Array<String>) {
        runAll()
        com.manroid.neonrebels.progress.ProgressChecks.runAll()
    }

    fun runAll() {
        checks = 0
        movement(); collisions(); jumping(); swapping(); damage(); melee(); dashSlash(); rifle(); dodge()
        projectiles(); falls(); repairStation(); robots(); drones(); boss(); bossSelection(); station()
        outcomes(); timing(); pointers(); route(); simulatedBossFight(); loadouts(); drops()
        dialogue(); tutorial(); session()
        println("PASS: $checks gameplay assertions")
    }

    private fun check(condition: Boolean, message: String) {
        checks++
        if (!condition) throw AssertionError(message)
    }

    private fun near(expected: Float, actual: Float, message: String) {
        check(abs(expected - actual) < .025f, "$message expected=$expected actual=$actual")
    }

    private fun ticks(w: World, c: Controls, count: Int) {
        repeat(count) { w.update(c, GameConfig.STEP) }
    }

    private fun seconds(s: Float): Int = ceil(s / GameConfig.STEP).toInt()

    /** A world with every enemy except [keep] removed, so mechanics can be tested in isolation. */
    private fun quiet(vararg keep: Int): World {
        val w = World()
        for (e in w.enemies) if (e.id !in keep && e !is TrainingDummy && e !is Boss) e.hp = 0
        ticks(w, Controls(), 2)
        return w
    }

    private fun movement() {
        val w = World()
        val c = Controls()
        ticks(w, c, 10)
        check(w.player.grounded, "Player lands on start floor")
        c.right = true; ticks(w, c, 60)
        near(3f + GameConfig.SPEED * 60 * GameConfig.STEP, w.player.x, "Run speed in world units")
        c.right = false; c.left = true; ticks(w, c, 200)
        near(0f, w.player.x, "Left map boundary")
        c.left = true; c.right = true
        val x = w.player.x
        ticks(w, c, 30)
        near(x, w.player.x, "Left and right together cancel")
        c.clear(); c.left = true; ticks(w, c, 1); c.left = false; ticks(w, c, 30)
        check(w.player.facing == -1, "Standing still keeps the last facing")
    }

    private fun collisions() {
        val l = Level()
        var b = Body(140f, 2f, .85f, 1.6f)
        b.vx = 170f; CollisionSystem.move(b, l, .1f)
        near(142.15f, b.x, "Fast dash cannot tunnel through gate")
        b = Body(5f, 15f, .85f, 1.6f); b.vy = -200f
        CollisionSystem.move(b, l, .1f)
        near(2f, b.y, "Fast fall cannot tunnel through floor")
        check(b.grounded, "Landing sets grounded")
        b = Body(12.5f, 2f, .85f, 1f); b.vy = 20f
        CollisionSystem.move(b, l, .1f)
        near(2.4f, b.y, "Platform underside collision")
        l.bossDefeated = true
        b = Body(140f, 2f, .85f, 1.6f); b.vx = 17f
        CollisionSystem.move(b, l, .5f)
        check(b.x > 144, "Gate removed after boss death")
        val w = quiet()
        w.player.y = 30f; w.player.vy = 0f; ticks(w, Controls(), seconds(1.5f))
        check(w.player.grounded || w.player.vy >= -GameConfig.MAX_FALL_SPEED, "Fall speed capped")
    }

    private fun jumping() {
        var w = quiet()
        var c = Controls()
        c.jump = true; ticks(w, c, 1)
        check(w.player.vy > 11 && w.jumps == 1, "Ground jump")
        ticks(w, c, 20)
        val before = w.player.vy
        c.jump = true; ticks(w, c, 1)
        check(w.player.vy < before && w.jumps == 1, "No double jump")
        // Coyote: just walked off a ledge.
        w = quiet(); c = Controls()
        w.player.x = 48.05f; w.player.y = 2f; w.player.vy = 0f
        w.player.grounded = false; w.player.coyote = .08f
        c.jump = true; ticks(w, c, 1)
        check(w.player.vy > 11, "Coyote jump after leaving ledge")
        check(w.player.coyote == 0f, "A jump consumes the coyote window")
        ticks(w, c, 2); c.jump = true; ticks(w, c, 1)
        check(w.jumps == 1, "Coyote time is not reused for a second jump")
        // Buffer: pressed just before landing.
        w = quiet(); c = Controls()
        w.player.y = 2.15f; w.player.vy = -3f; w.player.grounded = false; w.player.coyote = 0f
        c.jump = true; ticks(w, c, 10)
        check(w.player.vy > 0 && w.jumps == 1, "Buffered jump fires after landing")
        ticks(w, c, seconds(1.2f))
        check(w.jumps == 1, "A buffered press jumps only once")
    }

    private fun swapping() {
        val w = quiet()
        val c = Controls()
        val p = w.player
        p.x = 9f; p.y = 6f; p.vx = 5f; p.vy = -4f; p.facing = -1
        p.hp[0] = 57; p.skillCooldown[0] = 3f
        check(p.swap(false), "Swap to Vy")
        check(p.type == CharacterType.VY, "Active character switched")
        near(9f, p.x, "Swap preserves x"); near(6f, p.y, "Swap preserves y")
        near(5f, p.vx, "Swap preserves horizontal velocity"); near(-4f, p.vy, "Swap preserves vertical velocity")
        check(p.facing == -1, "Swap preserves facing")
        check(p.hp[0] == 57 && p.hp[1] == GameConfig.VY_HP, "Separate health")
        near(3f, p.skillCooldown[0], "Separate skill cooldown")
        check(!p.swap(false), "One second swap lock")
        // Reserve skill cooldown keeps running while playing.
        val w2 = quiet()
        w2.player.skillCooldown[1] = 2f
        ticks(w2, Controls(), seconds(1f))
        near(1f, w2.player.skillCooldown[1], "Reserve cooldown runs while playing")
        // Invulnerability is shared: swapping neither adds nor clears it.
        val w3 = quiet()
        val q = w3.player
        q.invulnerable = .3f
        q.swap(false)
        near(.3f, q.invulnerable, "Swap keeps remaining invulnerability")
        q.invulnerable = 0f; q.switchCooldown = 0f; q.swap(false)
        check(q.invulnerable == 0f, "Swap never grants invulnerability")
        // No swap mid-action or while stunned.
        val w4 = quiet()
        val c4 = Controls()
        c4.attack = true; ticks(w4, c4, 1)
        check(w4.player.striking(), "Strike started")
        c4.swap = true; ticks(w4, c4, 1)
        check(w4.player.type == CharacterType.AN, "Cannot swap mid-strike")
        w4.player.endAction(); w4.player.hurtTime = .1f
        check(!w4.player.canSwap(), "Cannot swap in hit-stun")
        // A valid swap drops this step's combat commands and needs a fresh fire press.
        val w5 = quiet()
        val c5 = Controls()
        c5.swap = true; c5.attack = true; c5.attackHeld = true; c5.skill = true
        ticks(w5, c5, 1)
        check(w5.player.type == CharacterType.VY && w5.activeProjectiles() == 0 && w5.player.skillCooldown[1] == 0f,
            "Swap step discards attack and skill")
        c5.attackHeld = true; ticks(w5, c5, 20)
        check(w5.activeProjectiles() == 0, "Held fire from before the swap does not shoot")
        c5.attackHeld = false; ticks(w5, c5, 1); c5.attackHeld = true; ticks(w5, c5, 2)
        check(w5.activeProjectiles() == 1, "A new press fires")
        // Dying forces a swap ignoring cooldown.
        val p6 = quiet().player
        p6.swap(false)
        p6.hp[1] = 1; p6.invulnerable = 0f
        p6.hurt(20, 1f)
        check(p6.type == CharacterType.AN && p6.hp[1] == 0, "Death forces swap through cooldown")
        check(!p6.swap(true), "Cannot switch to dead reserve")
        p6.invulnerable = 0f; p6.hurt(99999, 1f)
        check(p6.dead(), "Both down means dead")
        ticks(w, c, 1)
    }

    private fun damage() {
        val p = quiet().player
        check(p.hurt(10, -1f), "Hit lands")
        check(!p.hurt(10, 1f), "Hit invulnerability blocks the next hit")
        check(p.hp[0] == GameConfig.AN_HP - 10 && p.vx < 0, "Damage and knockback")
        near(GameConfig.HIT_INVULNERABLE, p.invulnerable, "Invulnerability after a hit")
        near(GameConfig.HIT_STUN, p.hurtTime, "Hit-stun after a hit")
        p.invulnerable = .1f
        p.hurt(10, 1f, ignoreInvulnerability = true)
        near(GameConfig.HIT_INVULNERABLE, p.invulnerable, "Invulnerability is set, not stacked")
        p.hp[0] = 5; p.invulnerable = 0f; p.hurt(500, 1f)
        check(p.hp[0] == 0, "Health never goes negative")
    }

    private fun melee() {
        var w = quiet(7)
        var c = Controls()
        val dummy = w.dummy
        w.player.x = dummy.x - 1.2f; w.player.facing = 1
        c.attack = true; ticks(w, c, 1)
        check(w.player.striking() && w.player.comboStep == 1, "Press starts strike 1")
        ticks(w, c, seconds(GameConfig.COMBO_STARTUP[0]) - 2)
        check(dummy.hitsTaken == 0, "No hitbox during startup")
        ticks(w, c, seconds(GameConfig.COMBO_ACTIVE[0]))
        check(dummy.hitsTaken == 1, "Active frames hit")
        ticks(w, c, 5)
        check(dummy.hitsTaken == 1, "One hit per strike per target")
        // Buffer: many presses during the strike queue exactly one follow-up.
        w = quiet(7); c = Controls()
        w.player.x = w.dummy.x - 1.2f; w.player.facing = 1
        c.attack = true; ticks(w, c, 1)
        ticks(w, c, seconds(GameConfig.COMBO_STARTUP[0]) + 1)
        repeat(10) { c.attack = true; ticks(w, c, 1) }
        var steps = 0
        while (w.player.striking() && steps < 400) {
            ticks(w, c, 1); steps++
        }
        check(w.dummy.hitsTaken == 2, "Mashing buffers one follow-up only (2 hits, got ${w.dummy.hitsTaken})")
        // Full combo with timed presses; third hit hardest, combo ends after 3.
        w = quiet(0); c = Controls()
        val robot = w.enemies[0]
        robot.x = 27f; robot.hp = 5000
        w.player.x = 25.2f; w.player.facing = 1
        val damages = ArrayList<Int>()
        var last = robot.hp
        c.attack = true; ticks(w, c, 1)
        for (i in 0 until 600) {
            if (w.player.striking() && w.player.strikePhase == Player.StrikePhase.ACTIVE) c.attack = true
            ticks(w, c, 1)
            if (robot.hp != last) { damages += last - robot.hp; last = robot.hp }
            robot.x = 27f
            if (damages.size == 3 || robot.hp == 0) break
        }
        check(damages == listOf(100, 110, 150), "Combo damage 1.0/1.1/1.5 x attack: $damages")
        // Air strike: single hit, no chain.
        w = quiet(7); c = Controls()
        w.player.x = w.dummy.x - 1.2f; w.player.facing = 1
        c.jump = true; ticks(w, c, 3)
        c.attack = true; ticks(w, c, 1)
        check(w.player.airStrike, "Strike in the air is an air strike")
        ticks(w, c, seconds(.12f)); c.attack = true; ticks(w, c, 1)
        check(!w.player.comboQueued, "Air strike does not chain")
        // Direction locked at strike start.
        w = quiet(7); c = Controls()
        w.player.x = w.dummy.x - 1.2f; w.player.facing = 1
        c.attack = true; ticks(w, c, 1)
        c.left = true; ticks(w, c, seconds(.2f))
        check(w.player.facing == 1 || !w.player.striking(), "Facing locked during a strike")
        check(w.dummy.hitsTaken == 1, "Hitbox stays on the locked side")
        // Being hit clears the combo.
        w = quiet(); c = Controls()
        c.attack = true; ticks(w, c, 1)
        w.player.hurt(10, 1f)
        check(!w.player.striking() && w.player.comboStep == 0, "Hit-stun clears the combo")
    }

    private fun dashSlash() {
        var w = quiet(0)
        var c = Controls()
        val robot = w.enemies[0]
        robot.x = 27f; robot.hp = 5000
        w.player.x = 24f; w.player.facing = 1
        c.skill = true; ticks(w, c, 1)
        check(w.player.dashing() && w.player.skillCooldown[0] > GameConfig.AN_SKILL_COOLDOWN - .1f, "Dash slash starts")
        check(w.player.invulnerable == 0f, "Dash slash grants no invulnerability")
        ticks(w, c, seconds(GameConfig.DASH_TIME) + 2)
        check(5000 - robot.hp == 200, "Dash slash: 2.0 x attack, once per target (${5000 - robot.hp})")
        // Stops at a wall, never passes it.
        w = quiet(); c = Controls()
        w.level.bossAwake = true
        w.player.x = Level.DOOR_X - 2f; w.player.facing = 1
        c.skill = true; ticks(w, c, seconds(.3f))
        check(w.player.x + w.player.w <= Level.DOOR_X + .01f, "Dash stops at a wall (x=${w.player.x}, boss=${w.level.bossAwake})")
        // Blocked from the start: refused, no cooldown spent.
        w = quiet(); c = Controls()
        w.level.bossAwake = true
        w.player.x = Level.DOOR_X - w.player.w - .05f; w.player.facing = 1
        c.skill = true; ticks(w, c, 1)
        check(!w.player.dashing() && w.player.skillCooldown[0] == 0f, "Dash against a wall is refused for free")
        // Interrupted by a hit: cooldown stays spent.
        w = quiet(); c = Controls()
        c.skill = true; ticks(w, c, 1)
        w.player.hurt(10, -1f)
        check(!w.player.dashing() && w.player.skillCooldown[0] > 0, "Hit cancels dash, cooldown kept")
        // Not in the air.
        w = quiet(); c = Controls()
        c.jump = true; ticks(w, c, 3); c.skill = true; ticks(w, c, 1)
        check(!w.player.dashing() && w.player.skillCooldown[0] == 0f, "Dash slash needs the ground")
    }

    private fun rifle() {
        var w = quiet()
        var c = Controls()
        w.player.swap(false); w.player.fireLatch = false; w.player.x = 40f
        c.attackHeld = true
        ticks(w, c, seconds(1f) - 1)
        check(w.activeProjectiles() == 4, "Holding fire shoots every 0.25s (${w.activeProjectiles()})")
        val shot = w.projectiles.first { it.active }
        near(GameConfig.SHOT_SPEED, abs(shot.vx), "Shot speed")
        check(shot.damage == GameConfig.VY_DAMAGE, "Shot damage 1.0 x attack")
        c.attackHeld = false; ticks(w, c, seconds(2f))
        check(w.activeProjectiles() == 0, "Shots expire after their lifetime")
        // Piercing shot locks normal fire briefly.
        w = quiet(); c = Controls()
        w.player.swap(false); w.player.fireLatch = false; w.player.x = 40f
        c.skill = true; c.attackHeld = true; ticks(w, c, 1)
        check(w.projectiles.count { it.active && it.piercing } == 1 && w.activeProjectiles() == 1, "Skill fires one piercing shot")
        ticks(w, c, seconds(GameConfig.PIERCE_FIRE_LOCK) - 2)
        check(w.activeProjectiles() == 1, "Normal fire locked after a piercing shot")
        val pierce = w.projectiles.first { it.piercing && it.active }
        check(pierce.damage == 150, "Pierce damage 2.5 x attack")
        // Fire works while running and in the air; not while dodging.
        w = quiet(); c = Controls()
        w.player.swap(false); w.player.fireLatch = false; w.player.x = 40f
        c.jump = true; ticks(w, c, 3); c.attackHeld = true; ticks(w, c, 2)
        check(w.activeProjectiles() == 1, "Vy fires in the air")
        w = quiet(); c = Controls()
        w.player.swap(false); w.player.fireLatch = false; w.player.x = 40f
        c.dodge = true; ticks(w, c, 1); c.attackHeld = true; ticks(w, c, seconds(.1f))
        check(w.activeProjectiles() == 0, "No fire while dodging")
    }

    private fun dodge() {
        var w = quiet()
        var c = Controls()
        c.dodge = true; ticks(w, c, 1)
        check(w.player.dodging() && w.player.dodgeInvulnerable(), "Dodge starts invulnerable")
        check(!w.player.hurt(50, 1f), "Dodge window avoids damage")
        ticks(w, c, seconds(GameConfig.DODGE_INVULNERABLE) + 1)
        check(!w.player.dodgeInvulnerable(), "Invulnerable only for the first part")
        ticks(w, c, seconds(.2f))
        near(3f + GameConfig.DODGE_SPEED * GameConfig.DODGE_TIME, w.player.x, "Dodge distance")
        c.dodge = true; ticks(w, c, 1)
        check(!w.player.dodging(), "Dodge cooldown")
        // Uses the held direction.
        w = quiet(); c = Controls()
        w.player.x = 10f
        c.left = true; c.dodge = true; ticks(w, c, 1)
        check(w.player.dodgeDirection() == -1, "Dodge follows the held direction")
        // Refused against a wall, cooldown untouched.
        w = quiet(); c = Controls()
        w.level.bossAwake = true
        w.player.x = Level.DOOR_X - w.player.w - .05f; w.player.facing = 1
        c.dodge = true; ticks(w, c, 1)
        check(!w.player.dodging() && w.player.dodgeCooldown == 0f, "Dodge into a wall refused for free")
        // Can cancel strike recovery, not startup.
        w = quiet(); c = Controls()
        c.attack = true; ticks(w, c, 1); c.dodge = true; ticks(w, c, 1)
        check(w.player.striking(), "Dodge cannot cancel startup")
        ticks(w, c, seconds(GameConfig.COMBO_STARTUP[0] + GameConfig.COMBO_ACTIVE[0]) + 1)
        c.dodge = true; ticks(w, c, 1)
        check(w.player.dodging() && w.player.comboStep == 0, "Dodge cancels recovery and clears the combo")
        // Not in the air.
        w = quiet(); c = Controls()
        c.jump = true; ticks(w, c, 3); c.dodge = true; ticks(w, c, 1)
        check(!w.player.dodging(), "Dodge needs the ground")
    }

    private fun projectiles() {
        var w = quiet(0, 1)
        var c = Controls()
        w.player.x = 25f
        w.enemies[0].x = 29f; w.enemies[1].x = 36f
        w.fire(28.6f, 2.7f, 17f, 0f, true, true, 20)
        ticks(w, c, 80)
        check(w.enemies[0].hp == GameConfig.ROBOT_HP - 20 && w.enemies[1].hp == GameConfig.ROBOT_HP - 20,
            "Piercing hits two targets once each (${w.enemies[0].hp}, ${w.enemies[1].hp})")
        w = quiet(); c = Controls(); w.player.x = 140f
        w.fire(142.7f, 3f, 17f, 0f, true, false, 15); ticks(w, c, 5)
        check(!w.projectiles[0].active, "Projectile stopped by a wall")
        w = quiet(); c = Controls()
        w.fire(3f, 10f, 0f, 0f, true, false, 15, life = .5f); ticks(w, c, seconds(.6f))
        check(!w.projectiles[0].active, "Projectile lifetime")
        repeat(100) { w.fire(5f, 8f, 0f, 0f, true, false, 1) }
        check(w.projectiles.count { it.active } == GameConfig.PROJECTILE_POOL, "Projectile pool bounded")
        // Snapshot damage survives a swap.
        w = quiet(0)
        c = Controls()
        w.player.swap(false); w.player.fireLatch = false
        w.enemies[0].x = 28f; w.player.x = 22f; w.player.facing = 1
        c.attackHeld = true; ticks(w, c, 1); c.attackHeld = false
        w.player.switchCooldown = 0f; w.player.swap(false)
        ticks(w, c, 60)
        check(GameConfig.ROBOT_HP - w.enemies[0].hp == GameConfig.VY_DAMAGE, "Shot keeps Vy's damage after a swap")
    }

    private fun falls() {
        var w = quiet()
        var c = Controls()
        w.player.y = -6f; w.player.invulnerable = 10f
        ticks(w, c, 1)
        val fall = ceil(GameConfig.AN_HP * GameConfig.FALL_DAMAGE_FRACTION).toInt()
        check(w.player.hp[0] == GameConfig.AN_HP - fall, "Fall: 15% max health, ignores invulnerability")
        near(3f, w.player.x, "Fall restores safe x"); near(2f, w.player.y, "Fall restores safe y")
        check(w.player.vx == 0f && w.player.vy <= 0f && w.player.invulnerable >= GameConfig.RESPAWN_PROTECTION - .01f,
            "Respawn: still, protected")
        ticks(w, c, 5)
        check(w.player.hp[0] == GameConfig.AN_HP - fall, "One fall, one damage event")
        // Fatal fall: forced swap, survivor at safe point; never revives the fallen.
        w = quiet(); c = Controls()
        w.player.hp[0] = 3; w.player.y = -6f
        ticks(w, c, 1)
        check(w.player.type == CharacterType.VY && w.player.hp[0] == 0 && !w.player.dead(), "Fatal fall swaps to the survivor")
        near(2f, w.player.y, "Survivor placed at the safe point")
        w.player.hp[1] = 3; w.player.y = -6f; ticks(w, c, 1)
        check(w.result == World.Result.LOST, "Fatal fall with no reserve loses")
        // Safe points are not recorded next to an enemy.
        w = World(); c = Controls()
        w.player.x = 28f; ticks(w, c, 2)
        check(w.player.safeX < 26f, "No safe point beside a robot")
    }

    private fun repairStation() {
        val w = quiet()
        val c = Controls()
        w.player.x = 109f; w.player.hp[0] = 0; w.player.hp[1] = 25
        w.player.type = CharacterType.VY; w.player.skillCooldown[1] = 4f
        ticks(w, c, 1)
        check(w.healed && w.player.hp[0] == 0 && w.player.hp[1] == GameConfig.VY_HP, "Station heals the living, never revives")
        check(w.player.skillCooldown[1] == 0f, "Station resets living skill cooldowns")
        w.player.hp[1] = 50; ticks(w, c, 1)
        check(w.player.hp[1] == 50, "Station works once per run")
    }

    /** Steps until the robot starts its wind-up (patrol → chase → wind-up takes a couple of steps). */
    private fun windUp(w: World, c: Controls, r: PatrolRobot) {
        var guard = 0
        while (r.mode != PatrolRobot.Mode.WINDUP && guard++ < 3) ticks(w, c, 1)
    }

    private fun robots() {
        var w = quiet(0)
        var c = Controls()
        val robot = w.enemies[0] as PatrolRobot
        w.player.x = 31.2f; robot.x = 29f; robot.facing = 1
        windUp(w, c, robot)
        check(robot.mode == PatrolRobot.Mode.WINDUP && robot.windup > 0, "Robot winds up when in reach")
        check(w.player.hp[0] == GameConfig.AN_HP, "Wind-up deals no damage")
        ticks(w, c, seconds(GameConfig.ROBOT_WINDUP) + 2)
        check(w.player.hp[0] == GameConfig.AN_HP - GameConfig.ROBOT_DAMAGE, "Strike lands after wind-up")
        ticks(w, c, seconds(GameConfig.ROBOT_ACTIVE))
        check(robot.mode == PatrolRobot.Mode.RECOVERY, "Recovery after the strike")
        // Leaving the hitbox during the wind-up avoids it.
        w = quiet(0); c = Controls()
        val r2 = w.enemies[0] as PatrolRobot
        w.player.x = 31.2f; r2.x = 29f; r2.facing = 1
        windUp(w, c, r2)
        w.player.x = 35f
        ticks(w, c, seconds(GameConfig.ROBOT_WINDUP + GameConfig.ROBOT_ACTIVE) + 2)
        check(w.player.hp[0] == GameConfig.AN_HP, "Leaving reach during wind-up avoids the hit")
        // Stagger cancels the strike.
        w = quiet(0); c = Controls()
        val r3 = w.enemies[0] as PatrolRobot
        w.player.x = 31.2f; r3.x = 29f; r3.facing = 1
        windUp(w, c, r3)
        r3.hit(1, 1, 0f)
        ticks(w, c, seconds(GameConfig.ROBOT_WINDUP) + 2)
        check(w.player.hp[0] == GameConfig.AN_HP, "Staggering a robot cancels its wind-up")
        // Never walks off its floor.
        w = quiet(1); c = Controls()
        val r4 = w.enemies[1] as PatrolRobot
        r4.x = 43f; r4.facing = 1
        ticks(w, c, seconds(6f))
        check(r4.y >= 1.99f && r4.x + r4.w <= 48f, "Robot does not walk into the pit")
        // A wall hides the player.
        w = quiet(5); c = Controls()
        w.level.bossAwake = true
        val r5 = w.enemies[5] as PatrolRobot
        r5.x = 104f
        w.player.x = 116f
        ticks(w, c, 2)
        check(r5.mode == PatrolRobot.Mode.PATROL, "Robot does not see through the arena door")
        // Dead robots do nothing.
        w = quiet(0); c = Controls()
        w.enemies[0].hit(99999, 1, 0f)
        w.player.x = w.enemies[0].x
        ticks(w, c, seconds(2f))
        check(w.player.hp[0] == GameConfig.AN_HP, "Dead robot cannot deal damage")
        check(!w.enemies[0].hit(10, 1, 0f), "Dead robot cannot be damaged again")
    }

    private fun drones() {
        val w = quiet(2)
        val c = Controls()
        val drone = w.enemies[2] as Drone
        w.player.x = 55f
        var aimed = false
        var i = 0
        while (i < seconds(3f) && !aimed) {
            ticks(w, c, 1); i++
            aimed = drone.mode == Drone.Mode.AIM
        }
        check(aimed, "Drone aims at a nearby player")
        val lockX = drone.aimX
        w.player.x = 50f
        ticks(w, c, seconds(GameConfig.DRONE_AIM) + 2)
        check(drone.aimX == lockX, "Aim point locked at the start of AIM")
        val shot = w.projectiles.firstOrNull { it.active && !it.friendly }
        check(shot != null && abs(abs(shot.vx) + abs(shot.vy) - 0f) > 0f, "Drone fires after the warning")
        near(GameConfig.DRONE_SHOT_SPEED, kotlin.math.sqrt(shot!!.vx * shot.vx + shot.vy * shot.vy), "Drone shot speed")
        check(drone.mode == Drone.Mode.REPOSITION, "Drone repositions after firing")
        // Reachable with level shots from a jump: altitude band.
        for (e in World().enemies) if (e is Drone) check(e.y < 2f + .85f + GameConfig.JUMP * GameConfig.JUMP / (-2 * GameConfig.GRAVITY),
            "Drone ${e.id} within a jumping shot's reach")
    }

    private fun boss() {
        var w = World()
        var c = Controls()
        for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        check(w.boss.phase == Boss.Phase.SLEEP && !w.boss.hit(100, 1, 0f), "Sleeping boss ignores damage")
        w.player.x = 116f; ticks(w, c, 1)
        check(w.level.bossAwake && w.boss.phase == Boss.Phase.INTRO, "Entering the arena wakes the boss")
        check(!w.player.overlaps(w.level.arenaDoor), "Door never closes on the player")
        ticks(w, c, seconds(GameConfig.BOSS_INTRO) - 2)
        check(w.boss.phase == Boss.Phase.INTRO, "No attack during the intro")
        // Slam: zone locked at telegraph start, finite height, matches the hit area.
        w.player.x = w.boss.x - 1.5f
        var guard = 0
        while (w.boss.phase != Boss.Phase.TELEGRAPH && guard++ < 600) ticks(w, c, 1)
        check(w.boss.attack == Boss.Attack.SLAM, "Near player gets a slam")
        val zx = w.boss.dangerX()
        val zw = w.boss.dangerWidth()
        val hp = w.player.hp[0]
        ticks(w, c, seconds(GameConfig.SLAM_TELEGRAPH) - 3)
        check(w.player.hp[0] == hp && w.boss.dangerX() == zx && w.boss.dangerWidth() == zw, "Telegraph deals no damage, zone locked")
        guard = 0
        while (w.boss.phase == Boss.Phase.TELEGRAPH && guard++ < 20) ticks(w, c, 1)
        ticks(w, c, 1)
        check(w.player.hp[0] == hp - GameConfig.SLAM_DAMAGE, "Slam damages inside the warned zone (${hp - w.player.hp[0]})")
        // A jump clears the slam.
        w = World(); c = Controls()
        for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        w.player.x = 116f; ticks(w, c, 1)
        w.player.x = w.boss.x - 1.5f
        guard = 0
        while (w.boss.phase != Boss.Phase.TELEGRAPH && guard++ < 600) ticks(w, c, 1)
        ticks(w, c, seconds(GameConfig.SLAM_TELEGRAPH) - seconds(.3f))
        c.jump = true
        ticks(w, c, seconds(.6f))
        check(w.player.hp[0] == GameConfig.AN_HP, "Jumping over the slam avoids it")
        // Phase 2 once, after the current attack; recovery shortened, telegraph unchanged.
        w = World(); c = Controls()
        for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        w.player.x = 116f; ticks(w, c, seconds(GameConfig.BOSS_INTRO) + 2)
        w.player.x = 116f
        guard = 0
        while (w.boss.phase != Boss.Phase.TELEGRAPH && guard++ < 600) ticks(w, c, 1)
        w.boss.hit((GameConfig.BOSS_HP * .61f).toInt(), 1, 0f)
        check(w.boss.transitionPending && w.boss.phase == Boss.Phase.TELEGRAPH, "Transition waits; telegraph not cut")
        guard = 0
        while (w.boss.phase != Boss.Phase.PHASE_TRANSITION && guard++ < 1200) { w.player.invulnerable = 1f; ticks(w, c, 1) }
        check(w.boss.phase == Boss.Phase.PHASE_TRANSITION, "Phase transition after the attack")
        ticks(w, c, seconds(GameConfig.BOSS_TRANSITION) + 1)
        check(w.boss.enraged() && !w.boss.transitionPending, "Phase 2 reached")
        w.boss.hit(1, 1, 0f)
        check(!w.boss.transitionPending, "Transition happens once")
        guard = 0
        while (w.boss.phase != Boss.Phase.RECOVERY && guard++ < 1200) { w.player.invulnerable = 1f; ticks(w, c, 1) }
        val base = if (w.boss.attack == Boss.Attack.SLAM) GameConfig.SLAM_RECOVERY else GameConfig.CHARGE_RECOVERY
        near(base * GameConfig.BOSS_RAGE_RECOVERY, w.boss.timer, "Phase 2 shortens recovery")
        // Death cancels everything pending and clears enemy shots.
        w = World(); c = Controls()
        for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        w.player.x = 116f; ticks(w, c, seconds(GameConfig.BOSS_INTRO) + 2)
        guard = 0
        while (w.boss.phase != Boss.Phase.TELEGRAPH && guard++ < 600) ticks(w, c, 1)
        w.fire(130f, 3f, -1f, 0f, friendly = false, piercing = false, damage = 10)
        ticks(w, c, 1)
        w.fire(w.boss.x - .6f, 2.5f, 17f, 0f, true, false, 99999); ticks(w, c, 10)
        check(w.boss.phase == Boss.Phase.DEAD && w.boss.dangerWidth() == 0f, "Boss death cancels the telegraph")
        check(w.projectiles.none { it.active && !it.friendly }, "Enemy shots cleared on boss death")
        check(w.level.bossDefeated && w.objective == World.Objective.ACTIVATE_STATION, "Gate opens, objective: station")
        check(w.result == World.Result.PLAYING, "Boss death alone is not a win")
        val hp2 = w.player.hp[0]
        ticks(w, c, seconds(2f))
        check(w.player.hp[0] == hp2, "Dead boss deals no damage")
        // Boss takes no knockback.
        val b = World().boss
        b.wake(); val bx = b.x; b.hit(10, 1, 5f)
        check(b.x == bx && b.vx == 0f, "Boss ignores knockback")
    }

    private fun bossSelection() {
        val w = World()
        val c = Controls()
        for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        w.player.x = 116f; ticks(w, c, seconds(GameConfig.BOSS_INTRO) + 2)
        w.player.x = 116f
        var guard = 0
        while (w.boss.phase != Boss.Phase.TELEGRAPH && guard++ < 600) ticks(w, c, 1)
        check(w.boss.attack == Boss.Attack.CHARGE, "Far player with room gets a charge")
        // The charge warning is the full reachable path.
        val reach = GameConfig.CHARGE_SPEED * GameConfig.CHARGE_TIME
        check(w.boss.dangerWidth() <= w.boss.w + reach + .01f && w.boss.dangerX() >= Level.ARENA_LEFT - .01f, "Charge zone = reach")
        // Never the same attack three times in a row when the other fits.
        val seq = ArrayList<Boss.Attack>()
        var lastPhase = w.boss.phase
        guard = 0
        while (seq.size < 8 && guard++ < 20000) {
            w.player.invulnerable = 1f
            w.player.x = 116f
            ticks(w, c, 1)
            if (w.boss.phase == Boss.Phase.TELEGRAPH && lastPhase != Boss.Phase.TELEGRAPH) seq += w.boss.attack
            lastPhase = w.boss.phase
        }
        var run = 1
        for (i in 1 until seq.size) {
            run = if (seq[i] == seq[i - 1]) run + 1 else 1
            check(run <= GameConfig.BOSS_MAX_REPEAT || seq[i] == Boss.Attack.SLAM, "No more than two repeats: $seq")
        }
        // A charge hits once even if overlapping for many steps.
        val w2 = World()
        for (e in w2.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        w2.player.x = 116f; ticks(w2, c, seconds(GameConfig.BOSS_INTRO) + 2)
        w2.player.x = 116f
        guard = 0
        while (w2.boss.attack != Boss.Attack.CHARGE && guard++ < 600) ticks(w2, c, 1)
        while (w2.boss.phase == Boss.Phase.TELEGRAPH) ticks(w2, c, 1)
        val before = w2.player.hp[0]
        repeat(seconds(GameConfig.CHARGE_TIME)) { w2.player.invulnerable = 0f; w2.player.x = w2.boss.x; w2.update(c, GameConfig.STEP) }
        check(before - w2.player.hp[0] <= GameConfig.CHARGE_DAMAGE, "One hit per charge")
    }

    private fun station() {
        val w = quiet()
        val c = Controls()
        w.player.x = 154f; ticks(w, c, 1)
        check(!w.canInteract, "Station inactive before the boss falls")
        c.interact = true; ticks(w, c, 1)
        check(w.result == World.Result.PLAYING, "Cannot activate a locked station")
        w.boss.wake(); w.boss.hit(99999, 1, 0f); ticks(w, c, 1)
        check(w.canInteract, "Interact offered at the active station")
        ticks(w, c, 5)
        check(w.result == World.Result.PLAYING, "Standing there is not enough")
        c.interact = true; ticks(w, c, 1)
        check(w.result == World.Result.WON, "Interact wins")
        c.interact = true; ticks(w, c, 1)
        check(w.result == World.Result.WON, "Result is final")
    }

    private fun outcomes() {
        val w = quiet()
        val c = Controls()
        w.player.hp[1] = 0; w.player.hp[0] = 10; w.player.invulnerable = 0f
        w.player.x = 124f; ticks(w, c, 1)
        w.boss.hp = 1
        // Same step: the boss dies and the last rebel falls. Shots spawned now go live next step,
        // then both land in the same step.
        w.fire(w.boss.x - .5f, 3f, 17f, 0f, true, false, 99999)
        w.fire(w.player.x + w.player.w + .05f, 2.8f, -17f, 0f, friendly = false, piercing = false, damage = 9999)
        ticks(w, c, 1)
        check(w.boss.hp == 1 && w.player.hp[0] == 10, "Queued shots wait one step")
        ticks(w, c, 1)
        check(w.boss.hp == 0 && w.player.dead(), "Both hits land in the same step")
        check(w.result == World.Result.LOST, "Both down in the step the boss dies: defeat")
        val fresh = World()
        check(!fresh.healed && !fresh.level.bossAwake && fresh.boss.hp == GameConfig.BOSS_HP, "Replay resets stage")
        for (s in fresh.projectiles) check(!s.active, "Replay clears shots")
    }

    private fun timing() {
        val w = quiet()
        val c = Controls()
        val clock = FixedStepper()
        w.player.skillCooldown[0] = 3f; w.paused = true; c.right = true
        clock.advance(w, c, 3f)
        near(3f, w.player.x, "Pause freezes movement"); near(3f, w.player.skillCooldown[0], "Pause freezes cooldown")
        check(w.time < .03f, "Pause freezes all gameplay time"); check(!c.right, "Pause clears held controls")
        w.paused = false
        check(clock.advance(w, c, 20f) <= GameConfig.MAX_STEPS, "Catch-up bounded after a long frame")
        check(w.time <= .13f, "Long frame cannot simulate seconds of damage")
        val a = quiet()
        val b = quiet()
        val ca = Controls()
        val cb = Controls()
        ca.right = true; cb.right = true
        val fa = FixedStepper()
        val fb = FixedStepper()
        repeat(30) { fa.advance(a, ca, 1f / 30) }
        repeat(144) { fb.advance(b, cb, 1f / 144) }
        check(abs(a.player.x - b.player.x) <= GameConfig.SPEED * GameConfig.STEP + .001f, "Frame rate independent movement")
    }

    private fun pointers() {
        val p = PointerButtons()
        p.move(0, ButtonLayout.RIGHT); p.move(1, ButtonLayout.JUMP); p.move(2, ButtonLayout.ATTACK)
        check(p.held(ButtonLayout.RIGHT) && p.held(ButtonLayout.JUMP) && p.held(ButtonLayout.ATTACK), "Move jump attack multitouch")
        p.move(0, -1)
        check(!p.held(ButtonLayout.RIGHT) && p.held(ButtonLayout.ATTACK), "Dragging out releases only that finger")
        p.move(3, ButtonLayout.ATTACK); p.move(2, -1)
        check(p.held(ButtonLayout.ATTACK), "Two fingers on same button")
        p.clear(); check(!p.held(ButtonLayout.ATTACK), "Focus loss clears fingers")
        val cx = ButtonLayout.CX
        val cy = ButtonLayout.CY
        val r = ButtonLayout.R
        val slop = ButtonLayout.TOUCH_SLOP
        for (i in cx.indices) {
            check(cx[i] - r[i] - slop >= 0 && cx[i] + r[i] + slop <= ButtonLayout.WIDTH &&
                cy[i] - r[i] >= 0 && cy[i] + r[i] + slop <= ButtonLayout.HEIGHT, "Button inside fitted viewport")
            if (i !in ButtonLayout.OVERLAY) {
                check(cy[i] + r[i] <= ButtonLayout.DECK_TOP, "Gameplay button stays below the street line")
            }
            check(ButtonLayout.hit(cx[i], cy[i]) == i, "Button center hits its own button")
            for (j in i + 1 until cx.size) {
                val dx = cx[i] - cx[j]
                val dy = cy[i] - cy[j]
                val reach = r[i] + r[j] + 2 * slop
                check(dx * dx + dy * dy >= reach * reach, "Touch areas do not overlap")
            }
        }
        check(ButtonLayout.hit(640f, 400f) == -1, "Touching the play area presses nothing")
        val enabled = BooleanArray(cx.size) { true }
        enabled[ButtonLayout.INTERACT] = false
        check(ButtonLayout.hit(cx[ButtonLayout.INTERACT], cy[ButtonLayout.INTERACT], enabled) == -1, "Hidden buttons ignore touches")
    }

    private fun route() {
        // Verify traversable platform links using real collision and the configured jump arc.
        jumpLink(10f, 2f, 12f, 4f, 16f)
        jumpLink(42f, 2f, 44f, 4.2f, 48f)
        jumpLink(46.5f, 4.2f, 50f, 4.9f, 53f)
        jumpLink(58f, 2f, 60f, 4.3f, 64f)
        jumpLink(62.5f, 4.3f, 67f, 5.4f, 71f)
        jumpLink(69f, 5.4f, 73f, 4.7f, 77f)
        jumpLink(75.5f, 4.7f, 77f, 2f, 83f)
        jumpLink(81f, 2f, 83f, 4.1f, 87f)
    }

    /** Bot: walks up to the boss, avoids every attack with existing moves, hits back, then activates the station. */
    private fun simulatedBossFight() {
        for (hero in listOf(CharacterType.AN, CharacterType.VY)) {
            val w = World()
            val c = Controls()
            for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
            if (hero == CharacterType.VY) w.player.swap(false)
            w.player.x = 116f
            var i = 0
            while (i < seconds(120f) && w.boss.hp > 0 && !w.player.dead()) {
                val b = w.boss
                val p = w.player
                c.clear()
                val dx = b.centerX() - p.centerX()
                val danger = b.telegraphing() || b.striking()
                if (danger && b.attack == Boss.Attack.SLAM) {
                    // Jump just before the slam lands.
                    c.jump = b.telegraphing() && b.timer < .3f
                } else if (danger && b.attack == Boss.Attack.CHARGE) {
                    // Get behind the boss: the charge only covers the path ahead.
                    val behind = if (b.facing > 0) b.x - 1.5f else b.x + b.w + 1.5f
                    c.left = p.centerX() > behind + .2f
                    c.right = p.centerX() < behind - .2f
                } else {
                    val want = if (hero == CharacterType.AN) 1.6f else 5f
                    c.right = dx > want + .3f
                    c.left = dx < -(want + .3f) || (dx > 0 && dx < want - 1f && hero == CharacterType.VY)
                    if (abs(dx) < want + .5f) {
                        p.facing = if (dx >= 0) 1 else -1
                        c.attack = (i % 6) == 0
                        c.attackHeld = true
                        c.skill = p.skillCooldown[p.slot()] == 0f
                    }
                }
                w.update(c, GameConfig.STEP)
                i++
            }
            check(w.boss.hp == 0 && !w.player.dead(), "$hero beats the boss with normal stats (boss ${w.boss.hp}, hp ${w.player.hp.toList()})")
            check(w.result == World.Result.PLAYING, "Not won before the station")
            c.clear(); c.right = true
            var guard = 0
            while (!w.canInteract && guard++ < seconds(10f)) w.update(c, GameConfig.STEP)
            c.clear(); c.interact = true; w.update(c, GameConfig.STEP)
            check(w.result == World.Result.WON, "$hero walks to the station and activates it")
        }
    }

    private fun loadouts() {
        val lead = Loadout(first = CharacterType.VY)
        check(World(lead).player.type == CharacterType.VY, "First squad slot deploys first")
        val strong = Loadout(maxHp = intArrayOf(1500, 1100), damage = intArrayOf(150, 80), skillCooldown = floatArrayOf(6f, 4f))
        var w = World(strong)
        var c = Controls()
        check(w.player.hp[0] == 1500 && w.player.hp[1] == 1100, "Run starts at full upgraded health")
        for (e in w.enemies) if (e.id != 0 && e !is TrainingDummy) e.hp = 0
        w.enemies[0].x = 27f
        w.player.x = 25.2f; w.player.facing = 1
        c.attack = true; ticks(w, c, seconds(.25f))
        check(GameConfig.ROBOT_HP - w.enemies[0].hp == 150, "Sword uses run damage")
        w = World(strong); c = Controls(); w.player.swap(false); c.skill = true; ticks(w, c, 1)
        near(4f, w.player.skillCooldown[1], "Skill cooldown uses run value")
    }

    private fun drops() {
        val w = World()
        val c = Controls()
        check(w.collectedParts == 0, "No components before any kill")
        w.enemies[0].hit(99999, 1, 0f); w.enemies[2].hit(99999, 1, 0f); ticks(w, c, 1)
        check(w.collectedParts == GameConfig.DROP_ROBOT + GameConfig.DROP_DRONE, "Destroyed enemies drop components")
        ticks(w, c, 10)
        check(w.collectedParts == GameConfig.DROP_ROBOT + GameConfig.DROP_DRONE, "Each enemy drops only once")
        repeat(5) { w.dummy.hit(500, 1, 0f) }
        check(w.dummy.alive() && w.collectedParts == GameConfig.DROP_ROBOT + GameConfig.DROP_DRONE, "Training target never dies or drops")
    }

    private fun dialogue() {
        val d = DialogueController()
        d.open(Dialogues.byId(Dialogues.INTRO))
        d.update(.1f)
        check(d.visibleChars(40) in 1 until 40, "Text reveals over time")
        check(d.next(40) == null && d.index == 0 && d.visibleChars(40) == 40, "First press completes the line")
        check(d.next(40) == null && d.index == 1 && d.visibleChars(40) == 0, "Second press moves on")
        val skipped = d.skip()
        check(skipped?.action == DialogueAction.START_TUTORIAL && !d.active, "Skip ends and reports the script")
    }

    private fun tutorial() {
        val record = HashMap<String, String>()
        val w = quiet(7)
        val c = Controls()
        val t = TutorialController(emptyMap()) { id, v -> record[id] = v }
        t.start(w)
        check(t.current == TutorialController.Step.MOVE, "Starts with movement")
        c.right = true; ticks(w, c, seconds(.8f)); t.update(w)
        check(record["move"] == TutorialController.DONE && t.current == TutorialController.Step.JUMP, "Move completes by walking")
        c.clear(); c.jump = true; ticks(w, c, 1); t.update(w)
        check(t.current == TutorialController.Step.ATTACK, "Jump completes on a real jump")
        ticks(w, c, seconds(1f))
        w.player.x = w.dummy.x - 1.2f; w.player.facing = 1
        c.attack = true; ticks(w, c, seconds(.25f)); t.update(w)
        check(t.current == TutorialController.Step.DODGE, "Attack completes on a hit on the target")
        c.dodge = true; ticks(w, c, seconds(1.1f)); t.update(w)
        check(t.current == TutorialController.Step.SKILL, "Dodge completes on a real dodge")
        w.player.skillCooldown[0] = 2f
        val t2 = TutorialController(mapOf("move" to "done", "jump" to "done", "attack" to "done", "dodge" to "done")) { _, _ -> }
        t2.start(w)
        check(t2.current == TutorialController.Step.SKILL && t2.waiting, "Skill step waits for the cooldown")
        w.player.hp[1] = 0
        val t3 = TutorialController(mapOf("move" to "done", "jump" to "done", "attack" to "done", "dodge" to "done", "skill" to "done")) { id, v -> record[id] = v }
        t3.start(w); t3.update(w)
        check(record["swap"] == TutorialController.SKIPPED && t3.finished(), "Swap step never blocks with one rebel left")
        val t4 = TutorialController(emptyMap()) { id, v -> record["x$id"] = v }
        t4.start(World()); t4.skipAll()
        check(t4.finished() && t4.current == null && record["xmove"] == TutorialController.SKIPPED, "Skip marks every step skipped")
        val t5 = TutorialController(TutorialController.Step.values().associate { it.id to "done" }) { _, _ -> }
        t5.start(World())
        check(t5.current == null, "A finished tutorial does not restart on replay")
    }

    private fun session() {
        var s = CombatSession(World(), story = true, tutorialRecord = emptyMap()) { _, _ -> }
        check(s.state == CombatSession.State.LOADING && s.world.paused, "Loading does not simulate")
        s.begin(); s.update(CombatSession.INTRO_SECONDS + .01f)
        check(s.state == CombatSession.State.DIALOGUE && s.world.paused, "Intro dialogue opens and freezes the run")
        s.pause()
        check(s.state == CombatSession.State.PAUSED && s.pausedFrom == CombatSession.State.DIALOGUE, "Pause remembers the dialogue")
        s.resume()
        check(s.state == CombatSession.State.DIALOGUE, "Resume returns to the dialogue, not to play")
        s.dialogueSkip()
        check(s.state == CombatSession.State.PLAYING && !s.world.paused, "Skip returns to play")
        check(s.tutorial.current != null, "Skipping the intro still starts the tutorial")
        // Boss dialogue waits for a safe moment.
        val w = s.world
        val c = Controls()
        for (e in w.enemies) if (e !is Boss && e !is TrainingDummy) e.hp = 0
        w.player.x = 116f; w.player.grounded = false; w.player.y = 3f
        w.update(c, GameConfig.STEP); s.afterStep()
        check(s.state == CombatSession.State.PLAYING || w.player.grounded, "Dialogue never opens mid-air")
        var guard = 0
        while (s.state == CombatSession.State.PLAYING && guard++ < 240) { w.update(c, GameConfig.STEP); s.afterStep() }
        check(s.state == CombatSession.State.DIALOGUE && s.dialogue.script?.id == Dialogues.BOSS, "Boss dialogue once safe")
        val time = w.time
        repeat(100) { w.update(c, GameConfig.STEP) }
        check(w.time == time, "Dialogue freezes the run clock")
        s.dialogueSkip()
        // Practice target: present only while the tutorial still needs it.
        val fresh = CombatSession(World(), story = false, tutorialRecord = emptyMap()) { _, _ -> }
        check(fresh.world.dummy.alive(), "Target present for a new tutorial")
        val done = CombatSession(World(), story = false, tutorialRecord = mapOf("move" to "done", "jump" to "done", "attack" to "done")) { _, _ -> }
        check(!done.world.dummy.alive(), "Target gone once the attack step is done")
        fresh.begin(); fresh.update(2f); fresh.skipTutorial()
        check(!fresh.world.dummy.alive() && !fresh.world.dummy.hit(10, 1, 0f), "Skipping the tutorial removes the target")
        val shotWorld = done.world
        shotWorld.player.swap(false); shotWorld.player.fireLatch = false
        val c2 = Controls(); c2.attackHeld = true
        done.begin(); done.update(2f)
        repeat(80) { shotWorld.update(c2, GameConfig.STEP) }
        check(shotWorld.projectiles.any { it.active && it.x > shotWorld.dummy.x + 1f }, "Removed target no longer blocks shots")
        // Victory is final and single.
        s = CombatSession(World(), story = false, tutorialRecord = emptyMap()) { _, _ -> }
        s.begin(); s.update(2f)
        check(s.state == CombatSession.State.PLAYING, "No story: straight to play")
        s.world.result = World.Result.WON; s.afterStep()
        check(s.state == CombatSession.State.VICTORY, "Victory")
        s.world.result = World.Result.LOST; s.afterStep()
        check(s.state == CombatSession.State.VICTORY, "Victory and defeat never both happen")
        s.pause()
        check(s.state == CombatSession.State.VICTORY, "No pause after the end")
    }

    private fun jumpLink(x: Float, y: Float, targetX: Float, targetY: Float, targetEnd: Float) {
        val w = World()
        val c = Controls()
        for (e in w.enemies) e.hp = 0
        w.player.x = x; w.player.y = y; w.player.grounded = true
        c.jump = true; c.right = true
        var landed = false
        for (i in 0 until 150) {
            if (w.player.x > targetX + .3f) c.right = false
            w.update(c, GameConfig.STEP)
            if (i > 5 && w.player.grounded && w.player.x + w.player.w > targetX &&
                w.player.x < targetEnd && abs(w.player.y - targetY) < .03f
            ) {
                landed = true
                break
            }
        }
        check(landed, "Jump link reachable $x,$y -> $targetX,$targetY ended ${w.player.x},${w.player.y}")
    }
}
