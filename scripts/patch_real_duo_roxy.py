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
        ?: \"legacy-${launcherBackgroundFile(context).lastModified()}\"
}
"""
new_identity = """internal fun launcherBackgroundIdentity(context: Context): String? {
    val file = launcherBackgroundFile(context)
    val prefs = launcherBackgroundPreferences(context)
    return if (prefs.getBoolean(BACKGROUND_ENABLED, false) && file.isFile)
        prefs.getString(BACKGROUND_ID, null) ?: \"legacy-${file.lastModified()}\"
    else \"roxy-default\"
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

# Diagnostic proof build: do not use Duo's PixelCopy experiment. On any expanded inner viewport,
# put the exact Roxy drawable above the live launcher as a visibly narrow center pane, hold it long
# enough to be unmistakable, expand it across the entire display for 2.5 seconds, hold full-screen,
# then fade it away. This intentionally favors proof of execution over polish and leaves Duo's
# already-working display handoff untouched.
main = root / "app/src/main/java/com/jake/duolauncher/MainActivity.kt"
m = main.read_text()
old_import = "import android.widget.Toast\n"
new_import = """import android.widget.Toast
import android.widget.ImageView
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
"""
old_attach = """        FoldRenderExperiment.attach(this)
        // Reassert the token after recreation"""
new_attach = """        window.decorView.postDelayed({ playRoxyDiagnosticReveal() }, 350L)
        // Reassert the token after recreation"""
old_marker = """    override fun onStart() {
"""
new_marker = """    private fun playRoxyDiagnosticReveal() {
        if (resources.configuration.screenWidthDp < 650 || isFinishing || isDestroyed) return
        val root = window.decorView as? ViewGroup ?: return
        if (root.width <= 0 || root.height <= 0) {
            root.postDelayed({ playRoxyDiagnosticReveal() }, 150L)
            return
        }
        val image = ImageView(this).apply {
            setImageResource(R.drawable.roxy_wallpaper)
            scaleType = ImageView.ScaleType.CENTER_CROP
            pivotX = root.width / 2f
            pivotY = root.height / 2f
            scaleX = 0.04f
            scaleY = 1f
            alpha = 1f
            elevation = 1000f
            contentDescription = \"Roxy diagnostic reveal\"
        }
        root.addView(image, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Hold the 4%-wide pane for 700 ms so the starting state cannot be missed.
        image.postDelayed({
            if (image.parent == null) return@postDelayed
            image.animate().scaleX(1f).setDuration(2500L).setInterpolator(LinearInterpolator()).withEndAction {
                // Hold full-screen Roxy for another second before restoring normal Duo.
                image.postDelayed({
                    if (image.parent == null) return@postDelayed
                    image.animate().alpha(0f).setDuration(500L).withEndAction { root.removeView(image) }.start()
                }, 1000L)
            }.start()
        }, 700L)
    }

    override fun onStart() {
"""
for old,new,label in ((old_import,new_import,"diagnostic imports"),(old_attach,new_attach,"diagnostic attachment"),(old_marker,new_marker,"diagnostic method")):
    if m.count(old)!=1: raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    m=m.replace(old,new,1)
main.write_text(m)
print("Applied deterministic Roxy background + unmistakable 2.5s inner-screen diagnostic reveal")
