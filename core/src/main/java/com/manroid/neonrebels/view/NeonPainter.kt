package com.manroid.neonrebels.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.manroid.neonrebels.MainGame

/**
 * Shape helpers for the menu UI in the mock-up style: hexagonal gold/cyan buttons, chamfered
 * cyan panels, slanted tabs and icons. Everything is drawn inside an open filled ShapeRenderer
 * batch, without per-frame allocations.
 */
class NeonPainter(private val game: MainGame) {
    private val shapes: ShapeRenderer get() = game.shapes
    // A small ring so several tints can be passed to one call (e.g. fill and edge) without clobbering.
    private val scratch = Array(12) { Color() }
    private var nextScratch = 0
    private val vx = FloatArray(6)
    private val vy = FloatArray(6)

    fun tint(color: Color, alpha: Float): Color {
        nextScratch = (nextScratch + 1) % scratch.size
        return scratch[nextScratch].set(color.r, color.g, color.b, alpha)
    }

    fun shade(color: Color, factor: Float, alpha: Float = 1f): Color {
        nextScratch = (nextScratch + 1) % scratch.size
        return scratch[nextScratch].set(color.r * factor, color.g * factor, color.b * factor, alpha)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        shapes.color = color
        shapes.rect(x, y, w, h)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color) {
        shapes.color = color
        shapes.rectLine(x1, y1, x2, y2, width)
    }

    fun circle(x: Float, y: Float, r: Float, color: Color) {
        shapes.color = color
        shapes.circle(x, y, r, 28)
    }

    fun ring(x: Float, y: Float, r: Float, width: Float, color: Color) {
        shapes.color = color
        val steps = 32
        for (k in 0 until steps) {
            val a = MathUtils.PI2 * k / steps
            val b = MathUtils.PI2 * (k + 1) / steps
            shapes.rectLine(x + MathUtils.cos(a) * r, y + MathUtils.sin(a) * r,
                x + MathUtils.cos(b) * r, y + MathUtils.sin(b) * r, width)
        }
    }

    /** Panel with the bottom-left and top-right corners cut, the frame shape used across the UI. */
    fun panel(x: Float, y: Float, w: Float, h: Float, cut: Float, fill: Color?, edge: Color?, edgeWidth: Float = 3f) {
        vx[0] = x + cut; vy[0] = y
        vx[1] = x + w; vy[1] = y
        vx[2] = x + w; vy[2] = y + h - cut
        vx[3] = x + w - cut; vy[3] = y + h
        vx[4] = x; vy[4] = y + h
        vx[5] = x; vy[5] = y + cut
        polygon(fill, edge, edgeWidth, x + w / 2, y + h / 2)
    }

    /** Hexagon with pointed left and right ends: the mock-up button silhouette. */
    fun hex(x: Float, y: Float, w: Float, h: Float, fill: Color?, edge: Color?, edgeWidth: Float = 3f) {
        val tip = h * .32f
        vx[0] = x; vy[0] = y + h / 2
        vx[1] = x + tip; vy[1] = y
        vx[2] = x + w - tip; vy[2] = y
        vx[3] = x + w; vy[3] = y + h / 2
        vx[4] = x + w - tip; vy[4] = y + h
        vx[5] = x + tip; vy[5] = y + h
        polygon(fill, edge, edgeWidth, x + w / 2, y + h / 2)
    }

    private fun polygon(fill: Color?, edge: Color?, edgeWidth: Float, cx: Float, cy: Float) {
        if (fill != null) {
            shapes.color = fill
            for (i in 0 until 6) {
                val j = (i + 1) % 6
                shapes.triangle(cx, cy, vx[i], vy[i], vx[j], vy[j])
            }
        }
        if (edge != null) {
            shapes.color = edge
            for (i in 0 until 6) {
                val j = (i + 1) % 6
                shapes.rectLine(vx[i], vy[i], vx[j], vy[j], edgeWidth)
            }
        }
    }

    /** Parallelogram slanted like the mock-up tabs; [slant] shifts the top edge right. */
    fun slanted(x: Float, y: Float, w: Float, h: Float, slant: Float, fill: Color?, edge: Color?, edgeWidth: Float = 2f) {
        if (fill != null) {
            shapes.color = fill
            shapes.triangle(x, y, x + w, y, x + w + slant, y + h)
            shapes.triangle(x, y, x + w + slant, y + h, x + slant, y + h)
        }
        if (edge != null) {
            line(x, y, x + w, y, edgeWidth, edge)
            line(x + w, y, x + w + slant, y + h, edgeWidth, edge)
            line(x + w + slant, y + h, x + slant, y + h, edgeWidth, edge)
            line(x + slant, y + h, x, y, edgeWidth, edge)
        }
    }

    /**
     * Action button. Primary is gold, secondary cyan; disabled turns grey; pressed brightens and
     * sinks by two pixels; busy pulses. Text and icon are drawn by the caller.
     */
    fun button(x: Float, y: Float, w: Float, h: Float, style: Int, enabled: Boolean, pressed: Boolean, busy: Boolean, time: Float) {
        val accent = if (!enabled) Palette.MUTED else if (style == PRIMARY) Palette.GOLD else Palette.CYAN
        val oy = if (pressed) -2f else 0f
        hex(x - 5, y - 5 + oy, w + 10, h + 10, null, tint(accent, if (enabled) .28f else .1f), 5f)
        val base = when {
            !enabled -> DISABLED_FILL
            style == PRIMARY -> GOLD_FILL
            else -> CYAN_FILL
        }
        val pulse = if (busy) .5f + .5f * MathUtils.sin(time * 8f) else 0f
        hex(x, y + oy, w, h, shade(base, if (pressed) 1.6f else 1f + pulse * .6f), accent, 3f)
        // Upper sheen like the mock-up's glossy buttons.
        rect(x + h * .4f, y + h * .58f + oy, w - h * .8f, h * .3f, tint(Color.WHITE, if (enabled) .07f else .03f))
    }

    // Icons are centered on (x, y), roughly 2*s across.

    fun icon(kind: Int, x: Float, y: Float, s: Float, color: Color, hole: Color = Palette.BACK) {
        when (kind) {
            ICON_PLAY -> play(x, y, s, color)
            ICON_GAMEPAD -> gamepad(x, y, s, color, hole)
            ICON_MAP -> map(x, y, s, color)
            ICON_GEAR -> gear(x, y, s, color, hole)
            ICON_WRENCH -> wrench(x, y, s, color)
            ICON_BACK -> arrowLeft(x, y, s, color)
            ICON_DEPLOY -> chevronsRight(x, y, s, color)
            ICON_UPGRADE -> chevronsUp(x, y, s, color)
            ICON_SWAP -> swap(x, y, s, color)
            ICON_PLUS -> plus(x, y, s, color)
            ICON_MINUS -> rect(x - s * .8f, y - s * .18f, s * 1.6f, s * .36f, color)
            ICON_CHECK -> check(x, y, s, color)
            ICON_LOCK -> lock(x, y, s, color, hole)
            ICON_EXIT -> exit(x, y, s, color)
            ICON_RETRY -> retry(x, y, s, color)
            ICON_CLOSE -> close(x, y, s, color)
        }
    }

    fun play(x: Float, y: Float, s: Float, color: Color) {
        shapes.color = color
        shapes.triangle(x - s * .6f, y - s * .8f, x - s * .6f, y + s * .8f, x + s * .85f, y)
    }

    fun arrowLeft(x: Float, y: Float, s: Float, color: Color) {
        shapes.color = color
        shapes.triangle(x - s, y, x - s * .1f, y + s * .75f, x - s * .1f, y - s * .75f)
        shapes.rect(x - s * .2f, y - s * .22f, s * 1.2f, s * .44f)
    }

    fun arrowRight(x: Float, y: Float, s: Float, color: Color) {
        shapes.color = color
        shapes.triangle(x + s, y, x + s * .1f, y + s * .75f, x + s * .1f, y - s * .75f)
        shapes.rect(x - s, y - s * .22f, s * 1.2f, s * .44f)
    }

    fun chevronsRight(x: Float, y: Float, s: Float, color: Color) {
        for (k in 0 until 2) {
            val ox = x - s * .45f + k * s * .8f
            line(ox - s * .4f, y + s * .7f, ox + s * .3f, y, s * .3f, color)
            line(ox + s * .3f, y, ox - s * .4f, y - s * .7f, s * .3f, color)
        }
    }

    fun chevronsUp(x: Float, y: Float, s: Float, color: Color) {
        for (k in 0 until 2) {
            val oy = y - s * .55f + k * s * .75f
            line(x - s * .75f, oy - s * .3f, x, oy + s * .35f, s * .3f, color)
            line(x, oy + s * .35f, x + s * .75f, oy - s * .3f, s * .3f, color)
        }
    }

    fun check(x: Float, y: Float, s: Float, color: Color) {
        line(x - s * .75f, y, x - s * .2f, y - s * .55f, s * .32f, color)
        line(x - s * .2f, y - s * .55f, x + s * .8f, y + s * .6f, s * .32f, color)
    }

    fun lock(x: Float, y: Float, s: Float, color: Color, hole: Color) {
        rect(x - s * .65f, y - s * .75f, s * 1.3f, s * 1f, color)
        rect(x - s * .5f, y + s * .2f, s * .22f, s * .5f, color)
        rect(x + s * .28f, y + s * .2f, s * .22f, s * .5f, color)
        rect(x - s * .5f, y + s * .62f, s * 1f, s * .22f, color)
        circle(x, y - s * .2f, s * .16f, hole)
        rect(x - s * .06f, y - s * .6f, s * .12f, s * .35f, hole)
    }

    fun gear(x: Float, y: Float, s: Float, color: Color, hole: Color) {
        for (k in 0 until 8) {
            val a = MathUtils.PI2 * k / 8
            line(x, y, x + MathUtils.cos(a) * s * .95f, y + MathUtils.sin(a) * s * .95f, s * .34f, color)
        }
        circle(x, y, s * .68f, color)
        circle(x, y, s * .28f, hole)
    }

    /** Three dots: "coming soon". */
    fun dots(x: Float, y: Float, s: Float, color: Color) {
        for (k in -1..1) circle(x + k * s * .7f, y, s * .22f, color)
    }

    private fun gamepad(x: Float, y: Float, s: Float, color: Color, hole: Color) {
        rect(x - s * .7f, y - s * .4f, s * 1.4f, s * .8f, color)
        circle(x - s * .7f, y - s * .05f, s * .45f, color)
        circle(x + s * .7f, y - s * .05f, s * .45f, color)
        rect(x - s * .85f, y - s * .07f, s * .4f, s * .14f, hole)
        rect(x - s * .72f, y - s * .2f, s * .14f, s * .4f, hole)
        circle(x + s * .55f, y + s * .05f, s * .1f, hole)
        circle(x + s * .8f, y - s * .15f, s * .1f, hole)
    }

    private fun map(x: Float, y: Float, s: Float, color: Color) {
        shapes.color = color
        shapes.triangle(x - s, y - s * .7f, x - s * .35f, y - s * .45f, x - s * .35f, y + s * .85f)
        shapes.triangle(x - s, y - s * .7f, x - s * .35f, y + s * .85f, x - s, y + s * .6f)
        shapes.triangle(x - s * .25f, y - s * .45f, x + s * .25f, y - s * .7f, x + s * .25f, y + s * .6f)
        shapes.triangle(x - s * .25f, y - s * .45f, x + s * .25f, y + s * .6f, x - s * .25f, y + s * .85f)
        shapes.triangle(x + s * .35f, y - s * .7f, x + s, y - s * .45f, x + s, y + s * .85f)
        shapes.triangle(x + s * .35f, y - s * .7f, x + s, y + s * .85f, x + s * .35f, y + s * .6f)
    }

    private fun wrench(x: Float, y: Float, s: Float, color: Color) {
        line(x - s * .75f, y - s * .75f, x + s * .35f, y + s * .35f, s * .34f, color)
        ring(x + s * .5f, y + s * .5f, s * .38f, s * .22f, color)
    }

    private fun swap(x: Float, y: Float, s: Float, color: Color) {
        rect(x - s * .8f, y + s * .2f, s * 1.1f, s * .26f, color)
        shapes.triangle(x + s * .9f, y + s * .33f, x + s * .3f, y + s * .8f, x + s * .3f, y - s * .14f)
        rect(x - s * .3f, y - s * .46f, s * 1.1f, s * .26f, color)
        shapes.triangle(x - s * .9f, y - s * .33f, x - s * .3f, y + s * .14f, x - s * .3f, y - s * .8f)
    }

    private fun plus(x: Float, y: Float, s: Float, color: Color) {
        rect(x - s * .8f, y - s * .18f, s * 1.6f, s * .36f, color)
        rect(x - s * .18f, y - s * .8f, s * .36f, s * 1.6f, color)
    }

    private fun exit(x: Float, y: Float, s: Float, color: Color) {
        line(x - s * .6f, y - s * .8f, x - s * .6f, y + s * .8f, s * .22f, color)
        line(x - s * .6f, y + s * .8f, x + s * .1f, y + s * .8f, s * .22f, color)
        line(x - s * .6f, y - s * .8f, x + s * .1f, y - s * .8f, s * .22f, color)
        arrowRight(x + s * .25f, y, s * .7f, color)
    }

    private fun retry(x: Float, y: Float, s: Float, color: Color) {
        shapes.color = color
        for (k in 0 until 10) {
            val a = MathUtils.PI2 * (.15f + k * .075f)
            val b = MathUtils.PI2 * (.15f + (k + 1) * .075f)
            shapes.rectLine(x + MathUtils.cos(a) * s * .7f, y + MathUtils.sin(a) * s * .7f,
                x + MathUtils.cos(b) * s * .7f, y + MathUtils.sin(b) * s * .7f, s * .25f)
        }
        shapes.triangle(x + s * .35f, y + s * .95f, x + s * .95f, y + s * .55f, x + s * .25f, y + s * .3f)
    }

    private fun close(x: Float, y: Float, s: Float, color: Color) {
        line(x - s * .65f, y - s * .65f, x + s * .65f, y + s * .65f, s * .28f, color)
        line(x - s * .65f, y + s * .65f, x + s * .65f, y - s * .65f, s * .28f, color)
    }

    companion object {
        const val PRIMARY = 0
        const val SECONDARY = 1
        const val ICON_NONE = 0
        const val ICON_PLAY = 1
        const val ICON_GAMEPAD = 2
        const val ICON_MAP = 3
        const val ICON_GEAR = 4
        const val ICON_WRENCH = 5
        const val ICON_BACK = 6
        const val ICON_DEPLOY = 7
        const val ICON_UPGRADE = 8
        const val ICON_SWAP = 9
        const val ICON_PLUS = 10
        const val ICON_MINUS = 11
        const val ICON_CHECK = 12
        const val ICON_LOCK = 13
        const val ICON_EXIT = 14
        const val ICON_RETRY = 15
        const val ICON_CLOSE = 16
        private val GOLD_FILL = Color(.34f, .19f, .04f, 1f)
        private val CYAN_FILL = Color(.03f, .15f, .23f, 1f)
        private val DISABLED_FILL = Color(.08f, .09f, .13f, 1f)
    }
}
