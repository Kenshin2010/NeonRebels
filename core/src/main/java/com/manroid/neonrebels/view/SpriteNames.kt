package com.manroid.neonrebels.view

/**
 * Contract between the world renderer and the art in app/src/main/assets/sprites (built by
 * scripts/build-world-art.py from the UI mock-ups). Kept free of libGDX so a JVM test can check
 * the files against it. world.atlas keeps the legacy actors, props and drone animation. Independent character
 * clips live in optional characters.atlas; see animation/CharacterFrames and animations.properties.
 * Adding indexed an_stance regions alone does not enable a character animation.
 */
object SpriteNames {
    const val ATLAS = "sprites/world.atlas"
    /** name=anchorX(0..1 of width),heightInWorldUnits,facesRight; plus sky=r,g,b. */
    const val ACTORS = "sprites/actors.properties"
    const val FAR = "sprites/bg_far.png"
    const val GROUND = "sprites/ground.png"
    const val LEDGE = "sprites/ledge.png"

    const val AN = "an_stance"
    const val VY = "vy_stance"
    const val ROBOT_SHIELD = "robot_shield"
    const val ROBOT_BLADE = "robot_blade"
    const val BOSS = "boss_stance"
    const val DRONE_FLY = "drone_fly"
    const val DRONE_FIRE = "drone_fire"
    const val REPAIR_ON = "repair_on"
    const val REPAIR_OFF = "repair_off"
    const val BARRIER = "barrier"
    const val DOOR = "door"
    const val TOWER_ON = "tower_on"
    const val TOWER_OFF = "tower_off"
    const val HAZARD = "hazard"

    /** Regions with per-actor metadata in [ACTORS]. */
    val ACTOR_REGIONS = listOf(AN, VY, ROBOT_SHIELD, ROBOT_BLADE, BOSS)

    /** Every region the renderer draws, with its minimum frame count. */
    val FRAMES: Map<String, Int> = ACTOR_REGIONS.associateWith { 1 } + mapOf(
        DRONE_FLY to 2, DRONE_FIRE to 1, REPAIR_ON to 1, REPAIR_OFF to 1, BARRIER to 1, DOOR to 1,
        TOWER_ON to 1, TOWER_OFF to 1, HAZARD to 1,
    )

    /** Repeating textures; GLES2 only repeats power-of-two sizes. */
    val TEXTURES = listOf(FAR, GROUND, LEDGE)
}
