package com.manroid.neonrebels.view.animation

import com.manroid.neonrebels.model.CharacterType
import com.manroid.neonrebels.model.GameConfig
import com.manroid.neonrebels.model.Player
import kotlin.math.abs

/** Presentation only. Sample after every fixed step so landings and repeated shots are not lost. */
class CharacterAnimator(private val clips: Map<String, AnimationClip>) {
    var id = "an.idle"
        private set
    var elapsed = 0f
        private set
    var frame = 0
        private set
    private var hero: CharacterType? = null
    private var grounded = true
    private var actionSerial = -1L
    private var shotSerial = -1L
    private var landRemaining = 0f
    private var shotRemaining = 0f
    private var shotSkill = false
    val clip: AnimationClip? get() = clips[id]

    fun update(p: Player, dt: Float) {
        val changedHero = hero != p.type
        val newAction = p.actionSerial != actionSerial
        val newShot = p.shotSerial != shotSerial
        if (changedHero) {
            hero = p.type
            landRemaining = 0f
            shotRemaining = 0f
            grounded = p.grounded
        } else {
            landRemaining = (landRemaining - dt).coerceAtLeast(0f)
            shotRemaining = (shotRemaining - dt).coerceAtLeast(0f)
            if (p.grounded && !grounded) landRemaining = clips["${prefix(p)}.land"]?.duration ?: .12f
        }
        if (newShot && p.shotSerial > 0 && !changedHero) {
            shotSkill = p.lastShotPiercing
            val name = if (shotSkill) "vy.skill" else "vy.shoot"
            shotRemaining = clips[name]?.duration ?: GameConfig.SHOT_INTERVAL
        }
        val action = when {
            !p.activeAlive() -> "death"
            p.hurtTime > 0 -> "hurt"
            p.dashing() -> "skill"
            p.dodging() -> "dodge"
            p.striking() -> "attack${p.comboStep}"
            p.type == CharacterType.VY && shotRemaining > 0 -> if (shotSkill) "skill" else "shoot"
            !p.grounded -> if (p.vy > 0) "jump" else "fall"
            landRemaining > 0 -> "land"
            abs(p.vx) > .1f -> "run"
            else -> "idle"
        }
        val next = "${prefix(p)}.$action"
        val restart = changedHero || next != id ||
            (newAction && (p.striking() || p.dashing() || p.dodging())) ||
            (newShot && (action == "shoot" || (action == "skill" && p.type == CharacterType.VY)))
        elapsed = if (restart) 0f else elapsed + dt * if (action == "run")
            (abs(p.vx) / GameConfig.SPEED).coerceIn(.5f, 1.5f) else 1f
        id = next
        val clip = clip
        frame = if (clip == null) 0 else when {
            p.striking() -> {
                val i = p.comboStep - 1
                val duration = when (p.strikePhase) {
                    Player.StrikePhase.STARTUP -> GameConfig.COMBO_STARTUP[i]
                    Player.StrikePhase.ACTIVE -> GameConfig.COMBO_ACTIVE[i]
                    Player.StrikePhase.RECOVERY -> GameConfig.COMBO_RECOVERY[i]
                }
                clip.phaseFrame(p.strikePhase.ordinal, p.actionTime / duration)
            }
            p.dashing() -> clip.frameAt(p.actionTime / GameConfig.DASH_TIME * clip.duration)
            p.dodging() -> clip.frameAt(p.actionTime / GameConfig.DODGE_TIME * clip.duration)
            else -> clip.frameAt(elapsed)
        }
        grounded = p.grounded
        actionSerial = p.actionSerial
        shotSerial = p.shotSerial
    }

    /** After defeat the simulation has ended; only the death presentation may finish. */
    fun advanceDeath(dt: Float) {
        if (!id.endsWith(".death")) return
        elapsed += dt.coerceIn(0f, .1f)
        frame = clip?.frameAt(elapsed) ?: 0
    }

    private fun prefix(p: Player) = if (p.type == CharacterType.AN) "an" else "vy"
}
