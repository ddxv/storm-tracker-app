package com.thirdgate.stormtracker


import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider.getUriForFile
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit


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

        Log.i("ImageWorker", "getting imageList!")
        val r: Result = Result.failure()

        // Update state with new data
        val (baseUri, numImagesWI) = storeStormImages(stormType = "HIIII", context)
        glanceIds.forEach { glanceId ->
            try {
                Log.i(
                    "ImageWorker",
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
                            rawPath = null,
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
                        "ImageWorker",
                        "LoopWidgets: glanceId: $glanceId, Fetch articles "
                    )

                    WidgetInfo(
                        stormData = thisWidgetInfo.stormData,
                        currentIndex = thisWidgetInfo.currentIndex,
                        numImagesWI = numImagesWI,
                        widgetGlanceId = thisWidgetInfo.widgetGlanceId,
                        baseUri = baseUri,
                        rawPath = "${context.cacheDir}/compareModels_${thisWidgetInfo.currentIndex}.jpg",
                    )

                    // Can this much easier replace above???
                    //thisWidgetInfo.copy(baseUri = baseUri)

                }
                MyWidget().update(context, glanceId)
                val r = Result.success()
            } catch (e: Exception) {
                Log.i(
                    "ImageWorker",
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
                            rawPath = null,
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


    suspend fun storeStormImages(stormType: String, context: Context): Pair<String, Int> {
        Log.i("ImageWorker", "Fetching stormType=$stormType")
        val apiService = ApiService()
        try {
            val compareStormBytes: List<ByteArray> =
                apiService.getStormCompareImageList()
            Log.i(
                "ImageWorker",
                "ApiService returned: compareStormBytes {${compareStormBytes}}"
            )

            val myNumImages = compareStormBytes.size

            val baseUri = "content://com.thirdgate.stormtracker.provider/cache_files"

            var index = 0
            for (myImg in compareStormBytes) {

                val fileName = "compareModels_$index.jpg"

                val imageFile = File(context.cacheDir, fileName).apply {
                    writeBytes(myImg)
                }
                val contentUri = getUriForFile(
                    context,
                    "${applicationContext.packageName}.provider",
                    imageFile,
                )
                // Find the current launcher every time to ensure it has read permissions
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
                        contentUri,
                        FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                    )
                }

                Log.i(
                    "ImageWorker",
                    "Finished image $index uri=${contentUri.toString()} and baseUri=$baseUri and the fileName=$fileName"
                )

                index++
            }

            val myStormInfo = StormData.StormInfo(
                images = compareStormBytes,
                baseUri = baseUri
            )

            val myMap = mapOf("CompareImages" to myStormInfo)
            //val myStormData = StormData.Available(myMap)

            return Pair(baseUri, myNumImages)
        } catch (e: Exception) {
            Log.e("ImageWorker", "Oops")
            return Pair("null", 0)
        }
    }

}