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

# Add a direct, deliberately visible Roxy overlay. It is independent of Duo's experimental
# PixelCopy renderer and does not alter Duo's display handoff. On an expanded viewport the exact
# embedded Roxy drawable is placed above the live launcher, starts as a narrow center pane, then
# expands/fades away to reveal normal Duo underneath.
main = root / "app/src/main/java/com/jake/duolauncher/MainActivity.kt"
m = main.read_text()
old_import = "import android.widget.Toast\n"
new_import = """import android.widget.Toast
import android.widget.ImageView
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
"""
old_attach = """        FoldRenderExperiment.attach(this)
        // Reassert the token after recreation"""
new_attach = """        window.decorView.post { playRoxyInnerReveal() }
        // Reassert the token after recreation"""
old_marker = """    override fun onStart() {
"""
new_marker = """    private fun playRoxyInnerReveal() {
        if (resources.configuration.screenWidthDp < 650 || isFinishing || isDestroyed) return
        val root = window.decorView as? ViewGroup ?: return
        val image = ImageView(this).apply {
            setImageResource(R.drawable.roxy_wallpaper)
            scaleType = ImageView.ScaleType.CENTER_CROP
            pivotX = root.width / 2f
            pivotY = root.height / 2f
            scaleX = 0.08f
            alpha = 1f
            elevation = 1000f
        }
        root.addView(image, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        image.animate().scaleX(1f).setDuration(620L)
            .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                image.animate().alpha(0f).setDuration(260L).withEndAction { root.removeView(image) }.start()
            }.start()
    }

    override fun onStart() {
"""
for old,new,label in ((old_import,new_import,"animation imports"),(old_attach,new_attach,"animation attachment"),(old_marker,new_marker,"animation method")):
    if m.count(old)!=1: raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    m=m.replace(old,new,1)
main.write_text(m)
print("Applied deterministic Roxy background + direct inner-screen reveal overlay")
