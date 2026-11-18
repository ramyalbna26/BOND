package com.sanad.bond.ui.settings.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.BuildConfig
import com.sanad.bond.R

/**
 * About: version info + contact shortcuts + legal links.
 */
class AboutSettingsFragment : BasePreferenceFragment() {

    private val phoneNumber   = "+201515938166"
    private val whatsappNumber = "201030669852"   // no leading +
    private val emailAddress   = "RAMYALBNA26@GMAIL.COM"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_about, rootKey)

        // ---- Version info ----
        find<Preference>("pref_about_version")?.summary =
            "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})"

        find<Preference>("pref_about_build")?.summary = buildString {
            append(BuildConfig.BUILD_TYPE.uppercase())
            append(" · Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(")")
        }

        // ---- Developer ----
        find<Preference>("pref_about_developer")?.summary = "رامي البنا — Rami Elbanna"

        // ---- Contact ----
        find<Preference>("pref_about_whatsapp")?.apply {
            summary = "+$whatsappNumber"
            setOnPreferenceClickListener {
                openUrl("https://wa.me/$whatsappNumber")
                true
            }
        }

        find<Preference>("pref_about_phone")?.apply {
            summary = phoneNumber
            setOnPreferenceClickListener {
                dial(phoneNumber)
                true
            }
        }

        find<Preference>("pref_about_email")?.apply {
            summary = emailAddress
            setOnPreferenceClickListener {
                email(emailAddress)
                true
            }
        }

        // ---- Legal ----
        find<Preference>("pref_about_licenses")?.setOnPreferenceClickListener {
            showLicensesDialog()
            true
        }
        find<Preference>("pref_about_privacy")?.setOnPreferenceClickListener {
            showComingSoonDialog(R.string.prefs_about_privacy_title)
            true
        }
        find<Preference>("pref_about_website")?.setOnPreferenceClickListener {
            showComingSoonDialog(R.string.prefs_about_website_title)
            true
        }
    }

    private fun showLicensesDialog() {
        val libraries = listOf(
            "AndroidX (AppCompat, Core, Activity, Fragment)",
            "Material Components for Android",
            "AndroidX Lifecycle + ViewModel",
            "AndroidX Preference",
            "AndroidX RecyclerView + ConstraintLayout",
            "Jetpack Compose (UI, Material3)",
            "compose-rich-editor (Mohamed Rejeb)",
            "Room (runtime + ktx + compiler)",
            "WorkManager (runtime-ktx)",
            "Kotlin Coroutines + kotlinx-serialization",
            "PDFBox-Android (Tom Roush)",
            "kotlin-gradle-plugin"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.prefs_about_licenses_title)
            .setMessage(libraries.joinToString("\n") { "• $it" })
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun showComingSoonDialog(titleRes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(R.string.settings_placeholder_message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            showMessage(R.string.no_app_to_open)
        }
    }

    private fun dial(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        } catch (_: Exception) {
            showMessage(R.string.no_dialer)
        }
    }

    private fun email(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
        } catch (_: Exception) {
            showMessage(R.string.no_email_app)
        }
    }
}