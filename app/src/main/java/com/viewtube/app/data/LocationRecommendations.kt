package com.viewtube.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Optional coarse administrative-area context for recommendations.
 * Exact coordinates and street/locality names are never added to feed queries.
 */
object LocationRecommendations {
    @Volatile var area: String? = null
        private set

    suspend fun refresh(context: Context): String? = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext area

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext area
        val location = runCatching {
            manager.getProviders(true).asSequence()
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return@withContext area

        val resolved = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()?.let { address ->
                    // District + state/province + country: deliberately omit locality, street,
                    // sub-thoroughfare and postal code to avoid over-localising recommendations.
                    listOfNotNull(address.subAdminArea, address.adminArea, address.countryName)
                        .map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (resolved != null) area = resolved
        area
    }
}
