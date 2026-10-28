package io.github.b1tsagar.liquiwin.core.design.motion

// The reference: every transition 0.85 s, CubicEase-out; the flip is RotationX 0 to 180 with the face swap at 0.185 s.
const val TILE_FLIP_MS = 850

/** The flip's angle, 0 to 180 degrees, after [elapsedMs]: a cubic ease-out over [TILE_FLIP_MS]. */
fun tileFlipAngle(elapsedMs: Float): Float {
    val left = 1f - (elapsedMs / TILE_FLIP_MS).coerceIn(0f, 1f)
    return 180f * (1f - left * left * left)
}
