package com.tv.launcher

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Starts the phone remote whenever any part of Vortrex runs, not only the
 * home screen, so it comes back by itself if the TV closes Vortrex to free
 * memory (Android restarts the accessibility service, which restarts us).
 */
class VortrexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val mainHandler = Handler(Looper.getMainLooper())
        RemoteServer.start(this) { command, text ->
            mainHandler.post { RemoteCommands.run(this, command, text) }
        }
        KeepAliveService.start(this)
    }
}
