package com.manroid.neonrebels.model

/**
 * Every gameplay number in one place, in world units (1 unit = 1/18 of the view height) and
 * seconds of simulation time. Movement values are tuned to this level's jump route (see the
 * `route()` regression check) rather than copied from the pixel-free design brief.
 */
object GameConfig {
    // Simulation. 120 Hz keeps fast dashes and shots from tunnelling; any FPS gives the same result.
    const val STEP = 1f / 120f
    const val MAX_STEPS = 12
    const val VIEW_WIDTH = 32f
    const val VIEW_HEIGHT = 18f
    const val LEVEL_WIDTH = 160f

    // Movement.
    const val GRAVITY = -28f
    const val MAX_FALL_SPEED = 18f
    const val SPEED = 6.5f
    const val JUMP = 12f
    const val COYOTE = .10f
    const val JUMP_BUFFER = .12f
    const val FALL_Y = -5f

    // Damage taken.
    const val HIT_INVULNERABLE = .6f
    const val HIT_STUN = .18f
    const val PLAYER_KNOCKBACK = 5f
    const val PLAYER_KNOCKBACK_UP = 3f
    /** Fall damage as a fraction of the falling character's max health, rounded up. */
    const val FALL_DAMAGE_FRACTION = .15f
    const val RESPAWN_PROTECTION = .6f

    // Swap and dodge (shared by An and Vy).
    const val SWITCH_COOLDOWN = 1f
    const val DODGE_SPEED = 10f
    const val DODGE_TIME = .2f
    const val DODGE_COOLDOWN = 1f
    const val DODGE_INVULNERABLE = .12f
    /** Free space needed in front before a dodge or dash may start. */
    const val MIN_CLEARANCE = .2f

    // Base character stats at upgrade level 0; the workshop scales them via progress.StatCalculator.
    const val AN_HP = 1200
    const val VY_HP = 1000
    const val AN_DAMAGE = 100
    const val VY_DAMAGE = 60
    const val AN_SKILL_COOLDOWN = 8f
    const val VY_SKILL_COOLDOWN = 6f

    // An: three-hit combo. Index = combo step - 1.
    val COMBO_STARTUP = floatArrayOf(.08f, .08f, .12f)
    val COMBO_ACTIVE = floatArrayOf(.10f, .10f, .12f)
    val COMBO_RECOVERY = floatArrayOf(.18f, .20f, .28f)
    val COMBO_DAMAGE = floatArrayOf(1f, 1.1f, 1.5f)
    val COMBO_KNOCKBACK = floatArrayOf(3f, 3f, 6f)
    const val SWORD_RANGE = 1.9f
    const val SWORD_HEIGHT = 1.95f

    // An: dash slash skill.
    const val DASH_SPEED = 12f
    const val DASH_TIME = .22f
    const val DASH_MULTIPLIER = 2f
    const val DASH_RANGE = 1.2f

    // Vy: rifle and piercing shot.
    const val SHOT_INTERVAL = .25f
    const val SHOT_SPEED = 14f
    const val SHOT_LIFE = 1.5f
    const val PIERCE_MULTIPLIER = 2.5f
    const val PIERCE_SPEED = 18f
    const val PIERCE_LIFE = 1.5f
    /** Normal fire is locked this long after a piercing shot. */
    const val PIERCE_FIRE_LOCK = .2f
    const val SHOT_KNOCKBACK = 1.5f
    const val PROJECTILE_POOL = 64

    // Patrol robot.
    const val ROBOT_HP = 180
    const val ROBOT_DAMAGE = 80
    const val ROBOT_SPEED = 2f
    const val ROBOT_SIGHT_RANGE = 6f
    const val ROBOT_ATTACK_RANGE = 1.2f
    const val ROBOT_WINDUP = .4f
    const val ROBOT_ACTIVE = .12f
    const val ROBOT_RECOVERY = .8f
    const val ENEMY_HIT_STUN = .18f

    // Drone.
    const val DRONE_HP = 120
    const val DRONE_DAMAGE = 60
    const val DRONE_AIM = .5f
    const val DRONE_REST = 1.2f
    const val DRONE_SHOT_SPEED = 5f
    const val DRONE_SHOT_LIFE = 3.5f
    const val DRONE_RANGE = 11f
    const val DRONE_PATROL = 2f
    const val DRONE_SPEED = 1.6f

    // Armored Gatekeeper.
    const val BOSS_HP = 3000
    const val BOSS_INTRO = 1.2f
    const val BOSS_IDLE = .45f
    const val SLAM_TELEGRAPH = .7f
    const val SLAM_ACTIVE = .15f
    const val SLAM_DAMAGE = 180
    const val SLAM_RECOVERY = 1.2f
    /** The slam zone reaches this far beyond each side of the boss. */
    const val SLAM_REACH = 2f
    /** Finite slam height: a jump clears it. */
    const val SLAM_HEIGHT = 1.1f
    const val CHARGE_TELEGRAPH = .8f
    const val CHARGE_TIME = .65f
    const val CHARGE_SPEED = 9f
    const val CHARGE_DAMAGE = 150
    const val CHARGE_RECOVERY = 1.4f
    /** Minimum free run ahead for a charge to start. */
    const val CHARGE_MIN_SPACE = 2f
    /** Player closer than this (centre to centre) gets a slam. */
    const val BOSS_NEAR = 4f
    const val BOSS_MAX_REPEAT = 2
    const val BOSS_RAGE_FRACTION = .4f
    const val BOSS_RAGE_RECOVERY = .75f
    const val BOSS_TRANSITION = 1f

    /** Components dropped by destroyed enemies; kept only if the run is won. */
    const val DROP_ROBOT = 10
    const val DROP_DRONE = 15
    const val DROP_BOSS = 40
}
