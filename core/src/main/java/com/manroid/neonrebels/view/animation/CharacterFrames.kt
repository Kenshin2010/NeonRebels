package com.manroid.neonrebels.view.animation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.utils.Disposable
import java.util.Properties

/** AtlasSprite preserves TexturePacker trim offsets and 90-degree packed rotation. */
class CharacterFrame(val clip: AnimationClip, val index: Int, region: TextureAtlas.AtlasRegion) {
    val name = clip.frames[index]
    val sprite = TextureAtlas.AtlasSprite(region)
    private var flipped = false

    fun draw(batch: SpriteBatch, cx: Float, feetY: Float, facing: Int) {
        place(cx, feetY, facing)
        sprite.setColor(batch.color)
        sprite.draw(batch)
    }

    fun place(cx: Float, feetY: Float, facing: Int) {
        val flip = (facing > 0) != clip.facesRight
        if (flip != flipped) {
            sprite.flip(true, false)
            flipped = flip
        }
        val unit = clip.height / clip.canvasHeight
        val anchor = clip.anchorAt(index)
        val x = if (flip) clip.canvasWidth - anchor.x else anchor.x
        sprite.setBounds(cx - x * unit, feetY - anchor.y * unit, clip.canvasWidth * unit, clip.height)
    }

    fun socketX(name: String, cx: Float, facing: Int): Float? {
        val socket = clip.socketAt(index, name) ?: return null
        val sign = if ((facing > 0) == clip.facesRight) 1f else -1f
        return cx + (socket.x - clip.anchorAt(index).x) * clip.height / clip.canvasHeight * sign
    }

    fun socketY(name: String, feetY: Float): Float? = clip.socketAt(index, name)?.let {
        feetY + (it.y - clip.anchorAt(index).y) * clip.height / clip.canvasHeight
    }
}

/** A missing or incomplete clip falls back as a whole; never mix new and legacy frames. */
class CharacterFrames(private val debug: Boolean) : Disposable {
    private val warned = HashSet<String>()
    private fun warn(key: String, message: String) {
        if (debug && warned.add(key)) Gdx.app?.log("CharacterAnimation", message)
    }

    val clips: Map<String, AnimationClip> = try {
        val p = Properties()
        Gdx.files.internal(MANIFEST).reader("UTF-8").use { p.load(it) }
        AnimationManifest.parse(p) { warn(it.substringBefore(':'), "Invalid animation: $it") }
    } catch (e: RuntimeException) {
        warn(MANIFEST, "Missing/invalid $MANIFEST: ${e.message}")
        emptyMap()
    }
    private val atlas: TextureAtlas? = if (!Gdx.files.internal(ATLAS).exists()) null else try {
        TextureAtlas(Gdx.files.internal(ATLAS)).also { loaded ->
            for (texture in loaded.textures) texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }
    } catch (e: RuntimeException) {
        warn(ATLAS, "Cannot load $ATLAS: ${e.message}")
        null
    }
    private val frames = LinkedHashMap<String, List<CharacterFrame>>()
    val status = LinkedHashMap<String, String>()

    init {
        for (id in AnimationManifest.ids) {
            val clip = clips[id]
            val regions = clip?.frames?.map { name ->
                atlas?.findRegion(name) ?: name.substringAfterLast('_').toIntOrNull()?.let { index ->
                    atlas?.findRegion(name.substringBeforeLast('_'), index)
                }
            }
            val reason = when {
                clip == null -> "invalid manifest"
                atlas == null -> "missing $ATLAS"
                regions!!.any { it == null } -> "missing regions: " + clip.frames.filterIndexed { i, _ -> regions[i] == null }.joinToString()
                regions.any { it!!.originalWidth != clip.canvasWidth || it.originalHeight != clip.canvasHeight } -> "inconsistent original canvas"
                regions.any { it!!.degrees != 0 && it.degrees != 90 } -> "unsupported atlas rotation (use 0/90)"
                else -> null
            }
            status[id] = reason ?: "atlas"
            if (reason == null) frames[id] = regions!!.mapIndexed { i, region -> CharacterFrame(clip!!, i, region!!) }
            else warn(id, "$id → legacy fallback ($reason)")
        }
    }

    fun frame(id: String, index: Int): CharacterFrame? = frames[id]?.let { it[index.coerceIn(0, it.lastIndex)] }
    fun available(id: String): Boolean = id in frames
    override fun dispose() { atlas?.dispose() }

    companion object {
        const val MANIFEST = "sprites/animations.properties"
        const val ATLAS = "sprites/characters.atlas"
    }
}
