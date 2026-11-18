package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.textview.MaterialTextView
import com.sanad.bond.R

/**
 * Temporary fragment for Level 3.
 *
 * Shows the section title + a "coming soon" message so the navigation works
 * end-to-end before Level 4 writes the real preference fragments.
 *
 * Level 4 will remove references to this class from SettingsSections.
 */
class PlaceholderSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val title = arguments?.getString(ARG_TITLE) ?: ""
        return MaterialTextView(requireContext()).apply {
            text = buildString {
                append(title)
                append("\n\n")
                append(context.getString(R.string.settings_placeholder_message))
            }
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(64, 120, 64, 120)
        }
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String): PlaceholderSettingsFragment =
            PlaceholderSettingsFragment().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
            }
    }
}