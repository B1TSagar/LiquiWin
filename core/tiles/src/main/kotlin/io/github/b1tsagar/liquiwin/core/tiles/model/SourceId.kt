package io.github.b1tsagar.liquiwin.core.tiles.model

/** Stable id for anything that can feed a tile, such as "app:<package>@<userSerial>" for all of one app's notifications. */
@JvmInline
value class SourceId(val value: String) {
    companion object {
        fun forApp(packageName: String, userSerial: Long): SourceId = SourceId("app:$packageName@$userSerial")
    }
}
