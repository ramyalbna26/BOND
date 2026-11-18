package com.sanad.bond.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.R
import com.sanad.bond.data.model.FileItem
import com.sanad.bond.data.repository.FilesRepository
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.databinding.FragmentFileListBinding
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.ui.editor.EditorActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FileListFragment : Fragment() {

    private var _binding: FragmentFileListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileAdapter
    private val mode: FileListMode by lazy {
        FileListMode.valueOf(requireArguments().getString(ARG_MODE) ?: FileListMode.RECENT.name)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileAdapter(
            onClick = { item -> openFile(item) },
            onFavoriteClick = { item -> toggleFavorite(item) },
            onLongClick = { item -> showItemMenu(item) }
        )
        binding.recyclerFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFiles.adapter = adapter

        binding.textEmpty.setText(emptyTextFor(mode))
        observeData()
    }

    override fun onResume() {
        super.onResume()
        if (mode == FileListMode.FILES) refreshFiles()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun observeData() {
        when (mode) {
            FileListMode.RECENT -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    FilesRepository.observeRecent().collectLatest { render(it) }
                }
            }
            FileListMode.FAVORITES -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    FilesRepository.observeFavorites().collectLatest { render(it) }
                }
            }
            FileListMode.FILES -> refreshFiles()
        }
    }

    private fun refreshFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val files = FilesRepository.scanAppFiles(requireContext())
            render(files)
        }
    }

    private fun render(items: List<FileItem>) {
        _binding ?: return
        adapter.submitList(items)
        val empty = items.isEmpty()
        binding.textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recyclerFiles.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun openFile(item: FileItem) {
        LogManager.i(TAG, "openFile: ${item.displayName} (${item.mimeType})")

        // Gate: the feature that handles this MIME type must be enabled
        // in Settings → Libraries.
        if (!SettingsRepository.isFeatureEnabledFor(item.mimeType)) {
            LogManager.w(TAG, "Feature disabled for mime=${item.mimeType}")
            Toast.makeText(
                requireContext(),
                R.string.feature_disabled_for_type,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Build intent first — if that throws, we never touch the recent table
        val intent = try {
            EditorActivity.intentFor(
                context = requireContext(),
                uri = item.uri,
                displayName = item.displayName,
                mimeType = item.mimeType
            )
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to build intent", t)
            Toast.makeText(requireContext(), "خطأ في الفتح: ${t.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Start the activity first, then record the recent entry only when
        // the intent actually resolved. Otherwise a broken URI could pollute
        // the recent list with entries that can never be opened again.
        try {
            startActivity(intent)
            LogManager.i(TAG, "EditorActivity started successfully")

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    FilesRepository.recordRecent(
                        uri = item.uri,
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        sizeBytes = item.sizeBytes
                    )
                } catch (t: Throwable) {
                    LogManager.e(TAG, "recordRecent failed", t)
                }
            }
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to start EditorActivity", t)
            Toast.makeText(requireContext(), "خطأ في الفتح: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFavorite(item: FileItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val nowFavorite = FilesRepository.toggleFavorite(item)
            val msg = if (nowFavorite) R.string.file_added_favorite else R.string.file_removed_favorite
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            if (mode == FileListMode.FILES) refreshFiles()
        }
    }

    /**
     * Long-press menu. Available actions depend on the current tab:
     *   - RECENT:    toggle favorite, remove from recent
     *   - FILES:     toggle favorite
     *   - FAVORITES: toggle favorite (removes from favorites)
     */
    private fun showItemMenu(item: FileItem) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Favorite toggle — always available
        labels.add(getString(R.string.file_action_favorite))
        actions.add { toggleFavorite(item) }

        // Remove from recent — only meaningful in RECENT mode
        if (mode == FileListMode.RECENT) {
            labels.add(getString(R.string.file_removed_recent))
            actions.add {
                viewLifecycleOwner.lifecycleScope.launch {
                    FilesRepository.removeRecent(item.uri)
                    Toast.makeText(
                        requireContext(),
                        R.string.file_removed_recent,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        if (labels.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.displayName)
            .setItems(labels.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun emptyTextFor(mode: FileListMode): String = when (mode) {
        FileListMode.RECENT -> getString(R.string.home_empty_recent)
        FileListMode.FILES -> getString(R.string.home_empty_files)
        FileListMode.FAVORITES -> getString(R.string.home_empty_favorites)
    }

    companion object {
        private const val TAG = "FileListFragment"
        private const val ARG_MODE = "arg_mode"

        fun newInstance(mode: FileListMode): FileListFragment =
            FileListFragment().apply {
                arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
            }
    }
}