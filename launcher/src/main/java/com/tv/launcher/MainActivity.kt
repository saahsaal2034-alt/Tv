package com.tv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var repository: AppRepository

    private lateinit var favoritesList: RecyclerView
    private lateinit var favoritesEmpty: TextView
    private lateinit var appsList: RecyclerView
    private val favoritesAdapter = AppAdapter(::launch, ::showAppMenu)
    private val appsAdapter = AppAdapter(::launch, ::showAppMenu)

    private var installed: List<App> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        repository = AppRepository(this)

        favoritesList = findViewById(R.id.favorites)
        favoritesEmpty = findViewById(R.id.favorites_empty)
        appsList = findViewById(R.id.apps)

        favoritesList.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        favoritesList.adapter = favoritesAdapter
        favoritesList.itemAnimator = null

        val tileWidth = resources.getDimension(R.dimen.tile_with_margin)
        val usableWidth = resources.displayMetrics.widthPixels -
            resources.getDimension(R.dimen.screen_padding) * 2
        appsList.layoutManager = GridLayoutManager(this, (usableWidth / tileWidth).toInt().coerceAtLeast(1))
        appsList.adapter = appsAdapter
        appsList.itemAnimator = null

        findViewById<View>(R.id.settings).apply {
            setOnClickListener { startSafely(Intent(Settings.ACTION_SETTINGS)) }
            setOnLongClickListener { showSettingsMenu(); true }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home jumps back to the top.
        appsList.scrollToPosition(0)
        favoritesList.scrollToPosition(0)
        (favoritesList.takeIf { favoritesAdapter.itemCount > 0 } ?: appsList).requestFocus()
    }

    @Deprecated("Back does nothing on the home screen.")
    override fun onBackPressed() = Unit

    private fun reload() {
        executor.execute {
            val apps = repository.load()
            mainHandler.post {
                installed = apps
                render()
            }
        }
    }

    private fun render() {
        val byPackage = installed.associateBy { it.packageName }
        val hidden = prefs.hidden
        val favorites = prefs.favorites.mapNotNull { byPackage[it] }

        favoritesAdapter.apps = favorites
        favoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
        favoritesList.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE

        // Most used first, then TV apps before phone-style apps, then by name.
        appsAdapter.apps = installed
            .filter { it.packageName !in hidden }
            .sortedWith(
                compareByDescending<App> { prefs.launchCount(it.packageName) }
                    .thenByDescending { it.isTvApp }
                    .thenBy { it.label.lowercase() }
            )
    }

    private fun launch(app: App) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (startSafely(intent)) prefs.recordLaunch(app.packageName)
    }

    private fun showAppMenu(app: App) {
        val favorites = prefs.favorites
        val isFavorite = app.packageName in favorites
        val actions = buildList<Pair<Int, () -> Unit>> {
            if (isFavorite) {
                add(R.string.unpin to { prefs.favorites = favorites - app.packageName })
                val index = favorites.indexOf(app.packageName)
                if (index > 0) add(R.string.move_left to { prefs.favorites = favorites.swap(index, index - 1) })
                if (index < favorites.lastIndex) add(R.string.move_right to { prefs.favorites = favorites.swap(index, index + 1) })
            } else {
                add(R.string.pin to { prefs.favorites = favorites + app.packageName })
            }
            add(R.string.hide to { prefs.hidden = prefs.hidden + app.packageName })
            add(R.string.app_info to {
                startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
            })
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, which ->
                actions[which].second()
                render()
            }
            .show()
    }

    private fun showSettingsMenu() {
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.tv_settings to { startSafely(Intent(Settings.ACTION_SETTINGS)) },
            R.string.network_settings to { startSafely(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            R.string.show_hidden to ::showHiddenApps,
        )
        AlertDialog.Builder(this)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun showHiddenApps() {
        val hiddenApps = installed.filter { it.packageName in prefs.hidden }
        if (hiddenApps.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show()
            return
        }
        // Ticked apps stay hidden; untick one to show it again.
        val checked = BooleanArray(hiddenApps.size) { true }
        AlertDialog.Builder(this)
            .setTitle(R.string.show_hidden)
            .setMultiChoiceItems(hiddenApps.map { it.label }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.hidden = hiddenApps.filterIndexed { i, _ -> checked[i] }.map { it.packageName }.toSet()
                render()
            }
            .show()
    }

    private fun startSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show()
        false
    }

    private fun List<String>.swap(a: Int, b: Int): List<String> =
        toMutableList().also { it[a] = this[b]; it[b] = this[a] }
}
