/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch

import kotlin.math.sqrt

/**
 * Calculates the dot product of two FloatArrays.
 *
 * The dot product of two arrays `a` and `b` of the same size is defined as the sum of the products of their corresponding elements:
 * `a[0] * b[0] + a[1] * b[1] + ... + a[n-1] * b[n-1]`.
 *
 * This function extends the `FloatArray` class with an infix function `dot` for convenient dot product calculation.
 *
 * @receiver The first FloatArray.
 * @param other The second FloatArray. Must have the same size as the receiver.
 * @return The dot product of the two arrays as a Float.
 * @throws IllegalArgumentException if the two arrays have different sizes.
 * @throws IndexOutOfBoundsException if an index `i` is out of the range of either arrays indices.
 *
 * @sample
 * ```
 * val array1 = floatArrayOf(1f, 2f, 3f)
 * val array2 = floatArrayOf(4f, 5f, 6f)
 * val result = array1 dot array2 // result will be 32f (1*4 + 2*5 + 3*6)
 * ```
 *
 * @sample
 * ```
 * val array3 = floatArrayOf(1f, 2f)
 * val array4 = floatArrayOf(4f, 5f, 6f)
 * try {
 *   val result = array3 dot array4 // throws IllegalArgumentException
 * } catch (e: IllegalArgumentException){
 *    println("Arrays should have the same size")
 * }
 * ```
 */
infix fun FloatArray.dot(other: FloatArray) =
    foldIndexed(0.0) { i, acc, cur -> acc + cur * other[i] }.toFloat()

/**
 * Normalizes a float array using the L2 norm (Euclidean norm).
 *
 * This function takes a float array as input and returns a new float array where each element
 * has been divided by the L2 norm of the original array. The L2 norm is the square root of the
 * sum of the squares of the elements. This process scales the vector represented by the array
 * to have a magnitude of 1, while preserving its direction.
 *
 * If the L2 norm is zero (i.e., all elements in the input array are zero), this function will
 * return a new array filled with NaNs (Not a Number). This behavior prevents division by zero.
 *
 * @param inputArray The float array to normalize.
 * @return A new float array representing the normalized vector.
 * @throws IllegalArgumentException if the input array is empty.
 *
 * @sample
 * ```kotlin
 * val arr = floatArrayOf(1f, 2f, 3f)
 * val normalizedArr = normalizeL2(arr)
 * println(normalizedArr.contentToString()) // Output will be approximately: [0.26726124, 0.5345225, 0.8017837]
 *
 * val zeroArr = floatArrayOf(0f, 0f, 0f)
 * val normalizedZeroArr = normalizeL2(zeroArr)
 * println(normalizedZeroArr.contentToString()) // Output will be: [NaN, NaN, NaN]
 *
 * val emptyArr = floatArrayOf()
 * try {
 *      normalizeL2(emptyArr)
 * } catch (e: IllegalArgumentException) {
 *      println(e.message) // Output will be: Input array cannot be empty.
 * }
 * ```
 */
fun normalizeL2(inputArray: FloatArray): FloatArray {
    var norm = 0.0f
    for (i in inputArray.indices) {
        norm += inputArray[i] * inputArray[i]
    }
    norm = sqrt(norm)
    return inputArray.map { it / norm }.toFloatArray()
}

