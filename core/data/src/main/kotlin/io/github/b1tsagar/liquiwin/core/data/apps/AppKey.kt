package io.github.b1tsagar.liquiwin.core.data.apps

/** One launcher activity in one user profile. */
data class AppKey(val packageName: String, val className: String, val userSerial: Long) {
    fun flatten(): String = "$packageName/$className@$userSerial"

    companion object {
        /** The inverse of [flatten], or null when [value] isn't one. */
        fun parse(value: String): AppKey? {
            val slash = value.indexOf('/')
            val at = value.lastIndexOf('@')
            if (slash <= 0 || at <= slash + 1) return null
            val serial = value.substring(at + 1).toLongOrNull() ?: return null
            return AppKey(value.substring(0, slash), value.substring(slash + 1, at), serial)
        }
    }
}
