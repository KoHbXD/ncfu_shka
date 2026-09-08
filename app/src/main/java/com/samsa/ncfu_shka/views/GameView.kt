package com.samsa.ncfu_shka.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.gson.Gson
import com.samsa.ncfu_shka.model.Food
import com.samsa.ncfu_shka.model.Mine
import com.samsa.ncfu_shka.model.Player
import kotlin.math.sqrt

class GameView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    private var players = listOf<Player>()
    private var foods = listOf<Food>()
    private var mines = listOf<Mine>()
    private var topPlayers = listOf<String>()
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

    private var mineProgress = 0f
    private var isPlacingMine = false
    private var mineRunnable: Runnable? = null
    private val MAP_SIZE = 4000f

    var onDirectionChanged: ((Float, Float) -> Unit)? = null
    var onPlaceMine: (() -> Unit)? = null

    fun updateState(state: Map<*, *>) {
        try {
            val playersList = state["players"] as? List<*>
            val newPlayers = mutableListOf<Player>()
            playersList?.forEach { item ->
                val json = gson.toJson(item)
                val player = gson.fromJson(json, Player::class.java)
                newPlayers.add(player)
            }
            players = newPlayers.filter { it.isAlive }

            val foodsList = state["foods"] as? List<*>
            val newFoods = mutableListOf<Food>()
            foodsList?.forEach { item ->
                val json = gson.toJson(item)
                val food = gson.fromJson(json, Food::class.java)
                newFoods.add(food)
            }
            foods = newFoods

            val minesList = state["mines"] as? List<*>
            val newMines = mutableListOf<Mine>()
            minesList?.forEach { item ->
                val json = gson.toJson(item)
                val mine = gson.fromJson(json, Mine::class.java)
                newMines.add(mine)
            }
            mines = newMines

            @Suppress("UNCHECKED_CAST")
            topPlayers = state["topPlayers"] as? List<String> ?: emptyList()

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
    }

    fun updateInterpolation() {
        val dx = targetX - renderX
        val dy = targetY - renderY

        if (kotlin.math.abs(dx) < 0.1f && kotlin.math.abs(dy) < 0.1f) {
            renderX = targetX
            renderY = targetY
        } else {
            renderX += dx * 0.15f
            renderY += dy * 0.15f
        }

        viewX = renderX - width / 2f
        viewY = renderY - height / 2f
    }

    fun updatePosition() {
        if (!isMoving) return
        val length = sqrt(directionX * directionX + directionY * directionY)
        if (length == 0f) return
        onDirectionChanged?.invoke(directionX, directionY)
    }

    // ============================================
    // ЛОКАЛЬНАЯ УСТАНОВКА МИНЫ
    // ============================================
    private fun startMinePlacement() {
        if (isPlacingMine) return
        isPlacingMine = true
        mineProgress = 0f

        mineRunnable = object : Runnable {
            override fun run() {
                if (!isPlacingMine) {
                    mineProgress = 0f
                    invalidate()
                    return
                }

                mineProgress += 0.05f // шаг 50ms
                invalidate()

                if (mineProgress >= 1f) {
                    // Мина заряжена!
                    isPlacingMine = false
                    mineProgress = 0f
                    onPlaceMine?.invoke()
                    invalidate()
                } else {
                    handler.postDelayed(this, 50)
                }
            }
        }
        handler.post(mineRunnable!!)
    }

    private fun stopMinePlacement() {
        isPlacingMine = false
        mineRunnable?.let { handler.removeCallbacks(it) }
        mineProgress = 0f
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

        drawGrid(canvas)

        drawMapBorder(canvas)

        // Еда
        foods.forEach { food ->
            val sx = food.x - viewX
            val sy = food.y - viewY
            val isVisible = sx > -100 && sx < width + 100 && sy > -100 && sy < height + 100
            if (!isVisible) return@forEach

            paint.color = food.color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(sx, sy, food.radius, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawCircle(sx, sy, food.radius, paint)
        }

        // Мины
        mines.forEach { mine ->
            if (!mine.isActive) return@forEach

            val sx = mine.x - viewX
            val sy = mine.y - viewY
            val isVisible = sx > -50 && sx < width + 50 && sy > -50 && sy < height + 50
            if (!isVisible) return@forEach

            val isMyMine = mine.ownerId == myPlayerId
            paint.color = if (isMyMine) Color.YELLOW else Color.RED
            paint.style = Paint.Style.FILL
            canvas.drawCircle(sx, sy, mine.radius, paint)

            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(sx, sy, mine.radius, paint)
        }

        // Игроки
        players.forEach { player ->
            val isMyPlayer = player.id == myPlayerId
            val sx = if (isMyPlayer) renderX - viewX else player.x - viewX
            val sy = if (isMyPlayer) renderY - viewY else player.y - viewY

            paint.color = player.color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(sx, sy, player.radius, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            canvas.drawCircle(sx, sy, player.radius, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            val name = if (isMyPlayer) "${player.name} (ты)" else player.name
            canvas.drawText(name, sx, sy - (player.radius*1.2f), paint)

            // ============================================
            // ПРОГРЕСС-БАР МИНЫ (локальный)
            // ============================================
            if (isMyPlayer && mineProgress > 0f) {
                val maxRadius = player.radius * 1.15f
                val currentRadius = player.radius + (maxRadius - player.radius) * mineProgress

                paint.color = Color.RED
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.drawCircle(sx, sy, currentRadius, paint)

                // Заполнение
                val fillRadius = player.radius * 0.4f * mineProgress
                paint.color = Color.argb(150, 255, 0, 0)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(sx, sy, fillRadius + player.radius * 0.2f, paint)
            }

            if (isMyPlayer && isMoving) {
                paint.color = Color.YELLOW
                paint.strokeWidth = 3f
                canvas.drawLine(sx, sy, sx + directionX * 2, sy + directionY * 2, paint)
            }
        }

        // UI
        val myPlayer = players.find { it.id == myPlayerId }
        myPlayer?.let {
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            paint.style = Paint.Style.FILL
            canvas.drawText("Размер: ${it.radius.toInt()}", 25f, 50f, paint)
            canvas.drawText("Игроков: ${players.size}", 25f, 90f, paint)
            canvas.drawText("Мин: ${mines.size}", 25f, 130f, paint)
        }

        if (topPlayers.isNotEmpty()) {
            paint.color = Color.YELLOW
            paint.textSize = 48f
            paint.textAlign = Paint.Align.RIGHT
            paint.style = Paint.Style.FILL
            var yPos = 55f
            canvas.drawText("🏆 Топ игроков:", width - 25f, yPos, paint)
            yPos += 50f

            topPlayers.forEach { playerInfo ->
                paint.color = Color.WHITE
                paint.textSize = 34f
                canvas.drawText(playerInfo, width - 25f, yPos, paint)
                yPos += 35f
            }
        }

    }

    private fun drawMapBorder(canvas: Canvas) {
        // Левая граница
        val leftX = 0f - viewX
        val rightX = MAP_SIZE - viewX
        val topY = 0f - viewY
        val bottomY = MAP_SIZE - viewY

        // Рисуем только если граница видна на экране
        val borderVisible = leftX < width && rightX > 0 && topY < height && bottomY > 0

        if (!borderVisible) return

        // Толстая красная линия по краям
        paint.color = Color.argb(150, 255, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f

        // Левая граница
        if (leftX in 0f..width.toFloat()) {
            canvas.drawLine(leftX, topY.coerceAtLeast(0f), leftX, bottomY.coerceAtMost(height.toFloat()), paint)
        }

        // Правая граница
        if (rightX in 0f..width.toFloat()) {
            canvas.drawLine(rightX, topY.coerceAtLeast(0f), rightX, bottomY.coerceAtMost(height.toFloat()), paint)
        }

        // Верхняя граница
        if (topY in 0f..height.toFloat()) {
            canvas.drawLine(leftX.coerceAtLeast(0f), topY, rightX.coerceAtMost(width.toFloat()), topY, paint)
        }

        // Нижняя граница
        if (bottomY in 0f..height.toFloat()) {
            canvas.drawLine(leftX.coerceAtLeast(0f), bottomY, rightX.coerceAtMost(width.toFloat()), bottomY, paint)
        }

        // ============================================
        // УГЛЫ (для наглядности)
        // ============================================
        val cornerSize = 30f
        paint.strokeWidth = 8f

        // Левый верхний угол
        if (leftX in -cornerSize..width.toFloat() && topY in -cornerSize..height.toFloat()) {
            canvas.drawLine(leftX, topY, leftX + cornerSize, topY, paint)
            canvas.drawLine(leftX, topY, leftX, topY + cornerSize, paint)
        }

        // Правый верхний угол
        if (rightX in -cornerSize..width.toFloat() && topY in -cornerSize..height.toFloat()) {
            canvas.drawLine(rightX, topY, rightX - cornerSize, topY, paint)
            canvas.drawLine(rightX, topY, rightX, topY + cornerSize, paint)
        }

        // Левый нижний угол
        if (leftX in -cornerSize..width.toFloat() && bottomY in -cornerSize..height.toFloat()) {
            canvas.drawLine(leftX, bottomY, leftX + cornerSize, bottomY, paint)
            canvas.drawLine(leftX, bottomY, leftX, bottomY - cornerSize, paint)
        }

        // Правый нижний угол
        if (rightX in -cornerSize..width.toFloat() && bottomY in -cornerSize..height.toFloat()) {
            canvas.drawLine(rightX, bottomY, rightX - cornerSize, bottomY, paint)
            canvas.drawLine(rightX, bottomY, rightX, bottomY - cornerSize, paint)
        }

        // ============================================
        // ПОДПИСЬ РАЗМЕРА КАРТЫ
        // ============================================
        if (topY in 0f..height.toFloat() && leftX in 0f..width.toFloat()) {
            paint.color = Color.argb(200, 255, 255, 255)
            paint.style = Paint.Style.FILL
            paint.textSize = 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${MAP_SIZE.toInt()}x${MAP_SIZE.toInt()}",
                leftX + 80f,
                topY + 30f,
                paint
            )
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 100f
        val offsetX = viewX % gridSize
        val offsetY = viewY % gridSize

        paint.color = Color.DKGRAY
        paint.strokeWidth = 1f

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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    handleSingleTouch(event)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    handleSingleTouch(event)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    Log.d("GameView", "💣 Начало установки мины")
                    startMinePlacement()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    if (isPlacingMine) {
                        Log.d("GameView", "💣 Установка мины отменена")
                        stopMinePlacement()
                    }
                    isMoving = false
                    directionX = 0f
                    directionY = 0f
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleSingleTouch(event: MotionEvent) {
        val myPlayer = players.find { it.id == myPlayerId } ?: return

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
        onDirectionChanged?.invoke(directionX, directionY)
    }
}