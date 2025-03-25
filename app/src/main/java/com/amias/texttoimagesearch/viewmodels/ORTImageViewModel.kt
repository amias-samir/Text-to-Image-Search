/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.viewmodels

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.*
import com.amias.texttoimagesearch.R
import com.amias.texttoimagesearch.centerCrop
import com.amias.texttoimagesearch.data.ImageEmbedding
import com.amias.texttoimagesearch.data.ImageEmbeddingDatabase
import com.amias.texttoimagesearch.data.ImageEmbeddingRepository
import com.amias.texttoimagesearch.normalizeL2
import com.amias.texttoimagesearch.preProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

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
class ORTImageViewModel(application: Application) : AndroidViewModel(application) {
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var repository: ImageEmbeddingRepository
    var idxList: ArrayList<Long> = arrayListOf()
    var embeddingsList: ArrayList<FloatArray> = arrayListOf()
    var progress: MutableLiveData<Double> = MutableLiveData(0.0)

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

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                progress.value = 0.0 // Set initial progress on main thread
            }
            val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            )
            val sortOrder = "${MediaStore.Images.Media._ID} ASC"
            val contentResolver: ContentResolver = getApplication<Application>().contentResolver
            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, sortOrder)
            val totalImages = cursor?.count ?: 0
            cursor?.use {
                val idColumn: Int = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn: Int =
                    it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val bucketColumn: Int =
                    it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id: Long = it.getLong(idColumn)
                    val date: Long = it.getLong(dateColumn)
                    val bucket: String = it.getString(bucketColumn)
                    // Don't add screenshots to image index
                    if (bucket == "Screenshots") continue
                    val record = repository.getRecord(id) as ImageEmbedding?
                    if (record != null) {
                        idxList.add(record.id)
                        embeddingsList.add(record.embedding)
                    } else {
                        try{
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
                                val imgData = withContext(Dispatchers.Default) {
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
                                                inputTensor
                                            )
                                        )
                                    output.use {
                                        @Suppress("UNCHECKED_CAST") var rawOutput =
                                            ((output?.get(0)?.value) as Array<FloatArray>)[0]
                                        rawOutput = withContext(Dispatchers.Default) {
                                            // execute in the background
                                            normalizeL2(rawOutput)
                                        }
                                        repository.addImageEmbedding(
                                            ImageEmbedding(
                                                id, date, rawOutput
                                            )
                                        )
                                        idxList.add(id)
                                        embeddingsList.add(rawOutput)

                                    }
                                }
                            }

                        }catch(exception: Exception){
                            Log.d("ORTImageViewModel", "Error loading image: $exception \n ${Uri.withAppendedPath(uri, id.toString())}")
                        }
                        }


                        withContext(Dispatchers.Main) {
                            // Record created/loaded, update progress
                            progress.value = it.position.toDouble() / totalImages.toDouble()
                        }

                }
            }
            cursor?.close()
            session.close()

            withContext(Dispatchers.Main) {
                progress.value = 1.0
            }

        }
    }
}



