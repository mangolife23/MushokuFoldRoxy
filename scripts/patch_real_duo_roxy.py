#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_real_duo_roxy.py <duo-source-root>")

root = Path(sys.argv[1])
p = root / "app/src/main/java/com/jake/duolauncher/LauncherBackground.kt"
s = p.read_text()

old_enabled = """internal fun launcherBackgroundEnabled(context: Context) =
    launcherBackgroundPreferences(context).getBoolean(BACKGROUND_ENABLED, false) &&
        launcherBackgroundFile(context).isFile
"""
new_enabled = """internal fun launcherBackgroundEnabled(context: Context) = true
"""

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

for old, new, label in (
    (old_enabled, new_enabled, "enabled"),
    (old_identity, new_identity, "identity"),
    (old_load, new_load, "loader"),
):
    if s.count(old) != 1:
        raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    s = s.replace(old, new, 1)

p.write_text(s)

# The pinned Duo source already contains its fold snapshot/plane animation lab in the debug
# source set. Alpha APKs are debug builds, so enable that existing implementation by default
# and remove its developer controls. This keeps the animation implementation tied to the exact
# pinned Duo source rather than replacing it with a separate approximation.
motion = root / "app/src/debug/java/com/jake/duolauncher/FoldRenderExperiment.kt"
m = motion.read_text()
old_flag = """    private var enabled = false
"""
new_flag = """    private var enabled = true
"""
old_attach = """    fun attach(activity: MainActivity) {
        if (activity.intent.getBooleanExtra(EXTRA, false)) {
            enabled = true
            Log.i(TAG, \"enabled from create intent\")
        }
        Controller(activity).also {
"""
new_attach = """    fun attach(activity: MainActivity) {
        enabled = true
        Controller(activity).also {
"""
old_controls = """        private fun addControls() {
            if (controls != null) return
            val pad = (10 * activity.resources.displayMetrics.density).toInt()
"""
new_controls = """        private fun addControls() {
            // Roxy Alpha runs Duo's fold renderer automatically; no developer overlay.
            return
            @Suppress(\"UNREACHABLE_CODE\")
            if (controls != null) return
            val pad = (10 * activity.resources.displayMetrics.density).toInt()
"""
for old, new, label in (
    (old_flag, new_flag, "fold animation enabled flag"),
    (old_attach, new_attach, "fold animation attach"),
    (old_controls, new_controls, "fold animation controls"),
):
    if m.count(old) != 1:
        raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    m = m.replace(old, new, 1)
motion.write_text(m)

print("Applied deterministic Roxy background + Duo fold-animation patch")
