package com.manroid.neonrebels.model

/** One line: who speaks and the localization key of the text. */
class DialogueLine(val speaker: Speaker, val key: String)

enum class Speaker { LINH, AN, VY }

/** A scripted conversation with a stable id; [action] always runs when it ends, even if skipped. */
class DialogueScript(val id: String, val lines: List<DialogueLine>, val action: DialogueAction = DialogueAction.NONE)

enum class DialogueAction { NONE, START_TUTORIAL }

object Dialogues {
    const val INTRO = "streets_01.intro"
    const val BOSS = "streets_01.boss"
    const val BOSS_DOWN = "streets_01.boss_down"

    val all = listOf(
        DialogueScript(INTRO, listOf(
            DialogueLine(Speaker.LINH, "dlg.intro1"),
            DialogueLine(Speaker.LINH, "dlg.intro2"),
            DialogueLine(Speaker.AN, "dlg.intro3"),
            DialogueLine(Speaker.VY, "dlg.intro4"),
        ), DialogueAction.START_TUTORIAL),
        DialogueScript(BOSS, listOf(
            DialogueLine(Speaker.LINH, "dlg.boss1"),
            DialogueLine(Speaker.LINH, "dlg.boss2"),
        )),
        DialogueScript(BOSS_DOWN, listOf(
            DialogueLine(Speaker.LINH, "dlg.bossDown1"),
            DialogueLine(Speaker.VY, "dlg.bossDown2"),
        )),
    )

    fun byId(id: String): DialogueScript = all.first { it.id == id }
}

/**
 * Runs one conversation at a time. Text reveals at [CHARS_PER_SECOND] in UI time. [next] first
 * completes a line still revealing; only a second press moves on, so one tap never both reveals
 * and skips. [skip] ends the conversation; the caller then runs the script's action either way.
 */
class DialogueController {
    var script: DialogueScript? = null
        private set
    var index = 0
        private set
    var revealed = 0f
        private set

    val active: Boolean get() = script != null
    val line: DialogueLine? get() = script?.lines?.getOrNull(index)

    fun open(s: DialogueScript) {
        script = s
        index = 0
        revealed = 0f
    }

    fun update(uiDt: Float) {
        if (active) revealed += uiDt * CHARS_PER_SECOND
    }

    fun visibleChars(length: Int): Int = minOf(length, revealed.toInt())

    /** Returns the finished script when the last line is passed, else null. */
    fun next(lineLength: Int): DialogueScript? {
        val s = script ?: return null
        if (revealed < lineLength) {
            revealed = lineLength.toFloat()
            return null
        }
        index++
        revealed = 0f
        if (index >= s.lines.size) {
            script = null
            return s
        }
        return null
    }

    fun skip(): DialogueScript? {
        val s = script
        script = null
        return s
    }

    companion object {
        const val CHARS_PER_SECOND = 45f
    }
}
