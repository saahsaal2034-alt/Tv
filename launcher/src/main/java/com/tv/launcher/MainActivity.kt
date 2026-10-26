package com.tv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var netflix: View
    private lateinit var qr: ImageView
    private lateinit var remoteHint: TextView
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

        netflix = setUpTile(R.id.netflix, R.id.netflix_banner, R.id.netflix_fallback, NETFLIX)
        setUpTile(R.id.youtube, R.id.youtube_banner, R.id.youtube_fallback, YOUTUBE)
        qr = findViewById(R.id.qr)
        remoteHint = findViewById(R.id.remote_hint)

        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        netflix.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateRemote()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        netflix.requestFocus()
    }

    @Deprecated("Back does nothing on the home screen.")
    override fun onBackPressed() = Unit

    private fun setUpTile(tileId: Int, bannerId: Int, fallbackId: Int, pkg: String): View {
        val tile = findViewById<View>(tileId)
        tile.clipToOutline = true
        Apps.banner(this, pkg)?.let { banner ->
            findViewById<ImageView>(bannerId).apply {
                setImageDrawable(banner)
                visibility = View.VISIBLE
            }
            findViewById<View>(fallbackId).visibility = View.GONE
        }
        tile.setOnClickListener { Apps.launch(this, pkg) }
        tile.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 24f else 0f)
                .setDuration(160).start()
        }
        return tile
    }

    private fun updateRemote() {
        val url = RemoteServer.url(this)
        when {
            !RemoteServer.running -> showRemoteHint(R.string.remote_off)
            url == null -> showRemoteHint(R.string.remote_no_wifi)
            else -> {
                if (url != qrUrl) {
                    qr.setImageBitmap(qrCode(url, 512))
                    qrUrl = url
                }
                qr.visibility = View.VISIBLE
                remoteHint.setText(R.string.remote_hint)
            }
        }
    }

    private fun showRemoteHint(text: Int) {
        qr.visibility = View.GONE
        remoteHint.setText(text)
    }

    private fun qrCode(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        val pixels = IntArray(size * size) { i ->
            if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
