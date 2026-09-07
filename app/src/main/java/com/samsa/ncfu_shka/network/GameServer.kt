package com.samsa.ncfu_shka.network

import android.util.Log
import com.google.gson.Gson
import com.samsa.ncfu_shka.model.Food
import com.samsa.ncfu_shka.model.Player
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class GameServer(private val port: Int = 8888) {
    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<String, PrintWriter>()
    private val players = CopyOnWriteArrayList<Player>()
    private val foods = CopyOnWriteArrayList<Food>()
    private val directions = ConcurrentHashMap<String, Pair<Float, Float>>()
    private val gson = Gson()

    private var isRunning = false
    private var counter = 0
    private val SPEED = 5f
    private val FOOD_COUNT = 50

    fun start() {
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d("GameServer", "✅ Server started on port $port")

            generateFood(FOOD_COUNT)

            // Поток для принятия клиентов
            Thread {
                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept()
                        socket?.let {
                            Thread { handleClient(socket) }.start()
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e("GameServer", "Accept error: ${e.message}")
                    }
                }
            }.start()

            // Главный игровой цикл
            Thread {
                var lastTime = System.currentTimeMillis()
                while (isRunning) {
                    try {
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = (currentTime - lastTime) / 1000f
                        lastTime = currentTime

                        // Движение игроков
                        players.forEach { player ->
                            val dir = directions[player.id]
                            dir?.let { (dx, dy) ->
                                if (dx != 0f || dy != 0f) {
                                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (length > 0) {
                                        player.x += (dx / length) * SPEED * deltaTime * 60
                                        player.y += (dy / length) * SPEED * deltaTime * 60
                                    }
                                }
                            }
                        }

                        updateGame()
                        broadcastState()

                        Thread.sleep(16)
                    } catch (e: Exception) {
                        Log.e("GameServer", "Game loop error: ${e.message}")
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e("GameServer", "Failed to start: ${e.message}")
        }
    }

    private fun generateFood(count: Int) {
        repeat(count) {
            foods.add(Food(
                x = Random.nextFloat() * 800 + 100,
                y = Random.nextFloat() * 800 + 100,
                color = Random.nextInt(0xFFFFFF)
            ))
        }
        Log.d("GameServer", "🍎 Generated $count food, total: ${foods.size}")
    }

    private fun updateGame() {
        try {
            // Сбор еды
            val toRemove = mutableListOf<Food>()
            players.forEach { player ->
                foods.forEach { food ->
                    val dx = player.x - food.x
                    val dy = player.y - food.y
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance < player.radius + food.radius) {
                        player.radius += 0.5f
                        toRemove.add(food)
                    }
                }
            }
            foods.removeAll(toRemove)

            if (foods.size < FOOD_COUNT) {
                generateFood(FOOD_COUNT - foods.size)
            }

            // Поедание игроков
            val playersCopy = players.toList()
            playersCopy.forEach { player1 ->
                playersCopy.forEach { player2 ->
                    if (player1.id != player2.id && player2.isAlive && player1.isAlive) {
                        val dx = player1.x - player2.x
                        val dy = player1.y - player2.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                        if (distance < player1.radius + player2.radius) {
                            if (player1.radius > player2.radius * 1.2f) {
                                player1.radius += player2.radius * 0.3f
                                player2.isAlive = false
                                Log.d("GameServer", "🍽️ ${player1.name} (${player1.radius}) ate ${player2.name} (${player2.radius})!")

                                player2.x = Random.nextFloat() * 800 + 100
                                player2.y = Random.nextFloat() * 800 + 100
                                player2.radius = 30f
                                player2.isAlive = true
                            } else if (player2.radius > player1.radius * 1.2f) {
                                player2.radius += player1.radius * 0.3f
                                player1.isAlive = false
                                Log.d("GameServer", "🍽️ ${player2.name} (${player2.radius}) ate ${player1.name} (${player1.radius})!")

                                player1.x = Random.nextFloat() * 800 + 100
                                player1.y = Random.nextFloat() * 800 + 100
                                player1.radius = 30f
                                player1.isAlive = true
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("GameServer", "Update error: ${e.message}")
        }
    }

    private fun broadcastState() {
        if (clients.isEmpty()) return

        try {
            val state = mapOf(
                "players" to players.toList(),
                "foods" to foods.toList()
            )
            val json = gson.toJson(state)

            clients.forEach { (id, writer) ->
                try {
                    writer.println(json)
                    writer.flush()
                } catch (e: Exception) {
                    clients.remove(id)
                    directions.remove(id)
                    players.removeAll { it.id == id }
                }
            }
        } catch (e: Exception) {
            Log.e("GameServer", "Broadcast error: ${e.message}")
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            val bufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))

            val name = bufferedReader.readLine() ?: "Player${++counter}"

            val player = Player(
                id = "P${System.currentTimeMillis()}",
                name = name,
                color = Random.nextInt(0xFFFFFF)
            ).apply {
                x = Random.nextFloat() * 800 + 100
                y = Random.nextFloat() * 800 + 100
            }

            players.add(player)
            directions[player.id] = Pair(0f, 0f)
            clients[player.id] = writer
            Log.d("GameServer", "✅ ${player.name} at (${player.x.toInt()}, ${player.y.toInt()})")

            writer.println(player.id)
            writer.flush()

            while (isRunning) {
                try {
                    val line = bufferedReader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        val update = gson.fromJson(line, Map::class.java)
                        val dx = (update["x"] as? Number)?.toFloat()
                        val dy = (update["y"] as? Number)?.toFloat()
                        if (dx != null && dy != null) {
                            directions[player.id] = Pair(dx, dy)
                        }
                    }
                } catch (e: Exception) {
                    Log.d("GameServer", "Client disconnected: ${e.message}")
                    break
                }
            }

            clients.remove(player.id)
            directions.remove(player.id)
            players.remove(player)
            socket.close()
            Log.d("GameServer", "❌ ${player.name} disconnected")

        } catch (e: Exception) {
            Log.e("GameServer", "Client error: ${e.message}")
        }
    }

    fun stop() {
        Log.d("GameServer", "🛑 Stopping server...")
        isRunning = false

        val closeMessage = gson.toJson(mapOf("kicked" to true, "reason" to "Сервер закрыт"))
        clients.values.forEach { writer ->
            try {
                writer.println(closeMessage)
                writer.flush()
            } catch (e: Exception) {}
        }

        serverSocket?.close()
        clients.clear()
        players.clear()
        foods.clear()
        directions.clear()
        Log.d("GameServer", "✅ Server stopped")
    }
}