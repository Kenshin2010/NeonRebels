package com.manroid.neonrebels.model

/**
 * The run's state machine around [World]: LOADING → INTRO → PLAYING ⇄ DIALOGUE, PAUSED (remembers
 * the state it interrupted), VICTORY / DEFEAT (terminal, mutually exclusive), EXITING.
 * Only PLAYING advances the simulation ([World.paused] mirrors that), so dialogue, intro and pause
 * freeze enemies, shots, damage, cooldowns and the run clock. UI animation uses its own time.
 *
 * Story dialogues are queued by [World] events and open only at a safe moment (grounded, no
 * action, not stunned), so the game never freezes mid-dodge.
 */
class CombatSession(
    val world: World,
    private val story: Boolean,
    tutorialRecord: Map<String, String>,
    onTutorialRecord: (String, String) -> Unit,
) {
    enum class State { LOADING, INTRO, PLAYING, DIALOGUE, PAUSED, VICTORY, DEFEAT, EXITING }

    var state = State.LOADING
        private set
    var pausedFrom: State? = null
        private set
    val dialogue = DialogueController()
    val tutorial = TutorialController(tutorialRecord, onTutorialRecord)
    private val queued = ArrayDeque<String>()
    private val seen = HashSet<String>()
    private var introTime = 0f
    /** Last notice (localization key) and how long it stays on screen, in UI time. */
    var notice: String? = null
        private set
    var noticeTime = 0f
        private set

    init {
        removeDummyIfDone()
        sync()
    }

    /** The training target stays only until the attack step is done or the tutorial is skipped. */
    private fun removeDummyIfDone() {
        val step = tutorial.current
        val needed = tutorial.status(TutorialController.Step.ATTACK) == null &&
            (step == null || step.ordinal <= TutorialController.Step.ATTACK.ordinal)
        if (!needed) world.dummy.remove()
    }

    fun begin() {
        if (state != State.LOADING) return
        state = State.INTRO
        introTime = INTRO_SECONDS
        sync()
    }

    /** UI-time update: intro card, dialogue reveal, notices. Never advances the simulation. */
    fun update(uiDt: Float) {
        if (noticeTime > 0) noticeTime -= uiDt
        when (state) {
            State.INTRO -> {
                introTime -= uiDt
                if (introTime <= 0f) {
                    state = State.PLAYING
                    if (story) queue(Dialogues.INTRO) else tutorial.start(world)
                    openQueuedIfSafe()
                }
            }
            State.DIALOGUE -> dialogue.update(uiDt)
            else -> Unit
        }
        sync()
    }

    /** Called after each fixed step while PLAYING. */
    fun afterStep() {
        if (state != State.PLAYING) return
        for (i in 0 until world.eventCount()) onEvent(world.eventAt(i))
        world.clearEvents()
        tutorial.update(world)
        removeDummyIfDone()
        when (world.result) {
            World.Result.WON -> finish(State.VICTORY)
            World.Result.LOST -> finish(State.DEFEAT)
            World.Result.PLAYING -> openQueuedIfSafe()
        }
        sync()
    }

    private fun onEvent(event: Int) {
        when (event) {
            World.EVENT_HEALED -> notice("notice.healed")
            World.EVENT_ARENA_LOCKED -> {
                notice("notice.arena")
                if (story) queue(Dialogues.BOSS)
            }
            World.EVENT_BOSS_DEFEATED -> {
                notice("notice.gate")
                if (story) queue(Dialogues.BOSS_DOWN)
            }
            World.EVENT_FELL -> notice("notice.fell")
        }
    }

    private fun notice(key: String) {
        notice = key
        noticeTime = NOTICE_SECONDS
    }

    private fun queue(id: String) {
        if (seen.add(id)) queued.addLast(id)
    }

    private fun safe(): Boolean {
        val p = world.player
        return p.grounded && p.action == Player.Action.NONE && p.hurtTime == 0f && !p.dead()
    }

    private fun openQueuedIfSafe() {
        if (state != State.PLAYING || queued.isEmpty() || !safe()) return
        dialogue.open(Dialogues.byId(queued.removeFirst()))
        state = State.DIALOGUE
    }

    fun dialogueNext(lineLength: Int) {
        if (state != State.DIALOGUE) return
        dialogue.next(lineLength)?.let { endDialogue(it) }
    }

    fun dialogueSkip() {
        if (state != State.DIALOGUE) return
        dialogue.skip()?.let { endDialogue(it) }
    }

    private fun endDialogue(script: DialogueScript) {
        // Required actions run whether the player read or skipped the conversation.
        if (script.action == DialogueAction.START_TUTORIAL) tutorial.start(world)
        state = State.PLAYING
        openQueuedIfSafe()
        sync()
    }

    fun pause() {
        if (state == State.PLAYING || state == State.DIALOGUE || state == State.INTRO) {
            pausedFrom = state
            state = State.PAUSED
        }
        sync()
    }

    /** Back to exactly what was interrupted (a dialogue stays open). */
    fun resume() {
        if (state != State.PAUSED) return
        state = pausedFrom ?: State.PLAYING
        pausedFrom = null
        sync()
    }

    fun skipTutorial() {
        tutorial.skipAll()
        removeDummyIfDone()
    }

    fun exit() {
        state = State.EXITING
        sync()
    }

    private fun finish(end: State) {
        if (state == State.VICTORY || state == State.DEFEAT) return
        state = end
        queued.clear()
    }

    private fun sync() {
        world.paused = state != State.PLAYING
    }

    val playing: Boolean get() = state == State.PLAYING
    val ended: Boolean get() = state == State.VICTORY || state == State.DEFEAT

    companion object {
        const val INTRO_SECONDS = 1.2f
        const val NOTICE_SECONDS = 2.5f
    }
}
