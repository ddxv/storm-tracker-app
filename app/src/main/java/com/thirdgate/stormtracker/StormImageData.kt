package com.thirdgate.stormtracker

import androidx.compose.ui.graphics.ImageBitmap

data class StormImageData(
    val storm: Map<String, String>,
    val imageBitmap: ImageBitmap?,
    val myImageBitmap: ImageBitmap?,
    val compareImageBitmap: ImageBitmap?
)