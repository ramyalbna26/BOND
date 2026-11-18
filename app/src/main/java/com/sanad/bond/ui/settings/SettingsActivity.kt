package com.sanad.bond.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsSection
import com.sanad.bond.databinding.ActivitySettingsBinding
import com.sanad.bond.core.logging.LogManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: SettingsAdapter

    /**
     * FIX: FragmentContainerView does NOT register an OnBackPressedCallback
     * automatically. Without this, the physical back button bypasses the
     * FragmentManager and either finishes the activity or leaves an empty
     * fragmentContainer on screen.
     *
     * Registered in onCreate, enabled only when backStackEntryCount > 0.
     */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                // Should not happen (isEnabled is false), but be defensive:
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, backCallback)

        setupToolbar()
        setupRecyclerView()
        setupBackStackListener()

        // Restore correct visibility after rotation / process death
        syncVisibilityToBackStack()

        // NEW: when launched with EXTRA_SECTION_ID (e.g. from MainActivity's
        // "About" button), open that section immediately.
        if (savedInstanceState == null) {
            val sectionId = intent.getStringExtra(EXTRA_SECTION_ID)
            if (sectionId != null) {
                val section = SettingsSections.ALL.firstOrNull { it.id == sectionId }
                if (section != null) {
                    openSection(section)
                } else {
                    LogManager.w(TAG, "Unknown EXTRA_SECTION_ID: $sectionId")
                }
            }
        }

        LogManager.i(TAG, "SettingsActivity opened (backstack=${supportFragmentManager.backStackEntryCount})")
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Same path as the physical back button → single source of truth
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = SettingsAdapter { section -> openSection(section) }
        binding.recyclerSections.layoutManager = LinearLayoutManager(this)
        binding.recyclerSections.adapter = adapter
        adapter.submitList(SettingsSections.ALL)
    }

    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            syncVisibilityToBackStack()
            backCallback.isEnabled = supportFragmentManager.backStackEntryCount > 0
        }
        // Set initial value in case we're recreated with a non-empty stack
        backCallback.isEnabled = supportFragmentManager.backStackEntryCount > 0
    }

    /**
     * Single source of truth for the state:
     *   backStack empty  → show RecyclerView, hide container, no back icon
     *   backStack > 0    → hide RecyclerView, show container, show back icon
     *
     * Never toggles "empty container" state — that was the previous bug.
     */
    private fun syncVisibilityToBackStack() {
        val hasFragment = supportFragmentManager.backStackEntryCount > 0

        binding.recyclerSections.visibility = if (hasFragment) View.GONE else View.VISIBLE
        binding.fragmentContainer.visibility = if (hasFragment) View.VISIBLE else View.GONE

        if (hasFragment) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        } else {
            binding.toolbar.navigationIcon = null
        }

        binding.toolbar.title = if (hasFragment) {
            currentSectionTitle() ?: getString(R.string.settings_title)
        } else {
            getString(R.string.settings_title)
        }
    }

    private fun currentSectionTitle(): String? {
        // The last added backstack entry holds the section id
        val count = supportFragmentManager.backStackEntryCount
        if (count == 0) return null
        val entry = supportFragmentManager.getBackStackEntryAt(count - 1)
        val id = entry.name ?: return null
        val section = SettingsSections.ALL.firstOrNull { it.id == id } ?: return null
        return getString(section.titleRes)
    }

    private fun openSection(section: SettingsSection) {
        // Avoid stacking the same section twice
        val currentId = if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.getBackStackEntryAt(
                supportFragmentManager.backStackEntryCount - 1
            ).name
        } else null
        if (currentId == section.id) return

        val fragment: Fragment =
            section.fragmentClass.java.getDeclaredConstructor().newInstance()

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out
            )
            replace(R.id.fragmentContainer, fragment, section.id)
            addToBackStack(section.id)
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"
        const val EXTRA_SECTION_ID = "extra_section_id"

        /**
         * Builds an Intent that opens [SettingsActivity] with [sectionId]
         * pre-selected (e.g. "about" from MainActivity).
         */
        fun intentFor(context: Context, sectionId: String): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_SECTION_ID, sectionId)
            }
    }
}