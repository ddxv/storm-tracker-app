package com.thirdgate.stormtracker


import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontStyle
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import kotlinx.coroutines.delay

class MyWidget : GlanceAppWidget() {

    override val stateDefinition = GlanceButtonWidgetStateDefinition()
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.i("MyWidget", "provideGlance started")
        provideContent {
            Content()
        }
    }

    @Composable
    fun Content() {
        Log.i(
            "MyWidget",
            "Content: start"
        )
        val widgetInfo = currentState<WidgetInfo>()
        val stormData = widgetInfo.stormData
        val currentIndex = widgetInfo.currentIndex
        val baseUri = widgetInfo.baseUri
        val rawPath = widgetInfo.rawPath // Only for checking locations
        val context = LocalContext.current
        val glanceId = LocalGlanceId.current

        val imagePath = "$baseUri/compareModels_$currentIndex.jpg"
        Log.i(
            "MyWidget",
            "Content: currentIndex=$currentIndex: check uri=$imagePath & rawPath=$rawPath"
        )
        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.background)
                    .cornerRadius(8.dp)
            ) {
                Log.i("MyWidget", "Content check when stormData=${stormData}")
                when (stormData) {
                    StormData.Loading -> {
                        RetryLoadingContent(
                            context = context,
                            glanceId = glanceId,
                            stormData = stormData
                        )
                    }

                    is StormData.Available -> {
                        Log.i("MyWidget", "Content Available: Ready to load uri=$imagePath")
                        Box(contentAlignment = Alignment.BottomEnd) {
                            val myImageProvider = getImageProvider(context, imagePath)
                            Log.i("MyWidget", "Content: got imageProvider")
                            Image(
                                provider = myImageProvider,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = GlanceModifier
                            )
                            Log.i("MyWidget", "Content: Image created")
                            Button(
                                text = "NEXT", onClick = actionRunCallback<NextImageAction>(),
                                modifier = GlanceModifier
                                    .background(GlanceTheme.colors.primary)
                            )
                        }
                    }

                    is StormData.Unavailable -> {
                        Log.e(
                            "MyWidget",
                            "Content Unavailable: Failing to load baseUri=$baseUri and imagePath=$imagePath"
                        )
                        CircularProgressIndicator()
                        // Enqueue the worker after the composition is completed using the glanceId as
                        // tag so we can cancel all jobs in case the widget instance is deleted
                        SideEffect {
                            //ImageWorker.enqueue(context, glanceId)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RetryLoadingContent(context: Context, glanceId: GlanceId, stormData: StormData) {
        // Declare a state to hold the retry count
        val retryCount = remember { mutableStateOf(0) }
        Log.i("MyWidget", "Widget=$glanceId is Loading, try refreshing retryCount=$retryCount")
//        // This composable gets launched within RetryLoadingContent
//        LaunchedEffect(retryCount.value, stormData) {
//            while (retryCount.value < 10 && stormData is StormData.Loading) {
//                Log.i(
//                    "MyWidget",
//                    "Widget=$glanceId is Loading, try refreshing retryCount=$retryCount"
//                )
//                // Increment retry count
//                retryCount.value += 1
//                // Pause between retries
//                delay(timeMillis = 10000000)  // 1 second
//                MyWidget().update(context, glanceId)
//            }
//        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            CircularProgressIndicator()
            Text(
                "Try refreshing ...", style = TextStyle(
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = GlanceTheme.colors.onBackground
                ), modifier = GlanceModifier.padding(20.dp)
            )
            Button("Refresh", actionRunCallback<RefreshAction>())
        }
    }

    /**
     * Called when the widget instance is deleted. We can then clean up any ongoing task.
     */
    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
        ImageWorker.cancel(context, glanceId)
    }

    /**
     * Create an ImageProvider using an URI if it's a "content://" type, otherwise load
     * the bitmap from the cache file
     *
     * Note: When using bitmaps directly your might reach the memory limit for RemoteViews.
     * If you do reach the memory limit, you'll need to generate a URI granting permissions
     * to the launcher.
     *
     * More info:
     * https://developer.android.com/training/secure-file-sharing/share-file#GrantPermissions
     */
    private fun setUriPermission(uri: Uri, context: Context) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val permissionResult = context.checkCallingOrSelfUriPermission(uri, flags)
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

        Log.i("MyWidget", "Check permission: launcherName=$launcherName")
        if (launcherName != null) {
            Log.i("MyWidget", "Lets try setting permission: launcherName=$launcherName")
            context.grantUriPermission(
                launcherName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        } else {
            Log.e("ImageWorker", "launcherName was null, did not set permissions")
        }

        //return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun getImageProvider(context: Context, path: String): ImageProvider {
        Log.i("MyWidget", "getImageProvider start")
        if (path.startsWith("content://")) {

            val uriPath = path.toUri()
            Log.i("MyWidget", "getImageProvider for path=$path to pathtoUri=$uriPath")
            setUriPermission(uriPath, context)
            val imageProvider = ImageProvider(uriPath)
            Log.i(
                "MyWidget",
                "getImageProvider for path=$path to pathtoUri=$uriPath returning imageProvider=${imageProvider}"
            )
            return imageProvider
        }
        Log.i("MyWidget", "getImageProvider will return bitmap for $path")
        val bitmap = BitmapFactory.decodeFile(path)
        return ImageProvider(bitmap)
    }
}


class NextImageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        Log.i("MyWidget", "NextImageAction: glanceId=$glanceId Start onAction")
        // Increment the currentIndex and update the widget state
        updateAppWidgetState(
            context = context,
            definition = GlanceButtonWidgetStateDefinition(),
            glanceId = glanceId,
        ) { thisWidgetInfo ->


            val nextIndex = (thisWidgetInfo.currentIndex + 1) % thisWidgetInfo.numImagesWI
            Log.i(
                "MyWidget",
                "NextImageAction: glanceId=$glanceId set currentIndex=${thisWidgetInfo.currentIndex} nextIndex=$nextIndex"
            )
            WidgetInfo(
                stormData = thisWidgetInfo.stormData,
                currentIndex = nextIndex,
                widgetGlanceId = thisWidgetInfo.widgetGlanceId,
                baseUri = thisWidgetInfo.baseUri,
                numImagesWI = thisWidgetInfo.numImagesWI,
                rawPath = "${context.cacheDir}/compareModels_${nextIndex}.jpg",
            )

            //thisWidgetInfo.copy(currentIndex = nextIndex)
        }
        Log.i("MyWidget", "NextImageAction: glanceId=$glanceId Update Widget State before")
        // Call update to refresh the widget
        MyWidget().update(context, glanceId)
        Log.i("MyWidget", "NextImageAction: glanceId=$glanceId Update Widget State after")


    }

}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Force the worker to refresh
        ImageWorker.cancel(context = context, glanceId = glanceId)
        ImageWorker.enqueue(context = context, glanceId = glanceId, force = true)
        //MyWidget().update(context, glanceId)
    }
}


class ImageGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyWidget()
}
