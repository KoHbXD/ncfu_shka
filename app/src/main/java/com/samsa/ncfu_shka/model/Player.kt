package com.samsa.ncfu_shka.model

import java.io.Serializable

data class Player(
    val id: String,
    var name: String = "Player",
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 30f,
    var color: Int = 0xFF0000.toInt(),
    var isAlive: Boolean = true,
    var isPlacingMine: Boolean = false,
    var mineProgress: Float = 0f
) : Serializable