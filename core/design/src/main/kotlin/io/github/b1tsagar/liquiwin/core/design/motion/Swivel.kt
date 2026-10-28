package io.github.b1tsagar.liquiwin.core.design.motion

/** WP's swivel motions (reference §5), turning around the horizontal axis. */
enum class Swivel(val fromDeg: Float, val toDeg: Float, val durationMs: Int, val exponent: Float, val easesIn: Boolean) {
    IN(fromDeg = -45f, toDeg = 0f, durationMs = 350, exponent = 6f, easesIn = false),
    OUT(fromDeg = 0f, toDeg = 90f, durationMs = 250, exponent = 15f, easesIn = true), // ends edge-on
}

/** The angle [elapsedMs] into [motion]; its end angle once it's done. */
fun swivelAngle(motion: Swivel, elapsedMs: Float): Float {
    val t = (elapsedMs / motion.durationMs).coerceIn(0f, 1f)
    return motion.fromDeg + (motion.toDeg - motion.fromDeg) * exponentialEase(t, motion.exponent, motion.easesIn)
}
