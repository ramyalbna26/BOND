package com.sanad.bond.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanad.bond.data.model.SettingsSection
import com.sanad.bond.databinding.ItemSettingsSectionBinding

class SettingsAdapter(
    private val onClick: (SettingsSection) -> Unit
) : ListAdapter<SettingsSection, SettingsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSettingsSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemSettingsSectionBinding,
        private val onClick: (SettingsSection) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: SettingsSection) {
            binding.textTitle.setText(section.titleRes)
            binding.imageIcon.setImageResource(section.iconRes)
            binding.root.isEnabled = section.enabled
            binding.root.alpha = if (section.enabled) 1f else 0.5f
            binding.root.setOnClickListener {
                if (section.enabled) onClick(section)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SettingsSection>() {
            override fun areItemsTheSame(a: SettingsSection, b: SettingsSection) = a.id == b.id
            override fun areContentsTheSame(a: SettingsSection, b: SettingsSection) = a == b
        }
    }
}