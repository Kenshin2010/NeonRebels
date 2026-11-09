package com.manroid.neonrebels.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.max

/** Fits the same logical canvas into the usable screen area, including display cutouts. */
class SafeViewport(width: Float, height: Float) : FitViewport(width, height) {
    override fun update(screenWidth: Int, screenHeight: Int, centerCamera: Boolean) {
        val left = max(0, Gdx.graphics.safeInsetLeft)
        val right = max(0, Gdx.graphics.safeInsetRight)
        val top = max(0, Gdx.graphics.safeInsetTop)
        val bottom = max(0, Gdx.graphics.safeInsetBottom)
        super.update(max(1, screenWidth - left - right), max(1, screenHeight - top - bottom), centerCamera)
        setScreenPosition(screenX + left, screenY + bottom)
        apply(centerCamera)
    }
}
