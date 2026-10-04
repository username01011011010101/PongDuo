package fr.pongduo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pong pour deux joueurs sur le même téléphone (tenu à plat, en portrait).
 * Joueur 1 = raquette du bas (cyan), Joueur 2 = raquette du haut (rose).
 * Chaque joueur glisse le doigt dans SA moitié d'écran. Multitouch géré.
 */
class PongView(context: Context, private val sound: Sound) : View(context) {

    private enum class State { WAITING, PLAYING, SERVING, GAME_OVER }

    private companion object {
        const val WIN_SCORE = 7
        const val MAX_ANGLE = Math.PI / 3          // 60° max au rebond
        const val SPEEDUP = 1.06f                  // +6 % à chaque touche
        const val SERVE_DELAY = 0.9f               // secondes avant le service
        val COLOR_BG = Color.parseColor("#0E1220")
        val COLOR_P1 = Color.parseColor("#3EE6E0")
        val COLOR_P2 = Color.parseColor("#FF4FA3")
    }

    // --- Dimensions (calculées selon l'écran) ---
    private var w = 0f
    private var h = 0f
    private var paddleW = 0f
    private var paddleH = 0f
    private var paddleMargin = 0f
    private var ballR = 0f
    private var baseSpeed = 0f
    private var maxSpeed = 0f

    // --- État du jeu ---
    private var state = State.WAITING
    private var p1x = 0f            // centre X raquette du bas
    private var p2x = 0f            // centre X raquette du haut
    private var bx = 0f
    private var by = 0f
    private var vx = 0f
    private var vy = 0f
    private var speed = 0f
    private var score1 = 0
    private var score2 = 0
    private var serveTimer = 0f
    private var serveTowardBottom = true
    private var winner = 0
    private var flash1 = 0f         // petit éclat quand une raquette touche
    private var flash2 = 0f
    private var overTimer = 0f      // évite un redémarrage accidentel

    private var lastFrameNs = 0L
    private var running = true

    // --- Pinceaux ---
    private val paintP1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_P1 }
    private val paintP2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_P2 }
    private val paintBall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    private val paintScore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val paintMsg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val paintBtn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val paintSlash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5A5A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var btnR = 0f   // rayon des boutons son (sur la ligne du milieu)

    init {
        setBackgroundColor(COLOR_BG)
    }

    override fun onSizeChanged(newW: Int, newH: Int, oldW: Int, oldH: Int) {
        w = newW.toFloat()
        h = newH.toFloat()
        paddleW = w * 0.24f
        paddleH = h * 0.016f
        paddleMargin = h * 0.07f
        ballR = w * 0.022f
        baseSpeed = h * 0.55f
        maxSpeed = h * 1.7f
        paintLine.strokeWidth = w * 0.006f
        paintLine.pathEffect = DashPathEffect(floatArrayOf(w * 0.03f, w * 0.025f), 0f)
        paintScore.textSize = w * 0.16f
        paintMsg.textSize = w * 0.055f
        btnR = w * 0.055f
        paintIcon.textSize = btnR * 1.1f
        paintSlash.strokeWidth = btnR * 0.14f
        p1x = w / 2
        p2x = w / 2
        resetBall()
    }

    fun pause() {
        running = false
        if (state == State.PLAYING) {
            // On remet la balle au centre pour ne pas pénaliser un joueur
            resetBall()
            state = State.SERVING
            serveTimer = SERVE_DELAY * 2
        }
    }

    fun resume() {
        running = true
        lastFrameNs = 0L
        postInvalidateOnAnimation()
    }

    // ------------------------------------------------------------------
    // Logique
    // ------------------------------------------------------------------

    private fun resetBall() {
        bx = w / 2
        by = h / 2
        vx = 0f
        vy = 0f
        speed = baseSpeed
    }

    private fun serve() {
        resetBall()
        // angle aléatoire entre -30° et 30°
        val angle = (Random.nextFloat() * 2 - 1) * (Math.PI / 6)
        val dir = if (serveTowardBottom) 1f else -1f
        vx = (speed * sin(angle)).toFloat()
        vy = (speed * cos(angle)).toFloat() * dir
        state = State.PLAYING
        sound.play(Sound.Fx.SERVE)
    }

    private fun newGame() {
        score1 = 0
        score2 = 0
        winner = 0
        serveTowardBottom = Random.nextBoolean()
        resetBall()
        state = State.SERVING
        serveTimer = SERVE_DELAY
    }

    private fun update(dt: Float) {
        flash1 = (flash1 - dt * 4).coerceAtLeast(0f)
        flash2 = (flash2 - dt * 4).coerceAtLeast(0f)

        when (state) {
            State.SERVING -> {
                serveTimer -= dt
                if (serveTimer <= 0f) serve()
            }
            State.PLAYING -> {
                // Sous-pas pour éviter que la balle traverse une raquette à grande vitesse
                val dist = speed * dt
                val steps = ceil(dist / (ballR * 0.5f)).toInt().coerceIn(1, 20)
                val sdt = dt / steps
                for (i in 0 until steps) {
                    if (state != State.PLAYING) break
                    step(sdt)
                }
            }
            State.GAME_OVER -> overTimer = (overTimer - dt).coerceAtLeast(0f)
            else -> {}
        }
    }

    private fun step(dt: Float) {
        bx += vx * dt
        by += vy * dt

        // Murs latéraux
        if (bx - ballR < 0f) { bx = ballR; vx = abs(vx); sound.play(Sound.Fx.WALL) }
        if (bx + ballR > w) { bx = w - ballR; vx = -abs(vx); sound.play(Sound.Fx.WALL) }

        // Raquette du bas (joueur 1)
        val p1Top = h - paddleMargin - paddleH
        if (vy > 0 && by + ballR >= p1Top && by - ballR <= p1Top + paddleH &&
            abs(bx - p1x) <= paddleW / 2 + ballR
        ) {
            bounce(p1x, towardTop = true)
            by = p1Top - ballR
            flash1 = 1f
            sound.play(Sound.Fx.HIT1)
        }

        // Raquette du haut (joueur 2)
        val p2Bottom = paddleMargin + paddleH
        if (vy < 0 && by - ballR <= p2Bottom && by + ballR >= paddleMargin &&
            abs(bx - p2x) <= paddleW / 2 + ballR
        ) {
            bounce(p2x, towardTop = false)
            by = p2Bottom + ballR
            flash2 = 1f
            sound.play(Sound.Fx.HIT2)
        }

        // Points
        if (by - ballR > h) pointScored(byPlayer = 2)
        else if (by + ballR < 0f) pointScored(byPlayer = 1)
    }

    /** L'angle de renvoi dépend de l'endroit où la balle touche la raquette. */
    private fun bounce(paddleX: Float, towardTop: Boolean) {
        val offset = ((bx - paddleX) / (paddleW / 2)).coerceIn(-1f, 1f)
        val angle = offset * MAX_ANGLE
        speed = min(speed * SPEEDUP, maxSpeed)
        vx = (speed * sin(angle)).toFloat()
        val vyAbs = (speed * cos(angle)).toFloat()
        vy = if (towardTop) -vyAbs else vyAbs
    }

    private fun pointScored(byPlayer: Int) {
        if (byPlayer == 1) score1++ else score2++
        // Le service part vers celui qui vient de perdre le point
        serveTowardBottom = byPlayer == 2
        resetBall()
        if (score1 >= WIN_SCORE || score2 >= WIN_SCORE) {
            winner = if (score1 > score2) 1 else 2
            overTimer = 1.0f
            state = State.GAME_OVER
            sound.play(Sound.Fx.WIN)
        } else {
            state = State.SERVING
            serveTimer = SERVE_DELAY
            sound.play(Sound.Fx.POINT)
        }
    }

    // ------------------------------------------------------------------
    // Contrôles tactiles
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // Boutons son : musique à gauche, effets à droite (sur la ligne du milieu)
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val i = event.actionIndex
            when (buttonAt(event.getX(i), event.getY(i))) {
                1 -> { sound.toggleMusic(); return true }
                2 -> { sound.toggleSfx(); return true }
            }
        }

        if (action == MotionEvent.ACTION_DOWN &&
            (state == State.WAITING || (state == State.GAME_OVER && overTimer <= 0f))
        ) {
            newGame()
            return true
        }

        if (action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_MOVE
        ) {
            val half = paddleW / 2
            for (i in 0 until event.pointerCount) {
                if (buttonAt(event.getX(i), event.getY(i)) != 0) continue
                val x = event.getX(i).coerceIn(half, w - half)
                if (event.getY(i) > h / 2) p1x = x else p2x = x
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (w == 0f) return

        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1e9f).coerceAtMost(0.05f)
        lastFrameNs = now
        if (running) update(dt)

        // Ligne centrale
        canvas.drawLine(0f, h / 2, w, h / 2, paintLine)

        // Scores (celui du haut est retourné pour le joueur 2)
        paintScore.color = Color.argb(60, Color.red(COLOR_P1), Color.green(COLOR_P1), Color.blue(COLOR_P1))
        canvas.drawText(score1.toString(), w / 2, h * 0.5f + h * 0.13f, paintScore)
        paintScore.color = Color.argb(60, Color.red(COLOR_P2), Color.green(COLOR_P2), Color.blue(COLOR_P2))
        canvas.save()
        canvas.rotate(180f, w / 2, h / 2)
        canvas.drawText(score2.toString(), w / 2, h * 0.5f + h * 0.13f, paintScore)
        canvas.restore()

        // Boutons son
        drawSoundButton(canvas, musicBtnX(), "♪", sound.musicOn)
        drawSoundButton(canvas, sfxBtnX(), "FX", sound.sfxOn)

        // Raquettes
        drawPaddle(canvas, p1x, h - paddleMargin - paddleH, paintP1, COLOR_P1, flash1)
        drawPaddle(canvas, p2x, paddleMargin, paintP2, COLOR_P2, flash2)

        // Balle
        if (state == State.PLAYING || state == State.SERVING) {
            val blink = state == State.SERVING && ((serveTimer * 6).toInt() % 2 == 1)
            if (!blink) {
                paintGlow.color = Color.argb(50, 255, 255, 255)
                canvas.drawCircle(bx, by, ballR * 1.9f, paintGlow)
                canvas.drawCircle(bx, by, ballR, paintBall)
            }
        }

        // Messages (affichés pour chaque joueur dans le bon sens)
        when (state) {
            State.WAITING -> {
                drawMessageBoth(canvas, "PONG DUO", "Touchez l'écran pour commencer")
            }
            State.GAME_OVER -> {
                drawMessageForPlayer(canvas, 1, if (winner == 1) "VICTOIRE !" else "Perdu…", "Touchez pour rejouer")
                drawMessageForPlayer(canvas, 2, if (winner == 2) "VICTOIRE !" else "Perdu…", "Touchez pour rejouer")
            }
            else -> {}
        }

        if (running) postInvalidateOnAnimation()
    }

    private fun musicBtnX() = btnR * 1.5f
    private fun sfxBtnX() = w - btnR * 1.5f

    /** 0 = aucun, 1 = musique, 2 = effets. Zone tactile un peu plus large que le dessin. */
    private fun buttonAt(x: Float, y: Float): Int {
        val r = btnR * 1.5f
        if (abs(y - h / 2) > r) return 0
        if (abs(x - musicBtnX()) <= r) return 1
        if (abs(x - sfxBtnX()) <= r) return 2
        return 0
    }

    private fun drawSoundButton(canvas: Canvas, cx: Float, label: String, on: Boolean) {
        val cy = h / 2
        paintBtn.color = COLOR_BG
        canvas.drawCircle(cx, cy, btnR, paintBtn)
        paintBtn.color = Color.argb(if (on) 70 else 35, 255, 255, 255)
        canvas.drawCircle(cx, cy, btnR, paintBtn)
        paintIcon.alpha = if (on) 255 else 110
        canvas.drawText(label, cx, cy - (paintIcon.descent() + paintIcon.ascent()) / 2, paintIcon)
        if (!on) {
            val d = btnR * 0.6f
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paintSlash)
        }
    }

    private fun drawPaddle(canvas: Canvas, cx: Float, top: Float, paint: Paint, color: Int, flash: Float) {
        rect.set(cx - paddleW / 2, top, cx + paddleW / 2, top + paddleH)
        if (flash > 0f) {
            paintGlow.color = Color.argb((110 * flash).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            val g = paddleH * 1.5f * flash
            canvas.drawRoundRect(
                rect.left - g, rect.top - g, rect.right + g, rect.bottom + g,
                paddleH, paddleH, paintGlow
            )
        }
        canvas.drawRoundRect(rect, paddleH / 2, paddleH / 2, paint)
    }

    private fun drawMessageBoth(canvas: Canvas, title: String, sub: String) {
        drawMessageForPlayer(canvas, 1, title, sub)
        drawMessageForPlayer(canvas, 2, title, sub)
    }

    /** Joueur 1 lit en bas à l'endroit, joueur 2 lit en haut retourné. */
    private fun drawMessageForPlayer(canvas: Canvas, player: Int, title: String, sub: String) {
        canvas.save()
        if (player == 2) canvas.rotate(180f, w / 2, h / 2)
        val y = h * 0.75f
        val size = paintMsg.textSize
        paintMsg.textSize = size * 1.6f
        paintMsg.color = if (player == 1) COLOR_P1 else COLOR_P2
        canvas.drawText(title, w / 2, y, paintMsg)
        paintMsg.textSize = size
        paintMsg.color = Color.WHITE
        canvas.drawText(sub, w / 2, y + size * 1.8f, paintMsg)
        canvas.restore()
    }
}
