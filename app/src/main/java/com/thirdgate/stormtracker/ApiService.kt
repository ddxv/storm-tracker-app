package com.thirdgate.stormtracker


import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.util.concurrent.TimeUnit

class ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS) // Set the connection timeout
        .readTimeout(
            10,
            TimeUnit.SECONDS
        )    // for data to be received after the connection is established
        .writeTimeout(
            10,
            TimeUnit.SECONDS
        )   // maximum time to wait for data to be sent after the connection is established
        .build()

    private val gson = Gson()
    private val myBaseUrl: String = "https://thirdgate.dev/api/storms"


    suspend fun getStormImage(dateStr: String, stormId: String): ByteArray {
        val url = "$myBaseUrl/$dateStr/$stormId/ucar/image"
        Log.i("ApiService", "Fetching image from url: $url")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                response.body?.bytes() ?: throw IOException("Response body is null")
            }
        }
    }

    suspend fun getStormMyImage(dateStr: String, stormId: String): ByteArray {
        val url = "$myBaseUrl/$dateStr/$stormId/ucar/myimage"
        Log.i("ApiService", "Fetching image from url: $url")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                response.body?.bytes() ?: throw IOException("Response body is null")
            }
        }
    }

    suspend fun getStormCompareImage(dateStr: String, stormId: String): ByteArray {
        val url = "$myBaseUrl/$dateStr/$stormId/compare"
        Log.i("ApiService", "Fetching image from url: $url")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                response.body?.bytes() ?: throw IOException("Response body is null")
            }
        }
    }

    suspend fun getStorms(): Map<String, List<Map<String, String>>> {
        val url = "$myBaseUrl/"
        Log.i("ApiService", "Calling url: $url")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val bodyString =
                    response.body?.string() ?: throw IOException("Response body is null")

                // Define the type for Gson parsing
                val type = object : TypeToken<Map<String, List<Map<String, String>>>>() {}.type

                // Parse the JSON response
                val myMap: Map<String, List<Map<String, String>>> = gson.fromJson(bodyString, type)

                myMap
            }
        }
    }

    suspend fun hasStormImage(dateStr: String, stormId: String): Boolean {
        return try {
            val imageBytes = getStormImage(dateStr, stormId)
            imageBytes.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }


}
