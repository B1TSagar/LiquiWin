package io.github.b1tsagar.liquiwin.core.tiles.model

/** Stable id for anything that can feed a tile, such as "app:<package>@<userSerial>" for all of one app's notifications. */
@JvmInline
value class SourceId(val value: String) {
    /** The package and user serial of an "app:<package>@<serial>" id, or null. */
    fun appPackageAndSerial(): Pair<String, Long>? {
        if (!value.startsWith(APP)) return null
        val at = value.lastIndexOf('@')
        if (at <= APP.length) return null
        val serial = value.substring(at + 1).toLongOrNull() ?: return null
        return value.substring(APP.length, at) to serial
    }

    companion object {
        private const val APP = "app:"

        fun forApp(packageName: String, userSerial: Long): SourceId = SourceId("$APP$packageName@$userSerial")
    }
}
