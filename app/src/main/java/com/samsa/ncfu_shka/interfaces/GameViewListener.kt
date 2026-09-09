package com.samsa.ncfu_shka.interfaces

interface GameViewListener {
    fun onDirectionChanged(dx: Float, dy: Float)
    fun onPlaceMine()
}