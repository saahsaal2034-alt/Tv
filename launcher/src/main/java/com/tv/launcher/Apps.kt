package com.tv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast

const val NETFLIX = "com.netflix.ninja"
const val YOUTUBE = "com.google.android.youtube.tv"

data class App(
    val component: ComponentName,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable,
)

object Apps {

    /** Every launchable app except this one, sorted by name. */
    fun all(context: Context): List<App> {
        val pm = context.packageManager
        val apps = LinkedHashMap<String, App>()
        // TV entry points first, so apps with both use their TV version and banner.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val activity = info.activityInfo
                if (activity.packageName == context.packageName || activity.packageName in apps) continue
                apps[activity.packageName] = App(
                    component = ComponentName(activity.packageName, activity.name),
                    label = info.loadLabel(pm).toString(),
                    banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                    icon = info.loadIcon(pm),
                )
            }
        }
        return apps.values.sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: App) {
        val intent = Intent(Intent.ACTION_MAIN).setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the app, or its Play Store page if it isn't installed. */
    fun launch(context: Context, pkg: String) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    /** The app's own TV banner (its logo artwork), if it has one. */
    fun banner(context: Context, pkg: String): Drawable? {
        val pm = context.packageManager
        return try {
            pm.getLeanbackLaunchIntentForPackage(pkg)?.let { pm.getActivityBanner(it) }
                ?: pm.getApplicationBanner(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
