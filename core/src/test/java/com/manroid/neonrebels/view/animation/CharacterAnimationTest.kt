package com.manroid.neonrebels.view.animation

import com.manroid.neonrebels.model.CharacterType
import com.manroid.neonrebels.model.GameConfig
import com.manroid.neonrebels.model.Player
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Properties

class CharacterAnimationTest {
    private fun manifest(): Properties {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        return Properties().apply {
            File(root, "app/src/main/assets/sprites/animations.properties").reader().use { load(it) }
        }
    }
    private fun clips() = AnimationManifest.parse(manifest())

    @Test fun authoredManifestCoversEverySupportedStateWithoutClaimingArtExists() {
        val errors = ArrayList<String>()
        val parsed = AnimationManifest.parse(manifest(), errors::add)
        assertEquals(emptyList<String>(), errors)
        assertEquals(AnimationManifest.ids.toSet(), parsed.keys)
        assertEquals(ClipEnd.HOLD, parsed.getValue("an.death").end)
        assertEquals(ClipEnd.LOOP, parsed.getValue("an.run").end)
    }

    @Test fun variableFrameDurationsLoopAndOneShotsHoldLastFrame() {
        val clip = clips().getValue("an.idle")
        assertEquals(0, clip.frameAt(0f))
        assertEquals(1, clip.frameAt(clip.durations[0] + .001f))
        assertEquals(0, clip.frameAt(clip.duration + .001f))
        val death = clips().getValue("an.death")
        assertEquals(death.frames.lastIndex, death.frameAt(1000f))
        val p = manifest()
        p.remove("an.idle.fps")
        p.setProperty("an.idle.durations", ".01,.02,.03,.04")
        val custom = AnimationManifest.parse(p).getValue("an.idle")
        assertEquals(1, custom.frameAt(.015f))
        assertEquals(2, custom.frameAt(.035f))
    }

    @Test fun malformedClipFallsBackAndDoesNotPreventOtherClipsLoading() {
        for ((key, value) in listOf("an.idle.fps" to "0", "an.idle.frames" to "an_idle_01,an_idle_00",
            "an.idle.durations" to "NaN,.1,.1,.1", "an.idle.frames" to "vy_idle_00,vy_idle_01", "an.attack1.phaseEnds" to "4,2")) {
            val p = manifest(); p.setProperty(key, value)
            val errors = ArrayList<String>()
            val parsed = AnimationManifest.parse(p, errors::add)
            assertEquals(1, errors.size)
            assertFalse(parsed.containsKey(key.substringBeforeLast('.')))
            assertTrue(parsed.containsKey("vy.idle"))
        }
    }

    @Test fun gameplayPhasesSelectDisjointFrameSegmentsIndependentOfFps() {
        val clip = clips().getValue("an.attack1")
        assertEquals(0, clip.phaseFrame(0, 0f))
        assertEquals(1, clip.phaseFrame(0, 1f))
        assertEquals(2, clip.phaseFrame(1, 0f))
        assertEquals(3, clip.phaseFrame(1, 1f))
        assertEquals(4, clip.phaseFrame(2, 0f))
        assertEquals(5, clip.phaseFrame(2, 1f))
        val p = Player()
        val animator = CharacterAnimator(clips())
        p.startStrike(1); animator.update(p, 0f)
        assertTrue(animator.frame in 0..1)
        p.advanceAction(GameConfig.COMBO_STARTUP[0]); animator.update(p, GameConfig.STEP)
        assertTrue(animator.frame in 2..3)
        p.advanceAction(GameConfig.COMBO_ACTIVE[0]); animator.update(p, GameConfig.STEP)
        assertTrue(animator.frame in 4..5)
    }

    @Test fun clockPersistsAcrossStepsAndFreezeAndResetsForCharacterAndNewStrike() {
        val p = Player()
        val a = CharacterAnimator(clips())
        a.update(p, 0f)
        repeat(20) { a.update(p, GameConfig.STEP) }
        val elapsed = a.elapsed
        assertTrue(elapsed > .15f)
        a.update(p, 0f)
        assertEquals(elapsed, a.elapsed, 0f)
        p.swap(false); a.update(p, GameConfig.STEP)
        assertEquals("vy.idle", a.id)
        assertEquals(0f, a.elapsed, 0f)
        p.swap(true); p.startStrike(1); a.update(p, GameConfig.STEP)
        repeat(4) { a.update(p, GameConfig.STEP) }
        assertTrue(a.elapsed > 0f)
        p.startStrike(1); a.update(p, GameConfig.STEP)
        assertEquals(0f, a.elapsed, 0f)
    }

    @Test fun risingFallingLandingAndRunningAreDistinctAndSpeedIsBounded() {
        val p = Player()
        val a = CharacterAnimator(clips())
        a.update(p, 0f)
        p.grounded = false; p.vy = 5f; a.update(p, GameConfig.STEP)
        assertEquals("an.jump", a.id)
        p.vy = -1f; a.update(p, GameConfig.STEP)
        assertEquals("an.fall", a.id)
        p.grounded = true; p.vy = 0f; a.update(p, GameConfig.STEP)
        assertEquals("an.land", a.id)
        repeat(30) { a.update(p, GameConfig.STEP) }
        assertEquals("an.idle", a.id)
        p.vx = GameConfig.SPEED; a.update(p, GameConfig.STEP)
        a.update(p, .1f)
        assertEquals(.1f, a.elapsed, .0001f)
        p.vx *= 100; a.update(p, .1f)
        assertEquals(.25f, a.elapsed, .0001f)
    }

    @Test fun everyShotRestartsAndSkillDoesNotChangeLogicalMuzzleOrDamage() {
        val p = Player(); p.swap(false)
        val a = CharacterAnimator(clips()); a.update(p, 0f)
        val muzzle = p.muzzleX() to p.muzzleY()
        val damage = p.shotDamage(true)
        p.recordShot(false); a.update(p, GameConfig.STEP)
        assertEquals("vy.shoot", a.id)
        a.update(p, .05f)
        p.recordShot(false); a.update(p, GameConfig.STEP)
        assertEquals(0f, a.elapsed, 0f)
        p.recordShot(true); a.update(p, GameConfig.STEP)
        assertEquals("vy.skill", a.id)
        assertEquals(muzzle, p.muzzleX() to p.muzzleY())
        assertEquals(damage, p.shotDamage(true))
        assertEquals(CharacterType.VY, p.type)
    }

    @Test fun deathHasPriorityAndFinishesWithoutRestartingTheWorld() {
        val p = Player(); val a = CharacterAnimator(clips())
        a.update(p, 0f)
        p.hp.fill(0); p.hurtTime = 1f
        a.update(p, GameConfig.STEP)
        assertEquals("an.death", a.id)
        repeat(30) { a.advanceDeath(.1f) }
        assertEquals(clips().getValue("an.death").frames.lastIndex, a.frame)
        val frame = a.frame; a.advanceDeath(0f)
        assertEquals(frame, a.frame)
        assertTrue(p.dead())
    }
}
