/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.adapters

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.amias.texttoimagesearch.fragments.ImageFragment
import com.amias.texttoimagesearch.MainActivity
import com.amias.texttoimagesearch.R


class ImageAdapter(private val context: Context, private val dataset: List<Long>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
    private val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)
    }

    /**
 * Creates a new [ImageViewHolder] instance for the RecyclerView adapter.
 * This method is called by the RecyclerView when it needs a new ViewHolder
 * to display an item.
 *
 * @param parent The ViewGroup that will contain the new ViewHolder.
 * @param viewType The type of item that needs to be displayed.
 * @return A new instance of [ImageViewHolder].
 */
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
    // Inflate the layout for the list item.
    val adapterLayout =
        LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
    // Create a new ImageViewHolder instance and pass the inflated layout.
    return ImageViewHolder(adapterLayout)
}

    override fun getItemCount(): Int = dataset.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
    // Get the current image data from the dataset based on the position.
    val item = dataset[position]

    // Construct a unique image URI by appending the image ID to the base URI.
    val imageUri = Uri.withAppendedPath(uri, item.toString())

    // Use Glide to load the image with a thumbnail preview into the ImageView.
    Glide.with(context).load(imageUri).thumbnail().into(holder.imageView)

    // Set an onClick listener for the ImageView to open the ImageFragment.
    holder.imageView.setOnClickListener {
        // Create a new Bundle to store data for the ImageFragment.
        val arguments = Bundle()

        // Put the image ID and URI into the Bundle.
        arguments.putLong("image_id", item)
        arguments.putString("image_uri", imageUri.toString())

        // Start a FragmentTransaction to add the ImageFragment to the backstack.
        val transaction: FragmentTransaction =
            (context as MainActivity).supportFragmentManager.beginTransaction()

        // Create a new instance of the ImageFragment and set its arguments.
        val fragment = ImageFragment()
        fragment.arguments = arguments

        // Add the ImageFragment to the backstack and replace the current fragment
        // in the fragmentContainerView.
        transaction.addToBackStack("search_fragment")
        transaction.replace(R.id.fragmentContainerView, fragment)
        transaction.addToBackStack("image_fragment")

        // Commit the FragmentTransaction.
        transaction.commit()
    }
}
}