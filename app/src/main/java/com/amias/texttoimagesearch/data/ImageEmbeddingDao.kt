/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) interface for interacting with the ImageEmbedding entity in the database.
 *
 * This interface provides methods for adding, retrieving and potentially other operations related to
 * storing and accessing image embedding data. Each method uses Room annotations to define database
 * interactions without requiring manual SQL queries.
 *
 * @see ImageEmbedding
 */
@Dao
interface ImageEmbeddingDao {
    /**
     * Adds an ImageEmbedding to the database, replacing any existing entry with the same primary key.
     *
     * @param imageEmbedding The ImageEmbedding object to be added or updated.
     *                       The primary key of this object will be used to determine if a replacement is needed.
     * @see ImageEmbedding
     * @see OnConflictStrategy.REPLACE
     *
     * This function is a suspend function, meaning it should be called within a coroutine scope.
     * It utilizes the Room library's `@Insert` annotation with the `onConflict` strategy set to `OnConflictStrategy.REPLACE`.
     *
     * **Conflict Resolution:**
     * If an `ImageEmbedding` with the same primary key (defined in the `ImageEmbedding` entity) already exists in the database,
     * the existing row will be replaced with the new `imageEmbedding` provided. This ensures that there are no duplicate entries
     * based on the primary key.
     *
     * **Example Usage:**
     * ```kotlin
     *  // Assuming you have an instance of your DAO called 'imageEmbeddingDao'
     *  val newEmbedding = ImageEmbedding(imageId = "someImageId", embedding = floatArrayOf(0.1f, 0.2f, 0.3f))
     *  lifecycleScope.launch {
     *    imageEmbeddingDao.addImageEmbedding(newEmbedding)
     *  }
     * ```
     *
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImageEmbedding(imageEmbedding: ImageEmbedding)

    /**
     * Data class representing an image embedding record in the database.
     *
     * @property id The unique identifier for the embedding.
     * @property embedding The embedding vector as a string.
     * @property imagePath The file path where image was stored.
     */
    @Query("SELECT * FROM image_embeddings WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: Long): ImageEmbedding
}