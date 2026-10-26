package com.tv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(private val onClick: (App) -> Unit) : RecyclerView.Adapter<AppAdapter.Holder>() {

    var apps: List<App> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(apps[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val fallback: View = view.findViewById(R.id.fallback)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val label: TextView = view.findViewById(R.id.label)

        init {
            view.clipToOutline = true
            view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus -> focusZoom(v, hasFocus) }
        }

        fun bind(app: App) {
            itemView.contentDescription = app.label
            banner.setImageDrawable(app.banner)
            banner.visibility = if (app.banner != null) View.VISIBLE else View.GONE
            fallback.visibility = if (app.banner != null) View.GONE else View.VISIBLE
            icon.setImageDrawable(app.icon)
            label.text = app.label
            itemView.setOnClickListener { onClick(app) }
        }
    }
}

/** Gently enlarges a tile while it has focus. */
fun focusZoom(view: View, hasFocus: Boolean) {
    val scale = if (hasFocus) 1.08f else 1f
    view.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 24f else 0f)
        .setDuration(160).start()
}
