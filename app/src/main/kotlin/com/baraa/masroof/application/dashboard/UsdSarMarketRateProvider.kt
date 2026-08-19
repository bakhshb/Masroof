package com.baraa.masroof.application.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

fun interface UsdSarMarketRateProvider {
    suspend fun rateFor(date: LocalDate): BigDecimal?
}

/**
 * Fetches USD→SAR from Frankfurter v2 (no API key). Caches per date in memory.
 */
class FrankfurterUsdSarRateProvider(
    private val httpClient: OkHttpClient,
    private val apiBaseUrl: String = "https://api.frankfurter.dev",
) : UsdSarMarketRateProvider {
    private val cache = ConcurrentHashMap<LocalDate, BigDecimal>()

    override suspend fun rateFor(date: LocalDate): BigDecimal? {
        cache[date]?.let { return it }
        val fetched = fetchRate(date) ?: return null
        cache[date] = fetched
        return fetched
    }

    private suspend fun fetchRate(date: LocalDate): BigDecimal? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$apiBaseUrl/v2/rate/USD/SAR?date=$date")
            .get()
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                if (!json.has("rate")) return@runCatching null
                val sar = json.getDouble("rate")
                BigDecimal.valueOf(sar)
            }
        }.getOrNull()
    }
}
