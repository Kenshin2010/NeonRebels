package com.manroid.neonrebels.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils

/**
 * Presentation-only effects in world units: sparks, smoke/dust puffs, shockwave rings, floating
 * damage numbers and screen shake. Everything lives in fixed pools (no allocation per frame) and
 * advances with simulation time, so effects freeze with the game during pause and dialogue.
 */
class Effects {
    private val n = MAX_PARTICLES
    private val px = FloatArray(n)
    private val py = FloatArray(n)
    private val pvx = FloatArray(n)
    private val pvy = FloatArray(n)
    private val life = FloatArray(n)
    private val maxLife = FloatArray(n)
    private val size = FloatArray(n)
    private val gravity = FloatArray(n)
    private val kind = IntArray(n)
    private val color = arrayOfNulls<Color>(n)
    private var next = 0

    private val rx = FloatArray(MAX_RINGS)
    private val ry = FloatArray(MAX_RINGS)
    private val rLife = FloatArray(MAX_RINGS)
    private val rMax = FloatArray(MAX_RINGS)
    private val rRadius = FloatArray(MAX_RINGS)
    private val rFlat = BooleanArray(MAX_RINGS)
    private val rColor = arrayOfNulls<Color>(MAX_RINGS)
    private var nextRing = 0

    private val tx = FloatArray(MAX_TEXTS)
    private val ty = FloatArray(MAX_TEXTS)
    private val tLife = FloatArray(MAX_TEXTS)
    private val tValue = IntArray(MAX_TEXTS)
    private val tBig = BooleanArray(MAX_TEXTS)
    private val tColor = arrayOfNulls<Color>(MAX_TEXTS)
    private var nextText = 0
    private val text = StringBuilder(8)
    private val scratch = Color()

    var shakeX = 0f
        private set
    var shakeY = 0f
        private set
    private var shake = 0f

    private fun spawn(x: Float, y: Float, vx: Float, vy: Float, t: Float, s: Float, g: Float, k: Int, c: Color) {
        val i = next
        next = (next + 1) % n
        px[i] = x; py[i] = y; pvx[i] = vx; pvy[i] = vy
        life[i] = t; maxLife[i] = t; size[i] = s; gravity[i] = g; kind[i] = k; color[i] = c
    }

    /** Bright streaks flying out from a hit, biased towards [dir] (-1, 0 or 1). */
    fun sparks(x: Float, y: Float, count: Int, c: Color, speed: Float = 9f, dir: Float = 0f) {
        repeat(count) {
            val a = MathUtils.random(MathUtils.PI2)
            val s = speed * MathUtils.random(.35f, 1f)
            spawn(x, y, MathUtils.cos(a) * s + dir * speed * .6f, MathUtils.sin(a) * s + 2f,
                MathUtils.random(.18f, .38f), MathUtils.random(.05f, .1f), -22f, SPARK, c)
        }
    }

    /** Soft round puffs: dust on the ground, smoke from wrecks, steam from the boss. */
    fun puffs(x: Float, y: Float, count: Int, c: Color, spread: Float = .5f, rise: Float = 1f, s: Float = .3f) {
        repeat(count) {
            spawn(x + MathUtils.random(-spread, spread), y + MathUtils.random(0f, spread * .5f),
                MathUtils.random(-1.2f, 1.2f), rise * MathUtils.random(.4f, 1.2f),
                MathUtils.random(.35f, .7f), s * MathUtils.random(.7f, 1.3f), 0f, PUFF, c)
        }
    }

    /** Ground dust kicked sideways (landing, dash start, charge). */
    fun dust(x: Float, y: Float, dir: Float, count: Int = 6) {
        repeat(count) {
            spawn(x + MathUtils.random(-.3f, .3f), y + .05f, -dir * MathUtils.random(.5f, 3f) + MathUtils.random(-1f, 1f),
                MathUtils.random(.3f, 1.4f), MathUtils.random(.25f, .45f), MathUtils.random(.15f, .3f), 0f, PUFF, DUST)
        }
    }

    /** Burst for a destroyed robot/drone/boss: sparks, embers and smoke. */
    fun explosion(x: Float, y: Float, scale: Float) {
        sparks(x, y, (14 * scale).toInt(), Palette.ORANGE, 11f * scale)
        sparks(x, y, (8 * scale).toInt(), Color.WHITE, 8f * scale)
        puffs(x, y, (6 * scale).toInt(), SMOKE, .6f * scale, 1.5f, .5f * scale)
        ring(x, y, 1.6f * scale, Palette.ORANGE, flat = false)
        shake(.15f + .1f * scale)
    }

    fun ring(x: Float, y: Float, radius: Float, c: Color, flat: Boolean, time: Float = .35f) {
        val i = nextRing
        nextRing = (nextRing + 1) % MAX_RINGS
        rx[i] = x; ry[i] = y; rLife[i] = time; rMax[i] = time; rRadius[i] = radius; rFlat[i] = flat; rColor[i] = c
    }

    fun number(x: Float, y: Float, value: Int, c: Color, big: Boolean) {
        val i = nextText
        nextText = (nextText + 1) % MAX_TEXTS
        tx[i] = x + MathUtils.random(-.25f, .25f); ty[i] = y; tLife[i] = TEXT_LIFE; tValue[i] = value; tBig[i] = big; tColor[i] = c
    }

    fun shake(amount: Float) {
        shake = maxOf(shake, amount)
    }

    fun update(dt: Float) {
        if (dt <= 0f) return
        for (i in 0 until n) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            pvy[i] += gravity[i] * dt
            if (kind[i] == PUFF) {
                pvx[i] *= 1f - minOf(1f, dt * 3f)
                pvy[i] *= 1f - minOf(1f, dt * 2f)
            }
            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt
        }
        for (i in 0 until MAX_RINGS) if (rLife[i] > 0f) rLife[i] -= dt
        for (i in 0 until MAX_TEXTS) if (tLife[i] > 0f) {
            tLife[i] -= dt
            ty[i] += dt * 1.6f
        }
        shake = maxOf(0f, shake - dt * 1.2f)
        shakeX = if (shake > 0f) MathUtils.random(-shake, shake) else 0f
        shakeY = if (shake > 0f) MathUtils.random(-shake, shake) * .6f else 0f
    }

    /** Smoke and dust: normal blending, drawn behind the bright effects. */
    fun drawSoft(shapes: ShapeRenderer) {
        for (i in 0 until n) {
            if (life[i] <= 0f || kind[i] != PUFF) continue
            val t = life[i] / maxLife[i]
            shapes.color = tint(color[i]!!, t * .32f)
            shapes.circle(px[i], py[i], size[i] * (1.6f - t * .6f), 12)
            shapes.color = tint(color[i]!!, t * .18f)
            shapes.circle(px[i], py[i], size[i] * (2.2f - t * .8f), 12)
        }
    }

    /** Sparks and rings: call with additive blending. */
    fun drawGlow(shapes: ShapeRenderer) {
        for (i in 0 until n) {
            if (life[i] <= 0f || kind[i] != SPARK) continue
            val t = life[i] / maxLife[i]
            shapes.color = tint(color[i]!!, t)
            shapes.rectLine(px[i], py[i], px[i] - pvx[i] * .035f, py[i] - pvy[i] * .035f, size[i])
        }
        for (i in 0 until MAX_RINGS) {
            if (rLife[i] <= 0f) continue
            val t = 1f - rLife[i] / rMax[i]
            val r = rRadius[i] * (.25f + .75f * t)
            shapes.color = tint(rColor[i]!!, (1f - t) * .8f)
            val segments = 24
            for (k in 0 until segments) {
                val a = MathUtils.PI2 * k / segments
                val b = MathUtils.PI2 * (k + 1) / segments
                val sy = if (rFlat[i]) .22f else 1f
                shapes.rectLine(rx[i] + MathUtils.cos(a) * r, ry[i] + MathUtils.sin(a) * r * sy,
                    rx[i] + MathUtils.cos(b) * r, ry[i] + MathUtils.sin(b) * r * sy, .12f * (1f - t) + .03f)
            }
        }
    }

    /** Floating damage numbers; [draw] prints the text with its own font call. */
    inline fun drawNumbers(draw: (CharSequence, Float, Float, Float, Color) -> Unit) {
        for (i in 0 until textCount()) {
            if (!textAlive(i)) continue
            draw(textAt(i), textX(i), textY(i), textScale(i), textColor(i))
        }
    }

    fun textCount() = MAX_TEXTS
    fun textAlive(i: Int) = tLife[i] > 0f
    fun textX(i: Int) = tx[i]
    fun textY(i: Int) = ty[i]
    fun textScale(i: Int) = (if (tBig[i]) .075f else .055f) * (1f + .3f * maxOf(0f, (tLife[i] - TEXT_LIFE + .12f) / .12f))
    fun textColor(i: Int): Color = tint(tColor[i]!!, minOf(1f, tLife[i] / .3f))
    fun textAt(i: Int): CharSequence {
        text.setLength(0)
        text.append(tValue[i])
        return text
    }

    private fun tint(c: Color, a: Float): Color = scratch.set(c.r, c.g, c.b, a.coerceIn(0f, 1f))

    companion object {
        const val MAX_PARTICLES = 320
        const val MAX_RINGS = 16
        const val MAX_TEXTS = 24
        const val TEXT_LIFE = .8f
        private const val SPARK = 0
        private const val PUFF = 1
        val DUST = Color(.62f, .6f, .78f, 1f)
        val SMOKE = Color(.3f, .3f, .38f, 1f)
        val STEAM = Color(.85f, .9f, 1f, 1f)
    }
}
