package com.manroid.neonrebels

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.manroid.neonrebels.progress.DraftTeam
import com.manroid.neonrebels.progress.GameData
import com.manroid.neonrebels.progress.StatCalculator
import com.manroid.neonrebels.progress.TeamService
import com.manroid.neonrebels.progress.UpgradeType
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette

/**
 * Squad select for [levelId]. Tapping a card only views it; ADD/REMOVE changes the in-memory
 * draft. The draft survives a trip to the workshop (this instance is kept alive) and is
 * discarded on Back; it is saved only when DEPLOY succeeds.
 */
class TeamScreen(game: MainGame, private val levelId: String) : MenuBase(game, "team") {
    private val draft: DraftTeam = game.team.draft()
    private val portraits: Map<String, Texture> = mapOf(
        GameData.AN.id to texture("ui/portrait_an.jpg"),
        GameData.VY.id to texture("ui/portrait_vy.jpg"),
        GameData.KHANG.id to texture("ui/portrait_khang.jpg"),
    )
    private val ids = GameData.characters.map { it.id }
    private var viewed: String = draft.slots[0] ?: GameData.AN.id
    private val levelLine: String
    private var count = ""
    private val statLines = arrayOfNulls<String>(3)
    private val levelTags = arrayOfNulls<String>(3)

    private val cards = ArrayList<UiButton>()
    private val slotButtons = ArrayList<UiButton>()
    private val actionButton: UiButton
    private val deployButton: UiButton

    init {
        val level = GameData.campaign.level(levelId)!!
        val chapter = GameData.campaign.chapterOf(level)
        levelLine = game.text.format("team.level", "${chapter.number}-${level.number}", ui.tr("map.zone${chapter.number}"))
        button(30f, 34f, 110f, 62f, UiButton.GHOST, NeonPainter.ICON_BACK) { back() }
        button(1066f, 34f, 184f, 62f, UiButton.SECONDARY, NeonPainter.ICON_WRENCH, ui.tr("map.workshop")) {
            navigate { game.openWorkshop(this) }
        }
        for (i in ids.indices) cards += button(CARD_X[i], CARD_TOP, CARD_W, CARD_H, UiButton.CUSTOM) { view(ids[i]) }
        for (k in 0..1) {
            slotButtons += button(SLOT_X[k], SLOT_TOP, SLOT_W, SLOT_H, UiButton.CUSTOM) { draft.slots[k]?.let { view(it) } }
        }
        button(322f, 488f, 70f, 56f, UiButton.GHOST, NeonPainter.ICON_SWAP) {
            game.team.swap(draft)
            refresh()
        }
        actionButton = button(730f, 478f, 330f, 64f, UiButton.SECONDARY) { toggleViewed() }
        deployButton = button(880f, 590f, 370f, 82f, UiButton.PRIMARY, NeonPainter.ICON_DEPLOY, ui.tr("team.go")) { deploy() }
    }

    override fun onShown() {
        // Upgrades may have changed in the workshop; the draft itself is kept.
        refresh()
    }

    private fun view(id: String) {
        viewed = id
        refresh()
    }

    private fun toggleViewed() {
        val result = if (viewed in draft) game.team.remove(draft, viewed) else game.team.add(draft, viewed)
        when (result) {
            TeamService.Change.TEAM_FULL -> toast("team.full")
            TeamService.Change.NOT_AVAILABLE -> toast("team.unavailable")
            else -> Unit
        }
        refresh()
    }

    private fun refresh() {
        count = game.text.format("team.squad", draft.count())
        val character = GameData.character(viewed)!!
        val selectable = game.team.selectable(viewed)
        actionButton.enabled = selectable
        actionButton.disabledHint = ui.tr("team.unavailable")
        if (!selectable) {
            actionButton.label = ui.tr("team.locked"); actionButton.icon = NeonPainter.ICON_LOCK
        } else if (viewed in draft) {
            actionButton.label = ui.tr("team.remove"); actionButton.icon = NeonPainter.ICON_MINUS
        } else {
            actionButton.label = ui.tr("team.add"); actionButton.icon = NeonPainter.ICON_PLUS
        }
        val stats = game.team.stats(viewed)!!
        statLines[0] = stats.maxHealth.toString()
        statLines[1] = stats.damage.toString()
        statLines[2] = StatCalculator.formatSeconds(stats.skillCooldown)
        for (t in UpgradeType.values()) {
            levelTags[t.ordinal] = game.text.format("shop.level", game.progress.state.upgradeLevel(character.id, t))
        }
        deployButton.enabled = game.team.valid(draft) && game.campaign.canPlay(levelId)
        deployButton.disabledHint = ui.tr(if (game.campaign.canPlay(levelId)) "team.needTwo" else "team.levelUnavailable")
    }

    private fun deploy() {
        navigate {
            val (setup, error) = game.team.start(draft, levelId)
            if (setup == null) {
                stay()
                toast(when (error) {
                    TeamService.StartError.LEVEL_NOT_PLAYABLE -> "team.levelUnavailable"
                    TeamService.StartError.INVALID_TEAM -> "team.needTwo"
                    else -> "menu.saveFailed"
                })
                return@navigate
            }
            try {
                game.play(setup)
            } catch (e: RuntimeException) {
                // Nothing was completed or rewarded; the player can simply try again.
                stay()
                toast("team.loadFailed")
            }
        }
    }

    override fun back() = navigate { game.open(CampaignScreen(game)) }

    override fun key(keycode: Int) {
        when (keycode) {
            Input.Keys.ENTER -> click(deployButton)
            Input.Keys.NUM_1 -> view(ids[0])
            Input.Keys.NUM_2 -> view(ids[1])
            Input.Keys.NUM_3 -> view(ids[2])
            Input.Keys.SPACE -> click(actionButton)
            Input.Keys.S -> { game.team.swap(draft); refresh() }
        }
    }

    /** key("role", "vy") -> "team.roleVy". */
    private fun key(kind: String, id: String) = "team." + kind + id.replaceFirstChar { it.uppercaseChar() }

    private fun accent(id: String): Color = when (id) {
        GameData.AN.id -> Palette.CYAN
        GameData.VY.id -> Palette.PINK
        else -> Palette.MUTED
    }

    override fun drawArt() {
        for (i in ids.indices) {
            val oy = if (isPressed(cards[i])) 2f else 0f
            spriteFill(portraits.getValue(ids[i]), CARD_X[i] + 6, CARD_TOP + 6 + oy, CARD_W - 12, 196f)
        }
        for (k in 0..1) {
            val id = draft.slots[k] ?: continue
            spriteFill(portraits.getValue(id), SLOT_X[k] + 8, SLOT_TOP + 8, 76f, 76f)
        }
    }

    override fun drawShapes() {
        neon.panel(876f, ty(96f), 176f, 62f, 12f, neon.tint(Palette.BACK, .9f), Palette.CYAN, 3f)
        for (i in ids.indices) drawCard(i)
        for (k in 0..1) {
            val id = draft.slots[k]
            val b = slotButtons[k]
            // Portraits are drawn before the shapes, so an occupied slot only darkens its text area.
            if (id != null) neon.rect(b.x + 90, b.y + 4, b.w - 94, b.h - 8, neon.tint(Palette.BACK, .85f))
            neon.panel(b.x, b.y, b.w, b.h, 12f, if (id == null) neon.tint(Palette.BACK, .85f) else null,
                if (id == null) Palette.MUTED else accent(id), if (k == 0) 3f else 2f)
            if (id == null) neon.icon(NeonPainter.ICON_PLUS, b.x + 46, b.y + b.h / 2, 14f, Palette.MUTED)
        }
        neon.panel(700f, ty(562f), 550f, 438f, 20f, neon.tint(Palette.PANEL, .94f), accent(viewed), 3f)
        neon.rect(730f, ty(262f), 490f, 2f, neon.tint(accent(viewed), .5f))
    }

    private fun drawCard(i: Int) {
        val id = ids[i]
        val b = cards[i]
        val viewedCard = id == viewed
        val selectable = game.team.selectable(id)
        val oy = if (isPressed(b)) -2f else 0f
        if (viewedCard) neon.panel(b.x - 6, b.y - 6 + oy, b.w + 12, b.h + 12, 20f, null, neon.tint(Palette.GOLD, .35f), 6f)
        neon.panel(b.x, b.y + oy, b.w, b.h, 18f, null, if (viewedCard) Palette.GOLD else accent(id), if (viewedCard) 4f else 3f)
        neon.rect(b.x + 6, b.y + 6 + oy, b.w - 12, 84f, neon.tint(Palette.BACK, .88f))
        if (!selectable) {
            neon.rect(b.x + 6, b.y + 90 + oy, b.w - 12, 196f, neon.tint(Palette.BACK, .45f))
            neon.circle(b.x + b.w / 2, b.y + 190 + oy, 30f, neon.tint(Palette.BACK, .8f))
            neon.lock(b.x + b.w / 2, b.y + 190 + oy, 18f, Palette.SILVER, Palette.BACK)
        }
        val slot = draft.slots.indexOf(id)
        if (slot >= 0) {
            neon.circle(b.x + b.w - 26, b.y + b.h - 26 + oy, 20f, accent(id))
        }
    }

    override fun drawText() {
        ui.label(ui.tr("team.title"), 640f, ty(52f), 2.3f, Color.WHITE)
        ui.label(levelLine, 640f, ty(92f), 1f, Palette.CYAN, slanted = false)
        ui.label(count, 964f, ty(65f), 1.35f, if (draft.count() == 2) Palette.GREEN else Palette.ORANGE, slanted = false)
        for (i in ids.indices) {
            val id = ids[i]
            val b = cards[i]
            val oy = if (isPressed(b)) -2f else 0f
            ui.label(ui.tr("team.$id"), b.x + 16, b.y + 62 + oy, 1.9f, if (game.team.selectable(id)) Color.WHITE else Palette.SILVER, align = -1)
            ui.label(ui.tr(key("weapon", id)), b.x + 18, b.y + 26 + oy, .85f, accent(id), align = -1, slanted = false)
            val slot = draft.slots.indexOf(id)
            if (slot >= 0) ui.label((slot + 1).toString(), b.x + b.w - 26, b.y + b.h - 26 + oy, 1.4f, Palette.BACK, slanted = false)
        }
        ui.label(ui.tr("team.lineup"), 40f, ty(452f), 1.2f, Color.WHITE, align = -1)
        for (k in 0..1) {
            val id = draft.slots[k]
            val b = slotButtons[k]
            ui.label(ui.tr(if (k == 0) "team.slot1" else "team.slot2"), b.x + 96, b.y + b.h - 24, .85f,
                if (k == 0) Palette.GOLD else Palette.SILVER, align = -1, slanted = false)
            ui.label(if (id == null) ui.tr("team.empty") else ui.tr("team.$id"), b.x + 96, b.y + 32, 1.5f,
                if (id == null) Palette.MUTED else Color.WHITE, align = -1)
        }
        ui.label(ui.tr("team.hint"), 40f, ty(600f), .9f, Palette.SILVER, align = -1, slanted = false)
        val c = GameData.character(viewed)!!
        ui.label(ui.tr("team.$viewed"), 730f, ty(168f), 2.4f, accent(viewed), align = -1)
        ui.label(ui.tr(key("role", viewed)), 730f, ty(212f), 1f, Color.WHITE, align = -1, slanted = false)
        ui.label(ui.tr(key("skill", viewed)), 730f, ty(242f), 1f, Palette.SILVER, align = -1, slanted = false)
        if (game.team.selectable(viewed)) {
            for (t in UpgradeType.values()) {
                val cy = ty(300f + t.ordinal * 52)
                ui.label(ui.tr(STAT_KEYS[t.ordinal]), 730f, cy, 1.25f, Color.WHITE, align = -1)
                ui.label(levelTags[t.ordinal]!!, 1010f, cy, 1f, Palette.CYAN, align = 1, slanted = false)
                ui.label(statLines[t.ordinal]!!, 1220f, cy, 1.4f, Color.WHITE, align = 1, slanted = false)
            }
            ui.label(ui.tr("team.statsNote"), 730f, ty(446f), .85f, Palette.MUTED, align = -1, slanted = false)
        } else {
            ui.label(ui.tr(if (c.implemented) "team.lockedNote" else "team.unlockKhang"), 730f, ty(320f), 1f, Palette.ORANGE,
                align = -1, slanted = false)
            ui.label(ui.tr("team.previewNote"), 730f, ty(352f), .9f, Palette.SILVER, align = -1, slanted = false)
        }
    }

    private companion object {
        val CARD_X = floatArrayOf(40f, 252f, 464f)
        const val CARD_TOP = 124f
        const val CARD_W = 196f
        const val CARD_H = 296f
        val SLOT_X = floatArrayOf(40f, 404f)
        const val SLOT_TOP = 470f
        const val SLOT_W = 270f
        const val SLOT_H = 92f
        val STAT_KEYS = arrayOf("shop.hp", "shop.damage", "shop.cooldown")
    }
}
