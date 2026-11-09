package com.manroid.neonrebels

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.I18NBundle
import com.manroid.neonrebels.progress.CampaignService
import com.manroid.neonrebels.progress.ProgressRepository
import com.manroid.neonrebels.progress.RunSetup
import com.manroid.neonrebels.progress.TeamService
import com.manroid.neonrebels.progress.UpgradeService
import com.manroid.neonrebels.view.FontSpec
import java.util.Locale

/**
 * Sole owner of shared GL resources and of campaign progress; screens only borrow them.
 * [version] is supplied by the launcher and shown on the main menu.
 */
class MainGame(val version: String = "dev", val debugTools: Boolean = false,
    private val startAnimationPreview: Boolean = false) : Game() {
    lateinit var batch: SpriteBatch
        private set
    lateinit var font: BitmapFont
        private set
    /** Slanted face for menu titles and buttons, matching the UI mock-ups. */
    lateinit var italic: BitmapFont
        private set
    lateinit var shapes: ShapeRenderer
        private set
    /** Device settings (touch controls), stored apart from campaign progress. */
    lateinit var save: SaveManager
        private set
    lateinit var progress: ProgressRepository
        private set
    lateinit var campaign: CampaignService
        private set
    lateinit var team: TeamService
        private set
    lateinit var upgrades: UpgradeService
        private set
    /** Set after the first load attempt; the main menu shows a loading state until then. */
    var progressChecked = false
        private set
    /** Screen that opened the workshop; kept alive (not disposed) until the workshop returns. */
    private var parked: Screen? = null
    lateinit var text: I18NBundle
        private set

    override fun create() {
        batch = SpriteBatch()
        font = loadFont(FontSpec.FILE).apply {
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            // World labels are drawn in world units; rounding glyph positions would collapse them.
            setUseIntegerPositions(false)
        }
        italic = loadFont(FontSpec.ITALIC_FILE).apply {
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        text = I18NBundle.createBundle(Gdx.files.internal(TEXT_BUNDLE), Locale.getDefault())
        shapes = ShapeRenderer()
        save = SaveManager()
        progress = ProgressRepository(PreferencesStore(save.preferences))
        campaign = CampaignService(progress)
        team = TeamService(progress, campaign)
        upgrades = UpgradeService(progress)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        open(if (debugTools && startAnimationPreview) AnimationPreviewScreen(this) else MenuScreen(this))
    }

    /**
     * Loads a baked font and re-derives the metrics that depend on the `base` value of the .fnt.
     * On a test device (Android 9/10), the first launch after install sometimes came back with
     * `ascent = -capHeight`, i.e. `base` read as 0 by libGDX's parser (most likely an ART JIT
     * issue in that very long method), which pushed every label down by about one line. Setting
     * ascent/descent from the file again makes text placement independent of that.
     */
    private fun loadFont(path: String): BitmapFont {
        val file = Gdx.files.internal(path)
        val result = BitmapFont(file)
        val common = file.readString("UTF-8").lineSequence().firstOrNull { it.startsWith("common ") }
        val base = common?.let { Regex("""\bbase=(\d+)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }
        if (base != null) {
            val data = result.data
            data.ascent = base - data.capHeight
            var descent = 0f
            for (page in data.glyphs) {
                if (page == null) continue
                for (glyph in page) {
                    if (glyph != null && glyph.width > 0 && glyph.height > 0) descent = minOf(descent, base + glyph.yoffset)
                }
            }
            data.descent = descent
        }
        return result
    }

    fun loadProgress(): ProgressRepository.LoadStatus {
        val status = progress.load()
        progressChecked = true
        return status
    }

    /** Replaces the current screen and disposes it (and any parked screen). */
    fun open(next: Screen) {
        val old: Screen? = getScreen()
        val stale = parked
        parked = null
        setScreen(next)
        old?.dispose()
        if (stale !== old) stale?.dispose()
    }

    /** Opens the workshop on top of [from]; Back returns to that same instance with its state. */
    fun openWorkshop(from: Screen) {
        if (parked != null) return
        parked = from
        setScreen(WorkshopScreen(this))
    }

    fun openAnimationPreview(from: Screen) {
        if (!debugTools || parked != null) return
        parked = from
        setScreen(AnimationPreviewScreen(this))
    }

    fun returnFromAnimationPreview(preview: Screen) = returnFromWorkshop(preview)

    fun returnFromWorkshop(workshop: Screen) {
        val back = parked ?: return open(MenuScreen(this))
        parked = null
        setScreen(back)
        workshop.dispose()
    }

    /** Starts a run whose setup was already confirmed and saved by [TeamService.start]. */
    fun play(setup: RunSetup) {
        open(GameScreen(this, setup))
    }

    override fun dispose() {
        val current: Screen? = getScreen()
        if (current != null) {
            current.hide()
            current.dispose()
            // Clear the backing field directly; setScreen(null) would call hide() a second time.
            screen = null
        }
        parked?.dispose()
        parked = null
        font.dispose()
        italic.dispose()
        batch.dispose()
        shapes.dispose()
    }

    private companion object {
        const val TEXT_BUNDLE = "i18n/strings"
    }
}
