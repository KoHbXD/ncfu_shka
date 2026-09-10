package com.samsa.ncfu_shka.controller

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.samsa.ncfu_shka.interfaces.GameClientListener
import com.samsa.ncfu_shka.interfaces.GameStopper
import com.samsa.ncfu_shka.interfaces.GameViewListener
import com.samsa.ncfu_shka.network.GameClient
import com.samsa.ncfu_shka.views.GameView
import kotlin.concurrent.thread

class GameController(
    private val context: Context,
    private val gameView: GameView,
    private val client: GameClient,
    private val serverIp: String,
    private val playerName: String,
    private val gameStopper: GameStopper
): GameClientListener, GameViewListener {
    private var isConnecting = false
    private var isGameActive = true

    private val handler = Handler(Looper.getMainLooper())
    private var gameLoopRunnable: Runnable? = null

    companion object {
        private const val TAG = "GameController"
    }

    fun start() {
        gameView.setListener(this)
        client.setListener(this)
        connectToServer()
    }

    fun stopGame() {
        isGameActive = false
        gameLoopRunnable?.let { handler.removeCallbacks(it) }
        client.disconnect()
        Log.d(TAG, "✅ Игра остановлена")
    }

    private fun connectToServer() {
        if (isConnecting) return
        isConnecting = true

        Toast.makeText(context, "Подключение к серверу...", Toast.LENGTH_SHORT).show()

        thread {
            try {

                val connected = client.connect(serverIp, 8888, playerName)

                handler.post {
                    isConnecting = false

                    if (!connected) {
                        Toast.makeText(context, "Не удалось подключиться к серверу", Toast.LENGTH_LONG).show()
                        stopGame()
                        return@post
                    }

                    client.playerId?.let { id ->
                        gameView.setMyPlayerId(id)
                        Log.d(TAG, "🎮 My ID: $id")
                    }

                    startGameLoop()

                    Toast.makeText(context, "Игра запущена!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                handler.post {
                    isConnecting = false
                    Log.e(TAG, "Error: ${e.message}")
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    stopGame()
                }
            }
        }
    }

    private fun startGameLoop() {
        gameLoopRunnable = object : Runnable {
            override fun run() {
                if (!isGameActive) return

                gameView.updateInterpolation()
                gameView.updatePosition()

                handler.postDelayed(this, 16)
            }
        }
        handler.post(gameLoopRunnable!!)
    }

    override fun onStateUpdate(state: Map<*, *>) {
        handler.post {
            if (isGameActive) {
                gameView.updateState(state)
            }
        }
    }

    override fun onDisconnect() {
        handler.post {
            Toast.makeText(context, "Соединение потеряно", Toast.LENGTH_LONG).show()
            stopGame()
        }
    }

    override fun onDirectionChanged(dx: Float, dy: Float) {
        thread {
            client.sendUpdate(dx, dy)
        }
    }

    override fun onPlaceMine() {
        Log.d(TAG, "💣 Мина установлена!")
        thread {
            client.sendPlaceMine()
        }
    }
}