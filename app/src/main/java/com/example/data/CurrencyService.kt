package com.example.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

class CurrencyService(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "currency_secure_cache",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val TAG = "CurrencyService"
        private const val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hour cache
        private const val KEY_LAST_FETCH = "last_fetch_time"
        
        // Base offline conversion rates: MZN/MT values for 1 unit of external currency
        // e.g., 1 USD = 63.8 MZN, 1 EUR = 69.5 MZN, 1 ZAR = 3.5 MZN
        private val FALLBACK_RATES = mapOf(
            "MT" to 1.0,
            "MZN" to 1.0,
            "USD" to 63.8,
            "EUR" to 69.5,
            "ZAR" to 3.5
        )
    }

    /**
     * Gets exchange rate for converting currency to MZN/MT.
     * e.g., If currency is USD, returns 63.8 (meaning 1 USD = 63.8 MZN/MT).
     */
    suspend fun getRateToMzn(currency: String): Double = withContext(Dispatchers.IO) {
        val normalized = if (currency == "MT") "MZN" else currency
        if (normalized == "MZN") return@withContext 1.0

        // Attempt to fetch fresh rates if cache expired
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()
        if (now - lastFetch > CACHE_DURATION_MS) {
            tryFetchRates()
        }

        // Return from cache or fallback
        val cachedRate = prefs.getFloat("rate_$normalized", 0f)
        if (cachedRate > 0) {
            cachedRate.toDouble()
        } else {
            FALLBACK_RATES[normalized] ?: 1.0
        }
    }

    private suspend fun tryFetchRates() {
        try {
            // Using a reliable free open exchange rates API that returns rates with USD base
            val url = URL("https://open.er-api.com/v6/latest/USD")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val ratesObj = json.getJSONObject("rates")

                // We want MZN rates relative to USD, EUR, ZAR.
                // Since base is USD:
                // rate_USD_to_MZN = ratesObj.getDouble("MZN")
                // rate_EUR_to_MZN = rate_USD_to_MZN / ratesObj.getDouble("EUR")
                // rate_ZAR_to_MZN = rate_USD_to_MZN / ratesObj.getDouble("ZAR")
                
                val mznValueInUsd = ratesObj.optDouble("MZN", 63.8)
                val eurValueInUsd = ratesObj.optDouble("EUR", 0.92)
                val zarValueInUsd = ratesObj.optDouble("ZAR", 18.2)

                val usdToMzn = mznValueInUsd
                val eurToMzn = if (eurValueInUsd > 0) mznValueInUsd / eurValueInUsd else 69.5
                val zarToMzn = if (zarValueInUsd > 0) mznValueInUsd / zarValueInUsd else 3.5

                prefs.edit().apply {
                    putFloat("rate_USD", usdToMzn.toFloat())
                    putFloat("rate_EUR", eurToMzn.toFloat())
                    putFloat("rate_ZAR", zarToMzn.toFloat())
                    putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                    apply()
                }
                Log.d(TAG, "Rates updated: USD=$usdToMzn, EUR=$eurToMzn, ZAR=$zarToMzn")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live exchange rates, using offline defaults", e)
        }
    }

    /**
     * Converts an amount from one currency to another.
     * baseCurrency is MZN/MT.
     */
    suspend fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return amount
        
        // Convert from source to MZN first
        val rateToMzn = getRateToMzn(fromCurrency)
        val amountInMzn = amount * rateToMzn

        // Convert from MZN to target currency
        val targetRateToMzn = getRateToMzn(toCurrency)
        return amountInMzn / targetRateToMzn
    }
}
