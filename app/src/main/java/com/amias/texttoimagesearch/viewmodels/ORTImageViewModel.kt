/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.viewmodels

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.amias.texttoimagesearch.R
import com.amias.texttoimagesearch.centerCrop
import com.amias.texttoimagesearch.data.ImageEmbedding
import com.amias.texttoimagesearch.data.ImageEmbeddingDatabase
import com.amias.texttoimagesearch.data.ImageEmbeddingRepository
import com.amias.texttoimagesearch.preProcess
import com.amias.texttoimagesearch.utils.normalizeL2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.Collections

/**
 * The `ORTImageViewModel` class is an Android ViewModel responsible for managing the image
 * embedding generation and storage process using ONNX Runtime (ORT). It interacts with the
 * device's MediaStore to retrieve images, generates embeddings using a pre-trained ONNX model,
 * and stores these embeddings along with image metadata in a local database.
 *
 * This ViewModel is designed to:
 * - Load and manage an ONNX model for image embedding generation.
 * - Query the device's MediaStore for images.
 * - Generate embeddings for images that haven't been processed yet.
 * - Store generated embeddings in a local database.
 * - Maintain lists of image IDs (`idxList`) and their corresponding embeddings (`embeddingsList`).
 * - Provide a progress indicator (`progress`) to track the embedding generation process.
 *
 * @param application The application instance.
 */
class ORTImageViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var repository: ImageEmbeddingRepository
    var idxList: ArrayList<Long> = arrayListOf()
    var embeddingsList: ArrayList<FloatArray> = arrayListOf()
    var progress: MutableLiveData<Double> = MutableLiveData(0.0)
    var progressData: MutableLiveData<ProgressData> = MutableLiveData(ProgressData(0, 0))

    /**
     * Initializes the ImageEmbeddingRepository by retrieving the ImageEmbeddingDao from the ImageEmbeddingDatabase.
     *
     * This block is executed when the ImageEmbeddingViewModel is initialized.
     */
    init {
        val imageEmbeddingDao = ImageEmbeddingDatabase.getDatabase(application).imageEmbeddingDao()
        repository = ImageEmbeddingRepository(imageEmbeddingDao)
    }

    /**
     * Generates an index of image embeddings from the device's media store.
     *
     * This function iterates through all images in the device's external media storage,
     * calculates an embedding for each image using an ONNX model, and stores the embedding
     * along with the image's ID and date in a repository. It also maintains two lists:
     * `idxList` which contains the IDs of the indexed images, and `embeddingsList` which
     * contains the corresponding embeddings.
     *
     * The function performs the following steps:
     * 1. **Loads the ONNX model:** Loads a pre-trained ONNX model from the raw resources.
     * 2. **Initializes progress:** Sets the initial progress to 0.0.
     * 3. **Queries the MediaStore:** Queries the MediaStore for all images, retrieving their ID,
     *    modification date, and bucket name.
     * 4. **Iterates through images:** Iterates through the cursor returned by the MediaStore query.
     * 5. **Excludes Screenshots:** Skips images that are located in the "Screenshots" bucket.
     * 6. **Checks for existing record:** Checks if an embedding record already exists in the repository for the current image ID.
     *    - **If record exists:** Adds the image ID to `idxList` and the existing embedding to `embeddingsList`.
     *    - **If record does not exist:**
     *      a. **Reads image bytes:** Reads the image bytes from the MediaStore using its URI.
     *      b. **Decodes bitmap:** Decodes the image bytes into a Bitmap.
     *      c. **Preprocesses bitmap:** Preprocesses the bitmap by centering and cropping it to 224x224 and normalizes it
     *      d. **Creates input tensor:** Creates an ONNX input tensor from the preprocessed bitmap.
     */
    fun generateIndex() {
        val modelID = R.raw.visual_quant
        val resources = getApplication<Application>().resources
        val model = resources.openRawResource(modelID).readBytes()
        val session = ortEnv.createSession(model)

        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                progress.value = 0.0 // Set initial progress on main thread
                progressData.value = ProgressData(0, 0)
            }
            val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                )

            val selectionForPre10 =
                "${MediaStore.Images.Media.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?, ?)" +
                    " AND ${MediaStore.Images.Media.DATE_MODIFIED} > ?" + // Exclude temporary files
                    "AND ${MediaStore.Images.Media.SIZE} > ?" // Include files greater than 10KB

            val selectionPriorTo10 =
                "${MediaStore.Images.Media.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?, ?)" +
                    " AND ${MediaStore.Images.Media.DATE_MODIFIED} > ?" + // Exclude temporary files
                    "AND (${MediaStore.Images.Media.IS_TRASHED} IS NULL OR ${MediaStore.Images.Media.IS_TRASHED} = 0)" +
                    "AND ${MediaStore.Images.Media.SIZE} > ?" + // Include files greater than 10KB
                    " AND ${MediaStore.Images.Media.IS_PENDING} = ?" + // Exclude pending files
                    " AND ${MediaStore.Images.Media.DATE_EXPIRES} IS NULL" // Exclude files with expiry dates

            val selection =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    selectionPriorTo10
                } else {
                    selectionForPre10
                }

            val selectionArgsForPre10 =
                arrayOf(
                    "image/jpeg", // JPEG
                    "image/jpg", // JPEG
                    "image/png", // PNG
                    "image/gif", // GIF
                    "image/bmp", // BMP
                    "image/webp", // WebP
                    "image/heif", // HEIF
                    "image/heic", // HEIC (variant of HEIF)
                    "0", // Exclude files with very old modification dates (temporary files)
                    "10240", // File size > 10KB (size in bytes)
                )

            val selectionArgsPriorTo10 =
                arrayOf(
                    "image/jpeg", // JPEG
                    "image/jpg", // JPEG
                    "image/png", // PNG
                    "image/gif", // GIF
                    "image/bmp", // BMP
                    "image/webp", // WebP
                    "image/heif", // HEIF
                    "image/heic", // HEIC (variant of HEIF)
                    "0", // Exclude files with very old modification dates (temporary files)
                    "10240", // File size > 10KB (size in bytes)
                    "0", // IS_PENDING = 0 (Not pending)
                )

            val selectionArgs =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    selectionArgsPriorTo10
                } else {
                    selectionArgsForPre10
                }

            val sortOrder = "${MediaStore.Images.Media._ID} ASC"
            val contentResolver: ContentResolver = getApplication<Application>().contentResolver
            val cursor: Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val totalImages = cursor?.count ?: 0
            cursor?.use {
                val seenFiles = mutableSetOf<String>() // Set to track unique files

                val idColumn: Int = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn: Int =
                    it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
//                val bucketColumn: Int = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id: Long = it.getLong(idColumn)
                    val date: Long = it.getLong(dateColumn)

                    val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val dateModified = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
                    // Create a unique identifier (e.g., based on DISPLAY_NAME + SIZE + DATE_MODIFIED)
                    val uniqueKey = "$displayName-$size-$dateModified"

                    if (!seenFiles.contains(uniqueKey)) {
                        seenFiles.add(uniqueKey) // Track the file as seen
                    } else {
                        continue
                    }

//                    val bucket: String = it.getString(bucketColumn)
                    // Don't add screenshots to image index
//                    if (bucket == "Screenshots") continue

                    val record = repository.getRecord(id) as ImageEmbedding?
                    if (record != null) {
                        if (idxList.contains(record.id)) continue
                        idxList.add(record.id)
                        embeddingsList.add(record.embedding)
                    } else {
                        if (idxList.contains(id)) continue
                        try {
                            val imageUri: Uri = Uri.withAppendedPath(uri, id.toString())

                            val inputStream = contentResolver.openInputStream(imageUri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()

                            // Can fail to create the image decoder if its not implemented for the image type
                            val bitmap: Bitmap? =
                                BitmapFactory.decodeByteArray(bytes, 0, bytes?.size ?: 0)
                            bitmap?.let {
                                val rawBitmap = centerCrop(bitmap, 224)
                                val inputShape = longArrayOf(1, 3, 224, 224)
                                val inputName = "pixel_values"
                                val imgData =
                                    withContext(Dispatchers.Default) {
                                        // execute in the background
                                        preProcess(rawBitmap)
                                    }
                                val inputTensor =
                                    OnnxTensor.createTensor(ortEnv, imgData, inputShape)

                                inputTensor.use {
                                    val output =
                                        session?.run(
                                            Collections.singletonMap(
                                                inputName,
                                                inputTensor,
                                            ),
                                        )
                                    output.use {
                                        @Suppress("UNCHECKED_CAST")
                                        var rawOutput =
                                            ((output?.get(0)?.value) as Array<FloatArray>)[0]
                                        rawOutput =
                                            withContext(Dispatchers.Default) {
                                                // execute in the background
                                                normalizeL2(rawOutput)
                                            }
                                        repository.addImageEmbedding(
                                            ImageEmbedding(
                                                id,
                                                date,
                                                rawOutput,
                                            ),
                                        )
                                        idxList.add(id)
                                        embeddingsList.add(rawOutput)
                                    }
                                }
                            }
                        } catch (exception: FileNotFoundException) {
//                            removeNonExistentFileFromMediaStore(contentResolver, Uri.withAppendedPath(uri, id.toString()))
                            Log.d("ORTImageViewModel", "Error loading image: $exception \n ${Uri.withAppendedPath(uri, id.toString())}")
                        } catch (exception: SecurityException) {
                            Log.d("ORTImageViewModel", "Error Opening image: $exception${Uri.withAppendedPath(uri, id.toString())}")
                        } catch (exception: Exception) {
                            Log.d("ORTImageViewModel", "Error: $exception${Uri.withAppendedPath(uri, id.toString())}")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        // Record created/loaded, update progress
                        progress.value = it.position.toDouble() / totalImages.toDouble()
                        progressData.value = ProgressData(it.position, totalImages)
                    }
                }
            }
            cursor?.close()
            session.close()

            withContext(Dispatchers.IO) {
                idxList.distinct()
                embeddingsList.distinct()
            }

            withContext(Dispatchers.Main) {
                progress.value = 1.0
                progressData.value = ProgressData(totalImages, totalImages)
            }
        }
    }
}

fun removeNonExistentFileFromMediaStore(
    contentResolver: ContentResolver,
    uri: Uri,
) {
    try {
        // Get the ID from the URI
        val id = ContentUris.parseId(uri)

        // Attempt to delete the reference
        val deletedRows =
            contentResolver.delete(
                uri,
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(id.toString()),
            )

        if (deletedRows > 0) {
            Log.d("MediaStore", "Removed non-existent file reference: $uri")
        }
    } catch (e: Exception) {
        Log.e("MediaStore", "Failed to remove reference: $uri", e)
    }
}

data class ProgressData(
    val indexed: Int,
    val total: Int,
)
