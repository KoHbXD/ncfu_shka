package com.samsa.ncfu_shka.interfaces

interface GameClientListener {
    fun onStateUpdate(state: Map<*, *>)
    fun onDisconnect()
}