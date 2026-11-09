package com.manroid.neonrebels.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.manroid.neonrebels.model.Controls

/**
 * Maps keyboard and multi-touch to [Controls]. Each pointer owns at most one button at a time
 * (dragging off releases it). Presses become edges consumed by one fixed step; held states
 * (left, right, fire) are sampled every frame. [enabled] hides context buttons (interact, skip
 * tutorial) from hit-testing while they are not on screen.
 *
 * Desktop: A/D or arrows move, Space jump, J attack/fire, K dodge, L skill, Q swap, E activate,
 * Escape pause, F3 debug overlay.
 */
class InputController(private val viewport: Viewport) : InputAdapter() {
    val controls = Controls()
    val pointers = PointerButtons()
    val enabled = BooleanArray(ButtonLayout.CX.size) { it != ButtonLayout.INTERACT && it != ButtonLayout.SKIP_TUTORIAL }
    private val keys = BooleanArray(256)
    private val point = Vector2()
    private var pausePressed = false
    private var skipTutorialPressed = false
    private var debugPressed = false

    private fun press(button: Int) {
        when (button) {
            ButtonLayout.JUMP -> controls.jump = true
            ButtonLayout.ATTACK -> controls.attack = true
            ButtonLayout.DODGE -> controls.dodge = true
            ButtonLayout.SKILL -> controls.skill = true
            ButtonLayout.SWAP -> controls.swap = true
            ButtonLayout.INTERACT -> controls.interact = true
            ButtonLayout.PAUSE -> pausePressed = true
            ButtonLayout.SKIP_TUTORIAL -> skipTutorialPressed = true
        }
    }

    /** Held states for this frame. */
    fun sample() {
        controls.left = keys[Input.Keys.A] || keys[Input.Keys.LEFT] || pointers.held(ButtonLayout.LEFT)
        controls.right = keys[Input.Keys.D] || keys[Input.Keys.RIGHT] || pointers.held(ButtonLayout.RIGHT)
        controls.attackHeld = keys[Input.Keys.J] || pointers.held(ButtonLayout.ATTACK)
    }

    fun takePause(): Boolean = pausePressed.also { pausePressed = false }
    fun takeSkipTutorial(): Boolean = skipTutorialPressed.also { skipTutorialPressed = false }
    fun takeDebugToggle(): Boolean = debugPressed.also { debugPressed = false }

    /** Releases everything: used on pause, focus loss, resize and screen changes. */
    fun clear() {
        keys.fill(false)
        pointers.clear()
        controls.clear()
        pausePressed = false
        skipTutorialPressed = false
        debugPressed = false
    }

    override fun keyDown(keycode: Int): Boolean {
        if (keycode < 0 || keycode >= keys.size) return false
        if (keys[keycode]) return true
        keys[keycode] = true
        when (keycode) {
            Input.Keys.SPACE -> press(ButtonLayout.JUMP)
            Input.Keys.J -> press(ButtonLayout.ATTACK)
            Input.Keys.K -> press(ButtonLayout.DODGE)
            Input.Keys.L -> press(ButtonLayout.SKILL)
            Input.Keys.Q -> press(ButtonLayout.SWAP)
            Input.Keys.E -> press(ButtonLayout.INTERACT)
            Input.Keys.ESCAPE, Input.Keys.BACK -> press(ButtonLayout.PAUSE)
            Input.Keys.F3 -> debugPressed = true
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode >= 0 && keycode < keys.size) keys[keycode] = false
        return true
    }

    private fun hit(x: Int, y: Int): Int {
        viewport.unproject(point.set(x.toFloat(), y.toFloat()))
        return ButtonLayout.hit(point.x, point.y, enabled)
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val hit = hit(screenX, screenY)
        if (pointers.move(pointer, hit)) press(hit)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val hit = hit(screenX, screenY)
        if (pointers.move(pointer, hit)) press(hit)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointers.move(pointer, -1)
        return true
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointers.move(pointer, -1)
        return true
    }
}
