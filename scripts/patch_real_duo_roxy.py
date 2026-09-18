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

# Preserve the hardware-validated live narrow -> expanded viewport detector, but animate
# Duo's existing launcher/window presentation instead of adding a duplicate Roxy ImageView.
main = root / "app/src/main/java/com/jake/duolauncher/MainActivity.kt"
m = main.read_text()
old_import = "import android.widget.Toast\n"
new_import = """import android.widget.Toast
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.ViewTreeObserver
"""
old_attach = """        FoldRenderExperiment.attach(this)
        // Reassert the token after recreation"""
new_attach = """        installRoxyViewportTransitionProbe()
        // Reassert the token after recreation"""
old_marker = """    override fun onStart() {
"""
new_marker = """    private var roxyLastViewportWidth = 0
    private var roxyRevealRunning = false
    private var roxyLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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
            if (previousDp < 650f && currentDp >= 650f && width >= previous * 1.35f)
                playRoxyLiveLauncherReveal()
        }
        roxyLayoutListener = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun playRoxyLiveLauncherReveal() {
        if (roxyRevealRunning || isFinishing || isDestroyed) return
        val content = findViewById<View>(android.R.id.content) ?: return
        if (content.width <= 0 || content.height <= 0) return
        roxyRevealRunning = true
        content.animate().cancel()
        content.pivotX = content.width / 2f
        content.pivotY = content.height / 2f
        content.scaleX = 0.82f
        content.scaleY = 0.96f
        content.alpha = 0.72f
        content.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator(1.7f))
            .withEndAction {
                content.scaleX = 1f
                content.scaleY = 1f
                content.alpha = 1f
                roxyRevealRunning = false
            }
            .start()
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
        recreatingShadeSetup = isChangingConfigurations
"""
for old,new,label in ((old_import,new_import,"viewport imports"),(old_attach,new_attach,"viewport attachment"),(old_marker,new_marker,"viewport method"),(old_destroy,new_destroy,"viewport cleanup")):
    if m.count(old)!=1: raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    m=m.replace(old,new,1)
main.write_text(m)
print("Applied deterministic Roxy background + live Duo launcher Fold7 expansion animation")
