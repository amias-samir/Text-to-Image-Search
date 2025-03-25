/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.data

/**
 * Repository class for managing [ImageEmbedding] data.
 *
 * This class provides a clean API for interacting with the [ImageEmbeddingDao]
 * and performing database operations related to image embeddings. It encapsulates
 * the data access logic, abstracting away the underlying implementation details
 * from the higher layers of the application.
 *
 * @property imageEmbeddingDao The [ImageEmbeddingDao] instance used for database operations.
 */
class ImageEmbeddingRepository(private val imageEmbeddingDao: ImageEmbeddingDao) {
    /**
     * Adds an image embedding to the data store.
     *
     * This function persists the provided [ImageEmbedding] object to the underlying data store
     * using the [ImageEmbeddingDao]. It allows for the storage and retrieval of image embedding
     * data.
     *
     * @param imageEmbedding The [ImageEmbedding] object to be added to the data store.
     *                       Must not be null. It should contain the embedding vector and any
     *                       associated metadata.
     * @throws Exception if there is an issue interacting with the data store. The specific
     *          exception depends on the implementation of the [ImageEmbeddingDao].
     * @see ImageEmbedding
     * @see ImageEmbeddingDao
     */
    suspend fun addImageEmbedding(imageEmbedding: ImageEmbedding) {
        imageEmbeddingDao.addImageEmbedding(imageEmbedding)
    }

    /**
 * This function retrieves an `ImageEmbedding` record from the database based on the provided ID.
 *
 * @param id The ID of the `ImageEmbedding` record to retrieve.
 *
 * @return The `ImageEmbedding` record associated with the given ID, or `null` if no record is found.
 *
 * @throws SQLException If an error occurs while accessing the database.
 */
suspend fun getRecord(id: Long): ImageEmbedding {
    return imageEmbeddingDao.getRecord(id)
}
}
