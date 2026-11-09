package com.manroid.neonrebels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.manroid.neonrebels.input.InputController
import com.manroid.neonrebels.model.CombatSession
import com.manroid.neonrebels.model.FixedStepper
import com.manroid.neonrebels.model.Speaker
import com.manroid.neonrebels.model.World
import com.manroid.neonrebels.progress.GameData
import com.manroid.neonrebels.progress.RewardOutcome
import com.manroid.neonrebels.progress.RunResult
import com.manroid.neonrebels.progress.RunSetup
import com.manroid.neonrebels.view.GameHud
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette
import com.manroid.neonrebels.view.UiPainter
import com.manroid.neonrebels.view.WorldRenderer

/**
 * One run of [setup], driven by [CombatSession] (LOADING → INTRO → PLAYING ⇄ DIALOGUE / PAUSED →
 * VICTORY | DEFEAT → EXITING). Only PLAYING feeds the fixed-step simulation; one input router sends
 * touches to the battle controls, the dialogue or the overlay depending on the state, so a tap is
 * never handled by two layers. The result is settled exactly once through
 * [com.manroid.neonrebels.progress.CampaignService.applyResult] (run id guarded).
 */
class GameScreen(private val game: MainGame, private val setup: RunSetup) : ScreenAdapter() {
    private val world = World(setup.loadout())
    /** Story dialogues play on runs of a level not cleared yet; replays go straight to the action. */
    private val session = CombatSession(world, story = game.campaign.status(setup.levelId)?.completed != true,
        tutorialRecord = game.campaign.tutorialRecord()) { id, value -> game.campaign.recordTutorial(id, value) }
    private val clock = FixedStepper()
    private val renderer = WorldRenderer(game)
    private val afterStep = Runnable { renderer.updateAnimation(world); session.afterStep() }
    private val hud = GameHud(game)
    private val input = InputController(hud.viewport)
    private val ui = UiPainter(game)
    private val neon = NeonPainter(game)
    private val point = Vector2()
    private val linh = Texture(Gdx.files.internal("ui/linh.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val levelTitle: String
    private var suspended = false
    private var leaving = false
    private var time = 0f
    private var outcome: RewardOutcome? = null
    private val lines = ArrayList<String>()
    private var pressed: UiButton? = null
    private val dialogueText = StringBuilder(160)
    private val debugText = StringBuilder(96)

    /** Sub-panel inside the pause overlay. */
    private var panel = PANEL_MAIN

    // Pause.
    private val resume = UiButton(330f, 300f, 300f, 66f, UiButton.PRIMARY, NeonPainter.ICON_PLAY, game.text.get("overlay.resume")) { resumeRun() }
    private val restart = UiButton(650f, 300f, 300f, 66f, UiButton.SECONDARY, NeonPainter.ICON_RETRY, game.text.get("overlay.retry")) { panel = PANEL_RETRY }
    private val settings = UiButton(330f, 390f, 300f, 66f, UiButton.SECONDARY, NeonPainter.ICON_GEAR, game.text.get("menu.settings")) { panel = PANEL_SETTINGS }
    private val toMenu = UiButton(650f, 390f, 300f, 66f, UiButton.SECONDARY, NeonPainter.ICON_EXIT, game.text.get("pause.menu")) { panel = PANEL_MENU }
    // Confirmations.
    private val cancel = UiButton(340f, 430f, 280f, 66f, UiButton.SECONDARY, NeonPainter.ICON_CLOSE, game.text.get("menu.cancel")) { panel = PANEL_MAIN }
    private val confirm = UiButton(660f, 430f, 280f, 66f, UiButton.PRIMARY, NeonPainter.ICON_CHECK, "") { confirmPanel() }
    // Settings.
    private val touchToggle = UiButton(390f, 280f, 500f, 60f, UiButton.SECONDARY) {
        game.save.setShowControls(!game.save.showControls())
        refreshSettings()
    }
    private val debugToggle = UiButton(390f, 356f, 500f, 60f, UiButton.SECONDARY) {
        game.save.setDebugOverlay(!game.save.debugOverlay())
        refreshSettings()
    }
    private val animationPreview = UiButton(920f, 210f, 310f, 50f, UiButton.SECONDARY,
        label = game.text.get("preview.title")) {
        if (game.debugTools) game.openAnimationPreview(this)
    }
    private val settingsBack = UiButton(490f, 440f, 300f, 64f, UiButton.PRIMARY, NeonPainter.ICON_BACK, game.text.get("menu.close")) { panel = PANEL_MAIN }
    // Results.
    private val retry = UiButton(282f, 470f, 290f, 66f, UiButton.PRIMARY, NeonPainter.ICON_RETRY, game.text.get("overlay.retry")) { primaryResult() }
    private val workshop = UiButton(590f, 470f, 200f, 66f, UiButton.SECONDARY, NeonPainter.ICON_WRENCH, game.text.get("map.workshop")) {
        go { game.openWorkshop(this) }
    }
    private val map = UiButton(808f, 470f, 190f, 66f, UiButton.SECONDARY, NeonPainter.ICON_MAP, game.text.get("overlay.map")) {
        go { game.open(CampaignScreen(game)) }
    }
    // Dialogue.
    private val next = UiButton(1004f, 594f, 214f, 66f, UiButton.PRIMARY, NeonPainter.ICON_PLAY, game.text.get("story.next")) { dialogueNext() }
    private val skip = UiButton(1046f, 30f, 204f, 56f, UiButton.GHOST, NeonPainter.ICON_DEPLOY, game.text.get("story.skip")) { session.dialogueSkip() }

    private val pauseButtons = listOf(resume, restart, settings, toMenu)
    private val confirmButtons = listOf(cancel, confirm)
    private val settingsButtons = listOf(touchToggle, debugToggle, settingsBack) +
        if (game.debugTools) listOf(animationPreview) else emptyList()
    private val resultButtons = listOf(retry, workshop, map)
    private val dialogueButtons = listOf(skip, next)
    private val none = emptyList<UiButton>()

    init {
        val level = GameData.campaign.level(setup.levelId)!!
        val chapter = GameData.campaign.chapterOf(level)
        levelTitle = game.text.format("team.level", "${chapter.number}-${level.number}", game.text.get("map.zone${chapter.number}"))
        refreshSettings()
        session.begin()
    }

    private fun refreshSettings() {
        touchToggle.label = game.text.get(if (game.save.showControls()) "menu.touchOn" else "menu.touchOff")
        debugToggle.label = game.text.get(if (game.save.debugOverlay()) "pause.debugOn" else "pause.debugOff")
    }

    /** Buttons that currently receive touches, by state. */
    private fun activeButtons(): List<UiButton> = when (session.state) {
        CombatSession.State.PAUSED -> when (panel) {
            PANEL_MAIN -> pauseButtons
            PANEL_SETTINGS -> settingsButtons
            else -> confirmButtons
        }
        CombatSession.State.VICTORY, CombatSession.State.DEFEAT -> resultButtons
        CombatSession.State.DIALOGUE -> dialogueButtons
        else -> none
    }

    /** One router: battle controls only while PLAYING; otherwise the overlay or dialogue. */
    private val router = object : InputAdapter() {
        private fun battle() = session.playing

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (suspended || leaving) return true
            if (battle()) return input.touchDown(screenX, screenY, pointer, button)
            if (pointer > 0) return true
            hud.viewport.unproject(point.set(screenX.toFloat(), screenY.toFloat()))
            pressed = activeButtons().firstOrNull { it.contains(point.x, point.y) }
            // Dialogue: tapping anywhere outside Skip acts like Next.
            if (pressed == null && session.state == CombatSession.State.DIALOGUE) pressed = next
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (battle()) return input.touchDragged(screenX, screenY, pointer)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (battle()) return input.touchUp(screenX, screenY, pointer, button)
            if (pointer > 0) return true
            val b = pressed
            pressed = null
            if (b == null || suspended || leaving) return true
            hud.viewport.unproject(point.set(screenX.toFloat(), screenY.toFloat()))
            if ((b === next && session.state == CombatSession.State.DIALOGUE) || (b.contains(point.x, point.y) && b.enabled)) b.onClick()
            return true
        }

        override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            pressed = null
            return input.touchCancelled(screenX, screenY, pointer, button)
        }

        override fun keyDown(keycode: Int): Boolean {
            if (suspended || leaving) return true
            if (keycode == Input.Keys.F4 && game.debugTools) {
                pauseRun()
                game.openAnimationPreview(this@GameScreen)
                return true
            }
            if (battle()) return input.keyDown(keycode)
            when (session.state) {
                CombatSession.State.DIALOGUE -> when (keycode) {
                    Input.Keys.ENTER, Input.Keys.SPACE, Input.Keys.J -> dialogueNext()
                    Input.Keys.ESCAPE, Input.Keys.BACK -> pauseRun()
                }
                CombatSession.State.PAUSED -> when (keycode) {
                    Input.Keys.ENTER, Input.Keys.SPACE -> if (panel == PANEL_MAIN) resumeRun() else if (panel != PANEL_SETTINGS) confirmPanel()
                    Input.Keys.ESCAPE, Input.Keys.BACK -> if (panel == PANEL_MAIN) resumeRun() else panel = PANEL_MAIN
                }
                CombatSession.State.VICTORY, CombatSession.State.DEFEAT -> when (keycode) {
                    Input.Keys.ENTER, Input.Keys.SPACE -> retry.onClick()
                    Input.Keys.ESCAPE, Input.Keys.BACK, Input.Keys.M -> map.onClick()
                    Input.Keys.U -> if (workshop.enabled) workshop.onClick()
                }
                else -> Unit
            }
            return true
        }

        override fun keyUp(keycode: Int): Boolean = input.keyUp(keycode)
    }

    override fun show() {
        // Returning from the workshop lands on the result panel again, without re-settling.
        leaving = false
        pressed = null
        input.clear()
        Gdx.input.inputProcessor = router
    }

    override fun render(delta: Float) {
        time += delta
        if (!suspended) {
            if (session.playing) {
                input.sample()
                if (input.takeDebugToggle()) {
                    game.save.setDebugOverlay(!game.save.debugOverlay())
                    refreshSettings()
                }
                if (input.takeSkipTutorial()) session.skipTutorial()
                if (input.takePause()) pauseRun()
                else clock.advance(world, input.controls, delta, afterStep)
            } else {
                clock.reset()
            }
            session.update(delta)
            if (!session.playing) input.clear()
            if (session.ended && outcome == null) settle()
        }
        Gdx.gl.glClearColor(Palette.BACK.r, Palette.BACK.g, Palette.BACK.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        renderer.draw(world, if (session.ended && !suspended) delta else 0f)
        val dialogue = session.state == CombatSession.State.DIALOGUE ||
            (session.state == CombatSession.State.PAUSED && session.pausedFrom == CombatSession.State.DIALOGUE)
        hud.draw(session, input, time, showPause = session.playing)
        when (session.state) {
            CombatSession.State.INTRO -> drawIntro()
            CombatSession.State.DIALOGUE -> drawDialogue()
            CombatSession.State.PAUSED, CombatSession.State.VICTORY, CombatSession.State.DEFEAT -> {
                if (dialogue) drawDialogue()
                drawOverlay()
            }
            else -> Unit
        }
        if (game.save.debugOverlay()) drawDebugInfo()
    }

    // ------------------------------------------------------------------ state changes

    private fun pauseRun() {
        session.pause()
        panel = PANEL_MAIN
        input.clear()
        clock.reset()
    }

    private fun resumeRun() {
        input.clear()
        clock.reset()
        session.resume()
    }

    private fun dialogueNext() {
        val line = session.dialogue.line ?: return
        session.dialogueNext(game.text.get(line.key).length)
    }

    private fun confirmPanel() {
        when (panel) {
            PANEL_RETRY -> go {
                // The cancelled run is settled as a loss (no reward) before a fresh run starts.
                abandonRun()
                val (fresh, _) = game.team.start(game.team.draft(), setup.levelId)
                if (fresh != null) game.play(fresh) else game.open(CampaignScreen(game))
            }
            PANEL_MENU -> go {
                abandonRun()
                game.open(MenuScreen(game))
            }
        }
    }

    private fun abandonRun() {
        session.exit()
        game.campaign.applyResult(RunResult(setup.runId, setup.levelId, false, world.time, world.collectedParts))
    }

    /** Applies the result once; a repeated call is answered from the saved marker. */
    private fun settle() {
        if (outcome?.accepted == true) return
        val won = session.state == CombatSession.State.VICTORY
        val (base, bonus) = game.campaign.rewardPreview(setup.levelId)
        val result = game.campaign.applyResult(RunResult(setup.runId, setup.levelId, won, world.time, world.collectedParts))
        outcome = result
        lines.clear()
        if (!result.accepted) {
            lines += game.text.get("overlay.saveFailed")
        } else if (won) {
            lines += game.text.format("overlay.reward", result.reward)
            lines += if (result.firstClear) game.text.format("overlay.breakdown", base, bonus, world.collectedParts)
            else game.text.format("overlay.breakdownReplay", base, world.collectedParts)
            val t = MenuBase.formatTime(world.time)
            lines += if (result.newRecord) game.text.format("overlay.record", t) else game.text.format("overlay.time", t)
            if (result.nextPlayable == null && game.campaign.allContentCleared()) lines += game.text.get("map.allCleared")
        } else {
            lines += game.text.format("overlay.lostParts", world.collectedParts)
            lines += game.text.get("overlay.rule")
        }
        retry.label = game.text.get(if (result.accepted) "overlay.retry" else "overlay.saveRetry")
        retry.icon = if (result.accepted) NeonPainter.ICON_RETRY else NeonPainter.ICON_CHECK
        workshop.enabled = result.accepted
    }

    private fun primaryResult() {
        if (outcome?.accepted != true) {
            // Same run id: the same result is retried, never duplicated.
            outcome = null
            settle()
            return
        }
        go {
            // A replay is a new run: fresh id, full health, current upgrades, saved squad.
            val (fresh, _) = game.team.start(game.team.draft(), setup.levelId)
            if (fresh != null) game.play(fresh) else game.open(CampaignScreen(game))
        }
    }

    private fun go(action: () -> Unit) {
        if (leaving) return
        leaving = true
        action()
    }

    // ------------------------------------------------------------------ drawing

    private fun begin() {
        val v = hud.viewport
        v.apply()
        game.shapes.projectionMatrix = v.camera.combined
        game.batch.projectionMatrix = v.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawButtons(buttons: List<UiButton>) {
        for (b in buttons) {
            val down = pressed === b
            if (b.style == UiButton.GHOST) {
                neon.panel(b.x, b.y, b.w, b.h, 12f, neon.tint(if (down) Palette.GROUND else Palette.BACK, .9f), Palette.CYAN, 3f)
                if (b.icon != NeonPainter.ICON_NONE) neon.icon(b.icon, b.x + 34f, b.y + b.h / 2, 12f, Palette.CYAN)
            } else {
                neon.button(b.x, b.y, b.w, b.h, b.style, b.enabled, down, false, time)
                if (b.icon != NeonPainter.ICON_NONE) {
                    neon.icon(b.icon, b.x + b.h * .7f, b.y + b.h / 2 + if (down) -2f else 0f, b.h * .26f, labelColor(b))
                }
            }
        }
    }

    private fun drawButtonLabels(buttons: List<UiButton>) {
        for (b in buttons) {
            if (b.label.isEmpty()) continue
            val down = if (pressed === b) -2f else 0f
            if (b.style == UiButton.GHOST) {
                ui.fitLabel(b.label, b.x + 56f, b.x + b.w - 14f, b.y + b.h / 2, 1.3f, Palette.CYAN)
            } else if (b.icon == NeonPainter.ICON_NONE) {
                ui.fitLabel(b.label, b.x + b.h * .4f, b.x + b.w - b.h * .4f, b.y + b.h / 2 + down, b.h * .028f, labelColor(b), centered = true)
            } else {
                ui.fitLabel(b.label, b.x + b.h * 1.2f, b.x + b.w - b.h * .4f, b.y + b.h / 2 + down, b.h * .03f, labelColor(b))
            }
        }
    }

    private fun labelColor(b: UiButton): Color = when {
        !b.enabled -> Palette.MUTED
        b.style == UiButton.PRIMARY -> MenuBase.CREAM
        else -> Color.WHITE
    }

    private fun drawIntro() {
        begin()
        game.shapes.begin(ShapeRenderer.ShapeType.Filled)
        neon.rect(0f, 300f, 1280f, 120f, neon.tint(Palette.BACK, .75f))
        neon.rect(0f, 418f, 1280f, 3f, Palette.CYAN)
        neon.rect(0f, 300f, 1280f, 3f, Palette.PINK)
        game.shapes.end()
        game.batch.begin()
        ui.label(levelTitle, 640f, 380f, 2.2f, Color.WHITE)
        ui.label(game.text.get("hud.goalSignal"), 640f, 334f, 1.05f, Palette.CYAN, slanted = false)
        game.batch.end()
    }

    private fun drawDialogue() {
        val line = session.dialogue.line ?: return
        val text = game.text.get(line.key)
        val visible = session.dialogue.visibleChars(text.length)
        dialogueText.setLength(0)
        dialogueText.append(text, 0, visible)
        begin()
        game.shapes.begin(ShapeRenderer.ShapeType.Filled)
        neon.rect(0f, 0f, 1280f, 720f, neon.tint(Palette.BACK, .45f))
        neon.panel(40f, 26f, 1200f, 168f, 24f, neon.tint(Palette.PANEL, .94f), Palette.CYAN, 3f)
        neon.slanted(292f, 176f, 170f, 40f, 16f, neon.tint(Palette.BACK, .95f), speakerColor(line.speaker), 2f)
        if (session.state == CombatSession.State.DIALOGUE) drawButtons(dialogueButtons)
        if (line.speaker != Speaker.LINH) neon.hex(70f, 40f, 180f, 180f, neon.tint(Palette.BACK, .9f), speakerColor(line.speaker), 3f)
        game.shapes.end()
        game.batch.begin()
        if (line.speaker == Speaker.LINH) {
            // Slight hologram flicker.
            game.batch.setColor(1f, 1f, 1f, .9f + .08f * kotlin.math.sin(time * 5f))
            game.batch.draw(linh, 40f, 26f, 270f, 261f)
        } else {
            game.batch.draw(hud.face(if (line.speaker == Speaker.AN) 0 else 1), 80f, 50f, 160f, 160f)
        }
        game.batch.setColor(1f, 1f, 1f, 1f)
        ui.label(game.text.get("speaker." + line.speaker.name.lowercase()), 384f, 196f, 1.55f, Color.WHITE)
        ui.fitLabel(dialogueText, 330f, 990f, 112f, 1.35f, Color.WHITE)
        val s = session.dialogue.script
        if (s != null) {
            debugText.setLength(0)
            debugText.append(session.dialogue.index + 1).append('/').append(s.lines.size)
            ui.label(debugText, 1140f, 52f, .85f, Palette.MUTED, align = 1, slanted = false)
        }
        if (session.state == CombatSession.State.DIALOGUE) drawButtonLabels(dialogueButtons)
        game.batch.end()
    }

    private fun speakerColor(s: Speaker): Color = when (s) {
        Speaker.LINH -> Palette.CYAN
        Speaker.AN -> Palette.CYAN
        Speaker.VY -> Palette.PINK
    }

    private fun drawOverlay() {
        val buttons = activeButtons()
        begin()
        game.shapes.begin(ShapeRenderer.ShapeType.Filled)
        neon.rect(0f, 0f, 1280f, 720f, neon.tint(Palette.BACK, .72f))
        val edge = when (session.state) {
            CombatSession.State.VICTORY -> Palette.GOLD
            CombatSession.State.DEFEAT -> Palette.RED
            else -> if (panel == PANEL_RETRY || panel == PANEL_MENU) Palette.ORANGE else Palette.CYAN
        }
        neon.panel(250f, 150f, 780f, 430f, 24f, neon.tint(Palette.PANEL, .97f), edge, 3f)
        drawButtons(buttons)
        game.shapes.end()
        game.batch.begin()
        when (session.state) {
            CombatSession.State.PAUSED -> drawPauseText(edge)
            else -> drawResultText(edge)
        }
        drawButtonLabels(buttons)
        game.batch.end()
    }

    private fun drawPauseText(edge: Color) {
        when (panel) {
            PANEL_MAIN -> {
                ui.label(game.text.get("overlay.paused"), 640f, 720f - 206f, 2.2f, edge)
                ui.label(game.text.get("overlay.pausedNote"), 640f, 720f - 252f, 1.05f, Palette.SILVER, slanted = false)
            }
            PANEL_SETTINGS -> ui.label(game.text.get("menu.settings"), 640f, 720f - 210f, 2.1f, Color.WHITE)
            else -> {
                val retryPanel = panel == PANEL_RETRY
                confirm.label = game.text.get(if (retryPanel) "overlay.retry" else "pause.menu")
                ui.label(game.text.get(if (retryPanel) "pause.retryTitle" else "pause.menuTitle"), 640f, 720f - 220f, 2f, edge)
                ui.label(game.text.get("pause.lostNote"), 640f, 720f - 290f, 1.05f, Palette.SILVER, slanted = false)
                ui.label(game.text.get("overlay.rule"), 640f, 720f - 330f, 1f, Palette.ORANGE, slanted = false)
            }
        }
    }

    private fun drawResultText(edge: Color) {
        val won = session.state == CombatSession.State.VICTORY
        ui.label(game.text.get(if (won) "overlay.won" else "overlay.lost"), 640f, 720f - 206f, 2.2f, edge)
        ui.label(game.text.get(if (won) "overlay.wonNote" else "overlay.lostNote"), 640f, 720f - 254f, 1.05f, Palette.SILVER, slanted = false)
        for ((i, line) in lines.withIndex()) {
            val color = when {
                outcome?.accepted != true -> Palette.RED
                i == 0 && won -> Palette.GOLD
                i == 0 -> Palette.ORANGE
                else -> Color.WHITE
            }
            ui.label(line, 640f, 720f - 304f - i * 34, if (i == 0) 1.6f else 1f, color, slanted = i == 0)
        }
    }

    private fun drawDebugInfo() {
        begin()
        game.batch.begin()
        debugText.setLength(0)
        debugText.append("FPS ").append(Gdx.graphics.framesPerSecond)
            .append("  ").append(session.state.name)
            .append("  enemies ").append(world.livingEnemies())
            .append("  shots ").append(world.activeProjectiles())
            .append("  t ").append((world.time * 10).toInt() / 10f)
        ui.label(debugText, 20f, 520f, .9f, Color.YELLOW, align = -1, slanted = false)
        game.batch.end()
    }

    override fun resize(width: Int, height: Int) {
        renderer.viewport.update(width, height, true)
        hud.viewport.update(width, height, true)
        input.clear()
        clock.reset()
    }

    /** Leaving the app pauses at once; coming back waits for the player (a dialogue stays open). */
    override fun pause() {
        suspended = true
        pressed = null
        if (!session.ended) pauseRun()
        input.clear()
        clock.reset()
    }

    override fun resume() {
        suspended = false
        input.clear()
        clock.reset()
    }

    override fun hide() {
        input.clear()
        pressed = null
        Gdx.input.inputProcessor = null
    }

    override fun dispose() {
        renderer.dispose()
        hud.dispose()
        linh.dispose()
    }

    private companion object {
        const val PANEL_MAIN = 0
        const val PANEL_RETRY = 1
        const val PANEL_MENU = 2
        const val PANEL_SETTINGS = 3
    }
}
