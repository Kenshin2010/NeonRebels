package com.manroid.neonrebels.view

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import kotlin.math.abs

/**
 * An arm/weapon that rotates around its joint ([pivotX],[pivotY]). It covers the image within
 * [radius] of the segment joint→[tipX],[tipY] (image fractions, x from the left, y from the feet;
 * radius in fractions of the image height), fading over [soft] so the joint bends smoothly and
 * nearby legs are not dragged along. [raiseSign] maps "raise" to the rotation sign: +1 for limbs
 * that extend to the right of their joint, -1 for limbs to the left.
 */
class Limb(
    val pivotX: Float, val pivotY: Float,
    val tipX: Float, val tipY: Float,
    val radius: Float, val raiseSign: Float, val soft: Float = .08f,
)

/** A zone that ripples (scarf, hair, cape); [amount] is the wave height in image fractions. */
class Flutter(val x0: Float, val x1: Float, val y0: Float, val y1: Float, val amount: Float)

/**
 * Skeleton for one painted pose. Legs are everything below [hipY]; the leg on the [frontFootX]
 * side and the one on the [backFootX] side swing in opposite directions, more strongly near the
 * feet, which reads as hips and knees moving. [forward] is +1 if the art faces right.
 */
class Rig(
    val hipX: Float, val hipY: Float,
    val backFootX: Float, val frontFootX: Float,
    val forward: Float,
    val limbs: Array<Limb>,
    val flutters: Array<Flutter> = emptyArray(),
)

/** Animation inputs for one draw; reused, so no allocation per frame. */
class Pose {
    /** Walk cycle phase (radians) and stride size (0..1). */
    var phase = 0f
    var stride = 0f
    /** Feet pulled up under the body (jump, 0..1). */
    var tuck = 0f
    /** Raise angle in degrees per limb (index = Rig.limbs index). */
    val raise = FloatArray(4)
    /** Flutter strength (0..1) and its clock. */
    var flutter = .3f
    var time = 0f
    /** Chest breathing, as a vertical scale delta above the hips. */
    var breath = 0f

    fun reset(time: Float) {
        phase = 0f; stride = 0f; tuck = 0f; flutter = .3f; breath = 0f
        raise.fill(0f)
        this.time = time
    }
}

object Rigs {
    // Measured on the painted poses (see scripts/build-world-art.py); update if the art changes.
    val AN = Rig(.44f, .40f, .12f, .62f, 1f,
        arrayOf(Limb(.42f, .60f, 1f, .25f, .13f, 1f)),
        arrayOf(Flutter(-.02f, .34f, .52f, .9f, .05f)))
    val VY = Rig(.45f, .40f, .08f, .78f, 1f,
        arrayOf(Limb(.46f, .64f, 1f, .68f, .14f, 1f)),
        arrayOf(Flutter(.3f, .56f, .8f, 1.02f, .025f), Flutter(-.02f, .16f, .4f, .6f, .04f)))
    val ROBOT_SHIELD = Rig(.55f, .42f, .9f, .3f, -1f,
        arrayOf(Limb(.45f, .62f, 0f, .6f, .14f, -1f), Limb(.62f, .62f, .8f, .55f, .3f, 1f, .05f)))
    val ROBOT_BLADE = Rig(.47f, .4f, .64f, .26f, -1f,
        arrayOf(Limb(.3f, .62f, 0f, .05f, .1f, -1f), Limb(.72f, .66f, 1f, .15f, .1f, 1f)))
    val BOSS = Rig(.62f, .26f, .82f, .5f, -1f,
        arrayOf(Limb(.45f, .62f, 0f, .72f, .36f, -1f, .14f), Limb(.76f, .6f, 1f, .4f, .2f, 1f)))
}

/**
 * Draws a painted pose as a deformed grid (free-form deformation) with SpriteBatch quads: legs
 * scissor around the hips, limbs rotate around their joints, flutter zones ripple. The final
 * placement matches [WorldRenderer]'s actor transform (feet anchored, mirroring, rotation, scaleY).
 */
class RigRenderer {
    private val vx = FloatArray((GX + 1) * (GY + 1))
    private val vy = FloatArray((GX + 1) * (GY + 1))
    private val quads = FloatArray(GX * GY * 20)

    /** World position of image point (u, v) after deformation; written to [outX]/[outY]. */
    var outX = 0f
        private set
    var outY = 0f
        private set

    fun point(art: ActorArt, rig: Rig, pose: Pose, cx: Float, y: Float, facing: Float, rotation: Float, scaleY: Float, u: Float, v: Float) {
        val flip = (facing >= 0) != art.facesRight
        place(art, rig, pose, u, v, cx, y, flip, MathUtils.cosDeg(rotation), MathUtils.sinDeg(rotation), scaleY)
    }

    private fun place(art: ActorArt, rig: Rig, pose: Pose, u: Float, v: Float, cx: Float, y: Float,
                      flip: Boolean, cos: Float, sin: Float, scaleY: Float) {
        val w = art.width
        val h = art.height
        var du = 0f
        var dv = 0f
        // Legs: scissor around the hip, stronger towards the feet; the leg moving forward lifts.
        if (v < rig.hipY && rig.hipY > 0f) {
            val depth = ((rig.hipY - v) / rig.hipY).coerceIn(0f, 1f)
            val toFront = rig.frontFootX - rig.hipX
            val toBack = rig.backFootX - rig.hipX
            val side = if ((u - rig.hipX) * toFront >= 0f) ((u - rig.hipX) / toFront).coerceIn(0f, 1f)
            else -((u - rig.hipX) / toBack).coerceIn(0f, 1f)
            val s = MathUtils.sin(pose.phase)
            val c = MathUtils.cos(pose.phase)
            du += side * s * STRIDE * pose.stride * depth * rig.forward
            dv += depth * depth * LIFT * pose.stride * maxOf(0f, side * c)
            // Jump tuck: feet draw in towards the hip and up.
            du -= (u - rig.hipX) * depth * .35f * pose.tuck
            dv += depth * .12f * pose.tuck
        } else if (pose.breath != 0f) {
            dv += (v - rig.hipY) * pose.breath
        }
        // Limbs: rotate around their joint, weighted by a soft mask along the limb.
        var pu = u + du
        var pv = v + dv
        for (k in rig.limbs.indices) {
            val a = pose.raise[k]
            if (a == 0f) continue
            val limb = rig.limbs[k]
            val weight = limbWeight(u, v, limb, w / h)
            if (weight <= 0f) continue
            val ang = a * limb.raiseSign * weight
            val rc = MathUtils.cosDeg(ang)
            val rs = MathUtils.sinDeg(ang)
            // Rotate in world proportions so angles look right on wide images.
            val lx = (pu - limb.pivotX) * w
            val ly = (pv - limb.pivotY) * h
            pu = limb.pivotX + (lx * rc - ly * rs) / w
            pv = limb.pivotY + (lx * rs + ly * rc) / h
        }
        for (f in rig.flutters) {
            val weight = zone(u, v, f.x0, f.x1, f.y0, f.y1, .06f)
            if (weight <= 0f) continue
            val tip = (abs(u - rig.hipX) * 2f).coerceIn(.2f, 1f)
            pv += MathUtils.sin(pose.time * 9f + u * 14f) * f.amount * pose.flutter * weight * tip
            pu += MathUtils.cos(pose.time * 7f + v * 10f) * f.amount * .5f * pose.flutter * weight * tip
        }
        // Into world space: anchor on the feet, mirror, scale, rotate about the feet.
        var lx = (pu - art.anchorX) * w
        if (flip) lx = -lx
        val ly = pv * h * scaleY
        outX = cx + lx * cos - ly * sin
        outY = y + lx * sin + ly * cos
    }

    fun draw(batch: SpriteBatch, art: ActorArt, rig: Rig, pose: Pose, cx: Float, y: Float, facing: Float, rotation: Float, scaleY: Float) {
        val flip = (facing >= 0) != art.facesRight
        val cos = MathUtils.cosDeg(rotation)
        val sin = MathUtils.sinDeg(rotation)
        for (j in 0..GY) {
            for (i in 0..GX) {
                place(art, rig, pose, i.toFloat() / GX, j.toFloat() / GY, cx, y, flip, cos, sin, scaleY)
                val idx = j * (GX + 1) + i
                vx[idx] = outX
                vy[idx] = outY
            }
        }
        val region = art.region
        val color = batch.packedColor
        val u0 = region.u
        val u1 = region.u2
        // Texture v grows downwards: image bottom (feet) is region.v2.
        val vBottom = region.v2
        val vTop = region.v
        var n = 0
        for (j in 0 until GY) {
            val t0 = vBottom + (vTop - vBottom) * j / GY
            val t1 = vBottom + (vTop - vBottom) * (j + 1) / GY
            for (i in 0 until GX) {
                val s0 = u0 + (u1 - u0) * i / GX
                val s1 = u0 + (u1 - u0) * (i + 1) / GX
                val a = j * (GX + 1) + i
                val b = a + 1
                val c = a + GX + 1
                val d = c + 1
                n = put(n, vx[a], vy[a], color, s0, t0)
                n = put(n, vx[c], vy[c], color, s0, t1)
                n = put(n, vx[d], vy[d], color, s1, t1)
                n = put(n, vx[b], vy[b], color, s1, t0)
            }
        }
        batch.draw(region.texture, quads, 0, n)
    }

    private fun put(n: Int, x: Float, y: Float, color: Float, u: Float, v: Float): Int {
        quads[n] = x; quads[n + 1] = y; quads[n + 2] = color; quads[n + 3] = u; quads[n + 4] = v
        return n + 5
    }

    /**
     * Weight of a limb at (u, v): 1 near the joint→tip segment, fading past [Limb.radius]; zero
     * behind the joint so the torso stays put. Distances use the image aspect ([aspect] = w/h).
     */
    private fun limbWeight(u: Float, v: Float, limb: Limb, aspect: Float): Float {
        val ax = (limb.tipX - limb.pivotX) * aspect
        val ay = limb.tipY - limb.pivotY
        val px = (u - limb.pivotX) * aspect
        val py = v - limb.pivotY
        val len2 = ax * ax + ay * ay
        if (len2 <= 0f) return 0f
        val t = (px * ax + py * ay) / len2
        if (t <= -.05f) return 0f
        val tc = t.coerceIn(0f, 1f)
        val dx = px - ax * tc
        val dy = py - ay * tc
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val radial = if (dist <= limb.radius) 1f else (1f - (dist - limb.radius) / limb.soft).coerceAtLeast(0f)
        // Ease in from the joint so the shoulder bends instead of shearing.
        val along = ((t + .05f) / .2f).coerceIn(0f, 1f)
        return radial * along
    }

    /** 1 inside the box, fading to 0 across [soft] outside it. */
    private fun zone(u: Float, v: Float, x0: Float, x1: Float, y0: Float, y1: Float, soft: Float): Float {
        val dx = maxOf(x0 - u, u - x1, 0f)
        val dy = maxOf(y0 - v, v - y1, 0f)
        val d = maxOf(dx, dy)
        return if (d >= soft) 0f else 1f - d / soft
    }

    companion object {
        const val GX = 12
        const val GY = 10
        /** Foot swing and lift at full stride, in image fractions. */
        const val STRIDE = .13f
        const val LIFT = .09f
    }
}
