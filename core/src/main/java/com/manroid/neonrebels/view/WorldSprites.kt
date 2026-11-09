package com.manroid.neonrebels.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import com.manroid.neonrebels.view.animation.CharacterFrames

/** Placement data for one actor image: body centre as a fraction of width, height in world units. */
class ActorArt(val region: TextureRegion, val anchorX: Float, val height: Float, val facesRight: Boolean) {
    val width: Float get() = height * region.regionWidth / region.regionHeight
}

/** World art: atlas regions, actor metadata and the repeating backdrop/ground textures. */
class WorldSprites(debugAnimations: Boolean = false) : Disposable {
    val characters = CharacterFrames(debugAnimations)
    private val atlas = TextureAtlas(Gdx.files.internal(SpriteNames.ATLAS))
    private val frames: Map<String, Array<TextureRegion>> = SpriteNames.FRAMES.keys.associateWith { name ->
        val regions = atlas.findRegions(name)
        require(regions.size > 0) { "Missing sprite $name in ${SpriteNames.ATLAS}" }
        Array<TextureRegion>(regions.size) { regions[it] }
    }
    private val meta: Map<String, String> = Gdx.files.internal(SpriteNames.ACTORS).readString("UTF-8")
        .lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val actors: Map<String, ActorArt> = SpriteNames.ACTOR_REGIONS.associateWith { name ->
        val parts = meta.getValue(name).split(',')
        ActorArt(frames.getValue(name)[0], parts[0].toFloat(), parts[1].toFloat(), parts[2].trim() == "true")
    }
    /** Sky colour sampled above the backdrop crop, to fill the top of the view. */
    val sky: Color = meta["sky"]?.split(',')?.let { Color(it[0].toFloat() / 255, it[1].toFloat() / 255, it[2].toFloat() / 255, 1f) }
        ?: Color(.13f, .2f, .45f, 1f)
    val far = texture(SpriteNames.FAR, repeatY = false)
    val ground = texture(SpriteNames.GROUND, repeatY = true)
    val ledge = texture(SpriteNames.LEDGE, repeatY = false)

    private fun texture(path: String, repeatY: Boolean) = Texture(Gdx.files.internal(path)).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        setWrap(Texture.TextureWrap.Repeat, if (repeatY) Texture.TextureWrap.Repeat else Texture.TextureWrap.ClampToEdge)
    }

    fun region(name: String): TextureRegion = frames.getValue(name)[0]

    /** Looping animation frame at [time] seconds and [fps]. */
    fun frame(name: String, time: Float, fps: Float): TextureRegion {
        val list = frames.getValue(name)
        return list[((time * fps).toInt() % list.size + list.size) % list.size]
    }

    override fun dispose() {
        characters.dispose()
        atlas.dispose()
        far.dispose()
        ground.dispose()
        ledge.dispose()
    }
}
