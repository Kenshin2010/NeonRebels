package com.manroid.neonrebels

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.manroid.neonrebels.progress.ChapterDefinition
import com.manroid.neonrebels.progress.GameData
import com.manroid.neonrebels.progress.LevelDefinition
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette
import kotlin.math.sin

/**
 * Campaign map: five zones on a route, a detail panel for the focused zone, its three levels,
 * and PLAY / WORKSHOP / BACK. Picking a zone or level only changes the selection; PLAY
 * re-checks the level with [com.manroid.neonrebels.progress.CampaignService] before leaving.
 */
class CampaignScreen(game: MainGame) : MenuBase(game, "map") {
    private val chapters = GameData.campaign.chapters
    private var chapter: ChapterDefinition = GameData.campaign.chapterOf(game.campaign.selectedLevel())
    private var level: LevelDefinition = game.campaign.selectedLevel()
    private var components = ""
    private var status = ""
    private var reward = ""
    private var best = ""
    private var zoneLine = ""
    private var lockLine = ""

    private val playButton: UiButton
    private val zoneButtons = ArrayList<UiButton>()
    private val tileButtons = ArrayList<UiButton>()

    init {
        button(30f, 34f, 110f, 62f, UiButton.GHOST, NeonPainter.ICON_BACK) { back() }
        button(1066f, 34f, 184f, 62f, UiButton.SECONDARY, NeonPainter.ICON_WRENCH, ui.tr("map.workshop")) {
            navigate { game.openWorkshop(this) }
        }
        for (i in chapters.indices) {
            zoneButtons += button(PLATE_X[i] - 105, NODE_Y[i] - 40, 210f, PLATE_Y[i] + 22 - (NODE_Y[i] - 40),
                UiButton.CUSTOM) { focusChapter(chapters[i]) }
        }
        for (k in 0 until 3) {
            tileButtons += button(TILE_X + k * TILE_STEP, TILE_TOP, TILE, TILE, UiButton.CUSTOM) {
                selectLevel(chapter.levels[k])
            }
        }
        playButton = button(962f, 604f, 266f, 74f, UiButton.PRIMARY, NeonPainter.ICON_PLAY, ui.tr("map.play")) { play() }
    }

    override fun onShown() {
        chapter = GameData.campaign.chapterOf(game.campaign.selectedLevel())
        level = game.campaign.selectedLevel()
        refresh()
    }

    private fun focusChapter(next: ChapterDefinition) {
        chapter = next
        selectLevel(game.campaign.levelForChapter(next))
    }

    private fun selectLevel(next: LevelDefinition) {
        if (!game.campaign.select(next.id)) toast("menu.saveFailed")
        level = next
        refresh()
    }

    private fun label(l: LevelDefinition) = "${GameData.campaign.chapterOf(l).number}-${l.number}"

    private fun zoneName(c: ChapterDefinition) = ui.tr("map.zone${c.number}")

    private fun refresh() {
        val campaign = game.campaign
        components = game.progress.state.components.toString()
        val s = campaign.status(level.id)!!
        val reason = campaign.lockReason(level.id)
        status = when {
            reason != null && reason.bossOfPreviousChapter -> game.text.format("map.lockedChapter",
                label(reason.requiredLevel), zoneName(GameData.campaign.chapterOf(reason.requiredLevel)))
            reason != null -> game.text.format("map.lockedLevel", label(reason.requiredLevel))
            !s.implemented -> ui.tr("map.comingSoon")
            s.completed -> ui.tr("map.cleared")
            else -> ui.tr("map.ready")
        }
        val (base, bonus) = campaign.rewardPreview(level.id)
        reward = if (bonus > 0) game.text.format("map.rewardFirst", base, bonus) else game.text.format("map.reward", base)
        best = campaign.bestTime(level.id)?.let { game.text.format("map.best", formatTime(it)) } ?: ""
        val cleared = chapter.levels.count { it.id in game.progress.state.completedLevels }
        zoneLine = game.text.format("map.zoneProgress", chapter.number, chapters.size, cleared, chapter.levels.size)
        lockLine = if (campaign.chapterUnlocked(chapter)) {
            ui.tr(if (chapter.bossLevel.id in game.progress.state.completedLevels) "map.bossDone" else "map.bossPending")
        } else {
            game.text.format("map.zoneLocked", zoneName(chapters[chapter.number - 2]))
        }
        playButton.enabled = s.playable
        playButton.disabledHint = status
    }

    private fun play() {
        // The button state is only a hint; the service decides.
        if (!game.campaign.canPlay(level.id)) {
            toastText(status)
            return
        }
        val id = level.id
        navigate { game.open(TeamScreen(game, id)) }
    }

    override fun back() = navigate { game.open(MenuScreen(game)) }

    override fun key(keycode: Int) {
        val index = chapters.indexOf(chapter)
        when (keycode) {
            Input.Keys.ENTER, Input.Keys.SPACE -> click(playButton)
            Input.Keys.LEFT -> if (index > 0) focusChapter(chapters[index - 1])
            Input.Keys.RIGHT -> if (index < chapters.size - 1) focusChapter(chapters[index + 1])
            Input.Keys.U -> navigate { game.openWorkshop(this) }
        }
    }

    override fun drawShapes() {
        neon.panel(876f, ty(96f), 176f, 62f, 12f, neon.tint(Palette.BACK, .9f), Palette.CYAN, 3f)
        neon.gear(906f, ty(65f), 14f, Palette.CYAN, Palette.BACK)
        for (i in chapters.indices) drawZone(i)
        neon.panel(30f, ty(704f), 1220f, 246f, 22f, neon.tint(Palette.PANEL, .95f), Palette.CYAN, 3f)
        neon.rect(60f, ty(514f), 180f, 3f, Palette.CYAN)
        neon.rect(246f, ty(514f), 34f, 3f, Palette.PINK)
        for (k in 0 until 3) drawTile(k)
    }

    private fun drawZone(i: Int) {
        val c = chapters[i]
        val unlocked = game.campaign.chapterUnlocked(c)
        val playable = c.levels.any { game.campaign.canPlay(it.id) }
        val cleared = c.levels.all { it.id in game.progress.state.completedLevels }
        val selected = c === chapter
        val accent = when {
            !unlocked -> Palette.MUTED
            cleared -> Palette.GREEN
            playable -> Palette.CYAN
            else -> Palette.ORANGE
        }
        val x = NODE_X[i]
        val y = ty(NODE_Y[i])
        if (selected) neon.circle(x, y, 40f, neon.tint(Palette.GOLD, .18f + .08f * sin(time * 4f)))
        neon.circle(x, y, 27f, neon.tint(if (isPressed(zoneButtons[i])) Palette.GROUND else Palette.BACK, .92f))
        neon.ring(x, y, 27f, 3f, if (selected) Palette.GOLD else accent)
        when {
            !unlocked -> neon.lock(x, y, 12f, Palette.SILVER, Palette.BACK)
            cleared -> neon.check(x, y, 13f, Palette.GREEN)
            playable -> neon.circle(x, y, 10f, Color.WHITE)
            else -> neon.dots(x, y, 12f, Palette.ORANGE)
        }
        neon.slanted(PLATE_X[i] - 108, ty(PLATE_Y[i] + 20), 200f, 40f, 16f, neon.tint(Palette.BACK, .9f),
            if (selected) Palette.GOLD else accent, if (selected) 3f else 2f)
    }

    private fun drawTile(k: Int) {
        val l = chapter.levels[k]
        val s = game.campaign.status(l.id)!!
        val b = tileButtons[k]
        val selected = l === level
        val accent = when {
            selected -> Palette.GOLD
            s.completed -> Palette.GREEN
            s.playable -> Palette.CYAN
            else -> Palette.MUTED
        }
        val oy = if (isPressed(b)) -2f else 0f
        if (selected) neon.panel(b.x - 5, b.y - 5 + oy, b.w + 10, b.h + 10, 16f, null, neon.tint(Palette.GOLD, .3f), 5f)
        neon.panel(b.x, b.y + oy, b.w, b.h, 12f, neon.tint(if (selected) GOLD_DARK else Palette.BACK, .95f), accent,
            if (selected) 4f else 2f)
        val cx = b.x + b.w / 2
        val cy = b.y + 34 + oy
        when {
            !s.unlocked -> neon.lock(cx, cy, 13f, Palette.SILVER, Palette.BACK)
            !s.implemented -> neon.dots(cx, cy, 13f, Palette.ORANGE)
            s.completed -> neon.check(cx, cy, 18f, Palette.GREEN)
            else -> neon.play(cx, cy, 16f, Palette.GOLD)
        }
    }

    override fun drawText() {
        ui.label(ui.tr("map.title"), 640f, ty(62f), 2.4f, Color.WHITE)
        ui.label(components, 1034f, ty(65f), 1.5f, Color.WHITE, align = 1, slanted = false)
        for (i in chapters.indices) {
            val unlocked = game.campaign.chapterUnlocked(chapters[i])
            ui.label(zoneName(chapters[i]), PLATE_X[i], ty(PLATE_Y[i]), 1.1f, if (unlocked) Color.WHITE else Palette.SILVER)
        }
        val unlocked = game.campaign.chapterUnlocked(chapter)
        ui.label(zoneName(chapter), 60f, ty(492f), 2f, Color.WHITE, align = -1)
        ui.label(zoneLine, 60f, ty(536f), .95f, Palette.CYAN, align = -1, slanted = false)
        ui.label(ui.tr("map.desc${chapter.number}"), 60f, ty(566f), .95f, Palette.SILVER, align = -1, slanted = false)
        ui.label(ui.tr("map.goal${chapter.number}"), 60f, ty(596f), .95f, Color.WHITE, align = -1, slanted = false)
        ui.label(lockLine, 60f, ty(626f), .95f, if (unlocked) Palette.ORANGE else Palette.RED, align = -1, slanted = false)
        if (game.campaign.allContentCleared()) {
            ui.label(ui.tr("map.allCleared"), 60f, ty(664f), .95f, Palette.GOLD, align = -1, slanted = false)
        }
        for (k in 0 until 3) {
            val l = chapter.levels[k]
            val b = tileButtons[k]
            val s = game.campaign.status(l.id)!!
            val oy = if (isPressed(b)) -2f else 0f
            ui.label(label(l), b.x + b.w / 2, b.y + b.h - 24 + oy, 1.6f, if (s.unlocked) Color.WHITE else Palette.SILVER)
            if (l.boss) ui.label(ui.tr("map.boss"), b.x + b.w / 2, b.y + 60 + oy, .85f, Palette.RED)
            else if (!s.implemented) ui.label(ui.tr("map.soon"), b.x + b.w / 2, b.y + 60 + oy, .75f, Palette.ORANGE)
        }
        ui.label(status, TILE_X, ty(616f), .95f, if (game.campaign.canPlay(level.id)) Palette.GREEN else Palette.ORANGE,
            align = -1, slanted = false)
        ui.label(reward, TILE_X, ty(646f), .95f, Palette.GOLD, align = -1, slanted = false)
        ui.label(game.text.format("map.levelTitle", label(level)), 1095f, ty(500f), 1.8f, Color.WHITE)
        ui.label(ui.tr(if (level.boss) "map.typeBoss" else "map.typeNormal"), 1095f, ty(536f), 1f,
            if (level.boss) Palette.RED else Palette.CYAN, slanted = false)
        if (best.isNotEmpty()) ui.label(best, 1095f, ty(566f), 1f, Palette.GREEN, slanted = false)
    }

    private companion object {
        val NODE_X = floatArrayOf(175f, 445f, 688f, 925f, 1140f)
        val NODE_Y = floatArrayOf(348f, 300f, 338f, 338f, 296f)
        val PLATE_X = floatArrayOf(182f, 446f, 691f, 922f, 1146f)
        val PLATE_Y = floatArrayOf(405f, 375f, 395f, 393f, 366f)
        const val TILE_X = 560f
        const val TILE_TOP = 478f
        const val TILE = 106f
        const val TILE_STEP = 124f
        val GOLD_DARK = Color(.3f, .17f, .04f, 1f)
    }
}
