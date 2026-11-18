package com.sanad.bond.ui.settings

import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsSection
import com.sanad.bond.ui.settings.fragments.AboutSettingsFragment
import com.sanad.bond.ui.settings.fragments.AdvancedSettingsFragment
import com.sanad.bond.ui.settings.fragments.ControlsSettingsFragment
import com.sanad.bond.ui.settings.fragments.EditorSettingsFragment
import com.sanad.bond.ui.settings.fragments.ExportSettingsFragment
import com.sanad.bond.ui.settings.fragments.FontsSettingsFragment
import com.sanad.bond.ui.settings.fragments.GeneralSettingsFragment
import com.sanad.bond.ui.settings.fragments.ImportSettingsFragment
import com.sanad.bond.ui.settings.fragments.LibrariesSettingsFragment
import com.sanad.bond.ui.settings.fragments.LoggingSettingsFragment
import com.sanad.bond.ui.settings.fragments.PdfSettingsFragment
import com.sanad.bond.ui.settings.fragments.PerformanceSettingsFragment
import com.sanad.bond.ui.settings.fragments.PrivacySettingsFragment
import com.sanad.bond.ui.settings.fragments.SecuritySettingsFragment
import com.sanad.bond.ui.settings.fragments.SharingSettingsFragment
import com.sanad.bond.ui.settings.fragments.StorageSettingsFragment
import com.sanad.bond.ui.settings.fragments.ToolsSettingsFragment
import com.sanad.bond.ui.settings.fragments.WordSettingsFragment

/**
 * The 18 settings sections, in display order.
 *
 * Icons: reused the bundled vectors (ic_settings, ic_doc_generic, ic_folder,
 * ic_recent). Each category shares an icon so the list stays visually
 * consistent without shipping 18 separate drawables.
 *
 *   - ic_settings   → app-level (logging, general, advanced, about,
 *                     privacy, security, performance, controls, sharing)
 *   - ic_doc_generic → document-related (libraries, editor, word, pdf,
 *                     fonts, tools)
 *   - ic_folder     → storage-related (export, import, storage)
 */
object SettingsSections {

    val ALL: List<SettingsSection> = listOf(

        SettingsSection(
            id = "logging",
            titleRes = R.string.settings_section_logging,
            iconRes = R.drawable.ic_settings,
            fragmentClass = LoggingSettingsFragment::class
        ),

        SettingsSection(
            id = "general",
            titleRes = R.string.settings_section_general,
            iconRes = R.drawable.ic_settings,
            fragmentClass = GeneralSettingsFragment::class
        ),

        SettingsSection(
            id = "libraries",
            titleRes = R.string.settings_section_libraries,
            iconRes = R.drawable.ic_doc_generic,
            fragmentClass = LibrariesSettingsFragment::class
        ),

        SettingsSection(
            id = "editor",
            titleRes = R.string.settings_section_editor,
            iconRes = R.drawable.ic_doc_generic,
            fragmentClass = EditorSettingsFragment::class
        ),

        SettingsSection(
            id = "word",
            titleRes = R.string.settings_section_word,
            iconRes = R.drawable.ic_doc_generic,
            fragmentClass = WordSettingsFragment::class
        ),

        SettingsSection(
            id = "pdf",
            titleRes = R.string.settings_section_pdf,
            iconRes = R.drawable.ic_doc_generic,
            fragmentClass = PdfSettingsFragment::class
        ),

        SettingsSection(
            id = "export",
            titleRes = R.string.settings_section_export,
            iconRes = R.drawable.ic_folder,
            fragmentClass = ExportSettingsFragment::class
        ),

        SettingsSection(
            id = "import",
            titleRes = R.string.settings_section_import,
            iconRes = R.drawable.ic_folder,
            fragmentClass = ImportSettingsFragment::class
        ),

        SettingsSection(
            id = "storage",
            titleRes = R.string.settings_section_storage,
            iconRes = R.drawable.ic_folder,
            fragmentClass = StorageSettingsFragment::class
        ),

        SettingsSection(
            id = "security",
            titleRes = R.string.settings_section_security,
            iconRes = R.drawable.ic_settings,
            fragmentClass = SecuritySettingsFragment::class
        ),

        SettingsSection(
            id = "performance",
            titleRes = R.string.settings_section_performance,
            iconRes = R.drawable.ic_settings,
            fragmentClass = PerformanceSettingsFragment::class
        ),

        SettingsSection(
            id = "privacy",
            titleRes = R.string.settings_section_privacy,
            iconRes = R.drawable.ic_settings,
            fragmentClass = PrivacySettingsFragment::class
        ),

        SettingsSection(
            id = "fonts",
            titleRes = R.string.settings_section_fonts,
            iconRes = R.drawable.ic_doc_generic,
            fragmentClass = FontsSettingsFragment::class
        ),

        SettingsSection(
            id = "tools",
            titleRes = R.string.settings_section_tools,
            iconRes = R.drawable.ic_doc_generic,
            fragmentClass = ToolsSettingsFragment::class
        ),

        SettingsSection(
            id = "sharing",
            titleRes = R.string.settings_section_sharing,
            iconRes = R.drawable.ic_settings,
            fragmentClass = SharingSettingsFragment::class
        ),

        SettingsSection(
            id = "controls",
            titleRes = R.string.settings_section_controls,
            iconRes = R.drawable.ic_settings,
            fragmentClass = ControlsSettingsFragment::class
        ),

        SettingsSection(
            id = "advanced",
            titleRes = R.string.settings_section_advanced,
            iconRes = R.drawable.ic_settings,
            fragmentClass = AdvancedSettingsFragment::class
        ),

        SettingsSection(
            id = "about",
            titleRes = R.string.settings_section_about,
            iconRes = R.drawable.ic_recent,
            fragmentClass = AboutSettingsFragment::class
        )
    )
}