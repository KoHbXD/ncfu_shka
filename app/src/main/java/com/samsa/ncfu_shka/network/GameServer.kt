package com.samsa.ncfu_shka.network

import android.util.Log
import com.google.gson.Gson
import com.samsa.ncfu_shka.model.Bot
import com.samsa.ncfu_shka.model.BotState
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
    private val respawnQueue = ConcurrentHashMap<String, Long>() // playerId -> время воскрешения
    private val gson = Gson()

    private var isRunning = false
    private var counter = 0
    private val SPEED = 4f
    private val FOOD_COUNT = 100
    private val MAX_SIZE = 400f
    private val MAP_SIZE = 4000f
    private val MINE_RADIUS_RATIO = 0.5f
    private val MINE_CHARGE_TIME = 1f // секунд
    private val MINE_PENALTY = 0.2f // 20%
    private val MINE_DAMAGE = 0.5f // 50%
    private val MIN_SIZE_TO_SURVIVE = 50f

    private val bots = mutableListOf<Bot>()
    private var botCount = 5

    fun setBotCount(count: Int) {
        botCount = count.coerceIn(0, 20)
    }

    fun start() {
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d("GameServer", "✅ Server started on port $port")

            generateFood(FOOD_COUNT)
            createBots()

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
                        updateBots()

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

    private fun createBots() {
        for (i in 1..botCount) {
            val bot = Bot(
                id = "BOT_$i",
                name = if (i == 1) "Самирчик" else if (i == 2) "Женька" else "Bot$i",
                color = Random.nextInt(0xFFFFFF),
                x = Random.nextFloat() * (MAP_SIZE - 200) + 100,
                y = Random.nextFloat() * (MAP_SIZE - 200) + 100,
                radius = Random.nextFloat() * 60 + 20
            )
            bots.add(bot)
            players.add(bot)
            Log.d("GameServer", "🤖 Бот ${bot.name} создан! Размер: ${bot.radius}")
        }
    }

    private fun updateBots() {
        val now = System.currentTimeMillis()

        bots.forEach { bot ->
            if (!bot.isAlive) return@forEach

            // Каждые 300 мс пересчитываем цель
            if (now - bot.updateTimer > 300) {
                bot.updateTimer = now
                updateBotTarget(bot)
            }

            // Движение к цели
            val dx = bot.targetX - bot.x
            val dy = bot.targetY - bot.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

            if (dist > 5f) {
                val speed = 3f
                bot.x += (dx / dist) * speed
                bot.y += (dy / dist) * speed
            }
        }
    }

    private fun updateBotTarget(bot: Bot) {
        var nearestDanger: Player? = null
        var nearestDangerDist = Float.MAX_VALUE

        var nearestFood: Food? = null
        var nearestFoodDist = Float.MAX_VALUE

        var nearestPlayer: Player? = null
        var nearestPlayerDist = Float.MAX_VALUE

        // Проверяем всех игроков
        players.forEach { player ->
            if (player.id == bot.id || !player.isAlive) return@forEach

            val dx = player.x - bot.x
            val dy = player.y - bot.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

            // Если игрок больше бота — он опасен
            if (player.radius > bot.radius * 1.21f && dist < (bot.radius*3f)) {
                if (dist < nearestDangerDist) {
                    nearestDanger = player
                    nearestDangerDist = dist
                }
            }

            // Если игрок меньше бота — он цель
            if (player.radius < bot.radius * 0.79f && dist < (bot.radius*4f)) {
                if (dist < nearestPlayerDist) {
                    nearestPlayer = player
                    nearestPlayerDist = dist
                }
            }
        }

        // Ищем еду (если нужно вообще)
        if (bot.radius < 350f) {
            foods.forEach { food ->
                val dx = food.x - bot.x
                val dy = food.y - bot.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist < nearestFoodDist) {
                    nearestFood = food
                    nearestFoodDist = dist
                }
            }
        }

        // Принимаем решение
        when {
            // 1. Если опасность рядом — убегаем
            nearestDanger != null && nearestDangerDist < (bot.radius*3f) -> {
                bot.state = BotState.FLEEING
                val dx = bot.x - nearestDanger!!.x
                val dy = bot.y - nearestDanger!!.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist > 0) {
                    bot.targetX = bot.x + (dx / dist) * 200f
                    bot.targetY = bot.y + (dy / dist) * 200f
                }
            }
            // 2. Если есть цель меньше бота — преследуем
            nearestPlayer != null && nearestPlayerDist < (bot.radius*4f) -> {
                bot.state = BotState.CHASING
                bot.targetX = nearestPlayer!!.x
                bot.targetY = nearestPlayer!!.y
            }
            // 3. Иначе ищем еду
            nearestFood != null -> {
                bot.state = BotState.WANDERING
                bot.targetX = nearestFood!!.x
                bot.targetY = nearestFood!!.y
            }
            // 4. Нет еды — бродим случайно
            else -> {
                bot.state = BotState.WANDERING
                bot.targetX = Random.nextFloat() * (MAP_SIZE - 200) + 100
                bot.targetY = Random.nextFloat() * (MAP_SIZE - 200) + 100
            }
        }

        // Ограничиваем картой
        bot.targetX = bot.targetX.coerceIn(50f, MAP_SIZE - 50f)
        bot.targetY = bot.targetY.coerceIn(50f, MAP_SIZE - 50f)
    }

    private fun respawnBot(bot: Bot) {
        var newX: Float
        var newY: Float
        var attempts = 0
        do {
            newX = Random.nextFloat() * (MAP_SIZE - 200) + 100
            newY = Random.nextFloat() * (MAP_SIZE - 200) + 100
            attempts++
        } while (attempts < 50 && !isSafePosition(newX, newY))

        bot.x = newX
        bot.y = newY
        bot.radius = Random.nextFloat() * 30 + 20
        bot.isAlive = true
        bot.targetX = newX
        bot.targetY = newY

        Log.d("GameServer", "💫 Бот ${bot.name} воскрес!")
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
            collectFood()

            // Проверка мин
            checkMines()

            // Поедание игроков
            checkPlayerEat()

            // Обработка очереди воскрешения
            processRespawnQueue()

        } catch (e: Exception) {
            Log.e("GameServer", "Update error: ${e.message}")
        }
    }

    private fun collectFood() {
        val toRemove = mutableListOf<Food>()
        players.forEach { player ->
            if (!player.isAlive || player.radius >= MAX_SIZE) return@forEach

            foods.forEach { food ->
                val dx = player.x - food.x
                val dy = player.y - food.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance < player.radius + food.radius) {
                    player.radius += 0.5f
                    player.score += 1 // +1 очко за еду
                    toRemove.add(food)
                }
            }
        }
        foods.removeAll(toRemove)

        if (foods.size < FOOD_COUNT) {
            generateFood(FOOD_COUNT - foods.size)
        }
    }

    private fun checkMines() {
        val minesToRemove = mutableListOf<Mine>()
        mines.forEach { mine ->
            players.forEach { player ->
                if (!player.isAlive || player.id == mine.ownerId) return@forEach

                val dx = player.x - mine.x
                val dy = player.y - mine.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                if (distance < player.radius + mine.radius) {
                    // Взрыв!
                    val damage = player.radius * MINE_DAMAGE
                    player.radius -= damage
                    minesToRemove.add(mine)

                    // Владелец мины получает 5 очков
                    val owner = players.find { it.id == mine.ownerId }
                    owner?.let { it.score += 5 }

                    Log.d("GameServer", "💥 ${player.name} подорвался на мине! Размер: ${player.radius}")

                    if (player.radius < MIN_SIZE_TO_SURVIVE) {
                        killPlayer(player, "мина")
                    }
                }
            }
        }
        mines.removeAll(minesToRemove)
    }

    private fun checkPlayerEat() {
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
                            player1.score += 10 // +10 очков за убийство
                            Log.d("GameServer", "🍽️ ${player1.name} ate ${player2.name}!")
                            killPlayer(player2, "${player1.name}")

                        } else if (player2.radius > player1.radius * 1.2f) {
                            val gain = (player1.radius * 0.3f).coerceAtMost(MAX_SIZE - player2.radius)
                            player2.radius += gain
                            player2.score += 10 // +10 очков за убийство
                            Log.d("GameServer", "🍽️ ${player2.name} ate ${player1.name}!")
                            killPlayer(player1, "${player2.name}")
                        }
                    }
                }
            }
        }
    }

    private fun killPlayer(player: Player, reason: String) {
        if (!player.isAlive) return

        player.isAlive = false

        if (player is Bot) {
            // Бот воскресает без задержки
            respawnBot(player)
            return
        }

        val playerMines = mines.filter { it.ownerId == player.id }
        if (playerMines.isNotEmpty()) {
            mines.removeAll(playerMines)
        }

        // Отправляем клиенту сообщение о смерти
        val deathMessage = gson.toJson(mapOf(
            "death" to true,
            "respawnTime" to 2000,
            "reason" to reason
        ))
        clients[player.id]?.let { writer ->
            try {
                writer.println(deathMessage)
                writer.flush()
            } catch (e: Exception) {}
        }

        // Добавляем в очередь на воскрешение через 2 секунды
        respawnQueue[player.id] = System.currentTimeMillis() + 2000

        Log.d("GameServer", "💀 ${player.name} убит (${reason})")
    }

    private fun processRespawnQueue() {
        val now = System.currentTimeMillis()
        respawnQueue.entries.removeIf { (playerId, respawnTime) ->
            if (now >= respawnTime) {
                val player = players.find { it.id == playerId }
                player?.let {
                    // Воскрешаем в безопасном месте
                    var newX: Float
                    var newY: Float
                    var attempts = 0
                    do {
                        newX = Random.nextFloat() * (MAP_SIZE - 200) + 100
                        newY = Random.nextFloat() * (MAP_SIZE - 200) + 100
                        attempts++
                    } while (attempts < 50 && !isSafePosition(newX, newY))

                    it.x = newX
                    it.y = newY
                    it.radius = 30f
                    it.isAlive = true

                    // Отправляем клиенту сообщение о воскрешении
                    val respawnMessage = gson.toJson(mapOf("respawn" to true))
                    clients[player.id]?.let { writer ->
                        try {
                            writer.println(respawnMessage)
                            writer.flush()
                        } catch (e: Exception) {}
                    }

                    Log.d("GameServer", "💫 ${it.name} воскрес в (${newX.toInt()}, ${newY.toInt()})")
                }
                true
            } else false
        }
    }

    private fun isSafePosition(x: Float, y: Float): Boolean {
        val minDistance = 150f

        // Проверяем расстояние до других игроков
        players.forEach { player ->
            if (!player.isAlive) return@forEach
            val dx = x - player.x
            val dy = y - player.y
            if (kotlin.math.sqrt(dx * dx + dy * dy) < minDistance) {
                return false
            }
        }

        // Проверяем расстояние до мин
        mines.forEach { mine ->
            val dx = x - mine.x
            val dy = y - mine.y
            if (kotlin.math.sqrt(dx * dx + dy * dy) < minDistance) {
                return false
            }
        }

        return true
    }

    private fun broadcastState() {
        if (clients.isEmpty()) return

        try {
            val topPlayers = players
                .filter { it.isAlive }
                .sortedByDescending { it.score }
                .take(5)
                .map { "${it.name}: ${it.score}" }

            val state = mapOf(
                "players" to players.toList(),
                "foods" to foods.toList(),
                "mines" to mines.toList(),
                "topPlayers" to topPlayers
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
        bots.clear()
        Log.d("GameServer", "✅ Server stopped")
    }
}