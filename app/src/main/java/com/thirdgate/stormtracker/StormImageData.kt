package com.thirdgate.stormtracker

import androidx.compose.ui.graphics.ImageBitmap

data class StormImageData(
    val basicStormInfo: BasicStormInfo,
    val imageBitmap: ImageBitmap?,
    val myImageBitmap: ImageBitmap?,
    val compareImageBitmap: ImageBitmap?,
    val spaghettiImageBitmap: ImageBitmap?
)