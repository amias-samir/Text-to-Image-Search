/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.amias.texttoimagesearch.viewmodels.ORTImageViewModel
import com.amias.texttoimagesearch.R
import androidx.core.net.toUri

//class IndexFragment : Fragment() {
//    private var progressBarView: ProgressBar? = null
//    private var progressBarTextView: TextView? = null
//    private val mORTImageViewModel: ORTImageViewModel by activityViewModels()
//
//    private val permissionsRequest: ActivityResultLauncher<String> = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (isGranted) {
//            mORTImageViewModel.generateIndex()
//        } else {
//                // Create an AlertDialog
//                val builder = AlertDialog.Builder(activity)
//                builder.setTitle("The app requires storage permissions!")
//                builder.setMessage("Storage permission is required to search for visually similar images by choosing a photo from your device's gallery and Text to image search functionality to search relevant images from your local gallery.")
//                builder.setPositiveButton("Grant") { dialog, _ ->
//                    // Handle the OK button action
//                    dialog.dismiss() // Closes the dialog
//
//                    // Request required permissions depending on the Android version
//                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//                    permissionsRequest.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
//                }
//                else {
//                    permissionsRequest.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
//                }
//
////                    openAppPermissionSettings()
//                }
//                builder.setNegativeButton("Cancel") { dialog, _ ->
//                    // Handle the Cancel button action
//                    dialog.dismiss()
//                }
//
//                // Display the dialog
//                builder.show()
//
////                Toast.makeText(context, "The app requires storage permissions!", Toast.LENGTH_SHORT)
////                    .show()
//        }
//    }
//
//    private fun openAppPermissionSettings() {
//        val packageName = activity?.application?.packageName
//        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
//            data = "package:$packageName".toUri()
//        }
//        startActivity(intent)
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?,
//    ): View? {
//        val view = inflater.inflate(R.layout.fragment_index, container, false)
//        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//
//        // Request required permissions depending on the Android version
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//            permissionsRequest.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
//        }
//        else {
//            permissionsRequest.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
//        }
//
//        progressBarView = view.findViewById(R.id.progressBar)
//        progressBarTextView = view.findViewById(R.id.progressBarText)
//
//        mORTImageViewModel.progress.observe(viewLifecycleOwner) { progress ->
//            var progressPercent: Int = (progress * 100).toInt()
//            progressBarView?.progress = progressPercent
//            progressBarTextView?.text = "Updating image index: ${progressPercent}%"
//
//            if (progress == 1.0) {
//                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//                findNavController().navigate(R.id.action_indexFragment_to_searchFragment)
//            }
//        }
//        return view
//    }
//}

class IndexFragment : Fragment() {
    private var progressBarView: ProgressBar? = null
    private var progressBarTextView: TextView? = null
    private val ortImageViewModel: ORTImageViewModel by activityViewModels()

    // Use a constant to better clarity
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_MESSAGE_TITLE = "Storage Permission Required"
        private const val STORAGE_PERMISSION_REQUEST_MESSAGE_BODY =
            "Photos and Videos permission is required to search for visually similar images by choosing a photo from your device's gallery and Text to image search functionality to search relevant images from your local gallery."
    }

    // Using RequestMultiplePermissions to request for permissions
    private val multiplePermissionsRequest: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value }
        if (allPermissionsGranted) {
            ortImageViewModel.generateIndex()
        } else {
            showPermissionRationaleDialog()
        }
    }

    private val requiredPermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(requireActivity()).apply {
            setTitle(STORAGE_PERMISSION_REQUEST_MESSAGE_TITLE)
            setMessage(STORAGE_PERMISSION_REQUEST_MESSAGE_BODY)
            setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                // Launch the request for multiple permissions
//                multiplePermissionsRequest.launch(requiredPermissions)
                openAppPermissionSettings()
            }
            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    private fun openAppPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${requireActivity().packageName}".toUri()
        }
        startActivity(intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_index, container, false)
        requireActivity().window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        progressBarView = view.findViewById(R.id.progressBar)
        progressBarTextView = view.findViewById(R.id.progressBarText)

        startIndexing()

        return view
    }

    private fun startIndexing(){
        // check for permissions before starting viewmodel
        checkPermissionsAndStartIndexing()

        ortImageViewModel.progress.observe(viewLifecycleOwner) { progress ->
            val progressPercent = (progress * 100).toInt()
            progressBarView?.progress = progressPercent
            progressBarTextView?.text = "Updating image index: ${progressPercent}%" // Use string resource
            if (progress == 1.0) {
                try{
                    requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    findNavController().navigate(R.id.action_indexFragment_to_searchFragment)                }
                catch (e: Exception){
                    e.printStackTrace()
                }

            }
        }
    }

    private fun checkPermissionsAndStartIndexing() {
        if (allPermissionsGranted()) {
            ortImageViewModel.generateIndex()
        } else {
            // Request all required permissions
            multiplePermissionsRequest.launch(requiredPermissions)
        }
    }

    // Check if all required permissions are granted
    private fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }


    override fun onResume() {
        super.onResume()
        // This is called when the fragment becomes visible and interactive again.

        if (allPermissionsGranted()) {
            ortImageViewModel.generateIndex()
        }
    }
}
