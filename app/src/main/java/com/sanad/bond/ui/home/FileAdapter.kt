package com.sanad.bond.ui.home

import android.content.Context
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanad.bond.R
import com.sanad.bond.data.model.FileItem
import com.sanad.bond.databinding.ItemFileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val onClick: (FileItem) -> Unit,
    private val onFavoriteClick: (FileItem) -> Unit,
    private val onLongClick: (FileItem) -> Unit
) : ListAdapter<FileItem, FileAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onClick, onFavoriteClick, onLongClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemFileBinding,
        private val onClick: (FileItem) -> Unit,
        private val onFavoriteClick: (FileItem) -> Unit,
        private val onLongClick: (FileItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileItem) {
            val ctx = binding.root.context
            binding.textName.text = item.displayName

            val size = Formatter.formatShortFileSize(ctx, item.sizeBytes)
            val date = DATE_FORMAT.format(Date(item.lastModified))
            binding.textMeta.text = ctx.getString(R.string.file_meta_format, size, date)

            // Icon + tint by mime (uses the bundled ic_doc_generic vector)
            binding.imageIcon.setImageResource(R.drawable.ic_doc_generic)
            binding.imageIcon.setColorFilter(iconTintFor(item.mimeType, ctx))

            // Star icon + tint by favorite state
            binding.buttonFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_empty
            )
            val starTint = if (item.isFavorite) {
                resolveAttrColor(ctx, com.google.android.material.R.attr.colorPrimary)
            } else {
                resolveAttrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            binding.buttonFavorite.setColorFilter(starTint)

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
            binding.buttonFavorite.setOnClickListener { onFavoriteClick(item) }
        }

        private fun iconTintFor(mime: String, context: Context): Int {
            val attr = when {
                mime.contains("pdf", ignoreCase = true) ->
                    com.google.android.material.R.attr.colorError
                mime.contains("word", ignoreCase = true) ||
                        mime.contains("document", ignoreCase = true) ->
                    com.google.android.material.R.attr.colorPrimary
                mime.startsWith("text/") ->
                    com.google.android.material.R.attr.colorTertiary
                mime.startsWith("image/") ->
                    com.google.android.material.R.attr.colorSecondary
                else ->
                    com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            return resolveAttrColor(context, attr)
        }

        /**
         * Safely resolves a theme attribute to a concrete color.
         *
         * - If the attribute resolves to a raw color (TYPE_*_COLOR_INT), returns it.
         * - If it resolves to a resource id, resolves via [ContextCompat.getColor].
         * - Falls back to a neutral gray so we never throw at bind time.
         */
        private fun resolveAttrColor(context: Context, attr: Int): Int {
            val tv = TypedValue()
            if (context.theme.resolveAttribute(attr, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                    tv.type <= TypedValue.TYPE_LAST_COLOR_INT
                ) {
                    return tv.data
                }
                if (tv.resourceId != 0) {
                    return ContextCompat.getColor(context, tv.resourceId)
                }
            }
            return ContextCompat.getColor(context, android.R.color.darker_gray)
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM", Locale.US)

        private val DIFF = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(a: FileItem, b: FileItem) = a.uri == b.uri
            override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
        }
    }
}