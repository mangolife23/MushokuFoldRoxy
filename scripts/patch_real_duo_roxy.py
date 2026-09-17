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

# Enable Duo's debug fold renderer automatically. Samsung's cover->inner display handoff can
# stop the cover-screen Activity without reporting isChangingConfigurations, so preserve the
# most recent cover frame across that lifecycle stop. The next inner-screen Activity can then
# consume it and run the existing cover->inner plane transition. Frames remain memory-only and
# expire quickly; extending the TTL gives the physical display switch enough handoff time.
motion = root / "app/src/debug/java/com/jake/duolauncher/FoldRenderExperiment.kt"
m = motion.read_text()
old_flag = """    private var enabled = false
"""
new_flag = """    private var enabled = true
"""
old_ttl = """    private const val FRAME_TTL_MS = 2_000L
"""
new_ttl = """    private const val FRAME_TTL_MS = 5_000L
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
old_stop = """        override fun onStop(owner: LifecycleOwner) {
            foreground = false
            suspendProbe(removeControls = true)
            if (!activity.isChangingConfigurations) clearFrames()
        }
"""
new_stop = """        override fun onStop(owner: LifecycleOwner) {
            foreground = false
            suspendProbe(removeControls = true)
            // Preserve the latest cover frame across Samsung's cover -> inner display handoff.
            // sourceFor() accepts it only while fresh and only for a differently-sized viewport.
        }
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
    (old_ttl, new_ttl, "fold frame TTL"),
    (old_attach, new_attach, "fold animation attach"),
    (old_stop, new_stop, "fold lifecycle handoff"),
    (old_controls, new_controls, "fold animation controls"),
):
    if m.count(old) != 1:
        raise SystemExit(f"Pinned upstream {label} block changed; refusing unsafe patch")
    m = m.replace(old, new, 1)
motion.write_text(m)

print("Applied deterministic Roxy background + Fold7 unfold handoff patch")
