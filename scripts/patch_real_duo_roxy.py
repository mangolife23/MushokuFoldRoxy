#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2: raise SystemExit("usage: patch_real_duo_roxy.py <duo-source-root>")
root = Path(sys.argv[1])
p = root / "app/src/main/java/com/jake/duolauncher/LauncherBackground.kt"
s = p.read_text()
old_enabled = """internal fun launcherBackgroundEnabled(context: Context) =
    launcherBackgroundPreferences(context).getBoolean(BACKGROUND_ENABLED, false) &&
        launcherBackgroundFile(context).isFile
"""
new_enabled = "internal fun launcherBackgroundEnabled(context: Context) = true\n"
old_identity = """internal fun launcherBackgroundIdentity(context: Context): String? {
    if (!launcherBackgroundEnabled(context)) return null
    return launcherBackgroundPreferences(context).getString(BACKGROUND_ID, null)
        ?: "legacy-${launcherBackgroundFile(context).lastModified()}"
}
"""
new_identity = """internal fun launcherBackgroundIdentity(context: Context): String? {
    val file = launcherBackgroundFile(context)
    val prefs = launcherBackgroundPreferences(context)
    return if (prefs.getBoolean(BACKGROUND_ENABLED, false) && file.isFile)
        prefs.getString(BACKGROUND_ID, null) ?: "legacy-${file.lastModified()}"
    else "roxy-default"
}
"""
old_load = """    val file = launcherBackgroundFile(context)
    val identity = launcherBackgroundIdentity(context)
    LauncherBackgroundCache.bitmap?.let {
        if (!it.isRecycled && LauncherBackgroundCache.identity == identity) return it
    }
    return BitmapFactory.decodeFile(file.absolutePath)
"""
new_load = """    val file = launcherBackgroundFile(context)
    val prefs = launcherBackgroundPreferences(context)
    val identity = launcherBackgroundIdentity(context)
    LauncherBackgroundCache.bitmap?.let {
        if (!it.isRecycled && LauncherBackgroundCache.identity == identity) return it
    }
    return if (prefs.getBoolean(BACKGROUND_ENABLED, false) && file.isFile)
        BitmapFactory.decodeFile(file.absolutePath)
    else BitmapFactory.decodeResource(context.resources, R.drawable.roxy_wallpaper)
"""
for old,new,label in ((old_enabled,new_enabled,"enabled"),(old_identity,new_identity,"identity"),(old_load,new_load,"loader")):
    if s.count(old)!=1: raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    s=s.replace(old,new,1)
p.write_text(s)

# Keep the hardware-validated Fold7 viewport transition and add a real-time Roxy
# mana effects layer over Duo's genuine launcher. The layer never consumes touch.
main = root / "app/src/main/java/com/jake/duolauncher/MainActivity.kt"
m = main.read_text()
old_import = "import android.widget.Toast\n"
new_import = """import android.widget.Toast
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.ViewTreeObserver
"""
old_attach = """        FoldRenderExperiment.attach(this)
        // Reassert the token after recreation"""
new_attach = """        installRoxyViewportTransitionProbe()
        installRoxyManaLayer()
        // Reassert the token after recreation"""
old_marker = """    override fun onStart() {
"""
new_marker = """    private var roxyLastViewportWidth = 0
    private var roxyRevealRunning = false
    private var roxyLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var roxyManaView: RoxyManaView? = null
    private data class RoxyBurst(val x: Float, val y: Float, val born: Long, val seed: Int)

    private fun installRoxyViewportTransitionProbe() {
        val root = window.decorView
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val width = root.width
            if (width <= 0) return@OnGlobalLayoutListener
            val previous = roxyLastViewportWidth
            roxyLastViewportWidth = width
            if (previous <= 0 || roxyRevealRunning) return@OnGlobalLayoutListener
            val density = resources.displayMetrics.density.coerceAtLeast(1f)
            val previousDp = previous / density
            val currentDp = width / density
            if (previousDp < 650f && currentDp >= 650f && width >= previous * 1.35f) {
                playRoxyLiveLauncherReveal()
                roxyManaView?.unfoldBurst()
            }
        }
        roxyLayoutListener = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun installRoxySceneLayer() {
        val host = findViewById<ViewGroup>(android.R.id.content) ?: return
        if (roxySceneView != null) return
        val scene = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        host.addView(scene, 0, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        roxySceneView = scene
        scheduleRoxyScene()
    }

    private fun scheduleRoxyScene() {
        val scene = roxySceneView ?: return
        val names = arrayOf("roxy_wallpaper", "roxy_scene_02", "roxy_scene_03", "roxy_scene_04", "roxy_scene_05")
        val run = object : Runnable {
            override fun run() {
                val id = resources.getIdentifier(names[roxySceneIndex % names.size], "drawable", packageName)
                if (id != 0) {
                    scene.setImageResource(id)
                    scene.scaleX = 1.025f
                    scene.scaleY = 1.025f
                    scene.alpha = 0f
                    scene.animate().alpha(0.42f).scaleX(1f).scaleY(1f).setDuration(1800L).start()
                }
                roxySceneIndex = (roxySceneIndex + 1) % names.size
                scene.postDelayed(this, 30000L)
            }
        }
        roxySceneRunnable = run
        scene.post(run)
    }

    private fun installRoxyManaLayer() {
        val host = findViewById<ViewGroup>(android.R.id.content) ?: return
        if (roxyManaView != null) return
        val mana = RoxyManaView()
        mana.isClickable = false
        mana.isFocusable = false
        host.addView(mana, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        roxyManaView = mana
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_MOVE)
            roxyManaView?.manaTouch(ev.x, ev.y, ev.eventTime)
        return super.dispatchTouchEvent(ev)
    }

    private inner class RoxyManaView : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bursts = ArrayDeque<RoxyBurst>()
        private var lastTouchBurst = 0L
        private var unfoldBorn = 0L

        fun manaTouch(x: Float, y: Float, now: Long) {
            if (now - lastTouchBurst < 38L) return
            lastTouchBurst = now
            bursts.addLast(RoxyBurst(x, y, now, (x.toInt() * 31 + y.toInt()) and 0x7fffffff))
            while (bursts.size > 14) bursts.removeFirst()
            postInvalidateOnAnimation()
        }

        fun unfoldBurst() {
            unfoldBorn = android.os.SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = android.os.SystemClock.uptimeMillis()
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            repeat(22) { i ->
                val phase = ((now * (5L + i % 3) + i * 7919L) % 24000L) / 24000f
                val x = ((i * 0.6180339f + 0.13f) % 1f) * w
                val y = h - phase * (h + 180f)
                val r = 2.5f + (i % 5) * 1.35f
                val shimmer = 0.45f + 0.55f * kotlin.math.sin(phase * 6.283f + i).let { kotlin.math.abs(it) }
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((55 + 85 * shimmer).toInt(), 118, 218, 255)
                canvas.drawCircle(x, y, r, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                paint.color = Color.argb((35 + 55 * shimmer).toInt(), 205, 245, 255)
                canvas.drawCircle(x, y, r + 3.5f, paint)
            }
            val iterator = bursts.iterator()
            val expired = ArrayList<RoxyBurst>()
            while (iterator.hasNext()) {
                val b = iterator.next()
                val age = (now - b.born).coerceAtLeast(0L)
                if (age > 850L) { expired.add(b); continue }
                val t = age / 850f
                val alpha = ((1f - t) * 210).toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f + (1f - t) * 3f
                paint.color = Color.argb(alpha, 120, 225, 255)
                canvas.drawCircle(b.x, b.y, 14f + t * 92f, paint)
                repeat(8) { j ->
                    val angle = j * 0.785398f + t * 2.7f + (b.seed % 17) * 0.05f
                    val radius = 12f + t * (42f + (j % 3) * 12f)
                    val px = b.x + kotlin.math.cos(angle) * radius
                    val py = b.y + kotlin.math.sin(angle) * radius - t * 26f
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb((alpha * 0.9f).toInt(), 175, 238, 255)
                    canvas.drawCircle(px, py, 2.5f + (j % 3), paint)
                }
            }
            expired.forEach { bursts.remove(it) }
            if (unfoldBorn > 0L) {
                val age = now - unfoldBorn
                if (age < 900L) {
                    val t = age / 900f
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 7f * (1f - t) + 1f
                    paint.color = Color.argb(((1f - t) * 150).toInt(), 145, 226, 255)
                    canvas.drawCircle(w / 2f, h / 2f, 40f + t * kotlin.math.max(w, h) * 0.62f, paint)
                } else unfoldBorn = 0L
            }
            if (bursts.isNotEmpty() || unfoldBorn > 0L || isShown) postInvalidateOnAnimation()
        }
    }

    private fun playRoxyLiveLauncherReveal() {
        if (roxyRevealRunning || isFinishing || isDestroyed) return
        val content = findViewById<View>(android.R.id.content) ?: return
        if (content.width <= 0 || content.height <= 0) return
        roxyRevealRunning = true
        content.animate().cancel()
        // Do not scale the live Activity content during Fold7 resize; that produced the
        // hardware-observed half-screen frame. Let Android settle full-size, then fade.
        content.scaleX = 1f
        content.scaleY = 1f
        content.alpha = 0.90f
        content.animate().alpha(1f)
            .setDuration(650L).setInterpolator(DecelerateInterpolator(1.7f))
            .withEndAction {
                content.scaleX = 1f
                content.scaleY = 1f
                content.alpha = 1f
                roxyRevealRunning = false
            }.start()
    }

    override fun onStart() {
"""
old_destroy = """    override fun onDestroy() {
        recreatingShadeSetup = isChangingConfigurations
"""
new_destroy = """    override fun onDestroy() {
        roxyLayoutListener?.let { listener ->
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(listener)
        }
        roxyLayoutListener = null
        roxyManaView = null
        recreatingShadeSetup = isChangingConfigurations
"""
for old,new,label in ((old_import,new_import,"mana imports"),(old_attach,new_attach,"mana attachment"),(old_marker,new_marker,"mana methods"),(old_destroy,new_destroy,"mana cleanup")):
    if m.count(old)!=1: raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    m=m.replace(old,new,1)
main.write_text(m)
print("Applied deterministic Roxy background + Fold7 transition + interactive mana effects")
