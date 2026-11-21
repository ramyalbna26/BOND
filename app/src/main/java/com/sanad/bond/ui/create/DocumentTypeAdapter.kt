package com.sanad.bond.ui.create

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanad.bond.R
import com.sanad.bond.databinding.ItemDocumentTypeBinding

class DocumentTypeAdapter(
    private val onClick: (DocumentType) -> Unit
) : ListAdapter<DocumentType, DocumentTypeAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDocumentTypeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemDocumentTypeBinding,
        private val onClick: (DocumentType) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(type: DocumentType) {
            val ctx = binding.root.context

            binding.textTitle.setText(type.titleRes)
            binding.textSubtitle.setText(type.subtitleRes)
            binding.imageIcon.setImageResource(type.iconRes)
            binding.imageIcon.setColorFilter(colorFor(type.id, ctx))

            binding.root.setOnClickListener { onClick(type) }
        }

        private fun colorFor(id: String, context: Context): Int {
            val attr = when (id) {
                "word" -> com.google.android.material.R.attr.colorPrimary
                "pdf" -> com.google.android.material.R.attr.colorError
                "markdown" -> com.google.android.material.R.attr.colorTertiary
                "text" -> com.google.android.material.R.attr.colorSecondary
                else -> com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            val tv = TypedValue()
            context.theme.resolveAttribute(attr, tv, true)
            return tv.data
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DocumentType>() {
            override fun areItemsTheSame(a: DocumentType, b: DocumentType) = a.id == b.id
            override fun areContentsTheSame(a: DocumentType, b: DocumentType) = a == b
        }
    }
}