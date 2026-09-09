package com.samsa.ncfu_shka.interfaces

interface GameServerListener {
    fun onPlayerConnected(playerName: String)
    fun onPlayerDisconnected(playerName: String)
}