package com.thirdgate.stormtracker

import kotlinx.serialization.Serializable

@Serializable
sealed interface StormData {

    @Serializable
    data object Loading : StormData

    @Serializable
    data class Available(val stormImages: Map<String, StormInfo>) : StormData

    @Serializable
    data class Unavailable(val message: String) : StormData

    @Serializable
    data class StormInfo(
        val baseUri: String,
        val images: List<ByteArray>,
    ) {
        val numImages: Int
            get() = images.size
    }

}



