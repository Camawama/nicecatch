# Nice Catch

A vanilla-plus fishing overhaul for Forge 1.20.1. No minigame screens — just the rod, your mouse,
and the fish.

## How it plays

**Casting**
- Tap right-click for a short flick cast right in front of you.
- Hold right-click and a power bar (cyan, in the XP bar slot — like the horse jump bar) sweeps up
  and down. Release at any point to cast at that strength. Fully charged casts fly well past
  vanilla range.

**The bite**
- When a fish grabs the line, the bar flashes red and you hear a ding. You have a few seconds
  (longer than vanilla, configurable) to grab the rod.

**The fight**
- Hold right-click to set the hook. Your view locks onto the water and zooms in slightly —
  the mouse now works the reel instead of the camera.
- **Spin the mouse in circles to crank the reel.** Direction doesn't matter, but shaking it back
  and forth won't fool it.
- The fish alternates between resting and **running** — you'll see it splash, drag the bobber,
  and feel the rod shake. Cranking hard during a run builds **line tension** (the bar shifts
  green → gold → red). Max tension snaps the line: fish, bobber, and a chunk of rod durability gone.
- **Pull the mouse upward** to lift the rod against a run — it won't gain you much line, but it
  stops the fish from taking it back.
- Release right-click and the fish pulls line back out; lose it all and the fish escapes.
- Fill the bar to land the catch. Strong fish fight longer and pay a little bonus XP.

Retrieving an empty bobber (no bite) is still an instant right-click, and right-clicking doors,
chests, and villagers while holding a rod works exactly like vanilla.

## Compatibility

Nice Catch never replaces rods or bobbers. It intercepts the click, then invokes the rod's own
`use()` logic and steers the resulting `FishingHook` entity — so modded rods (Aquaculture 2, etc.),
their custom bobbers, bait, and loot all keep working, with any rod that provides the
`fishing_rod_cast` tool action.

## Config

- `nicecatch-server.toml` — cast power range, bite window, fight difficulty, tension/line-snap,
  fish strength, bonus XP.
- `nicecatch-client.toml` — charge bar speed, reel sensitivity, FOV zoom, camera shake, reel
  click sounds, and `requireCircularMotion` (turn off to reel by simply holding right-click —
  accessibility option).

## License

[All Rights Reserved](LICENSE.txt)
