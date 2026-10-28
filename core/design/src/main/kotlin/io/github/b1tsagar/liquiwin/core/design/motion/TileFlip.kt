package io.github.b1tsagar.liquiwin.core.design.motion

// The reference: every transition 0.85 s, CubicEase-out; the flip is RotationX 0 to 180 with the face swap at 0.185 s.
const val TILE_FLIP_MS = 850

/** The flip's angle, 0 to 180 degrees, after [elapsedMs]: a cubic ease-out over [TILE_FLIP_MS]. */
fun tileFlipAngle(elapsedMs: Float): Float {
    val left = 1f - (elapsedMs / TILE_FLIP_MS).coerceIn(0f, 1f)
    return 180f * (1f - left * left * left)
}

/**
 * The flip's `cameraDistance` for a tile [heightPx] tall. `graphicsLayer` takes it in Skia's camera units of 72 px,
 * whatever the density, so this puts the camera 21 tile heights away: edge-on, the nearer edge is then
 * 21.5 / 20.5 ≈ 1.049 times as wide as the farther one, within §5.1's 5%.
 */
fun tileFlipCameraDistance(heightPx: Float): Float = 21f * heightPx / 72f
