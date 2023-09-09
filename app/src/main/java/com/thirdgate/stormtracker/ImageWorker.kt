package com.thirdgate.stormtracker


import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.core.content.FileProvider.getUriForFile
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.currentState
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import okio.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class ImageWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    companion object {

        private val uniqueWorkName = ImageWorker::class.java.simpleName

        fun enqueue(context: Context, glanceId: GlanceId, force: Boolean = false) {
            val manager = WorkManager.getInstance(context)
            val requestBuilder = OneTimeWorkRequestBuilder<ImageWorker>().apply {
                addTag(glanceId.toString())
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                setInputData(
                    Data.Builder()
                        .putBoolean("force", force)
                        .build(),
                )
            }
            val workPolicy = if (force) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }

            manager.enqueueUniqueWork(
                uniqueWorkName,
                workPolicy,
                requestBuilder.build(),
            )

            // Temporary workaround to avoid WM provider to disable itself and trigger an
            // app widget update
            manager.enqueueUniqueWork(
                "$uniqueWorkName-workaround",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ImageWorker>().apply {
                    setInitialDelay(365, TimeUnit.DAYS)
                }.build(),
            )
        }

        /**
         * Cancel any ongoing worker
         */
        fun cancel(context: Context, glanceId: GlanceId) {
            WorkManager.getInstance(context).cancelAllWorkByTag(glanceId.toString())
        }
    }

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(MyWidget::class.java)

        //StormsRepository.storeStormImages("A", context)
        Log.i("WidgetWorker", "getting imageList!")

        val r: Result = Result.failure()

        // Update state with new data
        glanceIds.forEach { glanceId ->
            try {
                Log.i(
                    "MyWidget",
                    "Looptime: Outside StateDefinition: this.glanceId: $glanceId"
                )
                updateAppWidgetState(
                    context = context,
                    definition = GlanceButtonWidgetStateDefinition(),
                    glanceId = glanceId,
                    updateState = { thisWidgdetInfo ->
                        WidgetInfo(
                            stormData = StormData.Loading,
                            currentIndex = thisWidgdetInfo.currentIndex,
                            numImagesWI = thisWidgdetInfo.numImagesWI,
                            widgetGlanceId = thisWidgdetInfo.widgetGlanceId,
                            baseUri = null,
                        )
                    }
                )
                MyWidget().update(context, glanceId)
                updateAppWidgetState(
                    context = context,
                    glanceId = glanceId,
                    definition = GlanceButtonWidgetStateDefinition()
                ) { thisWidgetInfo ->
                    Log.i(
                        "MyWidget",
                        "LoopWidgets: glanceId: $glanceId, Fetch articles "
                    )

                    val baseUri = StormsRepository.storeStormImages(stormType = "HIIII", context)
                    val currentIndex = thisWidgetInfo.currentIndex
                    val imageListSize = thisWidgetInfo.numImagesWI
                    var nextIndex: Int = currentIndex + 1
                    nextIndex %= imageListSize

                    WidgetInfo(
                        stormData = thisWidgetInfo.stormData,
                        currentIndex = nextIndex,
                        numImagesWI = thisWidgetInfo.numImagesWI,
                        widgetGlanceId = thisWidgetInfo.widgetGlanceId,
                        baseUri = baseUri,
                    )
                }
                MyWidget().update(context, glanceId)
                val r = Result.success()
            } catch (e: Exception) {
                Log.i(
                    "MyWidget",
                    "Looptime: Outside StateDefinition: this.glanceId: $glanceId"
                )
                updateAppWidgetState(
                    context = context,
                    definition = GlanceButtonWidgetStateDefinition(),
                    glanceId = glanceId,
                    updateState = { thisWidgetInfo ->
                        WidgetInfo(
                            stormData = StormData.Unavailable(e.message.orEmpty()),
                            currentIndex = thisWidgetInfo.currentIndex,
                            numImagesWI = thisWidgetInfo.numImagesWI,
                            widgetGlanceId = thisWidgetInfo.widgetGlanceId,
                            baseUri = null,
                        )
                    }
                )
                MyWidget().update(context, glanceId)
                if (runAttemptCount < 10) {
                    // Exponential backoff strategy will avoid the request to repeat
                    // too fast in case of failures.
                    val r = Result.retry()
                } else {
                    val r = Result.failure()
                }
            }
        }
        return r
    }


//    override suspend fun doWork(): Result {
//        return try {
//            getNextImageAndUpdateWidget()
//            Result.success()
//        } catch (e: Exception) {
//            Log.e(uniqueWorkName, "Error while loading image", e)
//            if (runAttemptCount < 10) {
//                // Exponential backoff strategy will avoid the request to repeat
//                // too fast in case of failures.
//                Result.retry()
//            } else {
//                Result.failure()
//            }
//        }
//    }


//    suspend fun getNextImageAndUpdateWidget() {
//        val apiService = ApiService()
//        Log.i("WidgetWorker", "getting imageList!")
//        val imageList = apiService.getStormCompareImageList()
//
//        Log.i("WidgetWorker", "got imageList!")
//
//        // Now, launch the suspend function using something like the LaunchedEffect
//        updateToNextImageWidget(imageList)
//
//    }
//
//
//    suspend fun updateToNextImageWidget(
//        imageList: List<ByteArray>
//    ) {
//        val imageListSize = imageList.size
//
//        if (imageList.isEmpty()) {
//            throw IOException("No storm compare images available")
//        }
//
//        val manager = GlanceAppWidgetManager(context)
//        val glanceIds = manager.getGlanceIds(MyWidget::class.java)
//        glanceIds.forEach { glanceId ->
//            updateAppWidgetState(context, glanceId) { prefs ->
//
//                // Fetch the current index from the state
//                val currentIndex: Int = prefs[MyWidget.widgetCurrentIndex]?.toInt() ?: 0
//                var nextIndex: Int = currentIndex + 1
//                nextIndex %= imageListSize
//
//                Log.i("MyWidget", "Index: $nextIndex")
//                // Fetch the image based on the current index
//                val selectedImage = imageList[nextIndex]
//
//                // Convert the ByteArray to a file
//                val imageFile =
//                    File(context.cacheDir, "nextStormImage_${nextIndex}.jpg").apply {
//                        writeBytes(selectedImage)
//                    }
//
//                // Use the FileProvider to create a content URI
//                val uri = getUriForFile(
//                    context,
//                    "${applicationContext.packageName}.provider",
//                    imageFile,
//                ).toString()
//
//                prefs[MyWidget.sourceUrlKey] = uri
//                prefs[MyWidget.widgetCurrentIndex] = nextIndex.toString()
//
//                Log.i("MyWidget", "Index: $nextIndex Saved uri=$uri")
//            }
//        }
//        MyWidget().updateAll(context)
//    }

}