/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.fragments

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.amias.texttoimagesearch.R
import com.amias.texttoimagesearch.utils.GeoLocation
import com.amias.texttoimagesearch.utils.getGeoLocationFromImage
import com.amias.texttoimagesearch.utils.getPlaceName
import com.amias.texttoimagesearch.viewmodels.ORTImageViewModel
import com.amias.texttoimagesearch.viewmodels.SearchViewModel
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.launch
import java.text.DateFormat

class ImageFragment : Fragment() {
    private var imageUri: Uri? = null
    private var imageId: Long? = null
    private val mORTImageViewModel: ORTImageViewModel by activityViewModels()
    private val mSearchViewModel: SearchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_image, container, false)
        val bundle = this.arguments
        bundle?.let {
            imageId = it.getLong("image_id")
            imageUri = it.getString("image_uri")?.toUri()
        }

        // Get image date from image URI
        val cursor: Cursor =
            requireContext().contentResolver.query(imageUri!!, null, null, null, null)!!
        cursor.moveToFirst()
        val idx: Int = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED)
        val date: Long = cursor.getLong(idx) * 1000
        cursor.close()

        val dateTextView: TextView = view.findViewById(R.id.dateTextView)
        val locationNameTextView: TextView = view.findViewById(R.id.locationNameTextView)
        val buttonViewInMap: ImageButton = view.findViewById(R.id.buttonViewInMap)

        dateTextView.text = DateFormat.getDateInstance().format(date)

        val singleImageView: PhotoView = view.findViewById(R.id.singeImageView)
        Glide.with(view).load(imageUri).into(singleImageView)

        val buttonImage2Image: Button = view.findViewById(R.id.buttonImage2Image)
        buttonImage2Image.setOnClickListener {
            try {
                imageId?.let {
                    val imageIndex = mORTImageViewModel.idxList.indexOf(it)
                    val imageEmbedding = mORTImageViewModel.embeddingsList[imageIndex]
                    mSearchViewModel.sortByCosineDistance(
                        imageEmbedding,
                        mORTImageViewModel.embeddingsList,
                        mORTImageViewModel.idxList,
                    )
                }
                mSearchViewModel.fromImg2ImgFlag = true
                parentFragmentManager.popBackStack()
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }

        val buttonShare: ImageButton = view.findViewById(R.id.buttonShare)
        buttonShare.setOnClickListener {
            val sendIntent: Intent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    type = "image/*"
                }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }

        val geoLocation = getGeoLocationFromImage(requireActivity().applicationContext, imageUri!!)
        if (geoLocation != null) {
            buttonViewInMap.setOnClickListener {
                openLocationInMap(requireActivity().applicationContext, geoLocation)
            }

            lifecycleScope.launch {
                getPlaceName(
                    requireActivity().applicationContext,
                    geoLocation,
                    onResult = fun(placeName: String?) {
                        if (placeName != null) {
                            locationNameTextView.text = placeName

                            println("Place name: $placeName")
                        } else {
                            locationNameTextView.visibility = View.INVISIBLE
                            buttonViewInMap.visibility = View.INVISIBLE
                            println("Unable to find place name.")
                        }
                    },
                )
            }
        } else {
            println("No location found in the image.")
        }

        return view
    }

    fun openLocationInMap(
        context: Context,
        geoLocation: GeoLocation,
        label: String = "Location",
    ) {
        // Create a Uri from latitude and longitude
        val uri = "geo:0,0?q=${geoLocation.latitude},${geoLocation.longitude}($label)".toUri()

        // Create an Intent
        val intent = Intent(Intent.ACTION_VIEW, uri)

        // Verify intent can be handled
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No map application found.", Toast.LENGTH_SHORT).show()
        }
    }
}
