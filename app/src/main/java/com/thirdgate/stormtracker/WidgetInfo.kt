package com.thirdgate.stormtracker

import kotlinx.serialization.Serializable

@Serializable
data class WidgetInfo(
    val stormData: StormData,
    val currentIndex: Int,
    val baseUri: String?,
    val rawPath: String?,
    val numImagesWI: Int,
    val widgetGlanceId: String
)
