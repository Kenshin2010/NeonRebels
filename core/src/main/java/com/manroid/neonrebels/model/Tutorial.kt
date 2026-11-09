package com.manroid.neonrebels.model

/**
 * Action tutorial, separate from the story. Steps run in order; each completes on a real gameplay
 * action read from [World] counters (never on a UI tap). Gameplay keeps running. Progress is
 * persisted by the caller through [onRecord] ("done" or "skipped" per step id).
 */
class TutorialController(record: Map<String, String>, private val onRecord: (String, String) -> Unit) {
    enum class Step(val id: String) { MOVE("move"), JUMP("jump"), ATTACK("attack"), DODGE("dodge"), SKILL("skill"), SWAP("swap") }

    private val state = HashMap(record)
    private var started = false
    private var baseline = 0f
    var current: Step? = null
        private set
    /** True while the current step waits for a precondition (e.g. skill still cooling down). */
    var waiting = false
        private set

    fun status(step: Step): String? = state[step.id]
    fun finished(): Boolean = Step.values().all { state.containsKey(it.id) }

    fun start(world: World) {
        if (started) return
        started = true
        advance(world)
    }

    fun update(world: World) {
        val step = current ?: return
        val p = world.player
        if (step == Step.SWAP && !p.reserveAlive()) {
            record(step, SKIPPED, world)
            return
        }
        if (step == Step.SKILL && waiting) {
            if (p.skillCooldown[p.slot()] == 0f) {
                waiting = false
                baseline = counter(step, world)
            }
            return
        }
        val goal = if (step == Step.MOVE) MOVE_DISTANCE else 1f
        if (counter(step, world) - baseline >= goal) record(step, DONE, world)
    }

    /** "Skip tutorial": every remaining step is marked skipped. */
    fun skipAll() {
        for (s in Step.values()) if (!state.containsKey(s.id)) {
            state[s.id] = SKIPPED
            onRecord(s.id, SKIPPED)
        }
        current = null
    }

    private fun record(step: Step, value: String, world: World) {
        state[step.id] = value
        onRecord(step.id, value)
        advance(world)
    }

    private fun advance(world: World) {
        current = Step.values().firstOrNull { !state.containsKey(it.id) }
        val step = current ?: return
        val p = world.player
        waiting = step == Step.SKILL && p.skillCooldown[p.slot()] > 0f
        baseline = counter(step, world)
    }

    private fun counter(step: Step, world: World): Float = when (step) {
        Step.MOVE -> world.distanceMoved
        Step.JUMP -> world.jumps.toFloat()
        Step.ATTACK -> world.hitsLanded.toFloat()
        Step.DODGE -> world.dodges.toFloat()
        Step.SKILL -> world.skillsUsed.toFloat()
        Step.SWAP -> world.swaps.toFloat()
    }

    companion object {
        const val DONE = "done"
        const val SKIPPED = "skipped"
        const val MOVE_DISTANCE = 3f
    }
}
