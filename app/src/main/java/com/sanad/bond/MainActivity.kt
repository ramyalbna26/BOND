package com.sanad.bond

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sanad.bond.databinding.ActivityMainBinding
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.ui.home.HomeActivity
import com.sanad.bond.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        LogManager.i("MainActivity", "onCreate")

        binding.textVersion.text = getString(
            R.string.main_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        binding.buttonOpenLibrary.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        binding.buttonOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonOpenAbout.setOnClickListener {
            startActivity(SettingsActivity.intentFor(this, "about"))
        }
    }

    override fun onResume() {
        super.onResume()
        LogManager.i("MainActivity", "onResume")
    }
}