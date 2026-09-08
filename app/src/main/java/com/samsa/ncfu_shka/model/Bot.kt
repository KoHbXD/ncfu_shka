package com.samsa.ncfu_shka.model

import kotlin.random.Random

enum class BotState {
    WANDERING,   // ищет еду
    CHASING,     // преследует игрока
    FLEEING      // убегает от опасности
}

class Bot(
    id: String = "BOT_${System.currentTimeMillis()}",
    name: String = "Bot${Random.nextInt(100)}",
    color: Int = Random.nextInt(0xFFFFFF),
    x: Float = Random.nextFloat() * 800 + 100,
    y: Float = Random.nextFloat() * 800 + 100,
    radius: Float = Random.nextFloat() * 30 + 20
) : Player(id, name, x, y, radius, color) {

    var targetX: Float = x
    var targetY: Float = y
    var state: BotState = BotState.WANDERING
    var updateTimer: Long = 0
    var isBot: Boolean = true
}