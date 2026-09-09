package com.samsa.ncfu_shka.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.samsa.ncfu_shka.controller.GameController
import com.samsa.ncfu_shka.interfaces.GameStopper
import com.samsa.ncfu_shka.network.GameClient
import com.samsa.ncfu_shka.network.GameServer
import com.samsa.ncfu_shka.network.ServiceDiscovery
import com.samsa.ncfu_shka.views.GameView

class GameActivity : AppCompatActivity(), GameStopper {
    private lateinit var gameView: GameView
    private lateinit var controller: GameController
    private var gameServer: GameServer? = null
    private var serviceDiscovery: ServiceDiscovery? = null

    companion object {
        private const val TAG = "GameActivity"

        fun newIntent(
            context: Context,
            serverIp: String,
            playerName: String,
            isHost: Boolean = false,
            botCount: Int = 5
        ): Intent {
            return Intent(context, GameActivity::class.java).apply {
                putExtra("SERVER_IP", serverIp)
                putExtra("PLAYER_NAME", playerName)
                putExtra("IS_HOST", isHost)
                putExtra("BOT_COUNT", botCount)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val serverIp = intent.getStringExtra("SERVER_IP") ?: "127.0.0.1"
        val playerName = intent.getStringExtra("PLAYER_NAME") ?: "Player"
        val isHost = intent.getBooleanExtra("IS_HOST", false)
        val botCount = intent.getIntExtra("BOT_COUNT", 5)

        Log.d(TAG, "🚀 Starting GameActivity")
        Log.d(TAG, "📡 Server IP: $serverIp")
        Log.d(TAG, "👤 Player: $playerName")
        Log.d(TAG, "🏠 Is Host: $isHost")
        Log.d(TAG, "🤖 Bot Count: $botCount")

        gameView = GameView(this)
        setContentView(gameView)

        val client = GameClient()

        if (isHost) {
            gameServer = GameServer().apply {
                setBotCount(botCount)
                start()
                Log.d(TAG, "✅ Сервер запущен с $botCount ботами")
            }

            serviceDiscovery = ServiceDiscovery(this).apply {
                registerService(
                    port = 8888,
                    onSuccess = { serviceName ->
                        Log.d(TAG, "✅ Сервис зарегистрирован: $serviceName")
                    },
                    onError = { error ->
                        Log.e(TAG, "❌ Ошибка регистрации: $error")
                    }
                )
            }
        }

        controller = GameController(
            context = this,
            gameView = gameView,
            client = client,
            serverIp = serverIp,
            playerName = playerName,
            gameStopper = this
        )
        controller.start()
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

    override fun onDestroy() {
        super.onDestroy()

        // Останавливаем контроллер
        controller.stopGame()

        // Останавливаем сервер и NSD (если были)
        if (intent.getBooleanExtra("IS_HOST", false)) {
            serviceDiscovery?.unregisterService()
            serviceDiscovery = null

            gameServer?.stop()
            gameServer = null
            Log.d(TAG, "✅ Сервер и NSD остановлены")
        }

        Log.d(TAG, "✅ GameActivity уничтожена")
    }

    override fun stopGame() {
        // Останавливаем сервер и NSD
        if (intent.getBooleanExtra("IS_HOST", false)) {
            serviceDiscovery?.unregisterService()
            serviceDiscovery = null
            gameServer?.stop()
            gameServer = null
            Log.d(TAG, "✅ Сервер и NSD остановлены")
        }
        finish()
    }
}