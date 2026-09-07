package com.samsa.ncfu_shka.model

import java.io.Serializable

data class Food(
    var x: Float,
    var y: Float,
    var radius: Float = 8f,
    var color: Int
) : Serializable