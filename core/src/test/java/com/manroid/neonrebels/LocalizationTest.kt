package com.manroid.neonrebels

import com.manroid.neonrebels.view.FontSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** Guards against missing translations and characters the baked font cannot draw. */
class LocalizationTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private val assets = File(root, "app/src/main/assets")
    private val bundles = listOf("strings.properties", "strings_en.properties")
        .associateWith { load(File(assets, "i18n/$it")) }

    @Test
    fun everyLanguageDefinesTheSameKeys() {
        val (base, other) = bundles.values.map { it.stringPropertyNames() }
        assertEquals("Keys missing from English", emptySet<String>(), base - other)
        assertEquals("Keys missing from Vietnamese", emptySet<String>(), other - base)
    }

    @Test
    fun everyKeyUsedInCodeExists() {
        val keyPattern = Regex("\"((?:menu|world|hud|button|overlay|map|team|shop|ui|story|dlg|tut|notice|speaker|pause|preview)\\.[A-Za-z0-9]+)\"")
        val used = File(root, "core/src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { file -> keyPattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue("No text keys found in sources", used.isNotEmpty())
        for ((name, bundle) in bundles) {
            val missing = used.filterNot { bundle.containsKey(it) }
            assertEquals("Missing in $name", emptyList<String>(), missing)
        }
    }

    @Test
    fun bakedFontCoversEveryCharacterInEveryLanguage() {
        val fnt = File(assets, FontSpec.FILE).readLines()
        val size = Regex("size=(\\d+)").find(fnt.first())!!.groupValues[1].toFloat()
        assertEquals("FontSpec.PIXEL_SIZE must match the baked font", size, FontSpec.PIXEL_SIZE, 0f)
        val glyphs = fnt.filter { it.startsWith("char ") }
            .map { Regex("id=(\\d+)").find(it)!!.groupValues[1].toInt() }
            .toSet()
        for ((name, bundle) in bundles) {
            val missing = bundle.stringPropertyNames()
                .flatMap { key -> bundle.getProperty(key).codePoints().toArray().map { key to it } }
                .filterNot { it.second in glyphs }
                .map { (key, cp) -> "$key:'${String(Character.toChars(cp))}'" }
            assertEquals("Glyphs missing for $name", emptyList<String>(), missing)
        }
    }

    private fun load(file: File) = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
}
