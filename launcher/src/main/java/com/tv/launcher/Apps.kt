package com.tv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast

const val NETFLIX = "com.netflix.ninja"
const val YOUTUBE = "com.google.android.youtube.tv"

object Apps {

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
