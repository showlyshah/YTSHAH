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

/** Optional coarse-location context. Only the resolved area name is used in search queries. */
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
                    listOfNotNull(address.locality, address.subAdminArea, address.adminArea, address.countryName)
                        .distinct().take(2).joinToString(", ")
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (resolved != null) area = resolved
        area
    }
}
