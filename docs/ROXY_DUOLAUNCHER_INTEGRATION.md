# Roxy DuoLauncher Fold Integration

This branch starts from the known-good v0.8 wallpaper baseline and is isolated from `main` and `v0.8-stable`.

## Goal
Create a Roxy-themed Fold launcher experience that preserves scene state while moving from the Galaxy Z Fold cover display to the inner display and masks the display handoff with a coordinated mana animation.

## Source projects
- MushokuFoldRoxy: existing Roxy scene renderer, rotation, mana effects and rotation-vector parallax.
- jakesgoodapps/DuoLauncher: launcher/foldable architecture reference. Preserve its MIT license/copyright when code is incorporated.

## Architecture
1. Keep `RoxyLiveWallpaperService` as an optional wallpaper mode.
2. Add a launcher-owned Roxy background renderer so home-screen continuity does not depend on Samsung forwarding one WallpaperService engine from cover to inner display.
3. Store active scene, scene start time and animation phase in shared state.
4. Detect launcher window size/fold-state changes and render the same scene with separate cover/inner focal framing.
5. During a cover-to-inner transition, play a short mana expansion/reveal animation while the new layout becomes active.
6. Continue rotation-vector parallax after the inner layout is active.
7. Keep only current/next display-sized images resident and preload the next scene off the UI/render thread.

## First hardware milestone
- Set the Roxy launcher as Home.
- Closed Fold: Roxy scene + launcher UI + motion effects.
- Open Fold without reapplying anything.
- Inner display: same Roxy scene appears immediately, animation continues, and the launcher switches to its unfolded layout.
- Close again: state returns to the cover layout without resetting the scene.

## Transition target
Cover Roxy -> mana buildup -> fold/window change -> expanding magic circle + reframing -> same Roxy scene on inner display -> particles/parallax settle.

The app cannot replace Samsung's system hinge animation; this transition is rendered inside the launcher/background surfaces to create visual continuity around it.
