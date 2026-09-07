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
import kotlin.math.sqrt

class GameView(context: Context) : View(context) {
    private val paint = Paint()
    private val gson = Gson()
    private var players = listOf<Player>()
    private var foods = listOf<Food>()
    private var myPlayerId: String? = null

    private var viewX = 0f
    private var viewY = 0f

    private var directionX = 0f
    private var directionY = 0f
    private var isMoving = false
    private var currentX = 500f
    private var currentY = 500f

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
                currentX = it.x
                currentY = it.y
                viewX = it.x - width / 2f
                viewY = it.y - height / 2f
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

    fun updatePosition() {
        if (!isMoving) return

        val length = sqrt(directionX * directionX + directionY * directionY)
        if (length == 0f) return

        val normX = directionX / length
        val normY = directionY / length

        val speed = 5f
        currentX += normX * speed
        currentY += normY * speed

        viewX = currentX - width / 2f
        viewY = currentY - height / 2f

        onPositionUpdate?.invoke(currentX, currentY)
        invalidate()
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

        // ============================================
        // СЕТКА — ВО ВЕСЬ ЭКРАН
        // ============================================
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

        // ============================================
        // ЕДА — С ОТОБРАЖЕНИЕМ ПО КРАЯМ ЭКРАНА
        // ============================================
        foods.forEach { food ->
            val sx = food.x - viewX
            val sy = food.y - viewY

            // Если еда за пределами экрана — рисуем на границе
            val clampedX = sx.coerceIn(0f, width.toFloat())
            val clampedY = sy.coerceIn(0f, height.toFloat())

            // Если еда далеко за пределами — не рисуем вообще
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
                // Рисуем на границе (уменьшенную копию)
                paint.color = food.color
                paint.style = Paint.Style.FILL
                canvas.drawCircle(clampedX, clampedY, food.radius * 0.7f, paint)

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawCircle(clampedX, clampedY, food.radius * 0.7f, paint)
            }
        }

        // ============================================
        // ИГРОКИ
        // ============================================
        players.forEach { player ->
            val sx = player.x - viewX
            val sy = player.y - viewY

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
            val name = if (player.id == myPlayerId) "${player.name} (ты)" else player.name
            canvas.drawText(name, sx, sy + 5, paint)

            if (player.id == myPlayerId && isMoving) {
                paint.color = Color.YELLOW
                paint.strokeWidth = 3f
                canvas.drawLine(sx, sy, sx + directionX * 2, sy + directionY * 2, paint)
            }
        }

        // ============================================
        // UI — ИНФОРМАЦИЯ В УГЛУ
        // ============================================
        val myPlayer = players.find { it.id == myPlayerId }
        myPlayer?.let {
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            paint.style = Paint.Style.FILL
            canvas.drawText("Размер: ${it.radius.toInt()}", 20f, 50f, paint)
            canvas.drawText("Игроков: ${players.size}", 20f, 90f, paint)
            canvas.drawText("Еды: ${foods.size}", 20f, 130f, paint)
            canvas.drawText("X: ${it.x.toInt()}, Y: ${it.y.toInt()}", 20f, 170f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val myPlayer = players.find { it.id == myPlayerId }
                myPlayer?.let {
                    val centerX = it.x - viewX
                    val centerY = it.y - viewY

                    directionX = event.x - centerX
                    directionY = event.y - centerY

                    val length = sqrt(directionX * directionX + directionY * directionY)
                    if (length > 0) {
                        directionX = directionX / length * 100
                        directionY = directionY / length * 100
                    }

                    isMoving = true
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