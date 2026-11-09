package com.manroid.neonrebels.model

/**
 * Reused from a fixed pool; [launch] resets every field. Damage is a snapshot taken when the
 * shot is created, so a swap never changes it. The hit mask (bit per Enemy.id) stops a piercing
 * shot from hitting the same target twice.
 */
class Projectile : Body(0f, 0f, .38f, .22f) {
    var active = false
    var friendly = false
    var piercing = false
    var damage = 0
    var life = 0f
    var hits = 0L

    fun launch(x: Float, y: Float, vx: Float, vy: Float, friendly: Boolean, piercing: Boolean, damage: Int, life: Float) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
        this.friendly = friendly
        this.piercing = piercing
        this.damage = damage
        this.life = life
        hits = 0
        grounded = false
        active = true
    }
}
