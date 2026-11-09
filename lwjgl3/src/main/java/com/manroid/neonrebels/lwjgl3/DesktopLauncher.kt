package com.manroid.neonrebels.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.manroid.neonrebels.MainGame

object DesktopLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val game = MainGame(System.getProperty("neonrebels.version") ?: "desktop",
            debugTools = "--debug" in args || "--animation-preview" in args,
            startAnimationPreview = "--animation-preview" in args)
        val wide = "--wide" in args
        val config = Lwjgl3ApplicationConfiguration().apply {
            setTitle("NEON REBELS")
            setWindowedMode(if (wide) 1600 else 1280, 720)
            setWindowSizeLimits(640, 360, -1, -1)
            useVsync(true)
            setForegroundFPS(60)
            setWindowListener(object : Lwjgl3WindowAdapter() {
                override fun focusLost() {
                    game.pause()
                }

                override fun focusGained() {
                    game.resume()
                }
            })
        }
        Lwjgl3Application(game, config)
    }
}
