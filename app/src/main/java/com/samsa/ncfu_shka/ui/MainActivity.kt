package com.samsa.ncfu_shka.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.samsa.ncfu_shka.R
import com.samsa.ncfu_shka.network.GameClient
import com.samsa.ncfu_shka.network.ServiceDiscovery
import kotlin.concurrent.thread
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private lateinit var etPlayerName: EditText
    private lateinit var btnCreateGame: Button
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvServers: RecyclerView
    private lateinit var serverAdapter: ServerAdapter

    private var serviceDiscovery: ServiceDiscovery? = null
    private var foundServers = mutableListOf<Triple<String, String, Int>>()
    private var isScanning = false
    private var isCreatingGame = false
    private val TIMEOUT_MS = 3000L // 3 секунды на проверку

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        initViews()
        setupListeners()
        checkPermissions()

        serviceDiscovery = ServiceDiscovery(this)
    }

    private fun initViews() {
        etPlayerName = findViewById(R.id.etPlayerName)
        btnCreateGame = findViewById(R.id.btnCreateGame)
        btnScan = findViewById(R.id.btnScan)
        tvStatus = findViewById(R.id.tvStatus)
        rvServers = findViewById(R.id.rvServers)

        serverAdapter = ServerAdapter { server ->
            connectToServer(server)
        }
        rvServers.layoutManager = LinearLayoutManager(this)
        rvServers.adapter = serverAdapter

        etPlayerName.setText("Player${System.currentTimeMillis() % 1000}")
    }

    private fun setupListeners() {
        btnCreateGame.setOnClickListener { createGame() }
        btnScan.setOnClickListener { scanForServers() }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    private fun createGame() {
        val playerName = etPlayerName.text.toString().trim()
        if (playerName.isEmpty()) {
            Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
            return
        }

        if (isCreatingGame) return
        isCreatingGame = true

        btnCreateGame.isEnabled = false
        tvStatus.text = "Запуск сервера..."

        thread {
            try {
                serviceDiscovery?.registerService(
                    port = 8888,
                    onSuccess = { serviceName ->
                        runOnUiThread {
                            tvStatus.text = "✅ Сервер запущен: $serviceName"
                            //Toast.makeText(this, "Сервер виден в сети!", Toast.LENGTH_SHORT).show()
                            startGameActivity("127.0.0.1", playerName, isHost = true)
                            btnCreateGame.isEnabled = true
                            isCreatingGame = false
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            tvStatus.text = "❌ $error"
                            Toast.makeText(this, "Ошибка: $error", Toast.LENGTH_LONG).show()
                            btnCreateGame.isEnabled = true
                            isCreatingGame = false
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "❌ Ошибка: ${e.message}"
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    btnCreateGame.isEnabled = true
                    isCreatingGame = false
                }
            }
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                        return address.hostAddress
                    }
                }
            }
            "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private fun scanForServers() {
        if (isScanning) return
        isScanning = true

        btnScan.isEnabled = false
        tvStatus.text = "🔍 Поиск серверов..."
        foundServers.clear()
        serverAdapter.updateList(foundServers)

        val localIp = getLocalIpAddress()

        serviceDiscovery?.discoverServices(
            onFound = { name, ip, port ->
                // Фильтр: пропускаем свой сервер
                if (ip == localIp) {
                    Log.d("MainActivity", "⏭️ Пропускаем свой сервер: $name")
                    return@discoverServices
                }

                // Проверяем, отвечает ли сервер
                thread {
                    val isAlive = try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, port), 3000)
                        socket.close()
                        true
                    } catch (e: Exception) {
                        false
                    }

                    runOnUiThread {
                        if (isAlive) {
                            if (foundServers.none { it.second == ip && it.third == port }) {
                                foundServers.add(Triple(name, ip, port))
                                serverAdapter.updateList(foundServers)
                                tvStatus.text = "✅ Найден активный сервер: $name"
                            }
                        } else {
                            Log.d("MainActivity", "⏭️ Сервер не отвечает: $name")
                        }
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    tvStatus.text = "❌ $error"
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Останавливаем поиск через 10 секунд
        thread {
            Thread.sleep(10000)
            runOnUiThread {
                serviceDiscovery?.stopDiscovery()
                btnScan.isEnabled = true
                isScanning = false
                tvStatus.text = if (foundServers.isEmpty()) "❌ Активные серверы не найдены" else "✅ Поиск завершён"
            }
        }
    }

    private fun connectToServer(server: Triple<String, String, Int>) {
        val playerName = etPlayerName.text.toString().trim()
        if (playerName.isEmpty()) {
            Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
            return
        }

        val (name, ip, port) = server
        tvStatus.text = "Подключение к $name..."

        thread {
            try {
                val client = GameClient()
                val connected = client.connect(ip, port, playerName)

                runOnUiThread {
                    if (connected) {
                        client.disconnect()
                        startGameActivity(ip, playerName, isHost = false)
                    } else {
                        Toast.makeText(this, "Не удалось подключиться", Toast.LENGTH_LONG).show()
                        // Удаляем неработающий сервер из списка
                        foundServers.remove(server)
                        serverAdapter.updateList(foundServers)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    foundServers.remove(server)
                    serverAdapter.updateList(foundServers)
                }
            }
        }
    }

    private fun startGameActivity(serverIp: String, playerName: String, isHost: Boolean = false, botCount: Int = 5) {
        val intent = GameActivity.newIntent(this, serverIp, playerName, isHost, botCount)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceDiscovery?.cleanup()
        isScanning = false
    }
}