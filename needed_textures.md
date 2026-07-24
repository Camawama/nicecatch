# Needed Textures

The HUD currently draws everything with flat colored quads over the vanilla XP-bar background
(`textures/gui/icons.png`, the 182×5 bar sprites). These are the custom textures that would
replace the placeholders. All paths are under `src/main/resources/assets/nicecatch/textures/gui/`.

Bars are drawn at vanilla GUI scale, so keep the exact pixel sizes below (no need for
higher-res unless you also scale the blits).

## Bars (182×5 px each, PNG with transparency)

| File | Purpose | Notes |
|---|---|---|
| `bar_background.png` | Empty bar frame shared by all phases | Replaces the vanilla XP bar background. Slightly darker/rope-and-wood look would fit the fishing theme. |
| `bar_cast.png` | Fill for the cast power sweep | Currently flat cyan `#3CB4E7`. Drawn left→right, cropped by power; design so any left-crop still looks good (no big right-side motif). |
| `bar_bite.png` | Full-bar flash while a fish is biting | Currently flat red `#E03434`, flashes on/off every 4 ticks. |
| `bar_fight_low.png` | Fight progress fill, relaxed line | Currently lime `#7EE044`. |
| `bar_fight_mid.png` | Fight progress fill, medium tension | Currently gold `#E8C33A`. The code cross-fades between the three tension colors; with textures we'd swap at thresholds instead, so make the three read clearly at a glance. |
| `bar_fight_high.png` | Fight progress fill, near-snap tension | Currently red `#E03434`, flashes brighter above 80% tension. |
| `bar_caught.png` | Full green flourish after landing a fish | Shown ~1 second. Could sparkle. |

## Icons (drawn beside/above the bar)

| File | Size | Purpose |
|---|---|---|
| `icon_bite.png` | 16×16 | Exclamation/bobber icon popped above the bar during a bite (would replace or accompany the flashing text hint). |
| `icon_run.png` | 16×16 | "Fish running" indicator (e.g. fish with motion lines) shown during runs. |
| `icon_tension.png` | 16×16 | Warning icon for near-snap tension. |
| `icon_spin.png` | 16×16 | Little circular-arrow glyph used by the reel hint so the motion is obvious without words. |

## Item textures (optional)

| File | Size | Purpose |
|---|---|---|
| `../item/line_arrow.png` | 16×16 | The Fishing Line Arrow item. Currently the item model just reuses the vanilla arrow sprite (`minecraft:item/arrow`), so it works with no custom art. To use a custom sprite, drop this PNG under `assets/nicecatch/textures/item/line_arrow.png` and point `models/item/line_arrow.json` `layer0` at `nicecatch:item/line_arrow`. |

The flying arrow entity also just reuses the vanilla arrow entity texture, so no entity texture
is needed unless you want a distinct look.

## Nice-to-have (not wired up yet)

| File | Size | Purpose |
|---|---|---|
| `bar_tension_marker.png` | 6×9 | A small notch/arrow riding on top of the bar to show tension as a position instead of a color (accessibility: color-blind friendly). |
| `fish_size_pips.png` | 3 sprites, 7×7 | Small/medium/large fish pips; could show next to the bar once a fish is hooked so you know what you're fighting. |

When any of these land in the repo, ping me and I'll switch `FishingBarOverlay` from
`graphics.fill(...)` placeholders to `graphics.blit(...)` with these sprites.
