/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * ImageEmbeddingDatabase is a Room database class responsible for managing the storage and
 * retrieval of image embeddings. It defines the database schema, including the entity
 * {@link ImageEmbedding}, and provides a singleton instance for database access.
 *
 * <p>
 * This database is designed to store vectors (embeddings) representing images, likely for
 * use in image similarity or search functionalities.
 * </p>
 *
 * <p>
 *     <b>Entities:</b>
 *     <ul>
 *         <li>{@link ImageEmbedding}: Represents a single image embedding and its associated metadata.</li>
 *     </ul>
 * </p>
 *
 * <p>
 *     <b>Version:</b> 1
 * </p>
 * <p>
 *     <b>Export Schema:</b> false (Schema is not exported to avoid performance issues)
 * </p>
 *
 * <p>
 *     <b>Usage:</b>
 *     <ol>
 *         <li>Get an instance of the database using {@link #getDatabase(Context)}.</li>
 *         <li>Obtain the {@link ImageEmbeddingDao} using {@link #imageEmbeddingDao()}.</li>
 *         <li>Use the DAO to perform database operations like inserting, querying, and deleting image embeddings.</li>
 *     </ol>
 * </p>
 */
@Database(entities = [ImageEmbedding::class], version = 1, exportSchema = false)
abstract class ImageEmbeddingDatabase : RoomDatabase() {
    abstract fun imageEmbeddingDao(): ImageEmbeddingDao

    /**
     * Singleton instance of the ImageEmbeddingDatabase.
     *
     * This ensures that only one instance of the database is created
     * across the application lifecycle, preventing issues like resource
     * contention and data corruption.
     *
     * The `@Volatile` annotation ensures that changes made to `INSTANCE`
     * by one thread are immediately visible to other threads.
     */
    companion object {
        @Volatile
        private var INSTANCE: ImageEmbeddingDatabase? = null

        fun getDatabase(context: Context): ImageEmbeddingDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) return tempInstance
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ImageEmbeddingDatabase::class.java,
                    "image_embedding_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}