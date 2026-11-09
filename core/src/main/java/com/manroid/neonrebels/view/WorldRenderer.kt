package com.manroid.neonrebels.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.manroid.neonrebels.MainGame
import com.manroid.neonrebels.input.ButtonLayout
import com.manroid.neonrebels.model.Boss
import com.manroid.neonrebels.model.CharacterType
import com.manroid.neonrebels.model.Drone
import com.manroid.neonrebels.model.Enemy
import com.manroid.neonrebels.model.GameConfig
import com.manroid.neonrebels.model.PatrolRobot
import com.manroid.neonrebels.model.Player
import com.manroid.neonrebels.model.TrainingDummy
import com.manroid.neonrebels.model.World
import com.manroid.neonrebels.view.animation.CharacterAnimator
import com.manroid.neonrebels.view.animation.CharacterFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Presentation only: reads [World] and never decides combat. Art comes from the UI mock-ups
 * ([WorldSprites]): independent character clips when supplied, legacy poses otherwise. Fallback motion is procedural:
 * each frame a pose (offset, lean, squash) is derived from the model state, and [Effects] adds
 * sparks, dust, shockwaves, damage numbers and screen shake. Effects are triggered by comparing the
 * model with the previous frame (health dropped, landed, boss changed phase…) and run on simulation
 * time, so they freeze with pause and dialogue. Actors stay anchored on their hitbox bottom centre.
 */
class WorldRenderer(private val game: MainGame) : Disposable {
    val viewport: FitViewport = SafeViewport(GameConfig.VIEW_WIDTH, GameConfig.VIEW_HEIGHT)
    private val shapes: ShapeRenderer = game.shapes
    private val batch: SpriteBatch = game.batch
    private val ui = UiPainter(game)
    private val sprites = WorldSprites(game.debugTools)
    private val animator = CharacterAnimator(sprites.characters.clips)
    private var characterFrame: CharacterFrame? = null
    private var animationInitialized = false
    private var previewId: String? = null
    private var previewIndex = 0

    private val effects = Effects()
    private val rigs = RigRenderer()
    private val playerPose = Pose()
    private val enemyPoses = Array(16) { Pose() }
    /** Rig and pose used by [actor] and [artPoint]; set before drawing each actor. */
    private var activeRig: Rig? = null
    private var activePose: Pose = playerPose
    private val scratch = Color()
    private var cameraX = Float.NaN

    // Previous-frame snapshot used to detect events for effects.
    private var lastTime = -1f
    private var prevEnemyHp = IntArray(0)
    private var prevEnemyX = FloatArray(0)
    // Enemy poses of this frame, for glows drawn over the sprites (index = enemy id).
    private val enemyPoseX = FloatArray(16)
    private val enemyPoseY = FloatArray(16)
    private val enemyPoseRot = FloatArray(16)
    private val enemyPoseScale = FloatArray(16)
    private val enemyDrawn = BooleanArray(16)
    private var prevDummyHits = 0
    private val prevPlayerHp = IntArray(2)
    private var prevGrounded = true
    private var prevVy = 0f
    private var prevJumps = 0
    private var prevAction = Player.Action.NONE
    private var prevComboStep = 0
    private var prevSlot = 0
    private var prevHealed = false
    private var prevBossPhase = Boss.Phase.SLEEP
    private var prevBossAttack = Boss.Attack.NONE
    private val prevShot = BooleanArray(GameConfig.PROJECTILE_POOL)
    private val shotX = FloatArray(GameConfig.PROJECTILE_POOL)
    private val shotY = FloatArray(GameConfig.PROJECTILE_POOL)
    private val shotLife = FloatArray(GameConfig.PROJECTILE_POOL)
    private var landSquash = 0f
    private var runDust = 0f
    private var steam = 0f
    private var bossBlast = 0f
    private var swapFlash = 0f

    // Player pose of this frame (also used to place the sword lightning and the muzzle flash).
    private var poseX = 0f
    private var poseY = 0f
    private var poseRot = 0f
    private var poseScaleY = 1f
    private var poseFacing = 1f
    private var poseArt: ActorArt? = null

    fun animationClip(id: String) = sprites.characters.clips[id]
    fun animationStatus(id: String) = sprites.characters.status[id] ?: "invalid manifest"
    fun animationFrame(id: String, index: Int) = sprites.characters.frame(id, index)

    /** Isolated debug preview; caller owns projection/batch and never updates a gameplay World. */
    fun drawPreviewPlayer(p: Player, time: Float, id: String, index: Int) {
        previewId = id
        previewIndex = index
        landSquash = if (id.endsWith(".land")) (LAND_TIME - time).coerceAtLeast(0f) else 0f
        try { drawPlayer(p, time) } finally { previewId = null }
    }

    /** Called after each fixed step; drawing never emits gameplay actions. */
    fun updateAnimation(world: World) {
        animator.update(world.player, GameConfig.STEP)
        animationInitialized = true
    }

    fun draw(world: World, terminalDelta: Float = 0f) {
        val p = world.player
        if (!animationInitialized) {
            animator.update(p, 0f)
            animationInitialized = true
        }
        if (world.result == World.Result.LOST) animator.advanceDeath(terminalDelta)
        val dt = if (lastTime < 0f) 0f else (world.time - lastTime).coerceIn(0f, .1f)
        lastTime = world.time
        detect(world, dt)
        effects.update(dt)
        val camera = viewport.camera as OrthographicCamera
        // Follows the squad (same body for An and Vy, so a swap never jumps the camera); during the
        // boss fight it settles on the arena. Smoothed in render time only.
        val target = if (world.level.bossAwake && !world.level.bossDefeated) ARENA_CENTER
        else max(16f, min(GameConfig.LEVEL_WIDTH - 16, p.centerX() + p.facing * 2))
        cameraX = if (cameraX.isNaN() || abs(target - cameraX) > 24f) target
        else cameraX + (target - cameraX) * min(1f, Gdx.graphics.deltaTime * 6f)
        camera.position.set(cameraX + effects.shakeX, cameraY() + effects.shakeY, 0f)
        camera.update()
        viewport.apply()
        val left = camera.position.x - GameConfig.VIEW_WIDTH / 2
        val bottom = camera.position.y - GameConfig.VIEW_HEIGHT / 2
        val top = bottom + GameConfig.VIEW_HEIGHT
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        beginShapes()
        rect(left, bottom, GameConfig.VIEW_WIDTH, GameConfig.VIEW_HEIGHT, sprites.sky)
        shapes.end()
        batch.begin()
        // Slightly dimmed so the actors in front read clearly.
        batch.setColor(.82f, .82f, .9f, 1f)
        // Backdrop from the mock-up: holo tower, pink moon, ruined bridge. 4:1 texture.
        val farHeight = max(FAR_HEIGHT, top - STREET + .5f)
        layer(sprites.far, left, STREET - .5f, GameConfig.VIEW_WIDTH, farHeight, left * .3f, farHeight * 4)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.end()

        beginShapes()
        // Pits: a deep shaft below the street, darker towards the bottom.
        shapes.rect(left, bottom, GameConfig.VIEW_WIDTH, STREET - bottom, PIT_LOW, PIT_LOW, PIT_HIGH, PIT_HIGH)
        shapes.end()

        batch.begin()
        drawGround(world, left, bottom)
        drawProps(world)
        batch.end()

        beginShapes()
        val boss = world.boss
        if (boss.telegraphing() || (boss.striking() && boss.attack == Boss.Attack.SLAM)) drawTelegraph(boss, world.time)
        shapes.end()

        // Glows behind the actors (enemy eyes, boss charge-up).
        glowPass { drawUnderGlow(world) }

        batch.begin()
        enemyDrawn.fill(false)
        for (e in world.enemies) drawEnemy(e, world.time)
        drawPlayer(p, world.time)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.end()

        beginShapes()
        effects.drawSoft(shapes)
        drawWarningSign(boss)
        for (e in world.enemies) {
            if (e.hp <= 0 || e is Boss || e is Drone || e is TrainingDummy) continue
            val y = e.y + art(e).height * .98f
            rect(e.x, y, e.w, .1f, Palette.GROUND)
            rect(e.x, y, e.w * e.hp / e.maxHp, .1f, Palette.RED)
        }
        shapes.end()

        // Bright effects with additive blending: lightning, slashes, shots, sparks, shockwaves.
        glowPass {
            drawEyes(world)
            drawLightning(world)
            drawSlash(world)
            drawShots(world)
            effects.drawGlow(shapes)
        }

        batch.begin()
        effects.drawNumbers { text, x, y, scale, color -> ui.text(text, x, y, scale, color) }
        if (world.dummy.alive()) ui.text(ui.tr("world.target"), world.dummy.x - 1.1f, world.dummy.y + 3.1f, .05f, Palette.CYAN)
        ui.text(ui.tr("world.district"), 3f, 7.5f, .065f, Palette.CYAN)
        ui.text(ui.tr("world.tutorial"), 12.5f, 6.4f, .065f, Color.WHITE)
        ui.text(ui.tr("world.patrol"), 29f, 7f, .065f, Palette.ORANGE)
        ui.text(ui.tr("world.gaps"), 47f, 8f, .065f, Palette.ORANGE)
        ui.text(ui.tr(if (world.healed) "world.repaired" else "world.repair"), 104.5f, 6.4f, .065f, Palette.GREEN)
        ui.text(ui.tr("world.boss"), 120f, 9f, .075f, Palette.RED)
        ui.text(ui.tr(if (world.level.bossDefeated) "world.signalOpen" else "world.signalLocked"),
            150.5f, 12.2f, .065f, Palette.GREEN)
        batch.end()
        if (game.save.debugOverlay()) drawDebug(world)
    }

    private inline fun glowPass(body: () -> Unit) {
        beginShapes()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        body()
        shapes.end()
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** SpriteBatch.end() turns GL blending off, so every shape pass re-enables it. */
    private fun beginShapes() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
    }

    // ------------------------------------------------------------------ events → effects

    private fun detect(world: World, dt: Float) {
        val p = world.player
        val enemies = world.enemies
        if (prevEnemyHp.size != enemies.size) {
            prevEnemyHp = IntArray(enemies.size) { enemies[it].hp }
            prevEnemyX = FloatArray(enemies.size) { enemies[it].x }
            prevPlayerHp[0] = p.hp[0]; prevPlayerHp[1] = p.hp[1]
            prevSlot = p.slot()
            prevJumps = world.jumps
        }
        if (dt <= 0f) return
        val hitColor = if (p.type == CharacterType.AN) Palette.CYAN else Palette.PINK
        // Enemies hit or destroyed.
        for (i in enemies.indices) {
            val e = enemies[i]
            val lost = prevEnemyHp[i] - e.hp
            if (lost > 0) {
                val side = if (p.centerX() < e.centerX()) -1f else 1f
                val hx = e.centerX() + side * e.w * .4f
                val hy = e.y + min(e.h, 2.4f) * .55f
                val big = lost >= 150
                effects.sparks(hx, hy, if (big) 14 else 8, hitColor, if (big) 11f else 8f, -side)
                effects.sparks(hx, hy, 4, Color.WHITE, 6f, -side)
                effects.number(e.centerX(), e.y + min(e.h, 3f) + .4f, lost, if (big) Palette.GOLD else Color.WHITE, big)
                if (big) effects.shake(.12f)
                if (e.hp == 0) {
                    when (e) {
                        is Boss -> bossBlast = 1.6f
                        is Drone -> effects.explosion(e.centerX(), e.centerY(), .8f)
                        else -> effects.explosion(e.centerX(), e.y + 1f, 1f)
                    }
                }
            }
            prevEnemyHp[i] = e.hp
        }
        if (world.dummy.hitsTaken > prevDummyHits) {
            val d = world.dummy
            effects.sparks(d.centerX() - p.facing * .3f, d.y + 1f, 8, hitColor, 7f, p.facing.toFloat())
            effects.ring(d.centerX(), d.y + 1f, .9f, hitColor, flat = false, time = .2f)
            prevDummyHits = d.hitsTaken
        }
        // Player hurt / healed.
        val slot = p.slot()
        val lostHp = prevPlayerHp[prevSlot] - p.hp[prevSlot]
        if (lostHp > 0) {
            effects.sparks(p.centerX(), p.y + 1.1f, 10, Palette.RED, 8f)
            effects.number(p.centerX(), p.y + 2.6f, lostHp, Palette.RED, lostHp >= 150)
            effects.shake(if (lostHp >= 150) .3f else .18f)
        }
        if (world.healed && !prevHealed) {
            effects.puffs(p.centerX(), p.y + .4f, 14, Palette.GREEN, .8f, 2.5f, .18f)
            effects.ring(p.centerX(), p.y + .9f, 2.2f, Palette.GREEN, flat = true, time = .6f)
        }
        prevHealed = world.healed
        if (slot != prevSlot) {
            swapFlash = .25f
            effects.ring(p.centerX(), p.y + 1.1f, 1.6f, if (slot == 0) Palette.CYAN else Palette.PINK, flat = false, time = .3f)
            effects.sparks(p.centerX(), p.y + 1.1f, 10, if (slot == 0) Palette.CYAN else Palette.PINK, 6f)
        }
        prevPlayerHp[0] = p.hp[0]; prevPlayerHp[1] = p.hp[1]
        prevSlot = slot
        swapFlash = max(0f, swapFlash - dt)
        // Movement: jump, landing, running dust, dash/dodge start.
        if (world.jumps > prevJumps) effects.dust(p.centerX(), p.y, p.facing.toFloat(), 5)
        prevJumps = world.jumps
        if (p.grounded && !prevGrounded && prevVy < -6f) {
            landSquash = LAND_TIME
            effects.dust(p.centerX(), p.y, 0f, 8)
        }
        prevGrounded = p.grounded
        prevVy = p.vy
        landSquash = max(0f, landSquash - dt)
        if (p.grounded && abs(p.vx) > .1f && p.action == Player.Action.NONE && p.hurtTime == 0f) {
            runDust -= dt
            if (runDust <= 0f) {
                runDust = .18f
                effects.dust(p.centerX() - p.facing * .3f, p.y, p.facing.toFloat(), 2)
            }
        }
        if ((p.dashing() || p.dodging()) && p.action != prevAction) {
            effects.dust(p.centerX(), p.y, p.facing.toFloat(), 8)
            if (p.dashing()) effects.sparks(p.centerX() + p.facing * .6f, p.y + 1.1f, 8, Palette.CYAN, 9f, p.facing.toFloat())
        }
        if (p.striking() && p.comboStep != prevComboStep && p.comboStep == 3) effects.dust(p.centerX(), p.y, -p.facing.toFloat(), 4)
        prevAction = p.action
        prevComboStep = p.comboStep
        // Shots: muzzle burst on spawn, impact sparks when one stops early (hit a target or wall).
        for (i in world.projectiles.indices) {
            val s = world.projectiles[i]
            if (s.active && !prevShot[i] && s.friendly) {
                val c = if (s.piercing) Palette.CYAN else Palette.PINK
                effects.sparks(s.x, s.y + s.h / 2, if (s.piercing) 10 else 4, c, 6f, if (s.vx > 0) 1f else -1f)
                if (s.piercing) effects.ring(s.x, s.y + s.h / 2, 1f, c, flat = false, time = .25f)
            } else if (!s.active && prevShot[i] && shotLife[i] > .05f) {
                val c = if (shotFriendly(i, world)) Palette.PINK else Palette.ORANGE
                effects.sparks(shotX[i], shotY[i], 6, c, 6f)
            }
            prevShot[i] = s.active
            if (s.active) {
                shotX[i] = s.x + s.w / 2; shotY[i] = s.y + s.h / 2; shotLife[i] = s.life
            }
        }
        detectBoss(world.boss, dt)
        // Drones and robots: per-step bits.
        for (i in enemies.indices) {
            val e = enemies[i]
            if (e is PatrolRobot && e.alive() && e.mode == PatrolRobot.Mode.ATTACK && e.timer > GameConfig.ROBOT_ACTIVE - dt) {
                effects.sparks(e.strikeX() + GameConfig.ROBOT_ATTACK_RANGE / 2, e.y + .9f, 5, Palette.ORANGE, 6f, e.strikeDir.toFloat())
            }
            if (!e.alive() && e !is TrainingDummy && e !is Boss && e.deathTime < 1f && MathUtils.randomBoolean(dt * 12f)) {
                effects.puffs(e.centerX(), e.y + .6f, 1, Effects.SMOKE, .3f, 1.6f, .3f)
            }
            prevEnemyX[i] = e.x
        }
    }

    private fun shotFriendly(i: Int, world: World): Boolean = world.projectiles[i].friendly

    private fun detectBoss(b: Boss, dt: Float) {
        if (b.phase != prevBossPhase || b.attack != prevBossAttack) {
            when {
                b.phase == Boss.Phase.INTRO -> {
                    effects.ring(b.centerX(), b.y + 2.5f, 4f, Palette.RED, flat = false, time = .6f)
                    effects.shake(.25f)
                }
                b.phase == Boss.Phase.ATTACK && b.attack == Boss.Attack.SLAM -> {
                    // Ground slam: shockwave across the warned zone, debris and a heavy shake.
                    val l = b.dangerX()
                    val r = b.dangerX() + b.dangerWidth()
                    effects.ring(b.centerX(), STREET + .05f, b.dangerWidth() / 2 + .5f, Palette.ORANGE, flat = true, time = .4f)
                    effects.ring(b.centerX(), STREET + .05f, b.dangerWidth() / 2, Color.WHITE, flat = true, time = .25f)
                    effects.dust(l + .5f, STREET, 1f, 8)
                    effects.dust(r - .5f, STREET, -1f, 8)
                    effects.sparks(b.centerX(), STREET + .3f, 16, Palette.ORANGE, 10f)
                    effects.shake(.45f)
                }
                b.phase == Boss.Phase.RECOVERY && prevBossAttack == Boss.Attack.CHARGE -> {
                    effects.sparks(b.centerX() + b.facing * b.w / 2, b.y + 1.5f, 10, Palette.ORANGE, 8f)
                    effects.shake(.2f)
                }
                b.phase == Boss.Phase.PHASE_TRANSITION -> {
                    effects.ring(b.centerX(), b.y + 2.4f, 5f, Palette.RED, flat = false, time = .7f)
                    effects.sparks(b.centerX(), b.y + 2.4f, 20, Palette.RED, 10f)
                    effects.shake(.35f)
                }
            }
            prevBossPhase = b.phase
            prevBossAttack = b.attack
        }
        if (b.phase == Boss.Phase.ATTACK && b.attack == Boss.Attack.CHARGE && MathUtils.randomBoolean(dt * 40f)) {
            effects.dust(b.centerX() - b.facing * b.w * .4f, STREET, b.facing.toFloat(), 2)
        }
        if (b.phase == Boss.Phase.PHASE_TRANSITION && MathUtils.randomBoolean(dt * 20f)) {
            effects.sparks(b.x + MathUtils.random(b.w), b.y + MathUtils.random(1f, 4f), 3, Palette.RED, 6f)
        }
        if (b.phase == Boss.Phase.RECOVERY || b.phase == Boss.Phase.SLEEP) {
            steam -= dt
            if (steam <= 0f) {
                steam = if (b.phase == Boss.Phase.SLEEP) .6f else .15f
                effects.puffs(b.centerX() - b.facing * .8f, b.y + 3.6f, 1, Effects.STEAM, .3f, 1.8f, .25f)
            }
        }
        // Chain of blasts across the wreck.
        if (bossBlast > 0f) {
            val before = bossBlast
            bossBlast = max(0f, bossBlast - dt)
            if ((before * 6).toInt() != (bossBlast * 6).toInt()) {
                effects.explosion(b.x + MathUtils.random(-.5f, b.w + .5f), b.y + MathUtils.random(.5f, 3.5f), 1.2f)
            }
        }
    }

    // ------------------------------------------------------------------ actors

    private fun rigFor(art: ActorArt): Rig? = when {
        art === sprites.actors[SpriteNames.AN] -> Rigs.AN
        art === sprites.actors[SpriteNames.VY] -> Rigs.VY
        art === sprites.actors[SpriteNames.ROBOT_SHIELD] -> Rigs.ROBOT_SHIELD
        art === sprites.actors[SpriteNames.ROBOT_BLADE] -> Rigs.ROBOT_BLADE
        art === sprites.actors[SpriteNames.BOSS] -> Rigs.BOSS
        else -> null
    }

    /** Pose for the player from the model: walk cycle, jump tuck, sword/rifle arm, scarf. */
    private fun playerPoseFor(p: Player, time: Float): Pose {
        val pose = playerPose
        pose.reset(time)
        val an = p.type == CharacterType.AN
        val down = p.dead() || p.hp[p.slot()] <= 0
        when {
            down -> { pose.tuck = .5f; pose.raise[0] = 25f }
            p.hurtTime > 0 -> { pose.tuck = .25f; pose.raise[0] = 22f; pose.flutter = 1f }
            p.dashing() || p.dodging() -> {
                pose.stride = .9f; pose.phase = MathUtils.HALF_PI; pose.flutter = 1.4f
                pose.raise[0] = if (an) -12f else 4f
            }
            !p.grounded -> {
                pose.tuck = if (p.vy > 0) .55f else .3f
                pose.flutter = 1f
                pose.raise[0] = if (p.vy > 0) 8f else -4f
            }
            abs(p.vx) > .1f -> {
                // Phase follows distance travelled, so feet do not slide.
                pose.stride = (abs(p.vx) / GameConfig.SPEED).coerceAtMost(1f)
                pose.phase = if (previewId != null) time * GameConfig.SPEED * 2.3f else p.x * 2.3f
                pose.flutter = 1f
                pose.raise[0] = MathUtils.sin(pose.phase) * (if (an) 7f else 3f)
                pose.breath = abs(MathUtils.sin(pose.phase)) * .015f
            }
            else -> {
                pose.breath = MathUtils.sin(time * 3f) * .015f
                pose.raise[0] = MathUtils.sin(time * 2f) * 3f
            }
        }
        if (an && p.striking() && !down) {
            // Raise the blade in startup, swing through on the active frames, settle in recovery.
            val i = (p.comboStep - 1).coerceIn(0, 2)
            val from = SWING_FROM[i]
            val to = SWING_TO[i]
            pose.raise[0] = when (p.strikePhase) {
                Player.StrikePhase.STARTUP -> from * (p.actionTime / GameConfig.COMBO_STARTUP[i]).coerceIn(0f, 1f)
                Player.StrikePhase.ACTIVE -> MathUtils.lerp(from, to, (p.actionTime / GameConfig.COMBO_ACTIVE[i]).coerceIn(0f, 1f))
                Player.StrikePhase.RECOVERY -> to * (1f - (p.actionTime / GameConfig.COMBO_RECOVERY[i]).coerceIn(0f, 1f))
            }
            pose.stride = .35f
            pose.phase = if (p.strikePhase == Player.StrikePhase.STARTUP) -MathUtils.HALF_PI else MathUtils.HALF_PI
        }
        if (!an && p.shotFlash < .12f && !down) pose.raise[0] += 10f * (1f - p.shotFlash / .12f)
        return pose
    }

    /** Pose for a robot or the boss from its AI state. */
    private fun enemyPoseFor(e: Enemy, time: Float): Pose {
        val pose = enemyPoses[e.id.coerceIn(0, enemyPoses.size - 1)]
        pose.reset(time)
        when (e) {
            is Boss -> when {
                e.hp <= 0 -> { pose.raise[0] = 30f; pose.raise[1] = 25f; pose.tuck = .3f }
                e.phase == Boss.Phase.SLEEP -> { pose.raise[0] = -12f; pose.breath = MathUtils.sin(time * 1.5f) * .01f }
                e.phase == Boss.Phase.INTRO -> {
                    val t = 1f - e.timer / GameConfig.BOSS_INTRO
                    pose.raise[0] = -12f + 30f * t; pose.raise[1] = 15f * t
                }
                e.phase == Boss.Phase.TELEGRAPH && e.attack == Boss.Attack.SLAM -> {
                    val t = 1f - e.timer / GameConfig.SLAM_TELEGRAPH
                    pose.raise[0] = 50f * t; pose.raise[1] = 25f * t
                }
                e.phase == Boss.Phase.ATTACK && e.attack == Boss.Attack.SLAM -> { pose.raise[0] = -30f; pose.raise[1] = -15f }
                e.phase == Boss.Phase.TELEGRAPH -> {
                    val t = 1f - e.timer / GameConfig.CHARGE_TELEGRAPH
                    pose.raise[0] = -15f * t; pose.raise[1] = 20f * t
                    pose.stride = .5f * t; pose.phase = -MathUtils.HALF_PI
                }
                e.phase == Boss.Phase.ATTACK -> {
                    pose.stride = 1f; pose.phase = time * 16f
                    pose.raise[0] = 8f + MathUtils.sin(time * 16f) * 6f; pose.raise[1] = -10f
                }
                e.phase == Boss.Phase.RECOVERY -> { pose.raise[0] = -18f; pose.raise[1] = -8f; pose.breath = MathUtils.sin(time * 4f) * .015f }
                e.phase == Boss.Phase.PHASE_TRANSITION -> { pose.raise[0] = MathUtils.sin(time * 20f) * 18f; pose.raise[1] = 25f }
                else -> { pose.breath = MathUtils.sin(time * 2f) * .012f; pose.raise[0] = MathUtils.sin(time * 1.5f) * 5f }
            }
            is PatrolRobot -> when {
                e.hp <= 0 -> { pose.raise[0] = 30f; pose.raise[1] = 20f; pose.tuck = .4f }
                e.state == Enemy.State.HURT -> { pose.raise[0] = 25f; pose.raise[1] = 15f; pose.tuck = .2f }
                e.mode == PatrolRobot.Mode.WINDUP -> {
                    val t = 1f - e.windup / GameConfig.ROBOT_WINDUP
                    pose.raise[0] = 40f * t; pose.raise[1] = 25f * t
                    pose.stride = .4f * t; pose.phase = -MathUtils.HALF_PI
                }
                e.mode == PatrolRobot.Mode.ATTACK -> {
                    pose.raise[0] = -30f; pose.raise[1] = -20f
                    pose.stride = .6f; pose.phase = MathUtils.HALF_PI
                }
                e.mode == PatrolRobot.Mode.RECOVERY -> {
                    val t = e.timer / GameConfig.ROBOT_RECOVERY
                    pose.raise[0] = -20f * t; pose.raise[1] = -10f * t; pose.breath = -.01f
                }
                e.state == Enemy.State.MOVE -> {
                    pose.stride = 1f; pose.phase = e.x * 3f
                    pose.raise[0] = MathUtils.sin(pose.phase) * 6f; pose.raise[1] = -MathUtils.sin(pose.phase) * 4f
                }
                else -> { pose.breath = MathUtils.sin(time * 2.5f + e.id) * .012f; pose.raise[0] = MathUtils.sin(time * 2f + e.id) * 3f }
            }
            is TrainingDummy -> if (e.state == Enemy.State.HURT) { pose.raise[0] = 20f; pose.raise[1] = 12f; pose.tuck = .15f }
            else pose.breath = MathUtils.sin(time * 2f) * .01f
            else -> Unit
        }
        return pose
    }

    private fun art(e: Enemy): ActorArt = when (e) {
        is TrainingDummy -> sprites.actors.getValue(SpriteNames.ROBOT_SHIELD)
        is Boss -> sprites.actors.getValue(SpriteNames.BOSS)
        // Alternate the two robot designs from the mock-up along the street.
        else -> sprites.actors.getValue(if (e.id % 2 == 0) SpriteNames.ROBOT_SHIELD else SpriteNames.ROBOT_BLADE)
    }

    private fun drawPlayer(p: Player, time: Float) {
        characterFrame = sprites.characters.frame(previewId ?: animator.id, if (previewId != null) previewIndex else animator.frame)
        val real = characterFrame
        if (real != null) {
            // Independent frames are already posed: no rig, lean, stretch or landing squash.
            poseX = p.centerX(); poseY = p.y; poseFacing = p.facing.toFloat()
            poseRot = 0f; poseScaleY = 1f
            poseArt = if (p.activeAlive()) sprites.actors.getValue(
                if (p.type == CharacterType.AN) SpriteNames.AN else SpriteNames.VY) else null
            val flicker = p.invulnerable > 0 && p.activeAlive() && (time * 18).toInt() % 2 == 0
            batch.setColor(1f, 1f, 1f, if (flicker) .45f else 1f)
            real.draw(batch, p.centerX(), p.y, p.facing)
            batch.setColor(1f, 1f, 1f, 1f)
            return
        }
        val art = sprites.actors.getValue(if (p.type == CharacterType.AN) SpriteNames.AN else SpriteNames.VY)
        activeRig = rigFor(art)
        activePose = playerPoseFor(p, time)
        val f = p.facing.toFloat()
        var x = p.centerX()
        var y = p.y
        var rotation = 0f
        var scaleY = 1f
        var r = 1f
        var g = 1f
        var b = 1f
        val down = p.dead() || p.hp[p.slot()] <= 0
        when {
            down -> { rotation = 90f * f; r = .55f; g = .55f; b = .65f }
            p.hurtTime > 0 -> {
                // Knocked back: tilt away, red flash, jitter.
                rotation = 12f * f
                x += sin(time * 90f) * .05f
                g = .45f; b = .45f
            }
            p.dashing() -> { rotation = -12f * f; scaleY = .92f; y += .05f }
            p.dodging() -> { rotation = -9f * f; scaleY = .88f }
            p.striking() -> {
                strikePose(p, f)
                x += strikeDx; rotation = strikeRot * .5f; y += strikeDy
            }
            !p.grounded -> {
                // Stretch going up, tuck coming down.
                rotation = if (p.vy > 0) -6f * f else 5f * f
                scaleY = if (p.vy > 0) 1.06f else .98f
            }
            abs(p.vx) > .1f -> {
                // Body bobs twice per stride (each footfall); legs come from the rig.
                val phase = if (previewId != null) time * GameConfig.SPEED * 2.3f else p.x * 2.3f
                y += abs(sin(phase)) * .08f
                rotation = -4f * f
            }
            else -> scaleY = 1f + .015f * sin(time * 3f)
        }
        if (landSquash > 0f && !down) scaleY *= 1f - .16f * (landSquash / LAND_TIME)
        if (p.type == CharacterType.VY && p.shotFlash < .1f && !down) {
            val k = 1f - p.shotFlash / .1f
            x -= .14f * f * k
            rotation += 3f * f * k
        }
        poseX = x; poseY = y; poseRot = rotation; poseScaleY = scaleY; poseFacing = f; poseArt = if (down) null else art
        if (!down && (p.dashing() || p.dodging())) {
            val c = if (p.type == CharacterType.AN) 1f else .3f
            for (k in 3 downTo 1) {
                batch.setColor(c * .5f, 1f, 1f, .14f * (4 - k))
                actor(art, x - f * .45f * k, y, f, rotation, scaleY)
            }
        }
        val flicker = p.invulnerable > 0 && !down && (time * 18).toInt() % 2 == 0
        batch.setColor(r, g, b, if (flicker) .45f else 1f)
        actor(art, x, y, f, rotation, scaleY)
        if (p.hurtTime > 0 && !down) flash(art, x, y, f, rotation, scaleY, .5f, Palette.RED)
        if (swapFlash > 0f && !down) flash(art, x, y, f, rotation, scaleY, swapFlash * 3f, Color.WHITE)
        if ((p.swordActive() || p.dashing()) && p.type == CharacterType.AN && !down) flash(art, x, y, f, rotation, scaleY, .35f, CYAN_FLASH)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private var strikeDx = 0f
    private var strikeRot = 0f
    private var strikeDy = 0f

    /**
     * Strike pose into [strikeDx]/[strikeRot]/[strikeDy]: wind back in startup, snap forward on the
     * active frames (further for later combo steps; strike 3 hops), ease back during recovery.
     */
    private fun strikePose(p: Player, f: Float) {
        val i = (p.comboStep - 1).coerceIn(0, 2)
        val reach = .22f + i * .08f
        val lean = 9f + i * 4f
        strikeDy = 0f
        when (p.strikePhase) {
            Player.StrikePhase.STARTUP -> {
                val t = (p.actionTime / GameConfig.COMBO_STARTUP[i]).coerceIn(0f, 1f)
                strikeDx = -.1f * f * t; strikeRot = 6f * f * t
            }
            Player.StrikePhase.ACTIVE -> {
                strikeDx = reach * f; strikeRot = -lean * f
                if (i == 2 && !p.airStrike) strikeDy = .18f
            }
            Player.StrikePhase.RECOVERY -> {
                val t = 1f - (p.actionTime / GameConfig.COMBO_RECOVERY[i]).coerceIn(0f, 1f)
                strikeDx = reach * f * t; strikeRot = -lean * f * t
            }
        }
    }

    private fun drawEnemy(e: Enemy, time: Float) {
        val f = e.facing.toFloat()
        if (e is Drone) {
            drawDrone(e, time)
            return
        }
        if (e is TrainingDummy) {
            if (!e.alive()) return
            // Practice robot: steady, tinted cyan so it reads as a target, not an enemy.
            val art = art(e)
            activeRig = rigFor(art)
            activePose = enemyPoseFor(e, time)
            val shake = if (e.state == Enemy.State.HURT) sin(time * 80f) * .06f else 0f
            batch.setColor(.55f, .95f, 1f, .85f)
            actor(art, e.centerX() + shake, e.y, -1f, if (e.state == Enemy.State.HURT) -5f else 0f, 1f)
            if (e.state == Enemy.State.HURT) flash(art, e.centerX() + shake, e.y, -1f, -5f, 1f, .6f, CYAN_FLASH)
            batch.setColor(1f, 1f, 1f, 1f)
            return
        }
        val art = art(e)
        activeRig = rigFor(art)
        activePose = enemyPoseFor(e, time)
        var x = e.centerX()
        var y = e.y
        var rotation = 0f
        var scaleY = 1f
        var r = 1f
        var g = 1f
        var b = 1f
        var alpha = 1f
        if (e is Boss) {
            when {
                e.hp <= 0 -> {
                    // Topples while blasts go off, then fades so the wreck does not block the station.
                    alpha = 1f - ((e.deathTime - 1.4f) / 1.2f).coerceIn(0f, 1f)
                    if (alpha <= 0f) return
                    rotation = -min(1f, e.deathTime * 1.2f) * 70f * f
                    y -= min(1f, e.deathTime) * .4f
                    x += sin(time * 50f) * .06f * max(0f, 1f - e.deathTime)
                    r = .5f; g = .5f; b = .55f
                }
                e.phase == Boss.Phase.SLEEP -> { r = .45f; g = .45f; b = .55f; scaleY = .96f + .01f * sin(time * 1.5f) }
                e.phase == Boss.Phase.INTRO -> {
                    // Powers up: rises from a crouch.
                    val t = 1f - e.timer / GameConfig.BOSS_INTRO
                    scaleY = .9f + .1f * t
                    x += sin(time * 40f) * .04f * (1f - t)
                }
                e.phase == Boss.Phase.TELEGRAPH && e.attack == Boss.Attack.SLAM -> {
                    val t = 1f - e.timer / GameConfig.SLAM_TELEGRAPH
                    y += .4f * t
                    rotation = 6f * f * t
                    x += sin(time * 60f) * .05f * t
                    g = 1f - .35f * t; b = 1f - .35f * t
                }
                e.phase == Boss.Phase.TELEGRAPH -> {
                    val t = 1f - e.timer / GameConfig.CHARGE_TELEGRAPH
                    rotation = 9f * f * t
                    x -= .3f * f * t
                    scaleY = 1f - .06f * t
                    g = 1f - .35f * t; b = 1f - .35f * t
                }
                e.phase == Boss.Phase.ATTACK && e.attack == Boss.Attack.SLAM -> { y -= .15f; scaleY = .9f; rotation = -7f * f }
                e.phase == Boss.Phase.ATTACK -> { rotation = -13f * f; y += abs(sin(time * 30f)) * .1f }
                e.phase == Boss.Phase.RECOVERY -> { r = .8f; g = .8f; b = .85f; scaleY = .95f + .01f * sin(time * 4f); rotation = -2f * f }
                e.phase == Boss.Phase.PHASE_TRANSITION -> {
                    x += sin(time * 70f) * .08f
                    scaleY = 1f + .03f * sin(time * 20f)
                    g = .45f + .25f * sin(time * 12f); b = .35f
                }
                else -> scaleY = 1f + .012f * sin(time * 2f)
            }
            if (e.enraged() && e.hp > 0) { g *= .75f; b *= .6f }
            if (e.phase == Boss.Phase.ATTACK && e.attack == Boss.Attack.CHARGE && e.hp > 0) {
                for (k in 3 downTo 1) {
                    batch.setColor(1f, .3f, .4f, .12f * (4 - k))
                    actor(art, x - f * .7f * k, y, f, rotation, scaleY)
                }
            }
        } else if (e is PatrolRobot) {
            when {
                e.hp <= 0 -> {
                    if (e.deathTime > 1.2f) return
                    rotation = -min(1f, e.deathTime * 2.5f) * 90f * f
                    alpha = 1f - e.deathTime / 1.2f
                    r = .6f; g = .6f; b = .65f
                }
                e.state == Enemy.State.HURT -> { rotation = 10f * f; x += sin(time * 80f) * .06f }
                e.mode == PatrolRobot.Mode.WINDUP -> {
                    // Leans back and shakes before striking.
                    val t = 1f - e.windup / GameConfig.ROBOT_WINDUP
                    rotation = 9f * f * t
                    x -= .12f * f * t
                    x += sin(time * 60f) * .03f * t
                    g = 1f - .4f * t; b = 1f - .4f * t
                }
                e.mode == PatrolRobot.Mode.ATTACK -> { x += .35f * f; rotation = -12f * f }
                e.mode == PatrolRobot.Mode.RECOVERY -> { scaleY = .97f; rotation = -3f * f }
                e.state == Enemy.State.MOVE -> {
                    y += abs(sin(e.x * 3f)) * .06f
                    rotation = -3f * f
                }
                else -> scaleY = 1f + .012f * sin(time * 2.5f + e.id)
            }
        }
        batch.setColor(r, g, b, alpha)
        actor(art, x, y, f, rotation, scaleY)
        if (e.id < enemyDrawn.size) {
            enemyDrawn[e.id] = e.hp > 0
            enemyPoseX[e.id] = x; enemyPoseY[e.id] = y; enemyPoseRot[e.id] = rotation; enemyPoseScale[e.id] = scaleY
        }
        if (e.hurtTime > 0f && e.hp > 0) flash(art, x, y, f, rotation, scaleY, .65f, Color.WHITE)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawDrone(e: Drone, time: Float) {
        if (e.hp <= 0 && e.deathTime > 1f) return
        val f = e.facing.toFloat()
        val name = if (e.state == Enemy.State.ATTACK) SpriteNames.DRONE_FIRE else SpriteNames.DRONE_FLY
        val region = sprites.frame(name, time + e.id, 16f)
        val w = 1.6f
        val h = w * region.regionHeight / region.regionWidth
        val i = indexOf(e)
        // Banks into its movement; spins as it falls when destroyed.
        val vx = if (i >= 0) (e.x - prevEnemyX[i]) * 60f else 0f
        var rotation = (-vx * 6f).coerceIn(-14f, 14f) + sin(time * 5f + e.id) * 2f
        var y = e.y
        if (e.hp <= 0) {
            y -= e.deathTime * e.deathTime * 6f
            rotation = e.deathTime * 540f
        }
        if (e.mode == Drone.Mode.AIM) rotation += sin(time * 50f) * 2f
        batch.setColor(1f, 1f, 1f, if (e.hp <= 0) 1f - e.deathTime else 1f)
        batch.draw(region, e.centerX() - w / 2, y, w / 2, h / 2, w, h, if (f >= 0) 1f else -1f, 1f, rotation)
        if (e.state == Enemy.State.HURT && e.hp > 0) {
            additive {
                batch.setColor(1f, 1f, 1f, .6f)
                batch.draw(region, e.centerX() - w / 2, y, w / 2, h / 2, w, h, if (f >= 0) 1f else -1f, 1f, rotation)
            }
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun indexOf(e: Enemy): Int = if (e.id < prevEnemyX.size) e.id else -1

    /**
     * Draws a painted actor with its body centre at [cx] and feet at [y], mirrored when [facing]
     * points away from the way the art faces. Rotation pivots on the feet.
     */
    private fun actor(art: ActorArt, cx: Float, y: Float, facing: Float, rotation: Float, scaleY: Float) {
        val rig = activeRig
        if (rig != null) {
            rigs.draw(batch, art, rig, activePose, cx, y, facing, rotation, scaleY)
            return
        }
        val w = art.width
        val h = art.height
        val flip = (facing >= 0) != art.facesRight
        val ox = art.anchorX * w
        batch.draw(art.region, cx - ox, y, ox, 0f, w, h, if (flip) -1f else 1f, scaleY, rotation)
    }

    /**
     * World position of a point given as fractions of [art] (fx from the left of the image, fy from
     * the feet) under the same transform [actor] uses. Result in [outX]/[outY].
     */
    private fun artPoint(art: ActorArt, cx: Float, y: Float, facing: Float, rotation: Float, scaleY: Float, fx: Float, fy: Float) {
        val rig = activeRig
        if (rig != null) {
            rigs.point(art, rig, activePose, cx, y, facing, rotation, scaleY, fx, fy)
            outX = rigs.outX
            outY = rigs.outY
            return
        }
        val flip = (facing >= 0) != art.facesRight
        val lx = (fx - art.anchorX) * art.width * (if (flip) -1f else 1f)
        val ly = fy * art.height * scaleY
        val c = MathUtils.cosDeg(rotation)
        val s = MathUtils.sinDeg(rotation)
        outX = cx + lx * c - ly * s
        outY = y + lx * s + ly * c
    }

    private fun playerEffectPoint(art: ActorArt, socket: String, fx: Float, fy: Float): Boolean {
        val frame = characterFrame
        if (frame != null) {
            outX = frame.socketX(socket, poseX, poseFacing.toInt()) ?: return false
            outY = frame.socketY(socket, poseY) ?: return false
        } else artPoint(art, poseX, poseY, poseFacing, poseRot, poseScaleY, fx, fy)
        return true
    }

    private var outX = 0f
    private var outY = 0f

    /** Same actor drawn again with additive blending: a quick bright flash for hits and slashes. */
    private fun flash(art: ActorArt, cx: Float, y: Float, facing: Float, rotation: Float, scaleY: Float, alpha: Float, color: Color) {
        additive {
            batch.setColor(color.r, color.g, color.b, alpha.coerceIn(0f, 1f))
            actor(art, cx, y, facing, rotation, scaleY)
        }
    }

    private inline fun additive(body: () -> Unit) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        body()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    // ------------------------------------------------------------------ glow effects

    /** Behind the sprites: robot eyes charging, drone lock-on, boss heat, An's blade aura. */
    private fun drawUnderGlow(world: World) {
        val t = world.time
        for (e in world.enemies) {
            if (!e.alive()) continue
            if (e is Drone && e.mode == Drone.Mode.AIM) {
                softGlow(e.centerX(), e.centerY(), .6f, Palette.RED, .35f + .2f * sin(t * 30f))
            }
            if (e is Boss && (e.telegraphing() || e.phase == Boss.Phase.PHASE_TRANSITION)) {
                val pulse = .5f + .5f * sin(t * 18f)
                softGlow(e.centerX(), e.y + 2f, 2.4f + .3f * pulse, if (e.enraged()) Palette.ORANGE else Palette.RED, .12f + .1f * pulse)
            }
        }
    }

    /** Radial glow: stacked translucent discs, bright in the middle and fading out (additive pass). */
    private fun softGlow(x: Float, y: Float, radius: Float, c: Color, alpha: Float) {
        for (k in 0 until 4) {
            shapes.color = tint(c, alpha * .28f)
            shapes.circle(x, y, radius * (1f - k * .22f), 20)
        }
    }

    /** Eye glints over the sprites: faint while patrolling, blazing during wind-ups and boss attacks. */
    private fun drawEyes(world: World) {
        val t = world.time
        for (e in world.enemies) {
            if (e.id >= enemyDrawn.size || !enemyDrawn[e.id]) continue
            val art = art(e)
            val (fx, fy) = when {
                e is Boss -> BOSS_EYE
                e is PatrolRobot && e.id % 2 == 0 -> SHIELD_EYE
                e is PatrolRobot -> BLADE_EYE
                else -> continue
            }
            activeRig = rigFor(art)
            activePose = enemyPoses[e.id.coerceIn(0, enemyPoses.size - 1)]
            artPoint(art, enemyPoseX[e.id], enemyPoseY[e.id], e.facing.toFloat(), enemyPoseRot[e.id], enemyPoseScale[e.id], fx, fy)
            val k = when {
                e is PatrolRobot && e.mode == PatrolRobot.Mode.WINDUP -> 1f - e.windup / GameConfig.ROBOT_WINDUP
                e is PatrolRobot && e.mode == PatrolRobot.Mode.ATTACK -> 1f
                e is Boss && (e.telegraphing() || e.striking() || e.phase == Boss.Phase.PHASE_TRANSITION) -> .8f + .2f * sin(t * 25f)
                e is Boss && e.phase == Boss.Phase.SLEEP -> 0f
                else -> 0f
            }
            val c = if (e is Boss && e.enraged()) Palette.ORANGE else Palette.RED
            val size = if (e is Boss) .22f + .3f * k else .1f + .2f * k
            softGlow(outX, outY, size, c, .15f + .45f * k)
            shapes.color = tint(Color.WHITE, .15f + .55f * k)
            shapes.circle(outX, outY, .03f + .04f * k, 8)
            if (k > .6f) {
                // Thin lens-flare streak.
                shapes.color = tint(c, .35f * k)
                shapes.rectLine(outX - size * 2f, outY, outX + size * 2f, outY, .025f)
            }
        }
    }

    /**
     * Electric arcs crawling along An's blade, placed with the same transform as the sprite. Idle
     * crackle, stronger during wind-up, a full storm on the active frames and the dash slash.
     */
    private fun drawLightning(world: World) {
        val p = world.player
        val art = poseArt ?: return
        if (p.type != CharacterType.AN) return
        val intensity = when {
            p.swordActive() || p.dashing() -> 1f
            p.striking() -> .65f
            else -> .3f
        }
        activeRig = rigFor(art)
        activePose = playerPose
        if (!playerEffectPoint(art, "hand", BLADE_HAND_X, BLADE_HAND_Y)) return
        val ax = outX
        val ay = outY
        if (!playerEffectPoint(art, "tip", BLADE_TIP_X, BLADE_TIP_Y)) return
        val bx = outX
        val by = outY
        val dx = bx - ax
        val dy = by - ay
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
        val nx = -dy / len
        val ny = dx / len
        val seed = (world.time * 22f).toInt()
        // Soft aura along the blade.
        shapes.color = tint(Palette.CYAN, .18f + .22f * intensity)
        shapes.rectLine(ax, ay, bx, by, .22f + .18f * intensity)
        val bolts = if (intensity >= 1f) 4 else if (intensity > .5f) 3 else 2
        for (k in 0 until bolts) {
            // Idle crackle flickers on and off.
            if (intensity < .5f && hash(seed, k, 99) < .45f) continue
            val t0 = hash(seed, k, 1) * .6f
            val t1 = (t0 + .25f + hash(seed, k, 2) * .5f).coerceAtMost(1.05f)
            var px = ax + dx * t0
            var py = ay + dy * t0
            val segments = 6
            for (j in 1..segments) {
                val tt = t0 + (t1 - t0) * j / segments
                val amp = (.12f + .22f * intensity) * (if (j == segments) .3f else 1f)
                val off = (hash(seed, k, j + 3) - .5f) * 2f * amp
                val qx = ax + dx * tt + nx * off
                val qy = ay + dy * tt + ny * off
                shapes.color = tint(Palette.CYAN, .55f + .35f * intensity)
                shapes.rectLine(px, py, qx, qy, .07f + .05f * intensity)
                shapes.color = tint(Color.WHITE, .8f)
                shapes.rectLine(px, py, qx, qy, .025f + .015f * intensity)
                px = qx
                py = qy
            }
        }
        // Branches leaping off the tip when the blade is live.
        if (intensity >= .65f) {
            for (k in 0 until 2) {
                var px = bx
                var py = by
                val ang = (hash(seed, k, 50) - .5f) * 2.4f + MathUtils.atan2(dy, dx)
                for (j in 1..3) {
                    val step = .25f + hash(seed, k, 60 + j) * .2f
                    val a = ang + (hash(seed, k, 70 + j) - .5f) * 1.2f
                    val qx = px + MathUtils.cos(a) * step
                    val qy = py + MathUtils.sin(a) * step
                    shapes.color = tint(Palette.CYAN, .7f)
                    shapes.rectLine(px, py, qx, qy, .05f)
                    px = qx
                    py = qy
                }
            }
        }
    }

    /** Crescent slash for each combo step (different arc per step) and the dash slash streak. */
    private fun drawSlash(world: World) {
        val p = world.player
        if (p.type != CharacterType.AN || poseArt == null) return
        val f = p.attackDir.toFloat()
        val cx = p.centerX() + f * .3f
        val cy = p.y + 1.1f
        if (p.dashing()) {
            val k = (p.actionTime / GameConfig.DASH_TIME).coerceIn(0f, 1f)
            for (i in 0 until 5) {
                val yy = cy - .5f + i * .25f
                shapes.color = tint(if (i == 2) Color.WHITE else Palette.CYAN, (1f - k) * (.5f - abs(i - 2) * .1f))
                shapes.rectLine(cx - f * (2.4f + i % 2 * .5f), yy, cx + f * 1.2f, yy, if (i == 2) .12f else .05f)
            }
            return
        }
        if (!p.striking() || p.strikePhase == Player.StrikePhase.STARTUP) return
        val i = (p.comboStep - 1).coerceIn(0, 2)
        val fade = if (p.strikePhase == Player.StrikePhase.ACTIVE) 1f
        else 1f - (p.actionTime / (GameConfig.COMBO_RECOVERY[i] * .5f)).coerceIn(0f, 1f)
        if (fade <= 0f) return
        // Step 1: high to low; step 2: low to high; step 3: a wide overhead sweep.
        val start = SLASH_START[i]
        val sweep = SLASH_SWEEP[i]
        val radius = GameConfig.SWORD_RANGE * (1f + i * .12f)
        val thickness = .26f + i * .07f
        val segments = 14
        for (k in 0 until segments) {
            val a0 = start + sweep * k / segments
            val a1 = start + sweep * (k + 1) / segments
            val along = (k + 1f) / segments
            val ax = cx + f * MathUtils.cosDeg(a0) * radius
            val ay = cy + MathUtils.sinDeg(a0) * radius * .8f
            val bx = cx + f * MathUtils.cosDeg(a1) * radius
            val by = cy + MathUtils.sinDeg(a1) * radius * .8f
            shapes.color = tint(Palette.CYAN, fade * along * .55f)
            shapes.rectLine(ax, ay, bx, by, thickness * along)
            shapes.color = tint(Color.WHITE, fade * along * .8f)
            shapes.rectLine(ax, ay, bx, by, thickness * along * .35f)
        }
    }

    private fun drawShots(world: World) {
        val p = world.player
        val vy = poseArt
        val muzzleVisible = if (vy != null && p.type == CharacterType.VY && p.shotFlash < .08f) {
            activeRig = rigFor(vy)
            activePose = playerPose
            playerEffectPoint(vy, "muzzle", MUZZLE_X, MUZZLE_Y)
        } else false
        if (muzzleVisible) {
            // A missing optional muzzle socket hides only the flash, never the flying shots.
            val k = 1f - p.shotFlash / .08f
            shapes.color = tint(Palette.PINK, .5f * k)
            shapes.circle(outX, outY, .5f * k + .1f, 16)
            shapes.color = tint(Color.WHITE, .9f * k)
            shapes.circle(outX, outY, .18f, 12)
            shapes.rectLine(outX, outY, outX + poseFacing * .7f * k, outY, .08f)
        }
        for (e in world.enemies) {
            if (e !is Drone || e.mode != Drone.Mode.AIM || !e.alive()) continue
            // Locked aim line: where the shot will go.
            val pulse = .45f + .35f * sin(world.time * 30f)
            shapes.color = tint(Palette.RED, pulse)
            shapes.rectLine(e.centerX(), e.centerY(), e.aimX, e.aimY, .05f)
            shapes.circle(e.aimX, e.aimY, .22f, 12)
        }
        for (shot in world.projectiles) {
            if (!shot.active) continue
            val cy = shot.y + shot.h / 2
            val head = shot.x + shot.w / 2
            if (!shot.friendly) {
                // Enemy plasma ball.
                val pulse = .8f + .2f * sin(world.time * 40f)
                shapes.color = tint(Palette.ORANGE, .35f)
                shapes.circle(head, cy, .3f * pulse, 14)
                shapes.color = tint(Color.WHITE, .9f)
                shapes.circle(head, cy, .1f, 10)
                continue
            }
            val color = if (shot.piercing) Palette.CYAN else Palette.PINK
            val length = if (shot.piercing) 2.2f else 1.2f
            val width = if (shot.piercing) .42f else .3f
            val dir = if (shot.vx >= 0) 1f else -1f
            shapes.color = tint(color, .3f)
            shapes.rectLine(head - dir * length, cy, head, cy, width)
            shapes.color = tint(color, .9f)
            shapes.rectLine(head - dir * length * .8f, cy, head, cy, width * .4f)
            shapes.color = Color.WHITE
            shapes.rectLine(head - dir * length * .4f, cy, head, cy, width * .15f)
            if (shot.piercing) {
                // Spiral rings along the beam.
                for (k in 0 until 3) {
                    val rx = head - dir * (length * (.2f + k * .3f) + (world.time * 20f % .3f))
                    shapes.color = tint(Palette.CYAN, .5f - k * .12f)
                    shapes.rectLine(rx, cy - .3f, rx + dir * .08f, cy + .3f, .06f)
                }
            }
        }
    }

    /** Cheap deterministic noise in 0..1 for the lightning (no allocation, stable within a tick). */
    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 73856093 xor b * 19349663 xor c * 83492791
        h = h xor (h ushr 13)
        h *= 0x5bd1e995
        h = h xor (h ushr 15)
        return (h and 0xFFFF) / 65535f
    }

    /** Colliders, live hitboxes, danger zones and AI states. Off by default (Settings or F3). */
    private fun drawDebug(world: World) {
        val p = world.player
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.GREEN
        shapes.rect(p.x, p.y, p.w, p.h)
        if (p.swordActive() || p.dashing()) {
            shapes.color = Color.YELLOW
            shapes.rect(p.swordX(), p.y, p.swordWidth(), GameConfig.SWORD_HEIGHT)
        }
        for (e in world.enemies) {
            if (!e.alive()) continue
            shapes.color = Color.RED
            shapes.rect(e.x, e.y, e.w, e.h)
            if (e is PatrolRobot && (e.mode == PatrolRobot.Mode.WINDUP || e.mode == PatrolRobot.Mode.ATTACK)) {
                shapes.color = Color.ORANGE
                shapes.rect(e.strikeX(), e.y, GameConfig.ROBOT_ATTACK_RANGE, e.h)
            }
        }
        val b = world.boss
        if (b.dangerWidth() > 0f) {
            shapes.color = Color.MAGENTA
            shapes.rect(b.dangerX(), STREET, b.dangerWidth(), if (b.attack == Boss.Attack.SLAM) GameConfig.SLAM_HEIGHT else b.h)
        }
        shapes.color = Color.CYAN
        for (s in world.projectiles) if (s.active) shapes.rect(s.x, s.y, s.w, s.h)
        shapes.color = Color.WHITE
        shapes.rect(p.safeX, p.safeY, .2f, .2f)
        shapes.end()
        batch.begin()
        for (e in world.enemies) {
            if (!e.alive() || e is TrainingDummy) continue
            val label = when (e) {
                is Boss -> e.phase.name + if (e.attack != Boss.Attack.NONE) ":" + e.attack.name else ""
                is PatrolRobot -> e.mode.name
                is Drone -> e.mode.name
                else -> e.state.name
            }
            ui.text(label, e.x, e.y + e.h + .9f, .045f, Color.YELLOW)
        }
        ui.text(p.action.name + (if (p.striking()) " " + p.comboStep + p.strikePhase.name.take(1) else ""), p.x, p.y + p.h + 1.2f, .045f, Color.GREEN)
        batch.end()
    }

    // ------------------------------------------------------------------ scenery

    /** Draws [texture] over x..x+w scrolled by [scroll] world units; one repeat spans [span] units. */
    private fun layer(texture: Texture, x: Float, y: Float, w: Float, h: Float, scroll: Float, span: Float) {
        val u = scroll / span
        batch.draw(texture, x, y, w, h, u, 1f, u + w / span, 0f)
    }

    private fun drawGround(world: World, left: Float, bottom: Float) {
        for (platform in world.level.platforms) {
            if (platform.x > left + GameConfig.VIEW_WIDTH || platform.x + platform.w < left) continue
            val top = platform.y + platform.h
            if (platform.y == 0f) {
                // Bridge deck and underside from the mock-up; its walkway edge sits on the street line.
                val u0 = platform.x / GROUND_SPAN
                val u1 = (platform.x + platform.w) / GROUND_SPAN
                val v1 = (top - bottom) / GROUND_HEIGHT
                batch.draw(sprites.ground, platform.x, bottom, platform.w, top - bottom, u0, v1, u1, 0f)
            } else {
                val u1 = platform.w / (platform.h * 8f)
                batch.draw(sprites.ledge, platform.x, platform.y, platform.w, platform.h, 0f, 1f, u1, 0f)
            }
        }
    }

    private fun drawProps(world: World) {
        prop(SpriteNames.HAZARD, 47.25f, STREET, 1.1f)
        prop(SpriteNames.HAZARD, 69.25f, STREET, 1.1f)
        prop(if (world.healed) SpriteNames.REPAIR_OFF else SpriteNames.REPAIR_ON, 109f, STREET, 3.2f, centered = true)
        if (world.level.bossAwake && !world.level.bossDefeated) {
            batch.draw(sprites.region(SpriteNames.DOOR), 113f - .2f, STREET, 1.1f, 12f)
        }
        if (!world.level.bossDefeated) {
            batch.draw(sprites.region(SpriteNames.BARRIER), 143f - .25f, STREET, 1.5f, 12f)
        }
        prop(if (world.level.bossDefeated) SpriteNames.TOWER_ON else SpriteNames.TOWER_OFF, 155.5f, STREET, 9f, centered = true)
    }

    /** Draws an atlas prop [height] units tall with its bottom at [y]; [x] is its left edge or centre. */
    private fun prop(name: String, x: Float, y: Float, height: Float, centered: Boolean = false) {
        val r = sprites.region(name)
        val w = height * r.regionWidth / r.regionHeight
        batch.draw(r, if (centered) x - w / 2 else x, y, w, height)
    }

    /**
     * Boss warning in the mock-up style: a flashing red floor zone with hazard stripes. It spans
     * exactly [Boss.dangerX]..[Boss.dangerWidth], the area that deals damage.
     */
    private fun drawTelegraph(boss: Boss, time: Float) {
        val x = boss.dangerX()
        val w = boss.dangerWidth()
        val striking = boss.striking()
        val pulse = .5f + .5f * sin(time * 14f)
        rect(x, STREET, w, if (striking) 1.4f else .9f, tint(Palette.RED, if (striking) .55f else .26f + .16f * pulse))
        rect(x, STREET, w, .07f, Palette.RED)
        var sx = x
        while (sx < x + w - .1f) {
            val sw = minOf(.28f, x + w - sx)
            shapes.color = tint(if (striking) Palette.ORANGE else Palette.RED, .75f + .25f * pulse)
            shapes.triangle(sx, STREET + .1f, sx + sw, STREET + .1f, sx + sw + .15f, STREET + .3f)
            shapes.triangle(sx, STREET + .1f, sx + sw + .15f, STREET + .3f, sx + .15f, STREET + .3f)
            sx += .55f
        }
    }

    /** Warning sign drawn after the sprites so the boss standing in the zone cannot hide it. */
    private fun drawWarningSign(boss: Boss) {
        if (!boss.telegraphing()) return
        val cx = boss.dangerX() + boss.dangerWidth() / 2
        val cy = STREET + .75f
        shapes.color = tint(Palette.RED, .95f)
        shapes.triangle(cx - .5f, cy - .4f, cx + .5f, cy - .4f, cx, cy + .45f)
        shapes.color = tint(Palette.BACK, .9f)
        shapes.triangle(cx - .36f, cy - .32f, cx + .36f, cy - .32f, cx, cy + .3f)
        rect(cx - .045f, cy - .1f, .09f, .28f, Color.WHITE)
        rect(cx - .045f, cy - .24f, .09f, .08f, Color.WHITE)
    }

    /** With touch buttons, lift the street to [ButtonLayout.DECK_TOP] so the buttons sit below the action. */
    private fun cameraY(): Float {
        val half = GameConfig.VIEW_HEIGHT / 2
        if (!game.save.showControls()) return half
        return STREET + half - ButtonLayout.DECK_TOP / ButtonLayout.HEIGHT * GameConfig.VIEW_HEIGHT
    }

    private fun tint(color: Color, alpha: Float): Color = scratch.set(color.r, color.g, color.b, alpha.coerceIn(0f, 1f))

    private fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        shapes.color = color
        shapes.rect(x, y, w, h)
    }

    override fun dispose() = sprites.dispose()

    private companion object {
        const val STREET = 2f
        val ARENA_CENTER = (com.manroid.neonrebels.model.Level.ARENA_LEFT + com.manroid.neonrebels.model.Level.ARENA_RIGHT) / 2
        const val FAR_HEIGHT = 12f
        /** One repeat of ground.png covers this many world units (texture is 2:1). */
        const val GROUND_HEIGHT = 7f
        const val GROUND_SPAN = 14f
        const val LAND_TIME = .12f
        /** Blade raise (degrees) at the start and end of each strike's swing. */
        val SWING_FROM = floatArrayOf(38f, -28f, 62f)
        val SWING_TO = floatArrayOf(-24f, 30f, -38f)
        val SLASH_START = floatArrayOf(75f, -55f, 115f)
        val SLASH_SWEEP = floatArrayOf(-130f, 120f, -175f)
        // Measured on an_stance / vy_stance (fractions of width from the left, of height from the feet).
        const val BLADE_HAND_X = .54f
        const val BLADE_HAND_Y = .49f
        const val BLADE_TIP_X = .985f
        const val BLADE_TIP_Y = .255f
        const val MUZZLE_X = .98f
        const val MUZZLE_Y = .70f
        val SHIELD_EYE = .40f to .83f
        val BLADE_EYE = .22f to .76f
        val BOSS_EYE = .78f to .63f
        val PIT_LOW = Color(.02f, .02f, .06f, 1f)
        val PIT_HIGH = Color(.06f, .08f, .2f, 1f)
        val CYAN_FLASH = Color(.4f, 1f, 1f, 1f)
    }
}
