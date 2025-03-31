/**
 * Copyright 2023 Viacheslav Barkov
 * Copyright 2021 Microsoft Corporation.
 *
 * Parts of the following code are a derivative work of the code from the ONNX Runtime project,
 * which is licensed MIT.
 */

package com.amias.texttoimagesearch

import android.graphics.Bitmap
import androidx.core.graphics.scale
import java.nio.FloatBuffer

const val DIM_BATCH_SIZE = 1
const val DIM_PIXEL_SIZE = 3
const val IMAGE_SIZE_X = 224
const val IMAGE_SIZE_Y = 224

/**
 * Preprocesses a Bitmap image for use with a machine learning model.
 *
 * This function takes a Bitmap image as input and transforms it into a FloatBuffer
 * suitable for inference. The preprocessing steps include:
 *
 * 1. **Pixel Extraction:** Extracts the pixel data from the Bitmap.
 * 2. **Normalization:** Normalizes the red, green, and blue color channels of each pixel
 *    to a range centered around 0 with unit variance. This involves:
 *    - Converting the pixel values from the 0-255 range to 0-1 by dividing by 255.
 *    - Subtracting a channel-specific mean (0.48145467 for red, 0.4578275 for green,
 *      0.40821072 for blue).
 *    - Dividing by a channel-specific standard deviation (0.26862955 for red, 0.2613026
 *      for green, 0.2757771 for blue).
 * 3. **Channel Interleaving:** Interleaves the normalized red, green, and blue channel data
 *    into the FloatBuffer.
 * 4. **Buffer Rewinding:** Rewinds the FloatBuffer to the beginning so that it's ready to be
 *    read from the start.
 *
 * The normalization values (means and standard deviations) are typically determined
 * during the training of the machine learning model and are crucial for consistent performance.
 *
 * @param bitmap The input Bitmap image. It is expected to have the dimensions `IMAGE_SIZE_X` x `IMAGE_SIZE_Y`.
 * @return A FloatBuffer containing the preprocessed image data, ready for inference.
 *
 * @throws IllegalArgumentException if the bitmap dimensions are not equal to IMAGE_SIZE_X x IMAGE_SIZE_Y.
 */
fun preProcess(bitmap: Bitmap): FloatBuffer {
    val imgData =
        FloatBuffer.allocate(
            DIM_BATCH_SIZE * DIM_PIXEL_SIZE * IMAGE_SIZE_X * IMAGE_SIZE_Y,
        )
    imgData.rewind()
    val stride = IMAGE_SIZE_X * IMAGE_SIZE_Y
    val bmpData = IntArray(stride)
    bitmap.getPixels(bmpData, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (i in 0 until IMAGE_SIZE_X) {
        for (j in 0 until IMAGE_SIZE_Y) {
            val idx = IMAGE_SIZE_Y * i + j
            val pixelValue = bmpData[idx]
            imgData.put(idx, (((pixelValue shr 16 and 0xFF) / 255f - 0.48145467f) / 0.26862955f))
            imgData.put(
                idx + stride,
                (((pixelValue shr 8 and 0xFF) / 255f - 0.4578275f) / 0.2613026f),
            )
            imgData.put(
                idx + stride * 2,
                (((pixelValue and 0xFF) / 255f - 0.40821072f) / 0.2757771f),
            )
        }
    }

    imgData.rewind()
    return imgData
}

/**
 * Crops a Bitmap to a square, centered region, and then scales it to the specified size.
 *
 * This function takes a Bitmap and an integer representing the desired square size.
 * It first determines the largest square that can be cropped from the center of the input Bitmap.
 * Then, it crops the Bitmap to this square region and scales it down (or up) to the desired `imageSize`.
 *
 * @param bitmap The input Bitmap to be cropped and scaled.
 * @param imageSize The desired width and height of the output square Bitmap.
 * @return A new Bitmap that is a square, centered crop of the input Bitmap, scaled to `imageSize` x `imageSize`.
 * @throws IllegalArgumentException if `imageSize` is not positive or if the bitmap width or height is less or equals to 0.
 * @throws IllegalArgumentException if `bitmap` is null.
 */
fun centerCrop(
    bitmap: Bitmap,
    imageSize: Int,
): Bitmap {
    val cropX: Int
    val cropY: Int
    val cropSize: Int
    if (bitmap.width >= bitmap.height) {
        cropX = bitmap.width / 2 - bitmap.height / 2
        cropY = 0
        cropSize = bitmap.height
    } else {
        cropX = 0
        cropY = bitmap.height / 2 - bitmap.width / 2
        cropSize = bitmap.width
    }
    var bitmapCropped =
        Bitmap.createBitmap(
            bitmap,
            cropX,
            cropY,
            cropSize,
            cropSize,
        )
    bitmapCropped = bitmapCropped.scale(imageSize, imageSize, false)
    return bitmapCropped
}
