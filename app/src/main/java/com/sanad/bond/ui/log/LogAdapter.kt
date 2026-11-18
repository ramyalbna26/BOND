package com.sanad.bond.ui.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sanad.bond.databinding.ItemLogEntryBinding
import com.sanad.bond.core.logging.LogEntry
import com.sanad.bond.core.logging.LogLevel

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = ArrayList<LogEntry>(512)

    fun submit(newItems: List<LogEntry>) {
        val old = items.toList()
        items.clear()
        items.addAll(newItems)
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(a: Int, b: Int) = old[a].timestamp == items[b].timestamp &&
                old[a].tag == items[b].tag && old[a].message == items[b].message
            override fun areContentsTheSame(a: Int, b: Int) = old[a] == items[b]
        }).dispatchUpdatesTo(this)
    }

    fun append(entry: LogEntry) {
        items.add(entry)
        notifyItemInserted(items.size - 1)
        if (items.size > 3000) {
            val removed = items.size - 3000
            repeat(removed) { items.removeAt(0) }
            notifyItemRangeRemoved(0, removed)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class VH(private val b: ItemLogEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: LogEntry) {
            b.textLine.text = e.format()
            b.textLine.setTextColor(colorFor(e.level))
        }
        private fun colorFor(level: LogLevel): Int = when (level) {
            LogLevel.VERBOSE -> Color.parseColor("#9E9E9E")
            LogLevel.DEBUG   -> Color.parseColor("#42A5F5")
            LogLevel.INFO    -> Color.parseColor("#66BB6A")
            LogLevel.WARN    -> Color.parseColor("#FFA726")
            LogLevel.ERROR   -> Color.parseColor("#EF5350")
        }
    }
}