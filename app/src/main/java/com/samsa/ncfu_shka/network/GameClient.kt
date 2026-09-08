package com.samsa.ncfu_shka.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class GameClient {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var bufferedReader: BufferedReader? = null
    private val gson = Gson()
    private var isConnected = false
    var playerId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pingRunnable: Runnable? = null
    var onGameStateUpdate: ((Map<*, *>) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    fun sendMineStart() {
        try {
            if (!isConnected || writer == null) {
                Log.e("GameClient", "❌ Не подключено")
                return
            }
            writer?.println("{\"mine\":true}")
            writer?.flush()
        } catch (e: Exception) {
            Log.e("GameClient", "❌ Send error: ${e.message}")
        }
    }

    fun sendMineStop() {
        try {
            if (!isConnected || writer == null) {
                Log.e("GameClient", "❌ Не подключено")
                return
            }
            writer?.println("{\"mine\":false}")
            writer?.flush()
        } catch (e: Exception) {
            Log.e("GameClient", "❌ Send error: ${e.message}")
        }
    }

    fun sendPlaceMine() {
        try {
            if (!isConnected || writer == null) {
                Log.e("GameClient", "❌ Не подключено")
                return
            }
            writer?.println("{\"placeMine\":true}")
            writer?.flush()
            Log.d("GameClient", "💣 Place mine command sent")
        } catch (e: Exception) {
            Log.e("GameClient", "❌ Send error: ${e.message}")
        }
    }

    fun connect(ip: String, port: Int = 8888, playerName: String): Boolean {
        return try {
            Log.d("GameClient", "🔄 Connecting to $ip:$port...")

            socket = Socket(ip, port)
            writer = PrintWriter(socket!!.getOutputStream(), true)
            bufferedReader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            writer?.println(playerName)
            writer?.flush()

            playerId = bufferedReader?.readLine()
            Log.d("GameClient", "✅ Connected, ID: $playerId")

            if (playerId == null) {
                Log.e("GameClient", "❌ Не получен ID от сервера")
                return false
            }

            isConnected = true

            // Поток для чтения данных
            Thread {
                while (isConnected) {
                    try {
                        val line = bufferedReader?.readLine() ?: break
                        if (line.isNotEmpty()) {
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            val state: Map<String, Any> = gson.fromJson(line, type)

                            if (state.containsKey("kicked")) {
                                Log.d("GameClient", "⛔ Кикнут с сервера")
                                onDisconnect?.invoke()
                                disconnect()
                                break
                            }

                            onGameStateUpdate?.invoke(state)
                        }
                    } catch (e: Exception) {
                        Log.e("GameClient", "Read error: ${e.message}")
                        break
                    }
                }
                Log.d("GameClient", "📥 Read thread ended")
                if (isConnected) {
                    Log.e("GameClient", "❌ Соединение потеряно")
                    onDisconnect?.invoke()
                    disconnect()
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e("GameClient", "❌ Connect failed: ${e.message}")
            isConnected = false
            false
        }
    }

    fun sendUpdate(x: Float, y: Float) {
        try {
            if (!isConnected || writer == null) {
                Log.e("GameClient", "❌ Не подключено")
                return
            }

            val json = "{\"x\":$x,\"y\":$y}"
            writer?.println(json)
            writer?.flush()
        } catch (e: Exception) {
            Log.e("GameClient", "❌ Send error: ${e.message}")
            isConnected = false
        }
    }

    fun isConnected(): Boolean = isConnected

    fun disconnect() {
        Log.d("GameClient", "Disconnecting...")
        isConnected = false

        try { bufferedReader?.close() } catch (e: Exception) {}
        try { writer?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}

        writer = null
        bufferedReader = null
        socket = null

        Log.d("GameClient", "Disconnected")
    }
}