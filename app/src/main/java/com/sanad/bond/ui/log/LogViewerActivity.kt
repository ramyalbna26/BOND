package com.sanad.bond.ui.log

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanad.bond.R
import com.sanad.bond.databinding.ActivityLogViewerBinding
import com.sanad.bond.core.logging.LogEntry
import com.sanad.bond.core.logging.LogManager

class LogViewerActivity : AppCompatActivity(), LogManager.LogListener {

    private lateinit var binding: ActivityLogViewerBinding
    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = LogAdapter()
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerLogs.layoutManager = lm
        binding.recyclerLogs.adapter = adapter

        // Seed with existing snapshot (may be empty if we just started)
        adapter.submit(LogManager.snapshot())

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.log_viewer_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear -> {
                    LogManager.clear()
                    adapter.submit(emptyList())
                    true
                }
                R.id.action_scroll_to_bottom -> {
                    binding.recyclerLogs.scrollToPosition(adapter.itemCount - 1)
                    true
                }
                else -> false
            }
        }

        LogManager.addListener(this)
        LogManager.i("LogViewer", "Log viewer opened")
    }

    override fun onDestroy() {
        LogManager.removeListener(this)
        super.onDestroy()
    }

    override fun onLog(entry: LogEntry) {
        runOnUiThread {
            adapter.append(entry)
            val lm = binding.recyclerLogs.layoutManager as? LinearLayoutManager
            val atBottom = lm != null && lm.findLastVisibleItemPosition() >= adapter.itemCount - 3
            if (atBottom) binding.recyclerLogs.scrollToPosition(adapter.itemCount - 1)
        }
    }
}