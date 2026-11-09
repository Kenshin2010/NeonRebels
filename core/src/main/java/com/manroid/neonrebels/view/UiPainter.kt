package com.manroid.neonrebels.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.manroid.neonrebels.MainGame

class UiPainter(private val game: MainGame) {
    private val layout = GlyphLayout()

    /** Localized UI string; throws if the key is missing so gaps surface during development. */
    fun tr(key: String): String = game.text.get(key)

    fun text(text: CharSequence, x: Float, y: Float, scale: Float, color: Color) {
        game.font.data.setScale(scale * FontSpec.UNIT)
        game.font.color = color
        game.font.draw(game.batch, text, x, y)
    }

    fun center(text: CharSequence, x: Float, y: Float, scale: Float, color: Color) {
        game.font.data.setScale(scale * FontSpec.UNIT)
        layout.setText(game.font, text)
        text(text, x - layout.width / 2, y, scale, color)
    }

    /**
     * Text vertically centered on [cy] (cap height), for placing labels inside art boxes.
     * [align]: -1 left edge at [x], 0 centered on [x], 1 right edge at [x].
     */
    fun label(text: CharSequence, x: Float, cy: Float, scale: Float, color: Color, align: Int = 0, slanted: Boolean = true) {
        val font = if (slanted) game.italic else game.font
        font.data.setScale(scale * FontSpec.UNIT)
        font.color = color
        layout.setText(font, text)
        val left = when (align) {
            0 -> x - layout.width / 2
            1 -> x - layout.width
            else -> x
        }
        font.draw(game.batch, text, left, cy + font.capHeight / 2)
    }

    /** Width of [text] at [scale], for fitting labels into widgets. */
    fun width(text: CharSequence, scale: Float, slanted: Boolean = true): Float {
        val font = if (slanted) game.italic else game.font
        font.data.setScale(scale * FontSpec.UNIT)
        layout.setText(font, text)
        return layout.width
    }

    /** Label between [left] and [right], shrunk if needed so long (translated) text never spills. */
    fun fitLabel(text: CharSequence, left: Float, right: Float, cy: Float, scale: Float, color: Color, centered: Boolean = false) {
        val room = right - left
        val width = width(text, scale)
        val fitted = if (width > room) scale * room / width else scale
        if (centered) label(text, (left + right) / 2, cy, fitted, color) else label(text, left, cy, fitted, color, align = -1)
    }

    fun box(x: Float, y: Float, w: Float, h: Float, color: Color) {
        game.shapes.color = color
        game.shapes.rect(x, y, w, h)
    }

    companion object {
        fun inside(x: Float, y: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean =
            x >= bx && x <= bx + bw && y >= by && y <= by + bh
    }
}
