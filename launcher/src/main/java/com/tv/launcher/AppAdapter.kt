package com.tv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (App) -> Unit,
    private val onLongClick: (App) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    var apps: List<App> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

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
            view.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.1f else 1f
                v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 8f else 0f)
                    .setDuration(120).start()
            }
        }

        fun bind(app: App) {
            itemView.contentDescription = app.label
            if (app.banner != null) {
                banner.setImageDrawable(app.banner)
                banner.visibility = View.VISIBLE
                fallback.visibility = View.GONE
            } else {
                banner.setImageDrawable(null)
                banner.visibility = View.GONE
                fallback.visibility = View.VISIBLE
                icon.setImageDrawable(app.icon)
                label.text = app.label
            }
            itemView.setOnClickListener { onClick(app) }
            itemView.setOnLongClickListener { onLongClick(app); true }
        }
    }
}
