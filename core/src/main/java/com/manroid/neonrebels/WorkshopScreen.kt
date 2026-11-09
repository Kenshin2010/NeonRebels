package com.manroid.neonrebels

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.manroid.neonrebels.progress.GameData
import com.manroid.neonrebels.progress.StatCalculator
import com.manroid.neonrebels.progress.UpgradePreview
import com.manroid.neonrebels.progress.UpgradeService
import com.manroid.neonrebels.progress.UpgradeType
import com.manroid.neonrebels.view.NeonPainter
import com.manroid.neonrebels.view.Palette

/**
 * Upgrade workshop. Opened on top of another screen (menu, map, squad or result) and returns to
 * that same instance. Purchases go through [UpgradeService]; the UI only refreshes after the
 * save succeeded. Upgrades apply to runs started afterwards.
 */
class WorkshopScreen(game: MainGame) : MenuBase(game, "shop") {
    private val heart = texture("ui/icon_heart.png")
    private val sword = texture("ui/icon_sword.png")
    private val bolt = texture("ui/icon_bolt.png")
    private val gun = texture("ui/icon_gun.png")
    private val portraits = mapOf(
        GameData.VY.id to texture("ui/portrait_vy.jpg"),
        GameData.KHANG.id to texture("ui/portrait_khang.jpg"),
    )
    private val ids = GameData.characters.map { it.id }
    private var character = GameData.AN.id
    private var row = UpgradeType.DAMAGE
    private lateinit var preview: UpgradePreview
    private val levelText = arrayOfNulls<String>(3)
    private val levels = IntArray(3)
    private var components = ""
    private var current = ""
    private var next = ""
    private var cost = ""
    private var missing = ""
    private var levelLine = ""
    private var flash = 0f

    private val tabs = ArrayList<UiButton>()
    private val rows = ArrayList<UiButton>()
    private val buyButton: UiButton

    init {
        button(30f, 34f, 110f, 62f, UiButton.GHOST, NeonPainter.ICON_BACK) { back() }
        for (i in ids.indices) tabs += button(TAB_X[i], 132f, 136f, 60f, UiButton.CUSTOM) { select(ids[i], row) }
        for (t in UpgradeType.values()) rows += button(470f, ROW_TOP[t.ordinal], 364f, 78f, UiButton.CUSTOM) { select(character, t) }
        buyButton = button(888f, 500f, 346f, 80f, UiButton.PRIMARY, NeonPainter.ICON_UPGRADE, "") { buy() }
        refresh()
    }

    override fun onShown() = refresh()

    private fun select(id: String, type: UpgradeType) {
        character = id
        row = type
        refresh()
    }

    private fun format(type: UpgradeType, value: Float): String =
        if (type == UpgradeType.COOLDOWN) StatCalculator.formatSeconds(value) else value.toInt().toString()

    private fun refresh() {
        val state = game.progress.state
        components = game.text.format("shop.parts", state.components)
        for (t in UpgradeType.values()) {
            levels[t.ordinal] = state.upgradeLevel(character, t)
            levelText[t.ordinal] = game.text.format("shop.levelOf", levels[t.ordinal], GameData.upgrade(t).maxLevel)
        }
        preview = game.upgrades.preview(character, row)
        current = format(row, preview.current)
        next = preview.next?.let { format(row, it) } ?: ""
        cost = preview.cost?.toString() ?: ""
        missing = if (preview.missing > 0) game.text.format("shop.missing", preview.missing) else ""
        levelLine = if (preview.maxed) game.text.format("shop.levelMax", preview.level)
        else game.text.format("shop.levelStep", preview.level, preview.level + 1)
        val unlocked = game.upgrades.canUpgrade(character)
        buyButton.enabled = unlocked && preview.affordable && !game.upgrades.processing
        buyButton.label = ui.tr(when {
            !unlocked -> "shop.locked"
            preview.maxed -> "shop.max"
            !preview.affordable -> "shop.short"
            else -> "shop.upgrade"
        })
        buyButton.icon = if (unlocked) NeonPainter.ICON_UPGRADE else NeonPainter.ICON_LOCK
        buyButton.disabledHint = when {
            !unlocked -> ui.tr("shop.lockedNote")
            preview.maxed -> ui.tr("shop.max")
            else -> missing
        }
    }

    private fun buy() {
        buyButton.busy = true
        val result = game.upgrades.purchase(character, row)
        buyButton.busy = false
        when (result) {
            UpgradeService.Purchase.OK -> {
                flash = 1f
                toast("shop.done")
            }
            UpgradeService.Purchase.INSUFFICIENT -> toastText(missing)
            UpgradeService.Purchase.MAXED -> toast("shop.max")
            UpgradeService.Purchase.LOCKED -> toast("shop.lockedNote")
            UpgradeService.Purchase.SAVE_FAILED -> toast("menu.saveFailed")
            UpgradeService.Purchase.BUSY -> Unit
        }
        refresh()
    }

    override fun back() = navigate { game.returnFromWorkshop(this) }

    override fun key(keycode: Int) {
        val index = ids.indexOf(character)
        when (keycode) {
            Input.Keys.LEFT -> select(ids[(index + ids.size - 1) % ids.size], row)
            Input.Keys.RIGHT, Input.Keys.TAB, Input.Keys.Q -> select(ids[(index + 1) % ids.size], row)
            Input.Keys.UP -> select(character, UpgradeType.values()[(row.ordinal + 2) % 3])
            Input.Keys.DOWN -> select(character, UpgradeType.values()[(row.ordinal + 1) % 3])
            Input.Keys.ENTER, Input.Keys.SPACE -> click(buyButton)
        }
    }

    override fun render(delta: Float) {
        if (flash > 0) flash = (flash - delta * 1.6f).coerceAtLeast(0f)
        super.render(delta)
    }

    private fun accent(id: String): Color = when (id) {
        GameData.AN.id -> Palette.CYAN
        GameData.VY.id -> Palette.PINK
        else -> Palette.MUTED
    }

    private fun icon(type: UpgradeType): Texture = when (type) {
        UpgradeType.HEALTH -> heart
        UpgradeType.DAMAGE -> if (character == GameData.VY.id) gun else sword
        UpgradeType.COOLDOWN -> bolt
    }

    override fun drawArt() {
        // The backdrop already shows An on the pad; other characters get a framed portrait.
        portraits[character]?.let { spriteFill(it, 84f, 222f, 356f, 320f) }
    }

    override fun drawTopArt() {
        for (t in UpgradeType.values()) {
            val tex = icon(t)
            spriteAt(tex, 510f, ROW_TOP[t.ordinal] + 39, 40f / tex.height)
        }
        val tex = icon(row)
        spriteAt(tex, 912f, 176f, 44f / tex.height)
    }

    override fun drawShapes() {
        neon.panel(930f, ty(96f), 320f, 62f, 12f, neon.tint(Palette.BACK, .9f), Palette.CYAN, 3f)
        neon.gear(962f, ty(65f), 15f, Palette.CYAN, Palette.BACK)
        if (character != GameData.AN.id) {
            neon.panel(84f, ty(542f), 356f, 320f, 0f, null, accent(character), 4f)
            if (!game.upgrades.canUpgrade(character)) {
                neon.rect(84f, ty(542f), 356f, 320f, neon.tint(Palette.BACK, .4f))
                neon.circle(262f, ty(330f), 36f, neon.tint(Palette.BACK, .8f))
                neon.lock(262f, ty(330f), 22f, Palette.SILVER, Palette.BACK)
            }
        }
        if (flash > 0) neon.circle(262f, ty(560f), 150f, neon.tint(accent(character), flash * .35f))
        for (i in ids.indices) {
            val b = tabs[i]
            val active = ids[i] == character
            val oy = if (isPressed(b)) -2f else 0f
            neon.slanted(b.x, b.y + oy, b.w - 16, b.h, 16f, if (active) accent(ids[i]) else neon.tint(Palette.BACK, .9f),
                accent(ids[i]), if (active) 3f else 2f)
            if (!game.upgrades.canUpgrade(ids[i])) {
                neon.lock(b.x + 26, b.y + b.h / 2 + oy, 8f, if (active) Palette.BACK else Palette.MUTED, if (active) Palette.MUTED else Palette.BACK)
            }
        }
        neon.panel(452f, ty(600f), 400f, 394f, 18f, neon.tint(Palette.PANEL, .94f), neon.tint(accent(character), .8f), 2f)
        for (t in UpgradeType.values()) {
            val b = rows[t.ordinal]
            val selected = t == row
            val oy = if (isPressed(b)) -2f else 0f
            if (selected) neon.panel(b.x - 4, b.y - 4 + oy, b.w + 8, b.h + 8, 14f, null, neon.tint(Palette.GOLD, .3f), 5f)
            neon.panel(b.x, b.y + oy, b.w, b.h, 12f, neon.tint(if (selected) GOLD_DARK else Palette.BACK, .95f),
                if (selected) Palette.GOLD else neon.tint(Palette.CYAN, .7f), if (selected) 3f else 2f)
            neon.chevronsRight(b.x + b.w - 24, b.y + b.h / 2 + oy, 7f, if (selected) Palette.GOLD else Palette.CYAN)
            for (p in 0 until GameData.upgrade(t).maxLevel) {
                neon.rect(b.x + 80 + p * 22, b.y + 12 + oy, 16f, 6f,
                    if (p < levels[t.ordinal]) (if (selected) Palette.GOLD else Palette.CYAN) else neon.tint(Palette.MUTED, .4f))
            }
        }
        neon.panel(866f, ty(600f), 390f, 468f, 20f, neon.tint(Palette.PANEL, .95f), Palette.CYAN, 3f)
        neon.rect(888f, ty(204f), 346f, 2f, neon.tint(Palette.CYAN, .4f))
        neon.panel(888f, ty(330f), 346f, 74f, 12f, neon.tint(Palette.BACK, .9f), neon.tint(Palette.CYAN, .6f), 2f)
        if (!preview.maxed) {
            neon.arrowRight(1061f, ty(293f), 16f, Palette.GREEN)
            neon.gear(1050f, ty(392f), 13f, Palette.SILVER, Palette.PANEL)
        }
    }

    override fun drawText() {
        ui.label(ui.tr("shop.title"), 640f, ty(62f), 2.4f, Color.WHITE)
        ui.label(components, 990f, ty(65f), 1.4f, Color.WHITE, align = -1)
        for (i in ids.indices) {
            val b = tabs[i]
            val active = ids[i] == character
            val locked = !game.upgrades.canUpgrade(ids[i])
            ui.label(ui.tr("team.${ids[i]}"), b.x + (b.w - 16) / 2 + if (locked) 18 else 8, b.y + b.h / 2, if (locked) 1.4f else 1.8f,
                if (active) Palette.BACK else if (game.upgrades.canUpgrade(ids[i])) accent(ids[i]) else Palette.MUTED)
        }
        for (t in UpgradeType.values()) {
            val b = rows[t.ordinal]
            val oy = if (isPressed(b)) -2f else 0f
            ui.label(ui.tr(NAME_KEYS[t.ordinal]), b.x + 80, b.y + 50 + oy, 1.45f, Color.WHITE, align = -1)
            ui.label(levelText[t.ordinal]!!, b.x + b.w - 48, b.y + 50 + oy, 1.2f, if (t == row) CREAM else Palette.CYAN,
                align = 1, slanted = false)
        }
        ui.label(ui.tr(NAME_KEYS[row.ordinal]), 950f, ty(176f), 1.7f, Color.WHITE, align = -1)
        ui.label(ui.tr(NOTE_KEYS[row.ordinal]), 1061f, ty(230f), 1f, Palette.SILVER, slanted = false)
        if (preview.maxed) {
            ui.label(current, 1061f, ty(293f), 2.2f, Color.WHITE, slanted = false)
        } else {
            ui.label(current, 975f, ty(293f), 2.1f, Color.WHITE, slanted = false)
            ui.label(next, 1148f, ty(293f), 2.1f, Palette.GREEN, slanted = false)
        }
        ui.label(levelLine, 1061f, ty(354f), 1f, Palette.CYAN, slanted = false)
        if (preview.maxed) {
            ui.label(ui.tr("shop.max"), 1061f, ty(392f), 1.5f, Palette.GOLD)
        } else {
            ui.label(ui.tr("shop.cost"), 1030f, ty(392f), 1.3f, Color.WHITE, align = 1, slanted = false)
            ui.label(cost, 1072f, ty(392f), 1.6f, if (preview.affordable) Color.WHITE else Palette.RED, align = -1, slanted = false)
        }
        // For a locked rebel the blocker is the lock, not the balance.
        if (missing.isNotEmpty() && !preview.maxed && game.upgrades.canUpgrade(character)) {
            ui.label(missing, 1061f, ty(428f), 1f, Palette.RED, slanted = false)
        }
        ui.label(ui.tr(if (game.upgrades.canUpgrade(character)) "shop.nextRun" else "shop.lockedNote"), 1061f, ty(464f), .9f,
            Palette.MUTED, slanted = false)
    }

    private companion object {
        val TAB_X = floatArrayOf(462f, 596f, 730f)
        val ROW_TOP = floatArrayOf(226f, 320f, 414f)
        val NAME_KEYS = arrayOf("shop.hp", "shop.damage", "shop.cooldown")
        val NOTE_KEYS = arrayOf("shop.hpNote", "shop.damageNote", "shop.cooldownNote")
        val GOLD_DARK = Color(.3f, .17f, .04f, 1f)
    }
}
