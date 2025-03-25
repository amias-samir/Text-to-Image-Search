/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type


/**
 *  `Converters` is a utility object that provides type conversion functions
 *  for Room database to handle `FloatArray` types, which are not directly
 *  supported by Room.
 *
 *  It uses Gson library to serialize and deserialize `FloatArray` to and from
 *  `String` respectively.
 *
 *  These methods should be annotated with `@TypeConverter` and registered
 *  within the `RoomDatabase` class using the `@TypeConverters` annotation
 *  to make Room aware of them.
 */
object Converters {
    @TypeConverter
    fun fromString(value: String?): FloatArray {
        val listType: Type = object : TypeToken<FloatArray>(){}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        val gson = Gson()
        return gson.toJson(array)
    }
}