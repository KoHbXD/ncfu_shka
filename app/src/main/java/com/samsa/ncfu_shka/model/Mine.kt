package com.samsa.ncfu_shka.model

import java.io.Serializable

data class Mine(
    val id: String,
    val ownerId: String,
    var x: Float,
    var y: Float,
    var radius: Float,
    var isActive: Boolean = true
) : Serializable