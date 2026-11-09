package com.manroid.neonrebels.view.animation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.Files
import com.badlogic.gdx.Application
import com.badlogic.gdx.files.FileHandle
import java.io.File
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercise the real libGDX AtlasSprite offset/UV code with a GL stub; no window or image uploads. */
class CharacterFrameTest {
    @Test fun missingAtlasFallsBackSafelyAndWarningsAreNotEmittedFromFrameLookup() {
        val oldFiles = Gdx.files; val oldApp = Gdx.app
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val temp = java.nio.file.Files.createTempDirectory("animation-fallback").toFile()
        File(temp, "sprites").mkdirs()
        File(root, "app/src/main/assets/sprites/animations.properties").copyTo(File(temp, CharacterFrames.MANIFEST))
        var logs = 0
        Gdx.files = Proxy.newProxyInstance(Files::class.java.classLoader, arrayOf(Files::class.java)) { _, method, args ->
            if (method.name == "internal") FileHandle(File(temp, args!![0].toString())) else null
        } as Files
        Gdx.app = Proxy.newProxyInstance(Application::class.java.classLoader, arrayOf(Application::class.java)) { _, method, _ ->
            if (method.name == "log") logs++
            null
        } as Application
        try {
            assertFalse(Gdx.files.internal(CharacterFrames.ATLAS).exists())
            val library = CharacterFrames(true)
            assertEquals(AnimationManifest.ids.size, logs)
            repeat(30) { for (id in AnimationManifest.ids) assertNull(library.frame(id, 0)) }
            assertEquals(AnimationManifest.ids.size, logs)
            assertTrue(library.status.getValue("an.death").startsWith("missing"))
            library.dispose()
            logs = 0
            CharacterFrames(false).dispose()
            assertEquals(0, logs)
        } finally { Gdx.files = oldFiles; Gdx.app = oldApp; temp.deleteRecursively() }
    }

    @Test fun trimRotationAndRepeatedFlipKeepFootAnchorAndSocketsFixed() {
        val oldGl = Gdx.gl; val oldGl20 = Gdx.gl20; val oldGraphics = Gdx.graphics
        val gl = Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, method, _ ->
            when (method.returnType) {
                java.lang.Integer.TYPE -> 1
                java.lang.Boolean.TYPE -> false
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        } as GL20
        Gdx.gl = gl; Gdx.gl20 = gl
        Gdx.graphics = Proxy.newProxyInstance(Graphics::class.java.classLoader, arrayOf(Graphics::class.java)) { _, method, _ ->
            if (method.returnType == java.lang.Boolean.TYPE) false else 0
        } as Graphics
        var texture: Texture? = null
        try {
            texture = Texture(object : TextureData {
                override fun getType() = TextureData.TextureDataType.Custom
                override fun isPrepared() = true
                override fun prepare() = Unit
                override fun consumePixmap(): Pixmap = error("No pixmap")
                override fun disposePixmap() = false
                override fun consumeCustomData(target: Int) = Unit
                override fun getWidth() = 256
                override fun getHeight() = 256
                override fun getFormat() = Pixmap.Format.RGBA8888
                override fun useMipMaps() = false
                override fun isManaged() = false
            })
            val clip = AnimationClip("vy.idle", listOf("vy_idle_00", "vy_idle_01"), listOf(.1f, .1f),
                ClipEnd.LOOP, 100, 100, 2f, true, Socket(30f, 10f),
                mapOf("muzzle" to Socket(60f, 50f)), emptyList())
            for (rotate in listOf(false, true)) {
                val region = TextureAtlas.AtlasRegion(texture, 0, 0, if (rotate) 50 else 40, if (rotate) 40 else 50)
                region.originalWidth = 100; region.originalHeight = 100
                region.packedWidth = region.regionWidth; region.packedHeight = region.regionHeight
                region.offsetX = 15f; region.offsetY = 8f
                region.rotate = rotate; region.degrees = if (rotate) 90 else 0
                val art = CharacterFrame(clip, 0, region)
                repeat(4) {
                    for (facing in listOf(1, -1)) {
                        art.place(8f, 2f, facing)
                        val vertices = art.sprite.vertices
                        val minX = listOf(0, 5, 10, 15).minOf { vertices[it] }
                        val expected = if (facing > 0) 8f + (15f - 30f) * .02f
                            else 8f + (30f - 15f - 40f) * .02f
                        assertEquals("trimmed left edge rotate=$rotate facing=$facing", expected, minX, .0001f)
                        assertEquals(8f + facing * .6f, art.socketX("muzzle", 8f, facing)!!, .0001f)
                        assertEquals(2.8f, art.socketY("muzzle", 2f)!!, .0001f)
                        assertEquals(2f - 2f * .02f, vertices[1], .0001f)
                    }
                }
            }
        } finally {
            texture?.dispose()
            Gdx.gl = oldGl; Gdx.gl20 = oldGl20; Gdx.graphics = oldGraphics
        }
    }
}
