package com.thirdgate.stormtracker


import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontStyle
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle

class MyWidget : GlanceAppWidget() {

    override val stateDefinition = GlanceButtonWidgetStateDefinition()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Content()
        }
    }

    @Composable
    fun Content() {
        val widgetInfo = currentState<WidgetInfo>()
        val currentIndex = widgetInfo.currentIndex
        val baseUri = widgetInfo.baseUri
        val rawPath = widgetInfo.rawPath
        val context = LocalContext.current

        val imagePath = baseUri + "/compareModels_$currentIndex.jpg"
        Log.i("MyWidget", "check uri=$imagePath & rawPath=$rawPath")
        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.background)
                    .appWidgetBackgroundCornerRadius(),
                contentAlignment = if (imagePath == null) {
                    Alignment.Center
                } else {
                    Alignment.BottomEnd
                },
            ) {
                if (imagePath != null && baseUri != null && rawPath != null) {
                    Log.i("MyWidget", "Ready to load baseUri=$baseUri with uri=$imagePath")
                    Image(
                        provider = getImageProvider(imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionRunCallback<RefreshAction>()),
                    )
                    Button(
                        text = "NEXT", onClick = actionRunCallback<NextImageAction>(),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(GlanceTheme.colors.primary)
                    )

                } else {
                    Log.e("MyWidget", "Failing to load baseUri=$baseUri and imagePath=$imagePath")
                    CircularProgressIndicator()

                    // Enqueue the worker after the composition is completed using the glanceId as
                    // tag so we can cancel all jobs in case the widget instance is deleted
                    val glanceId = LocalGlanceId.current
                    SideEffect {
                        ImageWorker.enqueue(context, glanceId)
                    }
                }
            }
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
    private fun getImageProvider(path: String): ImageProvider {
        if (path.startsWith("content://")) {
            Log.i("MyWidget", "getImageProvider for path=$path to pathtoUri=${path.toUri()}")
            return ImageProvider(path.toUri())
        }
        Log.i("MyWidget", "getImageProvider will return bitmap for $path")
        val bitmap = BitmapFactory.decodeFile(path)
        return ImageProvider(bitmap)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Clear the state to show loading screen
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.clear()
        }
        MyWidget().update(context, glanceId)

        // Enqueue a job for each size the widget can be shown in the current state
        // (i.e landscape/portrait)
        GlanceAppWidgetManager(context).getAppWidgetSizes(glanceId).forEach { size ->
            ImageWorker.enqueue(context, glanceId, force = true)
        }
    }
}

class NextImageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // TODO: Implement logic to fetch the next image and update the widget with it.
        // For now, just to demonstrate, let's refresh the widget
        RefreshAction().onAction(context, glanceId, parameters)
    }
}

class ImageGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyWidget()
}
