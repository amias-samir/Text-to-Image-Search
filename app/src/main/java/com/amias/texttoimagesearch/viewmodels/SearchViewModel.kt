/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.amias.texttoimagesearch.utils.dot

/**
 * ViewModel class responsible for managing the search functionality and results.
 *
 * This ViewModel handles the search process, including calculating cosine distances
 * between embeddings and sorting the results based on similarity. It also manages
 * the state of search results and a flag indicating if the search is initiated from an
 * image-to-image process.
 *
 * @property searchResults A nullable list of Long representing the image indices sorted by
 *                         cosine similarity to the search query. Null if no search has been
 *                         performed or if the search returned no results.
 * @property fromImg2ImgFlag A boolean flag indicating whether the search operation is triggered
 *                            from an image-to-image context (true) or from a text-based search
 *                            (false). Defaults to false.
 * @constructor Creates a SearchViewModel with the given application context.
 * @param application The application context.
 */
class SearchViewModel(
    application: Application,
) : AndroidViewModel(application) {
    var searchResults: List<Long>? = null
    var fromImg2ImgFlag: Boolean = false

    /**
     * Sorts a list of image indices based on their cosine distance to a search embedding.
     *
     * This function calculates the cosine similarity between a given `searchEmbedding` and a list of
     * `imageEmbeddingsList`. It then associates each calculated similarity score with the corresponding
     * image index from `imageIdxList`. Finally, it sorts the image indices in descending order of
     * their cosine similarity scores (higher score means more similar) and updates the `searchResults`
     * with the sorted list of image indices.
     *
     * @param searchEmbedding The embedding vector representing the search query.
     *                        This is a FloatArray.
     * @param imageEmbeddingsList A list of embedding vectors, each representing an image.
     *                            Each element in the list is a FloatArray.
     * @param imageIdxList A list of image indices, corresponding to the images in `imageEmbeddingsList`.
     *                     Each element in the list is a Long. The index at position `i` in this list
     *                     corresponds to the image embedding at position `i` in `imageEmbeddingsList`.
     * @throws IllegalArgumentException if `searchEmbedding`, `imageEmbeddingsList` or `imageIdxList` are empty.
     * @throws IllegalArgumentException if `imageEmbeddingsList` and `imageIdxList` do not have the same size.
     * @throws IllegalArgumentException if any element of `imageEmbeddingsList` has different size than `searchEmbedding`.
     *
     * @updates `searchResults` - a list of Long representing the image indices sorted based on cosine similarity.
     *                  The most similar image index is at the beginning of the list.
     *
     * @see FloatArray.dot for the calculation of cosine similarity.
     */
    fun sortByCosineDistance(
        searchEmbedding: FloatArray,
        imageEmbeddingsList: List<FloatArray>,
        imageIdxList: List<Long>,
    ) {
        val distances = LinkedHashMap<Long, Float>()
        Log.e("SearchViewModel", "sortByCosineDistance imageEmbeddingsList Size: ${imageEmbeddingsList.size}")
        Log.e("SearchViewModel", "sortByCosineDistance imageIdxList Size: ${imageIdxList.size}")

        for (i in imageEmbeddingsList.indices) {
            try {
                val dist = searchEmbedding.dot(imageEmbeddingsList[i])
                distances[imageIdxList[i]] = dist
            } catch (exception: ArrayIndexOutOfBoundsException) {
                Log.e("SearchViewModel", "sortByCosineDistance: ${exception.message}")
            }
        }
        searchResults =
            distances
                .toList()
                .sortedBy { (k, v) -> v }
                .map { (k, v) -> k }
                .reversed()
    }
}
