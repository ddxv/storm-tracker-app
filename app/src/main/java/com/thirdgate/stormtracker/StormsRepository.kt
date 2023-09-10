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