/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.amias.texttoimagesearch.R
import com.amias.texttoimagesearch.adapters.ImageAdapter
import com.amias.texttoimagesearch.utils.Debouncer
import com.amias.texttoimagesearch.viewmodels.ORTImageViewModel
import com.amias.texttoimagesearch.viewmodels.ORTTextViewModel
import com.amias.texttoimagesearch.viewmodels.SearchViewModel
import com.amias.texttoimagesearch.widgets.CircularProgressTracker

class SearchFragment : Fragment() {
    private var searchText: TextView? = null
    private lateinit var recyclerView: RecyclerView
    private var searchIconButton: ImageButton? = null
    private var toolbar: Toolbar? = null
    private var progressBar: CircularProgressTracker? = null
    private val mORTImageViewModel: ORTImageViewModel by activityViewModels()
    private val mORTTextViewModel: ORTTextViewModel by activityViewModels()
    private val mSearchViewModel: SearchViewModel by activityViewModels()

    private val searchHintsLabel =
        arrayOf("Mountains and Forest", "Cup of Tea", "Dark Sunset Night", "Green Forest", "Waterfalls and River")

    override fun onResume() {
        super.onResume()
        searchText = view?.findViewById(R.id.searchText)
        searchText!!.hint = "hint: \"${searchHintsLabel.random()}\""

        val recyclerView = view?.findViewById<RecyclerView>(R.id.recycler_view)

        if (mSearchViewModel.fromImg2ImgFlag) {
            searchText?.text = null
            recyclerView?.scrollToPosition(0)
            mSearchViewModel.fromImg2ImgFlag = false
        }

        startToListenIndexingProgress()
    }

    private fun registerMenuOption(recyclerView: RecyclerView) {
        // Register the MenuProvider
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    // Inflate the menu for this Fragment
                    menuInflater.inflate(R.menu.toolbar_menu, menu)

                    // Find the menu item where you want the custom widget
                    val menuItem = menu.findItem(R.id.toolbarMenu)

                    // Inflate the custom layout
                    val customView = LayoutInflater.from(activity).inflate(R.layout.progress_tracker_menu_widget_layout, null)

                    progressBar = customView?.findViewById<CircularProgressTracker>(R.id.progressTracker)
                    // Set the custom view to the MenuItem
                    menuItem.actionView = customView

                    startToListenIndexingProgress()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_refresh -> {
                            // Handle refresh action
                            searchText?.text = null
                            mSearchViewModel.searchResults = mORTImageViewModel.idxList.distinct().reversed()
                            recyclerView.adapter = ImageAdapter(requireContext(), mSearchViewModel.searchResults!!)
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        if (mSearchViewModel.searchResults == null) {
            mSearchViewModel.searchResults = mORTImageViewModel.idxList.distinct().reversed()
        }
        recyclerView.adapter = ImageAdapter(requireContext(), mSearchViewModel.searchResults!!)
        recyclerView.scrollToPosition(0)

        mORTTextViewModel.init()

        searchText = view?.findViewById(R.id.searchText)
        searchText!!.hint = "hint: \"${searchHintsLabel.random()}\""

        searchIconButton = view?.findViewById(R.id.searchIcon)
        toolbar = view?.findViewById(R.id.toolbar)
        // Set up the Toolbar
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar as androidx.appcompat.widget.Toolbar)
        registerMenuOption(recyclerView)

        searchIconButton?.setOnClickListener {
            val textEmbedding: FloatArray = mORTTextViewModel.getTextEmbedding(searchText?.text.toString())
            mSearchViewModel.sortByCosineDistance(textEmbedding, mORTImageViewModel.embeddingsList, mORTImageViewModel.idxList)
            recyclerView.adapter = ImageAdapter(requireContext(), mSearchViewModel.searchResults!!)
        }
        return view
    }

    private var tempProgress = 0
    private val debouncer = Debouncer(5000) // 5 seconds

    private fun updateProgressTracker(progress: Int) {
        if (progress >= 100) {
            progressBar?.visibility = View.GONE
        } else {
            Log.i("SearchFragment", "progressData Indexed: $progress%")

            progressBar?.visibility = View.VISIBLE
            progressBar?.setProgress(progress.toFloat(), animate = true)
            if (tempProgress != progress) {
                tempProgress = progress
                debouncer.debounce {
                    mSearchViewModel.searchResults = mORTImageViewModel.idxList.distinct().reversed()
                    recyclerView.adapter = ImageAdapter(requireContext(), mSearchViewModel.searchResults!!)
                }
            }
        }
    }

    private fun startToListenIndexingProgress() {
        mORTImageViewModel.progressData.observe(viewLifecycleOwner) { progressData ->
            try {
                updateProgressTracker(((progressData.indexed.toDouble() / progressData.total.toDouble()) * 100).toInt())
            } catch (exception: Exception) {
            }
        }
    }
}
