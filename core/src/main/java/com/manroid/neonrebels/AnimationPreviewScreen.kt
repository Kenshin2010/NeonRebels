package com.manroid.neonrebels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.manroid.neonrebels.model.CharacterType
import com.manroid.neonrebels.model.GameConfig
import com.manroid.neonrebels.model.Player
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette
import com.manroid.neonrebels.view.SafeViewport
import com.manroid.neonrebels.view.UiPainter
import com.manroid.neonrebels.view.WorldRenderer
import com.manroid.neonrebels.view.animation.AnimationManifest
import com.manroid.neonrebels.view.animation.ClipEnd

/** Debug-only, isolated actor. Does not create runs, fire projectiles or write campaign progress. */
class AnimationPreviewScreen(private val game: MainGame) : ScreenAdapter() {
    private val renderer = WorldRenderer(game)
    private val canvas = SafeViewport(16f, 9f)
    private val hud = SafeViewport(1280f, 720f)
    private val ui = UiPainter(game)
    private val neon = NeonPainter(game)
    private val p = Player()
    private val point = Vector2()
    private val buttons = ArrayList<UiButton>()
    private var selected = 0
    private var elapsed = 0f
    private var frame = 0
    private var playing = true
    private var suspended = false
    private var speedIndex = 2
    private val speeds = floatArrayOf(.25f, .5f, 1f, 2f)
    private var facing = 1
    private var pressed: UiButton? = null
    private val id: String get() = AnimationManifest.ids[selected]

    init {
        require(game.debugTools)
        fun button(x: Float, top: Float, width: Float, key: String, action: () -> Unit) {
            buttons += UiButton(x, top, width, 48f, UiButton.SECONDARY, label = game.text.get(key), onClick = action)
        }
        button(20f, 20f, 180f, "preview.hero") {
            val action = id.substringAfter('.')
            val target = (if (id.startsWith("an.")) "vy." else "an.") + action
            selected = AnimationManifest.ids.indexOf(target).takeIf { it >= 0 }
                ?: AnimationManifest.ids.indexOf(if (id.startsWith("an.")) "vy.idle" else "an.idle")
            reset()
        }
        button(220f, 20f, 180f, "preview.previous") { select(-1) }
        button(420f, 20f, 180f, "preview.next") { select(1) }
        button(620f, 20f, 180f, "preview.play") { playing = !playing }
        button(820f, 20f, 180f, "preview.flip") { facing = -facing }
        button(1020f, 20f, 240f, "preview.back") { game.returnFromAnimationPreview(this) }
        button(220f, 620f, 180f, "preview.frameBack") { step(-1) }
        button(420f, 620f, 180f, "preview.frameNext") { step(1) }
        button(620f, 620f, 180f, "preview.speed") { speedIndex = (speedIndex + 1) % speeds.size }
        button(820f, 620f, 180f, "preview.restart") { reset() }
    }

    private fun reset() { elapsed = 0f; frame = 0 }
    private fun select(direction: Int) {
        selected = (selected + direction + AnimationManifest.ids.size) % AnimationManifest.ids.size
        reset()
    }
    private fun step(direction: Int) {
        playing = false
        val clip = renderer.animationClip(id) ?: return
        frame = (frame + direction + clip.frames.size) % clip.frames.size
        elapsed = clip.durations.take(frame).sum()
    }

    private val input = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (suspended) return true
            when (keycode) {
                Input.Keys.ESCAPE, Input.Keys.BACK -> game.returnFromAnimationPreview(this@AnimationPreviewScreen)
                Input.Keys.LEFT -> step(-1)
                Input.Keys.RIGHT -> step(1)
                Input.Keys.UP -> select(-1)
                Input.Keys.DOWN -> select(1)
                Input.Keys.SPACE -> playing = !playing
                Input.Keys.F -> facing = -facing
                Input.Keys.TAB -> buttons[0].onClick()
                Input.Keys.R -> reset()
                Input.Keys.PLUS, Input.Keys.EQUALS -> speedIndex = (speedIndex + 1) % speeds.size
            }
            return true
        }
        override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
            if (!suspended && pointer == 0) {
                hud.unproject(point.set(x.toFloat(), y.toFloat()))
                pressed = buttons.firstOrNull { it.contains(point.x, point.y) }
            }
            return true
        }
        override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
            if (pointer != 0) return true
            val b = pressed
            pressed = null
            hud.unproject(point.set(x.toFloat(), y.toFloat()))
            if (!suspended && b?.contains(point.x, point.y) == true) b.onClick()
            return true
        }
        override fun touchCancelled(x: Int, y: Int, pointer: Int, button: Int): Boolean {
            if (pointer == 0) pressed = null
            return true
        }
    }

    /** Synthetic pose for the existing fallback only; no world simulation or combat side effects. */
    private fun pose() {
        p.endAction()
        p.type = if (id.startsWith("an.")) CharacterType.AN else CharacterType.VY
        p.x = 7.575f; p.y = 2f; p.facing = facing
        p.hp[0] = p.maxHp[0]; p.hp[1] = p.maxHp[1]
        p.grounded = true; p.vx = 0f; p.vy = 0f; p.hurtTime = 0f; p.shotFlash = 1f
        when (val action = id.substringAfter('.')) {
            "run" -> p.vx = facing * GameConfig.SPEED
            "jump", "fall" -> { p.grounded = false; p.vy = if (action == "jump") 4f else -4f }
            "dodge" -> p.startDodge(facing)
            "skill" -> if (p.type == CharacterType.AN) p.startDash() else p.recordShot(true)
            "shoot" -> p.recordShot(false)
            "hurt" -> p.hurtTime = GameConfig.HIT_STUN
            "death" -> { p.hp[0] = 0; p.hp[1] = 0 }
            "attack1", "attack2", "attack3" -> {
                p.startStrike(action.last().digitToInt())
                val clip = renderer.animationClip(id)
                val total = clip?.duration ?: .5f
                val i = p.comboStep - 1
                val phase = clip?.phaseEnds?.takeIf { it.size == 2 }?.let {
                    when { frame < it[0] -> 0; frame < it[1] -> 1; else -> 2 }
                } ?: ((elapsed / total).coerceIn(0f, .999f) * 3).toInt()
                if (phase >= 1) p.advanceAction(GameConfig.COMBO_STARTUP[i])
                if (phase >= 2) p.advanceAction(GameConfig.COMBO_ACTIVE[i])
            }
        }
    }

    override fun render(delta: Float) {
        val clip = renderer.animationClip(id)
        if (playing && !suspended) {
            elapsed += delta.coerceIn(0f, .1f) * speeds[speedIndex]
            if (clip != null && clip.end != ClipEnd.LOOP && elapsed >= clip.duration) {
                elapsed = clip.duration
                playing = false
            }
            frame = clip?.frameAt(elapsed) ?: 0
        }
        pose()
        Gdx.gl.glClearColor(.025f, .035f, .07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        canvas.apply()
        game.shapes.projectionMatrix = canvas.camera.combined
        game.batch.projectionMatrix = canvas.camera.combined
        game.shapes.begin(ShapeRenderer.ShapeType.Line)
        game.shapes.color = Palette.MUTED
        game.shapes.line(0f, 2f, 16f, 2f)
        game.shapes.end()
        game.batch.begin()
        renderer.drawPreviewPlayer(p, elapsed, id, frame)
        game.batch.end()
        game.shapes.begin(ShapeRenderer.ShapeType.Line)
        game.shapes.color = Color.GREEN
        game.shapes.rect(p.x, p.y, p.w, p.h)
        game.shapes.color = Color.YELLOW
        game.shapes.line(7.8f, 2f, 8.2f, 2f)
        game.shapes.line(8f, 1.8f, 8f, 2.2f)
        if (p.swordActive() || p.dashing()) {
            game.shapes.rect(p.swordX(), p.y, p.swordWidth(), GameConfig.SWORD_HEIGHT)
        }
        val art = renderer.animationFrame(id, frame)
        for (socket in SOCKETS) {
            val x = art?.socketX(socket, p.centerX(), facing) ?: continue
            val y = art.socketY(socket, p.y) ?: continue
            game.shapes.color = Palette.PINK
            game.shapes.circle(x, y, .06f, 12)
        }
        if (p.type == CharacterType.VY) {
            game.shapes.color = Color.CYAN
            game.shapes.rect(p.muzzleX(), p.muzzleY(), .38f, .22f)
        }
        game.shapes.end()
        hud.apply()
        game.shapes.projectionMatrix = hud.camera.combined
        game.batch.projectionMatrix = hud.camera.combined
        game.shapes.begin(ShapeRenderer.ShapeType.Filled)
        for (b in buttons) neon.button(b.x, b.y, b.w, b.h, b.style, true, pressed === b, false, 0f)
        game.shapes.end()
        game.batch.begin()
        for (b in buttons) ui.fitLabel(b.label, b.x + 8, b.x + b.w - 8, b.y + b.h / 2, 1f, Color.WHITE, centered = true)
        ui.label("$id   ${speeds[speedIndex]}x   " + game.text.get(if (playing && !suspended) "preview.playing" else "preview.stopped"), 640f, 590f, 1.4f, Color.WHITE, slanted = false)
        val status = renderer.animationStatus(id)
        ui.fitLabel(game.text.get(if (status == "atlas") "preview.real" else "preview.fallback") + " · " + status,
            30f, 1250f, 550f, 1f, if (status == "atlas") Palette.GREEN else Palette.ORANGE)
        val region = if (art != null) art.name else if (p.type == CharacterType.AN) "an_stance" else "vy_stance"
        ui.label("$region   ${if (art != null) frame + 1 else 1}/${if (art != null) clip?.frames?.size ?: 1 else 1}", 640f, 510f, 1.2f, Color.WHITE, slanted = false)
        ui.label(game.text.get("preview.legend"), 640f, 155f, .85f, Palette.SILVER, slanted = false)
        game.batch.end()
    }

    override fun show() { Gdx.input.inputProcessor = input }
    override fun hide() { pressed = null; Gdx.input.inputProcessor = null }
    override fun pause() { suspended = true; playing = false; pressed = null }
    override fun resume() { suspended = false }
    override fun resize(width: Int, height: Int) {
        canvas.update(width, height, true); hud.update(width, height, true); pressed = null
    }
    override fun dispose() { renderer.dispose() }

    private companion object { val SOCKETS = listOf("muzzle", "hand", "tip") }
}
