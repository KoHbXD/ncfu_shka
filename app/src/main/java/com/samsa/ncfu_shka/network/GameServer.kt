package com.samsa.ncfu_shka.network

import android.util.Log
import com.google.gson.Gson
import com.samsa.ncfu_shka.model.Food
import com.samsa.ncfu_shka.model.Mine
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
    private val mines = CopyOnWriteArrayList<Mine>()
    private val directions = ConcurrentHashMap<String, Pair<Float, Float>>()
    private val mineProgress = ConcurrentHashMap<String, Float>()
    private val gson = Gson()

    private var isRunning = false
    private var counter = 0
    private val SPEED = 5f
    private val FOOD_COUNT = 50
    private val MAX_SIZE = 400f
    private val MAP_SIZE = 2000f
    private val MINE_RADIUS_RATIO = 0.5f
    private val MINE_CHARGE_TIME = 1f // секунд
    private val MINE_PENALTY = 0.1f // 10%
    private val MINE_DAMAGE = 0.5f // 50%
    private val MIN_SIZE_TO_SURVIVE = 50f

    fun start() {
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d("GameServer", "✅ Server started on port $port")

            generateFood(FOOD_COUNT)

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

            Thread {
                var lastTime = System.currentTimeMillis()
                while (isRunning) {
                    try {
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = (currentTime - lastTime) / 1000f
                        lastTime = currentTime

                        // Движение игроков
                        players.forEach { player ->
                            if (!player.isAlive) return@forEach

                            val dir = directions[player.id]
                            dir?.let { (dx, dy) ->
                                if (dx != 0f || dy != 0f) {
                                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (length > 0) {
                                        var newX = player.x + (dx / length) * SPEED * deltaTime * 60
                                        var newY = player.y + (dy / length) * SPEED * deltaTime * 60

                                        // Ограничение карты
                                        newX = newX.coerceIn(50f, MAP_SIZE - 50f)
                                        newY = newY.coerceIn(50f, MAP_SIZE - 50f)

                                        player.x = newX
                                        player.y = newY
                                    }
                                }
                            }

                            // Обновление прогресса мины
                            if (player.isPlacingMine) {
                                val progress = mineProgress[player.id] ?: 0f
                                val newProgress = (progress + deltaTime).coerceAtMost(MINE_CHARGE_TIME)
                                mineProgress[player.id] = newProgress

                                if (newProgress >= MINE_CHARGE_TIME) {
                                    placeMine(player)
                                    mineProgress[player.id] = 0f
                                    player.isPlacingMine = false
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

    private fun placeMine(player: Player) {
        if (player.radius < 30f) return

        // Штраф 10%
        val penalty = player.radius * MINE_PENALTY
        player.radius = (player.radius - penalty).coerceAtLeast(10f)

        val mine = Mine(
            id = "M${System.currentTimeMillis()}",
            ownerId = player.id,
            x = player.x + Random.nextFloat() * 60 - 30,
            y = player.y + Random.nextFloat() * 60 - 30,
            radius = player.radius * MINE_RADIUS_RATIO
        )
        mines.add(mine)
        Log.d("GameServer", "💣 ${player.name} поставил мину! Размер: ${player.radius}")
    }

    private fun generateFood(count: Int) {
        repeat(count) {
            foods.add(Food(
                x = Random.nextFloat() * (MAP_SIZE - 200) + 100,
                y = Random.nextFloat() * (MAP_SIZE - 200) + 100,
                color = Random.nextInt(0xFFFFFF)
            ))
        }
    }

    private fun updateGame() {
        try {
            // Сбор еды (с ограничением размера)
            val toRemove = mutableListOf<Food>()
            players.forEach { player ->
                if (!player.isAlive) return@forEach
                if (player.radius >= MAX_SIZE) return@forEach

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

            // Проверка мин
            val minesToRemove = mutableListOf<Mine>()
            mines.forEach { mine ->
                players.forEach { player ->
                    if (!player.isAlive) return@forEach
                    if (player.id == mine.ownerId) return@forEach

                    val dx = player.x - mine.x
                    val dy = player.y - mine.y
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                    if (distance < player.radius + mine.radius) {
                        // Взрыв!
                        val damage = player.radius * MINE_DAMAGE
                        player.radius -= damage
                        minesToRemove.add(mine)

                        Log.d("GameServer", "💥 ${player.name} подорвался на мине! Размер: ${player.radius}")

                        if (player.radius < MIN_SIZE_TO_SURVIVE) {
                            player.isAlive = false
                            player.x = Random.nextFloat() * (MAP_SIZE - 200) + 100
                            player.y = Random.nextFloat() * (MAP_SIZE - 200) + 100
                            player.radius = 30f
                            player.isAlive = true
                            Log.d("GameServer", "💀 ${player.name} был убит миной!")
                        }
                    }
                }
            }
            mines.removeAll(minesToRemove)

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
                                val gain = (player2.radius * 0.3f).coerceAtMost(MAX_SIZE - player1.radius)
                                player1.radius += gain
                                player2.isAlive = false
                                Log.d("GameServer", "🍽️ ${player1.name} ate ${player2.name}!")

                                player2.x = Random.nextFloat() * (MAP_SIZE - 200) + 100
                                player2.y = Random.nextFloat() * (MAP_SIZE - 200) + 100
                                player2.radius = 30f
                                player2.isAlive = true
                            } else if (player2.radius > player1.radius * 1.2f) {
                                val gain = (player1.radius * 0.3f).coerceAtMost(MAX_SIZE - player2.radius)
                                player2.radius += gain
                                player1.isAlive = false
                                Log.d("GameServer", "🍽️ ${player2.name} ate ${player1.name}!")

                                player1.x = Random.nextFloat() * (MAP_SIZE - 200) + 100
                                player1.y = Random.nextFloat() * (MAP_SIZE - 200) + 100
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
                "foods" to foods.toList(),
                "mines" to mines.toList()
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
                x = Random.nextFloat() * (MAP_SIZE - 200) + 100
                y = Random.nextFloat() * (MAP_SIZE - 200) + 100
            }

            players.add(player)
            directions[player.id] = Pair(0f, 0f)
            clients[player.id] = writer
            mineProgress[player.id] = 0f
            Log.d("GameServer", "✅ ${player.name} at (${player.x.toInt()}, ${player.y.toInt()})")

            writer.println(player.id)
            writer.flush()

            while (isRunning) {
                try {
                    val line = bufferedReader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        val update = gson.fromJson(line, Map::class.java)

                        // Обработка установки мины
                        if (update.containsKey("placeMine")) {
                            placeMine(player)
                            continue
                        }

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
            mineProgress.remove(player.id)
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
        mines.clear()
        directions.clear()
        mineProgress.clear()
        Log.d("GameServer", "✅ Server stopped")
    }
}