package com.samsa.ncfu_shka.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.samsa.ncfu_shka.network.GameClient
import com.samsa.ncfu_shka.network.GameServer
import com.samsa.ncfu_shka.network.ServiceDiscovery
import com.samsa.ncfu_shka.views.GameView
import kotlin.concurrent.thread

class GameActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private var client: GameClient? = null
    private var gameServer: GameServer? = null
    private var serviceDiscovery: ServiceDiscovery? = null
    private var serverIp: String = ""
    private var playerName: String = ""
    private var isConnecting = false
    private var isGameActive = true
    private var isHost = false

    private val handler = Handler(Looper.getMainLooper())
    private var gameLoopRunnable: Runnable? = null

    companion object {
        private const val TAG = "GameActivity"

        fun newIntent(context: Context, serverIp: String, playerName: String, isHost: Boolean = false): Intent {
            return Intent(context, GameActivity::class.java).apply {
                putExtra("SERVER_IP", serverIp)
                putExtra("PLAYER_NAME", playerName)
                putExtra("IS_HOST", isHost)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )


        serverIp = intent.getStringExtra("SERVER_IP") ?: "127.0.0.1"
        playerName = intent.getStringExtra("PLAYER_NAME") ?: "Player"
        isHost = intent.getBooleanExtra("IS_HOST", false)

        Log.d(TAG, "🚀 Starting GameActivity")
        Log.d(TAG, "📡 Server IP: $serverIp")
        Log.d(TAG, "👤 Player: $playerName")
        Log.d(TAG, "🏠 Is Host: $isHost")

        // ============================================
        // ЗАПУСКАЕМ СЕРВЕР ТОЛЬКО ЕСЛИ ЭТО ХОСТ
        // ============================================
        if (isHost) {
            startServer()
        }

        gameView = GameView(this)
        setContentView(gameView)

        connectToServer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun startServer() {
        try {
            // Проверяем, не запущен ли уже сервер
            if (gameServer != null) {
                Log.w(TAG, "⚠️ Сервер уже запущен")
                return
            }

            gameServer = GameServer()
            gameServer?.start()
            Log.d(TAG, "✅ Сервер запущен")

            // Регистрируем сервис для обнаружения
            if (serviceDiscovery == null) {
                serviceDiscovery = ServiceDiscovery(this)
                serviceDiscovery?.registerService(
                    port = 8888,
                    onSuccess = { serviceName ->
                        Log.d(TAG, "✅ Сервис зарегистрирован: $serviceName")
                    },
                    onError = { error ->
                        Log.e(TAG, "❌ Ошибка регистрации: $error")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка запуска сервера: ${e.message}")
            Toast.makeText(this, "Ошибка запуска сервера: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun connectToServer() {
        if (isConnecting) return
        isConnecting = true

        Toast.makeText(this, "Подключение к серверу...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val newClient = GameClient()
                newClient.onGameStateUpdate = { state ->
                    runOnUiThread {
                        if (isGameActive) {
                            gameView.updateState(state)
                        }
                    }
                }

                val connected = newClient.connect(serverIp, 8888, playerName)

                runOnUiThread {
                    isConnecting = false

                    if (!connected) {
                        Toast.makeText(this, "Не удалось подключиться к серверу", Toast.LENGTH_LONG).show()
                        finish()
                        return@runOnUiThread
                    }

                    client = newClient

                    newClient.playerId?.let { id ->
                        gameView.setMyPlayerId(id)
                        Log.d(TAG, "🎮 My ID: $id")
                    }

                    gameView.onDirectionChanged = { dx, dy ->
                        thread {
                            client?.sendUpdate(dx, dy)
                        }
                    }

                    gameView.onPositionUpdate = { x, y ->
                        // Не отправляем каждое обновление
                    }

                    startGameLoop()

                    Toast.makeText(this, "Игра запущена! Нажмите на экран для движения", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isConnecting = false
                    Log.e(TAG, "Error: ${e.message}")
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
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

                handler.postDelayed(this, 16) // ~60 FPS
            }
        }
        handler.post(gameLoopRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "🛑 GameActivity уничтожается")
        isGameActive = false

        // Останавливаем игровой цикл
        gameLoopRunnable?.let { handler.removeCallbacks(it) }

        // Отключаем клиента
        try {
            client?.disconnect()
        } catch (e: Exception) {}
        client = null

        if (isHost) {
            try {
                serviceDiscovery?.cleanup()
                serviceDiscovery = null

                gameServer?.stop()
                gameServer = null
                Log.d(TAG, "✅ Сервер остановлен")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка остановки сервера: ${e.message}")
            }
        }

        Log.d(TAG, "✅ GameActivity уничтожена")
    }

    // ============================================
    // ОБРАБОТКА КНОПКИ "НАЗАД"
    // ============================================
    override fun onBackPressed() {
        if (isHost) {
            Toast.makeText(this, "Остановка сервера...", Toast.LENGTH_SHORT).show()
        }
        super.onBackPressed()
    }
}