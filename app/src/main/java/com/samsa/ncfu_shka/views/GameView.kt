package com.samsa.ncfu_shka.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.gson.Gson
import com.samsa.ncfu_shka.model.Food
import com.samsa.ncfu_shka.model.Player
import kotlin.math.abs
import kotlin.math.sqrt

class GameView(context: Context) : View(context) {
    private val paint = Paint()
    private val gson = Gson()
    private var players = listOf<Player>()
    private var foods = listOf<Food>()
    private var myPlayerId: String? = null

    private var renderX = 500f
    private var renderY = 500f

    private var targetX = 500f
    private var targetY = 500f

    private var viewX = 0f
    private var viewY = 0f

    private var directionX = 0f
    private var directionY = 0f
    private var isMoving = false

    private val INTERPOLATION_SPEED = 8f

    var onDirectionChanged: ((Float, Float) -> Unit)? = null
    var onPositionUpdate: ((Float, Float) -> Unit)? = null

    fun updateState(state: Map<*, *>) {
        try {
            val playersList = state["players"] as? List<*>
            val newPlayers = mutableListOf<Player>()
            playersList?.forEach { item ->
                val json = gson.toJson(item)
                val player = gson.fromJson(json, Player::class.java)
                newPlayers.add(player)
            }
            players = newPlayers

            val foodsList = state["foods"] as? List<*>
            val newFoods = mutableListOf<Food>()
            foodsList?.forEach { item ->
                val json = gson.toJson(item)
                val food = gson.fromJson(json, Food::class.java)
                newFoods.add(food)
            }
            foods = newFoods

            val myPlayer = players.find { it.id == myPlayerId }
            myPlayer?.let {
                targetX = it.x
                targetY = it.y
            }

            invalidate()
        } catch (e: Exception) {
            Log.e("GameView", "Update state error: ${e.message}")
        }
    }

    fun setMyPlayerId(id: String) {
        myPlayerId = id
        Log.d("GameView", "🎮 My ID: $id")
    }

    fun updateInterpolation() {
        // Плавно двигаем renderX к targetX
        val dx = targetX - renderX
        val dy = targetY - renderY

        // Если разница маленькая — просто ставим точную позицию
        if (abs(dx) < 0.1f && abs(dy) < 0.1f) {
            renderX = targetX
            renderY = targetY
        } else {
            // Интерполяция с фиксированной скоростью
            renderX += dx * 0.15f // Плавное приближение
            renderY += dy * 0.15f
        }

        // Обновляем камеру относительно интерполированной позиции
        viewX = renderX - width / 2f
        viewY = renderY - height / 2f
    }

    fun updatePosition() {
        if (!isMoving) return

        val length = sqrt(directionX * directionX + directionY * directionY)
        if (length == 0f) return

        val normX = directionX / length
        val normY = directionY / length

        val speed = 5f
        // Отправляем направление на сервер, но НЕ двигаем локально
        onDirectionChanged?.invoke(directionX, directionY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        if (players.isEmpty()) {
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Ожидание игроков...", width / 2f, height / 2f, paint)
            return
        }

        paint.color = Color.DKGRAY
        paint.strokeWidth = 1f
        val gridSize = 100f

        val offsetX = viewX % gridSize
        val offsetY = viewY % gridSize

        var x = -offsetX
        while (x < width + gridSize) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += gridSize
        }

        var y = -offsetY
        while (y < height + gridSize) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += gridSize
        }

        foods.forEach { food ->
            val sx = food.x - viewX
            val sy = food.y - viewY

            val isVisible = sx > -100 && sx < width + 100 && sy > -100 && sy < height + 100

            if (isVisible) {
                paint.color = food.color
                paint.style = Paint.Style.FILL
                canvas.drawCircle(sx, sy, food.radius, paint)

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawCircle(sx, sy, food.radius, paint)
            } else {
                val clampedX = sx.coerceIn(0f, width.toFloat())
                val clampedY = sy.coerceIn(0f, height.toFloat())

                paint.color = food.color
                paint.style = Paint.Style.FILL
                canvas.drawCircle(clampedX, clampedY, food.radius * 0.7f, paint)

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawCircle(clampedX, clampedY, food.radius * 0.7f, paint)
            }
        }

        players.forEach { player ->
            // Для своего игрока используем интерполированную позицию
            val isMyPlayer = player.id == myPlayerId
            val sx = if (isMyPlayer) {
                renderX - viewX
            } else {
                player.x - viewX
            }
            val sy = if (isMyPlayer) {
                renderY - viewY
            } else {
                player.y - viewY
            }

            paint.color = player.color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(sx, sy, player.radius, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(sx, sy, player.radius, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            val name = if (isMyPlayer) "${player.name} (ты)" else player.name
            canvas.drawText(name, sx, sy + 5, paint)

            if (isMyPlayer && isMoving) {
                paint.color = Color.YELLOW
                paint.strokeWidth = 3f
                canvas.drawLine(sx, sy, sx + directionX * 2, sy + directionY * 2, paint)
            }
        }

        val myPlayer = players.find { it.id == myPlayerId }
        myPlayer?.let {
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            paint.style = Paint.Style.FILL
            canvas.drawText("Размер: ${it.radius.toInt()}", 20f, 50f, paint)
            canvas.drawText("Игроков: ${players.size}", 20f, 90f, paint)
            canvas.drawText("Еды: ${foods.size}", 20f, 130f, paint)
            canvas.drawText("X: ${renderX.toInt()}, Y: ${renderY.toInt()}", 20f, 170f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val myPlayer = players.find { it.id == myPlayerId }
                myPlayer?.let {
                    // Используем интерполированную позицию для расчёта направления
                    val centerX = renderX - viewX
                    val centerY = renderY - viewY

                    directionX = event.x - centerX
                    directionY = event.y - centerY

                    val length = sqrt(directionX * directionX + directionY * directionY)
                    if (length > 0) {
                        directionX = directionX / length * 100
                        directionY = directionY / length * 100
                    }

                    isMoving = true
                    // Отправляем направление на сервер
                    onDirectionChanged?.invoke(directionX, directionY)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isMoving = false
                directionX = 0f
                directionY = 0f
                onDirectionChanged?.invoke(0f, 0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}