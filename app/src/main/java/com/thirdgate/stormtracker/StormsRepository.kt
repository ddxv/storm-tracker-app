package com.thirdgate.stormtracker

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.core.content.FileProvider.getUriForFile
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object StormsRepository {

    val Context.dataStore: DataStore<AppInfo> by dataStore(
        fileName = "app_stormdata",
        serializer = MySerializer
    )

    private val apiService = ApiService()

    // Create MutableState internally
    private var _stormDataImages = mutableStateOf<StormData>(StormData.Loading)

    // Expose read-only State
    val stormDataImages: State<StormData> = _stormDataImages

    private var fetchedStormImagesPages = mutableSetOf<Int>()


    suspend fun storeStormImages(stormType: String, context: Context): String {
        Log.i("StormsRepository", "Fetching stormType=$stormType")

        try {
            val compareStormBytes: List<ByteArray> =
                apiService.getStormCompareImageList()
            Log.i(
                "StormsRepository",
                "ApiService returned: compareStormBytes {${compareStormBytes}}"
            )

            val myNumImages = compareStormBytes.size

            //val baseUri = context.filesDir.toString()
            val baseUri = "content://com.thirdgate.stormtracker.provider/cache_files"

            var index = 0
            for (myImg in compareStormBytes) {

                val fileName = "compareModels_$index.jpg"
                //val file = File(context.filesDir, fileName)
                //val myFile = file.writeBytes(myImg)
//                Log.i(
//                    "StormsRepository",
//                    "Stored Image to cacheDir:${context.cacheDir} fileName=$fileName"
//                )
                val imageFile = File(context.cacheDir, fileName).apply {
                    writeBytes(myImg)
                }

                val uri = getUriForFile(
                    context,
                    "com.thirdgate.stormtracker.provider",
                    imageFile,
                )
//                context.grantUriPermission(
//                    "com.thirdgate.stormtracker",
//                    uri,
//                    Intent.FLAG_GRANT_READ_URI_PERMISSION
//                )


                // Find the current launcher everytime to ensure it has read permissions
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.resolveActivity(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.resolveActivity(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY,
                    )
                }
                val launcherName = resolveInfo?.activityInfo?.packageName
                if (launcherName != null) {
                    context.grantUriPermission(
                        launcherName,
                        uri,
                        FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                    )
                }

                Log.i(
                    "StormsRepository",
                    "Finished image $index uri=${uri.toString()} and baseUri=$baseUri and the fileName=$fileName"
                )

                index++
            }

            val myStormInfo = StormData.StormInfo(
                images = compareStormBytes,
                numImages = myNumImages,
                baseUri = baseUri
            )

            val myMap = mapOf("CompareImages" to myStormInfo)
            //val myStormData = StormData.Available(myMap)

            return baseUri
        } catch (e: Exception) {
            Log.e("StormsRepository", "Oops")
            return "null"
        }
    }


    /**
     * Custom serializer for ArticleData using Json.
     */
    object MySerializer : Serializer<AppInfo> {
        override val defaultValue = AppInfo(stormData = StormData.Loading, themeId = "default")


        override suspend fun readFrom(input: InputStream): AppInfo {
            return try {
                Json.decodeFromString(
                    AppInfo.serializer(),
                    input.readBytes().decodeToString()
                )
            } catch (exception: SerializationException) {
                throw CorruptionException("Could not read article data: ${exception.message}")
            }
        }

        override suspend fun writeTo(t: AppInfo, output: OutputStream) {
            output.use {
                it.write(
                    Json.encodeToString(AppInfo.serializer(), t).encodeToByteArray()
                )
            }
        }
    }

}