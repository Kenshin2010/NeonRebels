package com.manroid.neonrebels.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.manroid.neonrebels.MainGame
import com.manroid.neonrebels.input.ButtonLayout
import com.manroid.neonrebels.input.InputController
import com.manroid.neonrebels.model.Boss
import com.manroid.neonrebels.model.CharacterType
import com.manroid.neonrebels.model.GameConfig
import com.manroid.neonrebels.model.Player
import com.manroid.neonrebels.model.CombatSession
import com.manroid.neonrebels.model.TutorialController
import com.manroid.neonrebels.model.World
import kotlin.math.ceil

/**
 * Battle HUD in the style of ui/02_UI_Chien_dau.png: active rebel's framed portrait with a
 * numbered health bar, the reserve below it, the boss bar with portrait, a square pause button
 * and the touch controls. Reads [World] only; owns its three small portrait textures.
 */
class GameHud(private val game: MainGame) : Disposable {
    val viewport: FitViewport = SafeViewport(1280f, 720f)
    private val ui = UiPainter(game)
    private val neon = NeonPainter(game)
    private val faces = arrayOf(load("ui/hud_an.png"), load("ui/hud_vy.png"))
    private val bossFace = load("ui/hud_boss.png")
    private val text = StringBuilder(32)

    private fun load(path: String) = Texture(Gdx.files.internal(path)).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    /**
     * Draws the in-run HUD from [session] (never a copy of gameplay state). Touch controls show only
     * while PLAYING; [showPause] is false while a story dialogue owns the top-right corner.
     */
    fun draw(session: CombatSession, input: InputController, time: Float, showPause: Boolean = true) {
        val world = session.world
        viewport.apply()
        val p = world.player
        val active = p.slot()
        val reserve = 1 - active
        val bossVisible = world.boss.engaged() && world.level.bossAwake && !world.level.bossDefeated
        val playing = session.playing
        val controls = game.save.showControls() && playing
        val step = if (playing && session.tutorial.current != null && !session.tutorial.waiting) session.tutorial.current else null
        input.enabled[ButtonLayout.INTERACT] = playing && world.canInteract
        input.enabled[ButtonLayout.SKIP_TUTORIAL] = step != null
        game.shapes.projectionMatrix = viewport.camera.combined
        game.batch.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        game.shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Active rebel: portrait frame, name plate, numbered health bar.
        neon.hex(ACTIVE_X - 50, ACTIVE_Y - 50, 100f, 100f, neon.tint(Palette.BACK, .85f), hero(active), 3f)
        neon.slanted(132f, 664f, 112f, 32f, 12f, neon.tint(Palette.BACK, .9f), hero(active), 2f)
        bar(132f, 628f, 300f, 28f, p.hp[active], p.maxHp[active], hero(active))
        // Reserve rebel, smaller; a downed reserve gets a cross, not just a colour.
        neon.hex(RESERVE_X - 30, RESERVE_Y - 30, 60f, 60f, neon.tint(Palette.BACK, .85f), neon.tint(hero(reserve), .8f), 2f)
        neon.slanted(104f, 586f, 72f, 24f, 9f, neon.tint(Palette.BACK, .9f), neon.tint(hero(reserve), .8f), 2f)
        bar(104f, 560f, 160f, 16f, p.hp[reserve], p.maxHp[reserve], hero(reserve))
        // Components picked up this run (kept only on a win).
        neon.panel(1040f, 648f, 140f, 48f, 10f, neon.tint(Palette.BACK, .85f), neon.tint(Palette.GOLD, .8f), 2f)
        neon.gear(1066f, 672f, 13f, Palette.GOLD, Palette.BACK)
        if (bossVisible) {
            val rage = world.boss.enraged()
            neon.hex(BOSS_X - 36, BOSS_Y - 36, 72f, 72f, neon.tint(Palette.BACK, .85f), Palette.RED, 3f)
            neon.slanted(BOSS_X + 40, 664f, 330f, 32f, 12f, neon.tint(Palette.BACK, .9f), Palette.RED, 2f)
            neon.slanted(BOSS_X + 40, 634f, 440f, 22f, 10f, neon.tint(Palette.BACK, .85f), null)
            neon.slanted(BOSS_X + 40, 634f, 440f * clamp(world.boss.hp, world.boss.maxHp) / world.boss.maxHp, 22f, 10f,
                if (rage) Palette.ORANGE else Palette.RED, null)
            neon.slanted(BOSS_X + 40, 634f, 440f, 22f, 10f, null, neon.tint(Palette.RED, .8f), 2f)
        }
        if (session.noticeTime > 0) {
            neon.panel(440f, 496f, 400f, 40f, 10f, neon.tint(Palette.BACK, .85f), neon.tint(Palette.GREEN, .8f), 2f)
        }
        if (step != null) {
            // Tutorial hint: compact, above the street, with its own skip button.
            neon.panel(380f, 548f, 480f, 40f, 10f, neon.tint(Palette.BACK, .88f), Palette.GOLD, 2f)
            val b = ButtonLayout.SKIP_TUTORIAL
            neon.circle(ButtonLayout.CX[b], ButtonLayout.CY[b], ButtonLayout.R[b], neon.tint(Palette.BACK, .9f))
            ringOutline(ButtonLayout.CX[b], ButtonLayout.CY[b], ButtonLayout.R[b] - 2, 3f, Palette.GOLD)
            neon.icon(NeonPainter.ICON_CLOSE, ButtonLayout.CX[b], ButtonLayout.CY[b], 9f, Palette.GOLD)
        }
        // Pause is always available while playing, as a square button like the mock-up.
        if (showPause) {
            val pauseDown = input.pointers.held(ButtonLayout.PAUSE)
            neon.panel(1194f, 640f, 64f, 64f, 12f, neon.tint(if (pauseDown) Palette.GROUND else Palette.BACK, .85f), Palette.CYAN, 3f)
            neon.rect(1213f, 655f, 9f, 34f, Color.WHITE)
            neon.rect(1230f, 655f, 9f, 34f, Color.WHITE)
        }
        if (controls) {
            for (i in ButtonLayout.CX.indices) {
                if (i == ButtonLayout.PAUSE || i == ButtonLayout.SKIP_TUTORIAL) continue
                if (i == ButtonLayout.INTERACT && !world.canInteract) continue
                drawButton(i, p, input.pointers.held(i))
            }
            val target = tutorialButton(step)
            if (target >= 0) {
                val pulse = .5f + .5f * MathUtils.sin(time * 6f)
                ringOutline(ButtonLayout.CX[target], ButtonLayout.CY[target], ButtonLayout.R[target] + 8 + pulse * 5, 4f,
                    neon.tint(Palette.GOLD, .5f + .5f * pulse))
            }
        } else if (playing && world.canInteract) {
            drawButton(ButtonLayout.INTERACT, p, input.pointers.held(ButtonLayout.INTERACT))
        }
        game.shapes.end()

        game.batch.begin()
        game.batch.setColor(1f, 1f, 1f, 1f)
        game.batch.draw(faces[active], ACTIVE_X - 44, ACTIVE_Y - 44, 88f, 88f)
        if (p.hp[reserve] <= 0) game.batch.setColor(.4f, .4f, .45f, 1f)
        game.batch.draw(faces[reserve], RESERVE_X - 26, RESERVE_Y - 26, 52f, 52f)
        game.batch.setColor(1f, 1f, 1f, 1f)
        if (bossVisible) game.batch.draw(bossFace, BOSS_X - 31, BOSS_Y - 31, 62f, 62f)
        if (controls) {
            // The swap button shows who you would switch to.
            val b = ButtonLayout.SWAP
            if (readiness(b, p) < 1f) game.batch.setColor(.45f, .45f, .5f, 1f)
            val s = ButtonLayout.R[b] * 1.05f
            game.batch.draw(faces[reserve], ButtonLayout.CX[b] - s / 2, ButtonLayout.CY[b] - s * .32f, s, s)
            game.batch.setColor(1f, 1f, 1f, 1f)
        }
        game.batch.end()
        // The downed cross must sit on top of the portraits.
        if (p.hp[reserve] <= 0 || (controls && !p.reserveAlive())) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            game.shapes.begin(ShapeRenderer.ShapeType.Filled)
            if (p.hp[reserve] <= 0) neon.icon(NeonPainter.ICON_CLOSE, RESERVE_X, RESERVE_Y, 14f, Palette.RED)
            if (controls && !p.reserveAlive()) {
                val b = ButtonLayout.SWAP
                neon.icon(NeonPainter.ICON_CLOSE, ButtonLayout.CX[b], ButtonLayout.CY[b] + 4, 16f, Palette.RED)
            }
            game.shapes.end()
        }
        game.batch.begin()
        ui.label(ui.tr(if (active == 0) "hud.an" else "hud.vy"), 146f, 680f, 1.55f, Color.WHITE, align = -1)
        text.setLength(0)
        text.append(clamp(p.hp[active], p.maxHp[active])).append(" / ").append(p.maxHp[active])
        ui.label(text, 410f, 642f, 1.15f, Color.WHITE, align = 1)
        ui.label(ui.tr(if (reserve == 0) "hud.an" else "hud.vy"), 114f, 598f, 1.05f, Color.WHITE, align = -1)
        if (p.hp[reserve] <= 0) ui.label(ui.tr("hud.down"), 184f, 598f, .8f, Palette.RED, align = -1, slanted = false)
        text.setLength(0)
        text.append(world.collectedParts)
        ui.label(text, 1168f, 672f, 1.25f, Color.WHITE, align = 1, slanted = false)
        if (bossVisible) {
            ui.label(ui.tr(if (world.boss.enraged()) "hud.bossRage" else "hud.boss"), BOSS_X + 60, 680f, 1.35f, Color.WHITE, align = -1)
            val warning = when {
                world.boss.phase == Boss.Phase.PHASE_TRANSITION -> "hud.bossPhase"
                !world.boss.telegraphing() -> null
                world.boss.attack == Boss.Attack.SLAM -> "hud.warnSlam"
                else -> "hud.warnCharge"
            }
            if (warning != null) ui.label(ui.tr(warning), BOSS_X + 260, 612f, 1.05f, Palette.ORANGE, slanted = false)
        } else {
            val key = when (world.objective) {
                World.Objective.REACH_SIGNAL -> "hud.goalSignal"
                World.Objective.DEFEAT_BOSS -> "hud.goalBoss"
                World.Objective.ACTIVATE_STATION -> "hud.goalStation"
            }
            ui.label(ui.tr(key), 740f, 684f, 1.05f, Palette.CYAN, align = 0)
        }
        val notice = session.notice
        if (session.noticeTime > 0 && notice != null) ui.fitLabel(ui.tr(notice), 452f, 828f, 516f, 1f, Palette.GREEN, centered = true)
        if (step != null) ui.fitLabel(ui.tr("tut." + step.id), 400f, 840f, 568f, 1.1f, Palette.GOLD, centered = true)
        if (controls) {
            for (i in ButtonLayout.CX.indices) {
                if (i == ButtonLayout.INTERACT && !world.canInteract) continue
                drawLabel(i, p)
            }
        } else if (playing && world.canInteract) {
            drawLabel(ButtonLayout.INTERACT, p)
        }
        game.batch.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /** Portrait texture for An (0) or Vy (1), shared with the dialogue panel. */
    fun face(slot: Int): Texture = faces[slot]

    /** HUD button to highlight for a tutorial step (keyboard players read the hint text). */
    private fun tutorialButton(step: TutorialController.Step?): Int = when (step) {
        TutorialController.Step.MOVE -> ButtonLayout.RIGHT
        TutorialController.Step.JUMP -> ButtonLayout.JUMP
        TutorialController.Step.ATTACK -> ButtonLayout.ATTACK
        TutorialController.Step.DODGE -> ButtonLayout.DODGE
        TutorialController.Step.SKILL -> ButtonLayout.SKILL
        TutorialController.Step.SWAP -> ButtonLayout.SWAP
        null -> -1
    }

    private fun clamp(hp: Int, max: Int): Int = hp.coerceIn(0, max)

    private fun ringOutline(x: Float, y: Float, r: Float, width: Float, color: Color) = ring(x, y, r, width, 1f, color)

    /** Slanted health bar with a dark track; turns orange then red as health drops. */
    private fun bar(x: Float, y: Float, w: Float, h: Float, hp: Int, max: Int, accent: Color) {
        val fraction = if (max <= 0) 0f else hp.toFloat() / max
        val fill = when {
            fraction > .5f -> HEALTH
            fraction > .25f -> Palette.ORANGE
            else -> Palette.RED
        }
        neon.slanted(x, y, w, h, h * .45f, neon.tint(Palette.BACK, .85f), null)
        if (fraction > 0) {
            neon.slanted(x, y, w * fraction, h, h * .45f, fill, null)
            neon.slanted(x + 2, y + h * .58f, w * fraction - 4, h * .2f, h * .1f, neon.tint(Color.WHITE, .2f), null)
        }
        neon.slanted(x, y, w, h, h * .45f, null, accent, 2f)
    }

    private fun hero(slot: Int): Color = if (slot == 0) Palette.CYAN else Palette.PINK

    /** 0 while the action is unavailable, 1 when ready; drives the cooldown ring. */
    private fun readiness(i: Int, p: Player): Float = when (i) {
        ButtonLayout.SKILL -> 1f - p.skillCooldown[p.slot()] / p.skillCooldownMax(p.slot())
        ButtonLayout.DODGE -> 1f - p.dodgeCooldown / GameConfig.DODGE_COOLDOWN
        ButtonLayout.SWAP -> if (p.hp[1 - p.slot()] <= 0) 0f else 1f - p.switchCooldown / GameConfig.SWITCH_COOLDOWN
        else -> 1f
    }.coerceIn(0f, 1f)

    private fun drawButton(i: Int, p: Player, held: Boolean) {
        val x = ButtonLayout.CX[i]
        val y = ButtonLayout.CY[i]
        val r = ButtonLayout.R[i]
        val attack = i == ButtonLayout.ATTACK
        val color = when (i) {
            ButtonLayout.ATTACK -> Palette.ORANGE
            ButtonLayout.INTERACT -> Palette.GOLD
            ButtonLayout.SWAP -> hero(1 - p.slot())
            else -> Palette.CYAN
        }
        val ready = readiness(i, p)
        circle(x, y, r + 5, neon.tint(color, if (attack) .22f else .12f))
        circle(x, y, r, if (held) neon.tint(color, .35f) else neon.tint(Palette.BACK, .62f))
        ring(x, y, r - 2, if (attack) 5f else 4f, 1f, neon.tint(color, if (ready >= 1f) .35f else .2f))
        ring(x, y, r - 2, if (attack) 5f else 4f, ready, if (held) Color.WHITE else neon.tint(color, if (ready >= 1f) 1f else .8f))
        val icon = neon.tint(if (held) Color.WHITE else if (attack) ATTACK_ICON else Color.WHITE, if (ready >= 1f) .95f else .45f)
        val s = r * .36f
        val iy = if (i == ButtonLayout.LEFT || i == ButtonLayout.RIGHT) y else y + r * .16f
        when (i) {
            ButtonLayout.LEFT -> neon.arrowLeft(x + s * .1f, y, s * 1.25f, Color.WHITE)
            ButtonLayout.RIGHT -> neon.arrowRight(x - s * .1f, y, s * 1.25f, Color.WHITE)
            ButtonLayout.JUMP -> neon.chevronsUp(x, iy + s * .15f, s * 1.1f, icon)
            ButtonLayout.ATTACK -> if (p.type == CharacterType.AN) sword(x, iy, s, icon) else gun(x, iy, s, icon)
            ButtonLayout.DODGE -> runner(x, iy, s, icon)
            ButtonLayout.SKILL -> if (p.type == CharacterType.AN) bolt(x, iy, s, icon) else pierce(x, iy, s, icon)
            ButtonLayout.SWAP -> Unit // the reserve's portrait is drawn in the sprite pass
            ButtonLayout.INTERACT -> {
                neon.ring(x, iy, s * .9f, s * .22f, icon)
                neon.rect(x - s * .12f, iy - s * .1f, s * .24f, s * 1.1f, icon)
            }
        }
    }

    private fun drawLabel(i: Int, p: Player) {
        if (i == ButtonLayout.LEFT || i == ButtonLayout.RIGHT || i == ButtonLayout.PAUSE || i == ButtonLayout.SKIP_TUTORIAL) return
        val x = ButtonLayout.CX[i]
        val r = ButtonLayout.R[i]
        val ready = readiness(i, p) >= 1f
        val key = if (i == ButtonLayout.ATTACK && p.type == CharacterType.VY) "button.shoot" else LABEL_KEYS[i]
        val scale = if (i == ButtonLayout.ATTACK) 1.5f else if (r >= 50) .95f else .8f
        val color = if (i == ButtonLayout.ATTACK || i == ButtonLayout.INTERACT) ATTACK_LABEL else if (ready) Color.WHITE else Palette.SILVER
        ui.label(ui.tr(key), x, ButtonLayout.CY[i] - r * .52f, scale, color)
        if (i == ButtonLayout.SKILL && p.skillCooldown[p.slot()] > 0) {
            text.setLength(0)
            text.append(ceil(p.skillCooldown[p.slot()]).toInt()).append('s')
            ui.label(text, x, ButtonLayout.CY[i] + r * .64f, .95f, Color.WHITE, slanted = false)
        }
    }

    private fun circle(x: Float, y: Float, r: Float, color: Color) {
        game.shapes.color = color
        game.shapes.circle(x, y, r, SEGMENTS)
    }

    /** Thick arc from 12 o'clock, clockwise for [fraction] of the circle. */
    private fun ring(x: Float, y: Float, r: Float, width: Float, fraction: Float, color: Color) {
        val steps = (SEGMENTS * fraction).toInt()
        if (steps <= 0) return
        game.shapes.color = color
        var px = x
        var py = y + r
        for (k in 1..steps) {
            val a = MathUtils.PI2 * k / SEGMENTS
            val nx = x + MathUtils.sin(a) * r
            val ny = y + MathUtils.cos(a) * r
            game.shapes.rectLine(px, py, nx, ny, width)
            px = nx
            py = ny
        }
    }

    private fun sword(x: Float, y: Float, s: Float, c: Color) {
        neon.line(x - s * .9f, y - s * .9f, x + s * 1.1f, y + s * 1.1f, s * .34f, c)
        neon.line(x - s * .95f, y - s * .1f, x - s * .1f, y - s * .95f, s * .22f, c)
        neon.line(x - s * .9f, y - s * .9f, x - s * 1.3f, y - s * 1.3f, s * .28f, c)
    }

    private fun gun(x: Float, y: Float, s: Float, c: Color) {
        neon.rect(x - s * 1.1f, y - s * .05f, s * 1.7f, s * .55f, c)
        neon.rect(x + s * .6f, y + s * .12f, s * .65f, s * .25f, c)
        neon.rect(x - s * .9f, y - s * .85f, s * .48f, s * .85f, c)
    }

    private fun runner(x: Float, y: Float, s: Float, c: Color) {
        neon.circle(x + s * .35f, y + s * .85f, s * .22f, c)
        neon.line(x + s * .25f, y + s * .55f, x - s * .1f, y - s * .1f, s * .24f, c)
        neon.line(x - s * .1f, y - s * .1f, x + s * .45f, y - s * .7f, s * .2f, c)
        neon.line(x - s * .1f, y - s * .1f, x - s * .6f, y - s * .5f, s * .2f, c)
        neon.line(x + s * .2f, y + s * .4f, x + s * .7f, y + s * .15f, s * .18f, c)
        neon.line(x + s * .2f, y + s * .4f, x - s * .35f, y + s * .5f, s * .18f, c)
        for (k in 0 until 3) neon.rect(x - s * 1.25f, y + s * (.3f - k * .3f), s * (.55f - k * .1f), s * .1f, c)
    }

    private fun bolt(x: Float, y: Float, s: Float, c: Color) {
        game.shapes.color = c
        game.shapes.triangle(x + s * .3f, y + s * 1.1f, x - s * .6f, y - s * .1f, x + s * .1f, y - s * .1f)
        game.shapes.triangle(x - s * .3f, y - s * 1.1f, x + s * .6f, y + s * .1f, x - s * .1f, y + s * .1f)
    }

    private fun pierce(x: Float, y: Float, s: Float, c: Color) {
        neon.line(x - s * 1.1f, y - s * .5f, x + s * .7f, y + s * .5f, s * .26f, c)
        game.shapes.color = c
        game.shapes.triangle(x + s * 1.15f, y + s * .75f, x + s * .35f, y + s * .75f, x + s * .85f, y + s * .05f)
    }

    override fun dispose() {
        for (t in faces) t.dispose()
        bossFace.dispose()
    }

    private companion object {
        const val SEGMENTS = 40
        const val ACTIVE_X = 80f
        const val ACTIVE_Y = 646f
        const val RESERVE_X = 66f
        const val RESERVE_Y = 570f
        const val BOSS_X = 470f
        const val BOSS_Y = 662f
        val HEALTH = Color(.36f, .95f, .3f, 1f)
        val ATTACK_ICON = Color(1f, .82f, .55f, 1f)
        val ATTACK_LABEL = Color(1f, .93f, .82f, 1f)
        val LABEL_KEYS = ButtonLayout.LABEL_KEYS
    }
}
