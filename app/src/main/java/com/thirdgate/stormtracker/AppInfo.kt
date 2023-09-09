package com.thirdgate.stormtracker

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(
    val themeId: String = "default",
    val stormData: StormData
)

