/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.fragments

import android.os.Bundle
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
import com.amias.texttoimagesearch.viewmodels.ORTImageViewModel
import com.amias.texttoimagesearch.viewmodels.ORTTextViewModel
import com.amias.texttoimagesearch.viewmodels.SearchViewModel


class SearchFragment : Fragment() {
    private var searchText: TextView? = null
    private var searchIconButton: ImageButton? = null
    private var toolbar: Toolbar? = null
    private val mORTImageViewModel: ORTImageViewModel by activityViewModels()
    private val mORTTextViewModel: ORTTextViewModel by activityViewModels()
    private val mSearchViewModel: SearchViewModel by activityViewModels()

    private val searchHintsLabel = arrayOf("Mountains and Forest", "Cup of Tea", "Dark Sunset Night", "Green Forest", "Waterfalls and River")

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
    }

    private fun registerMenuOption(recyclerView: RecyclerView) {
        // Register the MenuProvider
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Inflate the menu for this Fragment
                menuInflater.inflate(R.menu.toolbar_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        // Handle refresh action
                        searchText?.text = null
                        mSearchViewModel.searchResults = mORTImageViewModel.idxList.reversed()
                        recyclerView.adapter = ImageAdapter(requireContext(), mSearchViewModel.searchResults!!)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        if (mSearchViewModel.searchResults == null) {
            mSearchViewModel.searchResults = mORTImageViewModel.idxList.reversed()
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
            val textEmbedding: FloatArray =
                mORTTextViewModel.getTextEmbedding(searchText?.text.toString())
            mSearchViewModel.sortByCosineDistance(textEmbedding, mORTImageViewModel.embeddingsList, mORTImageViewModel.idxList)
            recyclerView.adapter = ImageAdapter(requireContext(), mSearchViewModel.searchResults!!)
        }
        return view
    }


}