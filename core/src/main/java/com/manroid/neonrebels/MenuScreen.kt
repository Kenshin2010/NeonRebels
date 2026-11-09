package com.manroid.neonrebels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.manroid.neonrebels.progress.ProgressRepository
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette
import kotlin.math.sin

/**
 * Main menu. States: loading (start blocked), no campaign (NEW GAME is primary; continue, map
 * and workshop disabled), campaign in progress, and unreadable save (retry or explicit restart).
 */
class MenuScreen(game: MainGame) : MenuBase(game, "menu") {
    private val continueButton = button(X, 292f, W, H, UiButton.SECONDARY, NeonPainter.ICON_PLAY, ui.tr("menu.continue")) { continueCampaign() }
    private val newButton = button(X, 364f, W, H, UiButton.SECONDARY, NeonPainter.ICON_GAMEPAD, ui.tr("menu.newGame")) { newGame() }
    private val mapButton = button(X, 436f, W, H, UiButton.SECONDARY, NeonPainter.ICON_MAP, ui.tr("menu.map")) {
        navigate { game.open(CampaignScreen(game)) }
    }
    private val shopButton = button(X, 508f, W, H, UiButton.SECONDARY, NeonPainter.ICON_WRENCH, ui.tr("menu.workshop")) {
        navigate { game.openWorkshop(this) }
    }
    private val settingsButton = button(X, 580f, W, H, UiButton.SECONDARY, NeonPainter.ICON_GEAR, ui.tr("menu.settings")) {
        modal = SETTINGS
    }

    // Modal widgets.
    private val touchToggle = button(430f, 300f, 420f, 62f, UiButton.SECONDARY, modal = true) {
        game.save.setShowControls(!game.save.showControls())
        refreshLabels()
    }
    private val modalCancel = button(370f, 440f, 260f, 64f, UiButton.SECONDARY, NeonPainter.ICON_CLOSE, "", modal = true) { cancelModal() }
    private val modalConfirm = button(650f, 440f, 260f, 64f, UiButton.PRIMARY, NeonPainter.ICON_CHECK, "", modal = true) { confirmModal() }

    private var status: ProgressRepository.LoadStatus? = null
    private var loadingFrames = 0
    private var progressLine = ""

    override fun onShown() {
        refreshState()
    }

    private fun started() = game.campaign.started()

    private fun refreshState() {
        val ready = game.progressChecked && game.progress.loaded
        val started = ready && started()
        newButton.enabled = ready
        for (b in listOf(continueButton, mapButton, shopButton)) {
            b.enabled = started
            b.disabledHint = ui.tr(if (ready) "menu.needCampaign" else "menu.loading")
        }
        newButton.disabledHint = ui.tr("menu.loading")
        // The primary (gold) action is CONTINUE with a campaign, NEW GAME without one.
        replaceStyle(continueButton, started)
        replaceStyle(newButton, ready && !started)
        progressLine = when {
            !ready -> ""
            !started -> ui.tr("menu.noCampaign")
            else -> game.text.format("menu.progress", game.progress.state.completedLevels.size,
                game.progress.state.components)
        }
        refreshLabels()
    }

    private fun replaceStyle(b: UiButton, primary: Boolean) {
        b.style = if (primary) UiButton.PRIMARY else UiButton.SECONDARY
    }

    private fun refreshLabels() {
        touchToggle.label = ui.tr(if (game.save.showControls()) "menu.touchOn" else "menu.touchOff")
        when (modal) {
            CONFIRM_NEW -> {
                modalCancel.label = ui.tr("menu.cancel"); modalConfirm.label = ui.tr("menu.confirmYes")
            }
            CORRUPT -> {
                modalCancel.label = ui.tr("menu.retry"); modalConfirm.label = ui.tr("menu.corruptNew")
            }
            EXIT -> {
                modalCancel.label = ui.tr("menu.cancel"); modalConfirm.label = ui.tr("menu.exit")
            }
        }
        modalCancel.visible = modal != SETTINGS
        modalConfirm.visible = true
        touchToggle.visible = modal == SETTINGS
        if (modal == SETTINGS) modalConfirm.label = ui.tr("menu.close")
        modalConfirm.icon = if (modal == EXIT) NeonPainter.ICON_EXIT else NeonPainter.ICON_CHECK
        modalCancel.icon = if (modal == CORRUPT) NeonPainter.ICON_RETRY else NeonPainter.ICON_CLOSE
    }

    private fun openModal(id: Int) {
        modal = id
        refreshLabels()
    }

    private fun continueCampaign() {
        val level = game.campaign.continueLevel()
        game.campaign.select(level.id)
        navigate { game.open(CampaignScreen(game)) }
    }

    private fun newGame() {
        if (started()) openModal(CONFIRM_NEW) else startNewCampaign()
    }

    private fun startNewCampaign() {
        if (game.campaign.newCampaign()) {
            modal = 0
            navigate { game.open(CampaignScreen(game)) }
        } else toast("menu.saveFailed")
    }

    private fun cancelModal() {
        when (modal) {
            CORRUPT -> load()
            else -> closeModal()
        }
    }

    private fun confirmModal() {
        when (modal) {
            CONFIRM_NEW, CORRUPT -> startNewCampaign()
            EXIT -> Gdx.app.exit()
            else -> closeModal()
        }
    }

    override fun closeModal() {
        // The corrupt-save dialog cannot be dismissed: a choice is required.
        if (modal == CORRUPT) return
        modal = 0
        refreshState()
    }

    override fun back() = openModal(EXIT)

    override fun key(keycode: Int) {
        when (keycode) {
            Input.Keys.ENTER, Input.Keys.SPACE -> when {
                modal != 0 -> click(modalConfirm)
                started() -> click(continueButton)
                else -> click(newButton)
            }
            Input.Keys.H -> if (modal == 0) click(touchToggle)
        }
    }

    private fun load() {
        status = game.loadProgress()
        modal = 0
        when (status) {
            ProgressRepository.LoadStatus.CORRUPT -> openModal(CORRUPT)
            ProgressRepository.LoadStatus.RECOVERED_FROM_BACKUP -> toast("menu.recovered")
            else -> Unit
        }
        refreshState()
    }

    override fun render(delta: Float) {
        // Show the loading state for a frame before reading the save.
        if (!game.progressChecked && ++loadingFrames == 2) load()
        super.render(delta)
    }

    override fun drawBackground() {
        // Slow breathing zoom on the key art.
        val zoom = 1.015f + .015f * sin(time * .35f)
        val w = WIDTH * zoom
        val h = HEIGHT * zoom
        game.batch.draw(background, (WIDTH - w) * .3f, (HEIGHT - h) / 2, w, h)
    }

    override fun drawText() {
        ui.label(ui.tr("menu.subtitle"), 362f, ty(262f), 1.3f, Palette.GOLD)
        if (!game.progressChecked) {
            val dots = ".".repeat(1 + (time * 3).toInt() % 3)
            ui.label(ui.tr("menu.loading") + dots, X + 10, ty(668f), 1.2f, Palette.CYAN, align = -1, slanted = false)
        } else {
            ui.label(progressLine, X + 10, ty(668f), 1f, Palette.SILVER, align = -1, slanted = false)
        }
        ui.label("v${game.version}", 1262f, ty(700f), .85f, Palette.MUTED, align = 1, slanted = false)
    }

    override fun drawModalShapes() {
        val edge = if (modal == CORRUPT || modal == EXIT) Palette.ORANGE else Palette.CYAN
        neon.panel(330f, ty(540f), 620f, 360f, 22f, Palette.PANEL, edge, 3f)
    }

    override fun drawModalText() {
        val (title, note) = when (modal) {
            SETTINGS -> "menu.settings" to "menu.settingsNote"
            CONFIRM_NEW -> "menu.confirmTitle" to "menu.confirmNote"
            CORRUPT -> "menu.corruptTitle" to "menu.corruptNote"
            else -> "menu.exitTitle" to "menu.exitNote"
        }
        ui.label(ui.tr(title), 640f, ty(232f), 1.9f, if (modal == SETTINGS) Color.WHITE else Palette.ORANGE)
        val lines = ui.tr(note).split('|')
        for ((i, line) in lines.withIndex()) {
            ui.label(line, 640f, ty(290f + i * 30 + if (modal == SETTINGS) 110 else 0), 1.05f, Palette.SILVER, slanted = false)
        }
    }

    private companion object {
        const val X = 88f
        const val W = 410f
        const val H = 62f
        const val SETTINGS = 1
        const val CONFIRM_NEW = 2
        const val CORRUPT = 3
        const val EXIT = 4
    }
}
