package com.amias.texttoimagesearch.utils

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Represents a geographical location with latitude and longitude coordinates.
 *
 * @property latitude The latitude coordinate in degrees.
 * @property longitude The longitude coordinate in degrees.
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Tries to extract the geolocation from an image using its EXIF metadata.
 * If the image contains geolocation data, it returns a `GeoLocation` object
 * representing the latitude and longitude. Otherwise, returns null.
 *
 * @param context   The application context.
 * @param imageUri The URI of the image to extract geolocation from.
 *
 * @return A `GeoLocation` object if successful, otherwise null.
 */
fun getGeoLocationFromImage(
    context: Context,
    imageUri: Uri,
): GeoLocation? =
    try {
        context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
            // Opens the input stream of the image
            val exifInterface = ExifInterface(inputStream) // Creates an ExifInterface object to read image metadata

            // Retrieves the latitude data from the EXIF metadata
            val latitude = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val latitudeRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val longitude = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val longitudeRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
            Log.d("GeoLocation", "Latitude: $latitude, LatitudeRef: $latitudeRef, Longitude: $longitude, LongitudeRef: $longitudeRef")

            val latLong = exifInterface.latLong // Retrieves the latitude and longitude data from the EXIF metadata
            if (latLong != null) { // Checks if geolocation data exists
                GeoLocation(latLong[0], latLong[1])
            } else {
                null // Returns null if no geolocation data was found
            }
        }
    } catch (e: Exception) {
        e.printStackTrace() // Prints stack trace if an error occurs
        null // Returns null on error
    }

/**
 * **Given a geographical location, attempts to asynchronously retrieve the place name associated with it.**
 *
 * This function utilizes the operating system's geocoding service to convert the provided latitude and longitude
 * coordinates into a human-readable place name. The result is delivered asynchronously through the `onResult` callback.
 *
 * @param context     The application context.
 * @param geoLocation The GeoLocation object containing the latitude and longitude coordinates.
 * @param onResult    The callback function to receive the place name as a String, or null if the place name cannot be determined.
 *
 * **Note**: This function requires the `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` permission to access the device's location.
 *
 * **Example usage:**
 * ```kotlin
 * val geoLocation = GeoLocation(latitude = 48.8584, longitude = 2.2945)
 *
 * getPlaceName(applicationContext, geoLocation) { placeName ->
 *     // Handle the place name result
 *     if (placeName != null) {
 *         Log.d("Place Name", placeName)
 *     } else {
 *         Log.e("Place Name", "Could not find place name")
 *     }
 * }
 * ```
 */
suspend fun getPlaceName(
    context: Context,
    geoLocation: GeoLocation,
    onResult: (String?) -> Unit,
) = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val geocoder = Geocoder(context, Locale.getDefault())

        geocoder.getFromLocation(geoLocation.latitude, geoLocation.longitude, 1) { addresses ->
            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                val placeName =
                    listOfNotNull(
                        address.locality,
                        address.adminArea,
                        address.countryName,
                    ).joinToString(", ")
                onResult(placeName)
            } else {
                onResult(null)
            }
        }
    } else {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(geoLocation.latitude, geoLocation.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // You can customize this according to your needs:
                onResult("${address.locality ?: ""}, ${address.adminArea ?: ""}, ${address.countryName ?: ""}")
            } else {
                onResult(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(null)
        }
    }
}
