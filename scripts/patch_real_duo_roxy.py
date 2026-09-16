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
print("Applied deterministic Roxy default-background patch")
