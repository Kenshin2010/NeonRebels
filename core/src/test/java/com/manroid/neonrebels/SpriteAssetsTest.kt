package com.manroid.neonrebels

import com.manroid.neonrebels.view.SpriteNames
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** The world atlas and backdrop files must provide every animation the renderer draws. */
class SpriteAssetsTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private val assets = File(root, "app/src/main/assets")

    @Test
    fun atlasHasEveryAnimationWithTheExpectedFrameCount() {
        val lines = File(assets, SpriteNames.ATLAS).readLines()
        val counts = HashMap<String, Int>()
        var current: String? = null
        // Legacy atlas text: a region name line, then indented "key: value" lines.
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            if (!line.startsWith(" ") && !line.contains(":") && !line.endsWith(".png")) current = line.trim()
            else if (line.trim().startsWith("index:") && current != null) counts[current] = (counts[current] ?: 0) + 1
        }
        for ((name, frames) in SpriteNames.FRAMES) {
            assertTrue("Frames for $name: ${counts[name]}", (counts[name] ?: 0) >= frames)
        }
    }

    @Test
    fun everyActorHasPlacementData() {
        val meta = File(assets, SpriteNames.ACTORS).readLines()
            .filter { '=' in it && !it.startsWith("#") }
            .associate { it.substringBefore('=') to it.substringAfter('=').split(',') }
        for (name in SpriteNames.ACTOR_REGIONS) {
            val parts = meta[name]
            assertTrue("Missing actor data for $name", parts != null && parts.size == 3)
            val anchor = parts!![0].toFloat()
            assertTrue("$name anchor in 0..1", anchor in 0f..1f)
            assertTrue("$name height positive", parts[1].toFloat() > 0f)
        }
    }

    @Test
    fun repeatingTexturesArePowerOfTwo() {
        // GLES2 only repeats power-of-two textures.
        for (path in SpriteNames.TEXTURES) {
            val image = ImageIO.read(File(assets, path))
            assertTrue("$path width", Integer.bitCount(image.width) == 1)
            assertTrue("$path height", Integer.bitCount(image.height) == 1)
        }
    }
}
