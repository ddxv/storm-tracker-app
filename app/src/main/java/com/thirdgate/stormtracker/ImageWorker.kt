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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit


class ImageWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    companion object {

        private val uniqueWorkName = ImageWorker::class.java.simpleName

        fun enqueue(context: Context, glanceId: GlanceId, force: Boolean = false) {
            Log.i("ImageWorker", "Enqueue: start")
            val manager = WorkManager.getInstance(context)
            if (force) {
                Log.i("ImageWorker", "Enqueue: Onetime worker")
                val requestBuilder = OneTimeWorkRequestBuilder<ImageWorker>().apply {
                    addTag(glanceId.toString())
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    setInputData(
                        Data.Builder()
                            .putBoolean("force", force)
                            .build(),
                    )

                }
                val workPolicy = ExistingWorkPolicy.REPLACE
                manager.enqueueUniqueWork(
                    uniqueWorkName,
                    workPolicy,
                    requestBuilder.build(),
                )
                Log.i("ImageWorker", "Enqueue: Onetime worker enqueued")
            } else {
                Log.i("ImageWorker", "Enqueue: Periodic worker")
                val requestBuilder =
                    PeriodicWorkRequestBuilder<ImageWorker>(Duration.ofMinutes(60)).apply {
                        addTag("${glanceId}_periodic")
                        setInputData(
                            Data.Builder()
                                .putBoolean("force", false)
                                .build(),
                        )
                    }
                manager.enqueueUniquePeriodicWork(
                    uniqueWorkName,
                    ExistingPeriodicWorkPolicy.KEEP,
                    requestBuilder.build(),
                )
                Log.i("ImageWorker", "Enqueue: Periodic worker enqueued")
            }

            // Temporary workaround to avoid WM provider to disable itself and trigger an
            // app widget update
            Log.i("ImageWorker", "Enqueue: workaround")
            manager.enqueueUniqueWork(
                "$uniqueWorkName-workaround",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ImageWorker>().apply {
                    setInitialDelay(365, TimeUnit.DAYS)
                }.build(),
            )
            Log.i("ImageWorker", "Enqueue: finished")
        }

        /**
         * Cancel any ongoing worker
         */
        fun cancel(context: Context, glanceId: GlanceId) {
            WorkManager.getInstance(context).cancelAllWorkByTag(glanceId.toString())
        }
    }

    override suspend fun doWork(): Result {
        Log.i("ImageWorker", "doWork Start")
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(MyWidget::class.java)
        var r: Result = Result.failure()

        if (glanceIds.size == 0) {
            Log.i("ImageWorker", "doWork: no glanceIds=${glanceIds} return retry")
            if (runAttemptCount < 10) {
                // Exponential backoff strategy will avoid the request to repeat
                // too fast in case of failures.
                Log.i(
                    "ImageWorker",
                    "doWork failed: Set state to UnAvailable and Result.retry()"
                )
                r = Result.retry()
            } else {
                Log.i("ImageWorker", "doWork failed: runAttempt >= 10 return Result.failure()")
                r = Result.failure()
            }
        }


        Log.i("ImageWorker", "doWork: glanceIds=${glanceIds}")
        glanceIds.forEach { glanceId ->
            Log.i("ImageWorker", "doWork:forEach(glanceId=$glanceId start loop")
            try {
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
                Log.i("ImageWorker", "doWork:forEach(glanceId=$glanceId update widget to loading")
                MyWidget().update(context, glanceId)
                // Update state with new data
                val (baseUri, numImagesWI, myStormData) = storeStormImages(context)
                updateAppWidgetState(
                    context = context,
                    glanceId = glanceId,
                    definition = GlanceButtonWidgetStateDefinition()
                ) { thisWidgetInfo ->
                    WidgetInfo(
                        stormData = myStormData,
                        currentIndex = thisWidgetInfo.currentIndex,
                        numImagesWI = numImagesWI,
                        widgetGlanceId = thisWidgetInfo.widgetGlanceId,
                        baseUri = baseUri,
                        rawPath = "${context.cacheDir}/compareModels_${thisWidgetInfo.currentIndex}.jpg",
                    )

                }
                Log.i("ImageWorker", "doWork:forEach(glanceId=$glanceId Finished Data Update")
                MyWidget().update(context, glanceId)
                Log.i(
                    "ImageWorker",
                    "doWork:forEach(glanceId=$glanceId Finished Widget Update, returning success"
                )
                r = Result.success()
            } catch (e: Exception) {
                Log.e(
                    "ImageWorker",
                    "Catch error this.glanceId: $glanceId $e"
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
                    Log.i(
                        "ImageWorker",
                        "doWork failed: Set state to UnAvailable and Result.retry()"
                    )
                    r = Result.retry()
                } else {
                    Log.i("ImageWorker", "doWork failed: runAttempt >= 10 return Result.failure()")
                    r = Result.failure()
                }
                Log.e(
                    "ImageWorker",
                    "Catch error this.glanceId: $glanceId Returning"
                )
            }
        }
        return r
    }


    suspend fun storeStormImages(
        context: Context
    ): Triple<String, Int, StormData> {
        Log.i("ImageWorker", "storeStormImages Fetching storms from API")
        val apiService = ApiService()
        try {
            Log.i("ImageWorker", "storeStormImages ApiService: calling...")
            val myImagesBytes: List<ByteArray> = apiService.getStormCompareImageList()
            Log.i(
                "ImageWorker",
                "storeStormImages ApiService returned: compareStormBytes {${myImagesBytes}}"
            )

            val myNumImages = myImagesBytes.size
            var baseUri = "content://com.thirdgate.stormtracker.provider/my_files"

            Log.i(
                "ImageWorker",
                "storeImages: cache=${context.cacheDir} filesDir=${context.filesDir}"
            )

            for ((index, myImg) in myImagesBytes.withIndex()) {
                val fileName = "compareModels_$index.jpg"
                val imageFile = File(context.filesDir, fileName).apply {
                    writeBytes(myImg)
                }
                Log.i("ImageWorker", "storeStormImages imageFile=$imageFile")
                val contentUri = getUriForFile(
                    context,
                    "${applicationContext.packageName}.provider",
                    imageFile,
                )

                val baseAuthority = contentUri.authority
                val firstPathSegment = contentUri.pathSegments.firstOrNull()

                baseUri = if (baseAuthority != null && firstPathSegment != null) {
                    "content://$baseAuthority/$firstPathSegment"
                } else {
                    baseUri
                }

                Log.i(
                    "ImageWorker",
                    "storeStormImages imageFile=$imageFile uri=$contentUri, baseUri=$baseUri"
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
                Log.i("ImageWorker", "launcherName=$launcherName set permissions")
                if (launcherName != null) {
                    context.grantUriPermission(
                        launcherName,
                        contentUri,
                        FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                    )
                } else {
                    Log.e("ImageWorker", "launcherName was null, did not set permissions")
                }
                Log.i(
                    "ImageWorker",
                    "Finished image=$index uri=${contentUri} and baseUri=$baseUri and the fileName=$fileName"
                )
            }

            val myStormInfo = StormData.StormInfo(
                images = myImagesBytes,
                baseUri = baseUri
            )

            val myMap = mapOf("CompareImages" to myStormInfo)
            val myStormData = StormData.Available(myMap)

            return Triple(baseUri, myNumImages, myStormData)
        } catch (e: Exception) {
            Log.e("ImageWorker", "storeStormImages failed due to $e")
            return Triple("null", 0, StormData.Unavailable("Error in fetching images $e"))
        }
    }

}