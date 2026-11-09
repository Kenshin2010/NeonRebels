package com.manroid.neonrebels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array as GdxArray
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette
import com.manroid.neonrebels.view.SafeViewport
import com.manroid.neonrebels.view.UiPainter

/**
 * A tappable widget. Layout uses art pixels with the origin at the TOP-left ([top]), matching
 * the background images; [y] is the bottom-up viewport coordinate used for drawing.
 * [CUSTOM] buttons only get press handling; their owner screen draws them.
 */
class UiButton(
    var x: Float, var top: Float, var w: Float, var h: Float,
    var style: Int,
    var icon: Int = NeonPainter.ICON_NONE,
    var label: String = "",
    /** Belongs to the modal layer: active only while a modal is open, and vice versa. */
    val modal: Boolean = false,
    val onClick: () -> Unit,
) {
    var enabled = true
    var visible = true
    var busy = false
    /** Shown as a toast when a disabled button is tapped, so the reason is never hidden. */
    var disabledHint: String? = null

    val y: Float get() = MenuBase.HEIGHT - top - h

    fun contains(px: Float, py: Float) = visible && px >= x && px <= x + w && py >= y && py <= y + h

    companion object {
        const val PRIMARY = NeonPainter.PRIMARY
        const val SECONDARY = NeonPainter.SECONDARY
        /** Small chamfered cyan frame, for back and utility buttons. */
        const val GHOST = 10
        const val CUSTOM = 11
    }
}

/**
 * Shared frame for the menu screens: 1280x720 safe viewport, a backdrop cut from the UI mock-up
 * (scripts/extract-ui-art.py), real [UiButton] widgets with pressed/disabled/busy states, a
 * modal layer that blocks the widgets below it, Back handling (modal first, then screen) and a
 * one-shot [navigate] guard so fast repeated taps cannot open several screens.
 */
abstract class MenuBase(protected val game: MainGame, art: String) : ScreenAdapter() {
    protected val viewport = SafeViewport(WIDTH, HEIGHT)
    protected val ui = UiPainter(game)
    protected val neon = NeonPainter(game)
    protected var time = 0f
    protected val buttons = ArrayList<UiButton>()
    /** 0 = no modal; screens define their own modal ids. */
    protected var modal = 0
    private val textures = GdxArray<Texture>()
    protected val background = texture("ui/$art.jpg")
    private val point = Vector2()
    private var suspended = false
    private var leaving = false
    private var pressed: UiButton? = null
    private var pressedInside = false
    private var toastText: String? = null
    private var toastTime = 0f

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (blocked() || pointer > 0) return true
            unproject(screenX, screenY)
            pressed = buttons.lastOrNull { it.layerActive() && it.contains(point.x, point.y) }
            pressedInside = pressed != null
            if (pressed == null) tapEmpty(point.x, point.y)
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            val b = pressed ?: return true
            if (pointer > 0) return true
            unproject(screenX, screenY)
            pressedInside = b.contains(point.x, point.y)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer > 0) return true
            val b = pressed
            pressed = null
            if (b == null || blocked()) return true
            unproject(screenX, screenY)
            if (b.contains(point.x, point.y) && b.layerActive()) click(b)
            return true
        }

        override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            pressed = null
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (blocked()) return true
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                if (modal != 0) closeModal() else back()
            } else key(keycode)
            return true
        }
    }

    private fun UiButton.layerActive() = visible && modal == (this@MenuBase.modal != 0)

    private fun blocked() = suspended || leaving

    private fun unproject(screenX: Int, screenY: Int) {
        viewport.unproject(point.set(screenX.toFloat(), screenY.toFloat()))
    }

    protected fun click(b: UiButton) {
        if (b.busy) return
        if (!b.enabled) {
            b.disabledHint?.let { toastText(it) }
            return
        }
        b.onClick()
    }

    protected fun button(
        x: Float, top: Float, w: Float, h: Float, style: Int, icon: Int = NeonPainter.ICON_NONE,
        label: String = "", modal: Boolean = false, onClick: () -> Unit,
    ): UiButton = UiButton(x, top, w, h, style, icon, label, modal, onClick).also { buttons += it }

    protected fun isPressed(b: UiButton) = pressed === b && pressedInside

    protected abstract fun back()
    protected open fun closeModal() {
        modal = 0
    }
    protected open fun key(keycode: Int) = Unit
    protected open fun tapEmpty(x: Float, y: Float) = Unit
    /** Called each time the screen becomes current, including when returning from the workshop. */
    protected open fun onShown() = Unit
    protected open fun drawBackground() {
        game.batch.draw(background, 0f, 0f, WIDTH, HEIGHT)
    }
    protected open fun drawArt() = Unit
    protected open fun drawShapes() = Unit
    /** Sprites that must sit on top of code-drawn panels (drawn just before the text). */
    protected open fun drawTopArt() = Unit
    protected open fun drawText() = Unit
    protected open fun drawModalShapes() = Unit
    protected open fun drawModalText() = Unit

    /**
     * Runs a screen change once. Further input is ignored until this screen is shown again,
     * so a double tap cannot open two screens or start two runs.
     */
    protected fun navigate(action: () -> Unit) {
        if (leaving) return
        leaving = true
        try {
            action()
        } catch (e: RuntimeException) {
            leaving = false
            throw e
        }
    }

    /** Re-enables input after a navigation attempt that did not leave (e.g. a failed start). */
    protected fun stay() {
        leaving = false
    }

    /** Loads a texture owned by this screen; all of them are disposed with the screen. */
    protected fun texture(path: String): Texture {
        val texture = Texture(Gdx.files.internal(path))
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        textures.add(texture)
        return texture
    }

    /** Art-space top coordinate to the viewport's bottom-up y. */
    protected fun ty(top: Float) = HEIGHT - top

    /** Draws [texture] into an art-space box, cropping it to fill the box without stretching. */
    protected fun spriteFill(texture: Texture, x: Float, top: Float, w: Float, h: Float) {
        val tw = texture.width.toFloat()
        val th = texture.height.toFloat()
        val scale = maxOf(w / tw, h / th)
        val sw = w / scale
        val sh = h / scale
        game.batch.draw(texture, x, ty(top + h), w, h,
            ((tw - sw) / 2).toInt(), ((th - sh) / 2).toInt(), sw.toInt(), sh.toInt(), false, false)
    }

    /** Draws [texture] at its natural size times [scale], centered on an art-space point. */
    protected fun spriteAt(texture: Texture, cx: Float, cy: Float, scale: Float = 1f) {
        val w = texture.width * scale
        val h = texture.height * scale
        game.batch.draw(texture, cx - w / 2, ty(cy) - h / 2, w, h)
    }

    protected fun toast(key: String) = toastText(ui.tr(key))

    protected fun toastText(text: String) {
        toastText = text
        toastTime = TOAST_SECONDS
    }

    override fun show() {
        Gdx.input.inputProcessor = input
        leaving = false
        pressed = null
        onShown()
    }

    override fun render(delta: Float) {
        time += delta
        if (toastTime > 0) toastTime -= delta
        Gdx.gl.glClearColor(Palette.BACK.r, Palette.BACK.g, Palette.BACK.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        game.shapes.projectionMatrix = viewport.camera.combined
        game.batch.projectionMatrix = viewport.camera.combined
        game.batch.begin()
        game.batch.setColor(1f, 1f, 1f, 1f)
        drawBackground()
        drawArt()
        game.batch.end()
        shapes {
            drawShapes()
            for (b in buttons) if (!b.modal && b.visible) drawButtonShape(b)
            if (toastVisible() && modal == 0) {
                neon.panel(340f, ty(TOAST_TOP + 46), 600f, 46f, 12f, neon.tint(Palette.BACK, .94f), Palette.ORANGE, 2f)
            }
        }
        game.batch.begin()
        drawTopArt()
        drawText()
        for (b in buttons) if (!b.modal && b.visible) drawButtonText(b)
        if (toastVisible() && modal == 0) {
            ui.label(toastText!!, 640f, ty(TOAST_TOP + 23), 1.05f, Palette.ORANGE, slanted = false)
        }
        game.batch.end()
        if (modal != 0) {
            shapes {
                neon.rect(0f, 0f, WIDTH, HEIGHT, neon.tint(Palette.BACK, .82f))
                drawModalShapes()
                for (b in buttons) if (b.modal && b.visible) drawButtonShape(b)
                if (toastVisible()) neon.panel(340f, ty(TOAST_TOP + 46), 600f, 46f, 12f, neon.tint(Palette.BACK, .94f), Palette.ORANGE, 2f)
            }
            game.batch.begin()
            drawModalText()
            for (b in buttons) if (b.modal && b.visible) drawButtonText(b)
            if (toastVisible()) ui.label(toastText!!, 640f, ty(TOAST_TOP + 23), 1.05f, Palette.ORANGE, slanted = false)
            game.batch.end()
        }
    }

    private fun drawButtonShape(b: UiButton) {
        val down = isPressed(b)
        when (b.style) {
            UiButton.CUSTOM -> return
            UiButton.GHOST -> {
                val edge = if (b.enabled) Palette.CYAN else Palette.MUTED
                val oy = if (down) -2f else 0f
                neon.panel(b.x, b.y + oy, b.w, b.h, minOf(14f, b.h * .25f),
                    neon.tint(if (down) Palette.GROUND else Palette.BACK, .9f), edge, 3f)
            }
            else -> neon.button(b.x, b.y, b.w, b.h, b.style, b.enabled, down, b.busy, time)
        }
        if (b.icon != NeonPainter.ICON_NONE) {
            val s = b.h * .26f
            val cx = if (b.label.isEmpty()) b.x + b.w / 2 else b.x + b.h * .7f
            val oy = if (down) -2f else 0f
            neon.icon(b.icon, cx, b.y + b.h / 2 + oy, s, labelColor(b), Palette.BACK)
        }
    }

    private fun drawButtonText(b: UiButton) {
        if (b.style == UiButton.CUSTOM || b.label.isEmpty()) return
        val text = if (b.busy) ui.tr("ui.busy") else b.label
        val scale = if (b.style == UiButton.GHOST) b.h * .026f else b.h * .032f
        val cy = b.y + b.h / 2 + if (isPressed(b)) -2f else 0f
        if (b.icon == NeonPainter.ICON_NONE) ui.fitLabel(text, b.x + b.h * .4f, b.x + b.w - b.h * .4f, cy, scale, labelColor(b), centered = true)
        else ui.fitLabel(text, b.x + b.h * 1.2f, b.x + b.w - b.h * .4f, cy, scale, labelColor(b))
    }

    private fun labelColor(b: UiButton): Color = when {
        !b.enabled -> Palette.MUTED
        b.style == UiButton.PRIMARY -> CREAM
        b.style == UiButton.GHOST -> Palette.CYAN
        else -> Color.WHITE
    }

    private fun toastVisible() = toastText != null && toastTime > 0

    protected inline fun shapes(body: () -> Unit) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        game.shapes.begin(ShapeRenderer.ShapeType.Filled)
        body()
        game.shapes.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun pause() {
        suspended = true
        pressed = null
    }

    override fun resume() {
        // Coming back from the background never replays a tap or a purchase.
        suspended = false
        pressed = null
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
        pressed = null
    }

    override fun dispose() {
        for (texture in textures) texture.dispose()
        textures.clear()
    }

    companion object {
        const val WIDTH = 1280f
        const val HEIGHT = 720f
        val CREAM = Color(1f, .95f, .85f, 1f)
        private const val TOAST_TOP = 104f
        private const val TOAST_SECONDS = 2.2f

        /** "5:12" style run time. */
        fun formatTime(seconds: Float): String {
            val total = seconds.toInt()
            val s = total % 60
            return "${total / 60}:${if (s < 10) "0" else ""}$s"
        }
    }
}
