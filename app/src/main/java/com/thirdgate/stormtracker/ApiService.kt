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
        Log.i("ApiService", "getStormImage fetching image from url: $url")
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
        Log.i("ApiService", "getStormMyImage fetching image from url: $url")
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
        Log.i("ApiService", "getStormCompareImage fetching image from url: $url")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                response.body?.bytes() ?: throw IOException("Response body is null")
            }
        }
    }

    suspend fun getStormCompareImageList(): List<ByteArray> {
        Log.i("ApiService", "getStormCompareImageList start")
        val fetchedStorms = getStorms()
        val storms = fetchedStorms.storms

        val listCompareImages = mutableListOf<ByteArray>()
        Log.i("ApiService", "getStormCompareImageList found storms length: ${storms.size}")
        for (storm in storms) {
            // Launch a new coroutine for each storm
            val date = storm.date
            val stormId = storm.id
            Log.i("ApiService", "getStormCompareImageList looping stormId: $stormId")
            try {
                val imageBytes = getStormCompareImage(date, stormId)
                listCompareImages.add(imageBytes)
            } catch (e: Exception) {
                Log.i(
                    "ApiService",
                    "getStormCompareImageList looping stormId: $stormId Failed with error $e\") "
                )
            }
        }
        return listCompareImages
    }


    suspend fun getStorms(): ActiveStorms {
        // API returns {"storms": [{"id": "ALXX", "date": "2022-02-2"} ] }
        val url = "$myBaseUrl/"
        Log.i("ApiService", "getStorms calling url: $url")

        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }

        val bodyString = response.body?.string() ?: throw IOException("Response body is null")

        // Close the response after usage
        response.close()

        // Parse the JSON response directly into ActiveStorms
        return gson.fromJson(bodyString, ActiveStorms::class.java)
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
