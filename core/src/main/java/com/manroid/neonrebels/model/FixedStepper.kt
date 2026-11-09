package com.manroid.neonrebels.model

import kotlin.math.max
import kotlin.math.min

class FixedStepper {
    private var accumulator = 0f

    /**
     * Runs as many fixed steps as [delta] allows (bounded). [afterStep] runs after each step; if
     * it stops play (dialogue, pause, result) the remaining steps of this frame are dropped.
     */
    fun advance(world: World, controls: Controls, delta: Float, afterStep: Runnable? = null): Int {
        if (world.paused || world.result != World.Result.PLAYING) {
            reset()
            controls.clear()
            return 0
        }
        accumulator += max(0f, min(delta, GameConfig.STEP * GameConfig.MAX_STEPS))
        var count = 0
        while (accumulator >= GameConfig.STEP && count < GameConfig.MAX_STEPS) {
            world.update(controls, GameConfig.STEP)
            accumulator -= GameConfig.STEP
            count++
            afterStep?.run()
            if (world.paused || world.result != World.Result.PLAYING) {
                reset()
                break
            }
        }
        return count
    }

    fun reset() {
        accumulator = 0f
    }
}
