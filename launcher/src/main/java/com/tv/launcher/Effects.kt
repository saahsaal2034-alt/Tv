package com.tv.launcher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** Gently enlarges a tile while it has focus, with a slight spring. */
fun focusZoom(view: View, hasFocus: Boolean) {
    val scale = if (hasFocus) 1.08f else 1f
    view.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 24f else 0f)
        .setInterpolator(if (hasFocus) OvershootInterpolator(2.2f) else DecelerateInterpolator())
        .setDuration(if (hasFocus) 320 else 200).start()
}

/**
 * Paints the text in polished gold with a highlight that sweeps across it
 * every few seconds.
 */
fun goldShimmer(view: TextView) {
    val context = view.context
    val dark = context.getColor(R.color.gold_dark)
    val gold = context.getColor(R.color.accent)
    val light = context.getColor(R.color.gold_light)
    val matrix = Matrix()
    var width = 0f

    fun applyShader() {
        width = view.width.toFloat().coerceAtLeast(1f)
        // Gold across the text, with a bright band that starts off to the left.
        view.paint.shader = LinearGradient(
            -width, 0f, width * 2, view.height * 0.4f,
            intArrayOf(dark, gold, gold, light, gold, gold, dark),
            floatArrayOf(0f, 0.2f, 0.42f, 0.5f, 0.58f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
        view.invalidate()
    }

    // The clock's width changes as the time changes, so rebuild the gradient.
    view.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) applyShader() }
    view.post { applyShader() }

    // Sweeps across, rests, then sweeps back, so it never jumps.
    ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = 5200
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            // The sweep crosses during the first part, then rests off to the side.
            val t = (it.animatedValue as Float * 2.2f).coerceIn(-1f, 1f)
            matrix.setTranslate(t * width, 0f)
            view.paint.shader?.setLocalMatrix(matrix)
            view.invalidate()
        }
        start()
    }
}

/** Fades and glides the views in one after another. */
fun entrance(views: List<View>, distance: Float) {
    views.forEachIndexed { i, v ->
        v.alpha = 0f
        v.translationY = distance
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(90L * i).setDuration(650)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }
}

/** A very slow drift-and-zoom on the background, so the city feels alive. */
fun kenBurns(view: View) {
    for (property in listOf("scaleX", "scaleY")) {
        ObjectAnimator.ofFloat(view, property, 1f, 1.08f).apply {
            duration = 40_000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
}
