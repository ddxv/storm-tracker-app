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

        fun enqueue(context: Context, size: DpSize, glanceId: GlanceId, force: Boolean = false) {
            val manager = WorkManager.getInstance(context)
            val requestBuilder = OneTimeWorkRequestBuilder<ImageWorker>().apply {
                addTag(glanceId.toString())
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                setInputData(
                    Data.Builder()
                        .putFloat("width", size.width.value.toPx)
                        .putFloat("height", size.height.value.toPx)
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
                uniqueWorkName + size.width + size.height,
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
        return try {
            val width = inputData.getFloat("width", 0f)
            val height = inputData.getFloat("height", 0f)
            val force = inputData.getBoolean("force", false)
            val uri = getRandomImage(width, height, force)
            updateImageWidget(width, height, uri)
            Result.success()
        } catch (e: Exception) {
            Log.e(uniqueWorkName, "Error while loading image", e)
            if (runAttemptCount < 10) {
                // Exponential backoff strategy will avoid the request to repeat
                // too fast in case of failures.
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun updateImageWidget(width: Float, height: Float, uri: String) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(MyWidget::class.java)
        val currentIndexKey = stringPreferencesKey("current_image_index")
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[MyWidget.getImageKey(width, height)] = uri
                prefs[MyWidget.sourceKey] = "Picsum Photos"
                prefs[MyWidget.sourceUrlKey] = "https://picsum.photos/"
            }
        }
        MyWidget().updateAll(context)
    }


    @Composable
    fun getNextImageAndUpdateWidget(width: Float, height: Float) {
        val currentIndexKey = stringPreferencesKey("current_image_index")
        val currentIndex = (currentState(currentIndexKey) as? Int) ?: 0

        // Now, launch the suspend function using something like the LaunchedEffect
        LaunchedEffect(currentIndex) {
            updateToNextImageWidget(width, height, currentIndex)
        }
    }


//    object NextImageAction : ActionCallback {
//        override suspend fun onAction(
//            context: Context,
//            glanceId: GlanceId,
//            parameters: ActionParameters,
//        ) {
//            val manager = GlanceAppWidgetManager(context)
//            val size = manager.getAppWidgetSizes(glanceId)
//                .first() // or some other logic to determine the right size
//
//            updateToNextImageWidget(size.width.value.toPx, size.height.value.toPx)
//
//            MyWidget().update(context, glanceId)
//        }
//    }

    suspend fun updateToNextImageWidget(
        width: Float,
        height: Float,
        currentIndex: Int
    ) {

        val apiService = ApiService()
        val imageList = apiService.getStormCompareImageList()
        val imageListSize = imageList.size

        if (imageList.isEmpty()) {
            throw IOException("No storm compare images available")
        }


        // Fetch the current index from the state
        val currentIndexKey = stringPreferencesKey("current_image_index")

        // Fetch the image based on the current index
        val selectedImage = imageList[currentIndex % imageList.size]

        // Convert the ByteArray to a file
        val imageFile = File(context.cacheDir, "nextStormImage.jpg").apply {
            writeBytes(selectedImage)
        }

        // Use the FileProvider to create a content URI
        val uri = getUriForFile(
            context,
            "${applicationContext.packageName}.provider",
            imageFile,
        ).toString()

        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(MyWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[MyWidget.getImageKey(width, height)] = uri
                prefs[MyWidget.sourceKey] = "Picsum Photos"
                prefs[MyWidget.sourceUrlKey] = "https://picsum.photos/"


                //val currentIndex = (currentState(currentIndexKey) as? Int) ?: 0


                // Increment the current image index for the next cycle
                var currentIndex = prefs[currentIndexKey] as Int

                currentIndex += 1
                currentIndex = currentIndex % imageListSize

                prefs[currentIndexKey] = currentIndex.toString()
            }
        }
        MyWidget().updateAll(context)
    }


    private suspend fun getRandomImage(width: Float, height: Float, force: Boolean): String {
        val apiService = ApiService()
        val imageList = apiService.getStormCompareImageList()

        if (imageList.isEmpty()) {
            throw IOException("No storm compare images available")
        }

        // Select a random image from the list
        val randomImage = imageList.random()

        // Since the image is already in ByteArray form, you don't need to make another Coil request.
        // Convert the ByteArray to a file
        val imageFileName = "stormImage_$currentIndex.jpg"
        val imageFile = File(context.cacheDir, imageFileName).apply {
            writeBytes(selectedImage)
        }

        // Use the FileProvider to create a content URI
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

        // return the path
        return contentUri.toString()
    }

}