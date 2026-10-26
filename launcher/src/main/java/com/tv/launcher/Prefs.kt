package com.tv.launcher

import android.content.Context

/** Favorites order, hidden apps and launch counts, keyed by package name. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, null)?.split(',')?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) = prefs.edit().putString(KEY_FAVORITES, value.joinToString(",")).apply()

    var hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN, value).apply()

    fun launchCount(pkg: String): Int = prefs.getInt(KEY_COUNT + pkg, 0)

    fun recordLaunch(pkg: String) =
        prefs.edit().putInt(KEY_COUNT + pkg, launchCount(pkg) + 1).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_COUNT = "count:"
    }
}
