package com.tv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class App(
    val component: ComponentName,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable,
    val isTvApp: Boolean,
) {
    val packageName: String get() = component.packageName
}

/** Finds launchable apps, preferring each app's TV entry point and banner. */
class AppRepository(private val context: Context) {

    fun load(): List<App> {
        val pm = context.packageManager
        val apps = LinkedHashMap<String, App>()

        fun add(info: ResolveInfo, isTvApp: Boolean) {
            val activity = info.activityInfo
            if (activity.packageName == context.packageName || activity.packageName in apps) return
            apps[activity.packageName] = App(
                component = ComponentName(activity.packageName, activity.name),
                label = info.loadLabel(pm).toString(),
                banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                icon = info.loadIcon(pm),
                isTvApp = isTvApp,
            )
        }

        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { add(it, isTvApp = true) }
        query(Intent.CATEGORY_LAUNCHER).forEach { add(it, isTvApp = false) }
        return apps.values.toList()
    }

    private fun query(category: String): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(category), 0
        )
}
