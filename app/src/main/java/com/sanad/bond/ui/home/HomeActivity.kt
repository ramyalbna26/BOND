package com.sanad.bond.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.sanad.bond.R
import com.sanad.bond.databinding.ActivityHomeBinding
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.ui.create.CreateActivity
import com.sanad.bond.ui.settings.SettingsActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNav()
        setupFab()

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_recent
        }

        LogManager.i(TAG, "HomeActivity opened")
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.home_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_recent -> { showList(FileListMode.RECENT); true }
                R.id.nav_files -> { showList(FileListMode.FILES); true }
                R.id.nav_favorites -> { showList(FileListMode.FAVORITES); true }
                else -> false
            }
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, CreateActivity::class.java))
        }
    }

    private fun showList(mode: FileListMode) {
        val fragment = FileListFragment.newInstance(mode)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment)
        }
    }

    companion object {
        private const val TAG = "HomeActivity"
    }
}