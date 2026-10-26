package com.tv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var tabs: List<View>
    private lateinit var panels: List<View>
    private lateinit var netflix: View
    private lateinit var smallClock: View
    private lateinit var qr: ImageView
    private lateinit var remoteHint: TextView
    private val appsAdapter = AppAdapter { Apps.launch(this, it) }
    private var selectedTab = 0
    private var qrUrl: String? = null

    // Always show the launcher in English, whatever language the TV is set to.
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabs = listOf(R.id.tab_home, R.id.tab_apps, R.id.tab_settings).map { findViewById(it) }
        panels = listOf(R.id.panel_home, R.id.panel_apps, R.id.panel_settings).map { findViewById(it) }
        smallClock = findViewById(R.id.small_clock)
        qr = findViewById(R.id.qr)
        remoteHint = findViewById(R.id.remote_hint)

        netflix = setUpTile(R.id.netflix, NETFLIX)
        setUpTile(R.id.youtube, YOUTUBE)

        val appContext = applicationContext
        RemoteServer.start(this) { command, text ->
            mainHandler.post { RemoteCommands.run(appContext, command, text) }
        }
        setUpApps()
        setUpSettings()

        // Moving onto a tab shows its page.
        tabs.forEachIndexed { index, tab ->
            tab.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) selectTab(index) }
            tab.setOnClickListener { panels[index].requestFocus(View.FOCUS_DOWN) }
        }
        // Pressing Up from a page lands on that page's tab, not whichever tab is closest.
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            val fromPage = oldFocus == null || oldFocus !in tabs
            if (newFocus != null && newFocus in tabs && fromPage && newFocus != tabs[selectedTab]) {
                tabs[selectedTab].requestFocus()
            }
        }

        selectTab(0)
        netflix.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateRemote()
        if (selectedTab == 1) loadApps()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        selectTab(0)
        netflix.requestFocus()
    }

    @Deprecated("Back returns to the Home tab, and does nothing there.")
    override fun onBackPressed() {
        if (selectedTab != 0) {
            selectTab(0)
            netflix.requestFocus()
        }
    }

    private fun selectTab(index: Int) {
        if (index == 1 && selectedTab != 1) loadApps()
        selectedTab = index
        tabs.forEachIndexed { i, tab -> tab.isSelected = i == index }
        panels.forEachIndexed { i, panel -> panel.visibility = if (i == index) View.VISIBLE else View.GONE }
        smallClock.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
    }

    private fun setUpTile(tileId: Int, pkg: String): View {
        val tile = findViewById<View>(tileId)
        tile.clipToOutline = true
        tile.setOnClickListener { Apps.launch(this, pkg) }
        tile.setOnFocusChangeListener { v, hasFocus -> focusZoom(v, hasFocus) }
        return tile
    }

    private fun setUpApps() {
        val list = findViewById<RecyclerView>(R.id.panel_apps)
        val tileWidth = resources.displayMetrics.density * 212 // 192dp tile + margins.
        val columns = ((resources.displayMetrics.widthPixels - list.paddingStart - list.paddingEnd) / tileWidth)
            .toInt().coerceAtLeast(1)
        list.layoutManager = GridLayoutManager(this, columns)
        list.adapter = appsAdapter
        list.itemAnimator = null
    }

    private fun loadApps() {
        executor.execute {
            val apps = Apps.all(this)
            mainHandler.post { appsAdapter.apps = apps }
        }
    }

    private fun setUpSettings() {
        val panel = findViewById<ViewGroup>(R.id.panel_settings)
        val items = listOf(
            Triple(R.drawable.ic_wifi, R.string.set_wifi, Settings.ACTION_WIFI_SETTINGS),
            Triple(R.drawable.ic_display, R.string.set_display, Settings.ACTION_DISPLAY_SETTINGS),
            Triple(R.drawable.ic_accessibility, R.string.set_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Triple(R.drawable.ic_settings, R.string.set_all, Settings.ACTION_SETTINGS),
        )
        for ((icon, label, action) in items) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_setting, panel, false)
            item.findViewById<ImageView>(R.id.icon).setImageResource(icon)
            item.findViewById<TextView>(R.id.label).setText(label)
            item.clipToOutline = true
            item.setOnClickListener { openSettings(action) }
            item.setOnFocusChangeListener { v, hasFocus -> focusZoom(v, hasFocus) }
            panel.addView(item)
        }
    }

    // Some TVs don't have every settings page, so fall back to the main one.
    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun updateRemote() {
        val url = RemoteServer.url(this)
        if (url == null) {
            qr.visibility = View.GONE
            remoteHint.setText(R.string.remote_no_wifi)
            return
        }
        if (url != qrUrl) {
            qr.setImageBitmap(qrCode(url, 512))
            qrUrl = url
        }
        qr.visibility = View.VISIBLE
        remoteHint.setText(if (HomeButtonService.instance != null) R.string.remote_hint else R.string.remote_off)
    }

    private fun qrCode(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        val pixels = IntArray(size * size) { i ->
            if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
