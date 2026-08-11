# 🎣 Nice Catch

NOTICE: This mod is in Beta. There will be many bugs! Please report all bugs on the issue tracker!

## Overview
Nice Catch turns fishing into an ecosystem. There is no minigame screen and no invisible loot table pretending to be a fish: real fish live in the water, notice your bobber, work up the nerve to bite, and fight back on the line with real 3D physics. Every individual fish has its own size, weight, and personality (some are born feisty, some cosmic), and when you finally land the monster, you carry it in both arms for everyone to see. Rod, net, trap, or a fishing line tied to an arrow: pick your method.

## Features

### The Cast & The Fight
- 🎯 **Charge Casting:** Tap right-click for a flick cast at your feet, or hold it and release on the sweeping power bar to send the bobber flying well past vanilla range.
- ⚡ **Directional Hook-Set:** When a fish bites, the HUD tells you which way to strike: grip the rod (hold right-click) and **yank LEFT or RIGHT** inside the reaction window. Yank the wrong way and the hook pulls straight out of its mouth.
- 🌀 **The Reel Is Your Mouse:** Spin the mouse in circles to crank (shaking back and forth won't fool it), pull straight up to lift the rod. Holding the button alone reels nothing; the crank is the reel. (An accessibility option restores hold-to-reel.)
- 🐟 **Five Fighting Tactics:** A hooked fish cycles through holding, thrashing, boring away, sounding for the bottom, and charging straight back at you, and the HUD coaches the right response to each. Line tension climbs green → gold → red; max it out and the line snaps.
- 💪 **Stamina War:** Every crank tires the fish and every run burns its reserves, but slack lets it rest. Fish weight sets the depth of those reserves: under 5 lbs comes in easy, 10-15 lbs is a genuine battle, and past 15 lbs you are in for a long war.
- 🧭 **True 3D Reeling:** A fish below you is hauled upward, a diagonal fish moves along the diagonal, and fights are winnable from cliffs, bridges, and boat decks. Deep divers must genuinely be lifted before they can be landed.
- 🎥 **Cinematic Feel:** The camera locks on and tracks the fish, the rod bends with tension, judders through runs, whips on the cast, and rises as you lift, plus reel clicks, line stripping off the spool, and the rod creaking near the snapping point.

### Living Fish
- 🧠 **Curiosity, Not Magnetism:** Fish accumulate interest in your bobber over time: some investigate at once, some circle warily, some ignore it and come around on a later cast. Chumming the water with food genuinely improves a spot.
- 🐠 **Species Personalities:** Boldness, curiosity, bite eagerness, fight strength, stamina, preferred depth, and cover affinity are per-species profiles (fully config-overridable). Little schoolers dart about in open water; big wary fish lurk in kelp and seagrass.
- 🦈 **Predators Hunt:** Predatory species chase down and eat much smaller fish. Prey flees, schools scatter, and the water settles again. Fish habituate: a crowded pond calms down instead of thrashing itself frantic forever.
- 🤫 **Fieldcraft:** Sneaking makes you invisible to fish on land. Swimming straight at them sends them bolting, every time. Boats, splashing, and sword swings all spook the water.
- 🌨️ **Environment Matters:** Snowy-cold water makes fish sluggish and slow to bite. Fish idle organically (cruising, loitering, drifting between depths, hiding in vegetation) and pitch their bodies up and down as they swim.
- 🐡 **Schools That Feel Real:** Fish spawn in proper shoals (boids schooling included), and a supplemental spawner keeps the water around you naturally stocked without touching mob caps or other mobs' spawning.

### Every Fish Is Different
- ⚖️ **Weight & Size:** Every fish, modded ones included, rolls its own body size, hitbox and model both. Weight comes from real body volume, shows on the catch line and the item tooltip (lbs by default, kg configurable), and smaller salmon genuinely are lighter, easier fights.
- ✨ **25+ Born Traits:** Feisty fish thrash harder, Tough ones strain the line, Slippery ones spit the hook faster, Cunning ones love the charge, Lucky ones pay bonus XP, and the rare aura fish (Verdant, Glimmering, Molten, Ghostly, Abyssal, Cosmic, Ancient) visibly shimmer in the water so you know exactly which one to cast at. Trait fish gleam with the enchantment glint, and traits are named in color on the tooltip. The system is modular: a new trait is one line of registration.
- 🏋️ **Show Off the Catch:** A held fish renders as the actual fish, in first and third person. A small one dangles by the tail from your outstretched hand; an 8+ pounder is hefted across both arms, scaled to the real individual you caught.
- 🔄 **Catch & Release:** Toss a fresh catch back into water and the exact same fish swims away: same size, same weight, same traits.

### More Ways To Fish
- 🥅 **Fishing Net:** Sweep it through the shallows to scoop small fish by surprise. Approach from behind (or while they're eyeing someone's bobber), because a fish that sees the sweep coming evades, and trophies burst straight out of the mesh. **Teamwork:** netting a fish another player has hooked lands the catch for them instantly.
- 🪤 **Fish Trap:** A passive, placeable trap with a real inventory: bait slot on the left (any food, or Aquaculture worms and leeches), the haul on the right. Bait draws fish over to nose around it, which is exactly how they get caught. Deliberately slow; it will never outfish a rod.
- 🏹 **Fishing Line Arrow:** An arrow with line attached, fired from any bow. Strike a fish and the reel fight begins at range, and a speared fish tires fast and never recovers on slack.
- 🚤 **Boat Trolling:** Line pays out audibly as you drift away from the bobber, and at the end of the spool the bobber trails behind your boat instead of snapping back. Fish can chase and bite a moving lure.
- 🧍 **Hooked a Player?** They're told on screen how to escape: digging in (sneaking) anchors them against the drag, and holding it breaks the line. Hooked mobs get reeled with real physics (on land, up cliffs, over ledges), and the bigger the animal, the harder it drags.

### Quality of Life
- 🐛 **No Fake Fish:** Fish items only come from actual fish. Fishless water still yields junk and treasure, vanilla-style.
- 🚪 **Vanilla-Friendly:** Right-clicking doors, chests, and villagers while holding a rod works exactly like vanilla. Rods and bobbers are never replaced.
- 🧗 **Honest Physics:** The reeled bobber climbs up and over terrain instead of phasing through it, and the hook-set, bites, and fights are fully server-authoritative for multiplayer.
- 📊 **Trophy Text:** The catch line announces name, weight, and traits the moment you land it.

## Configuration
Nearly everything is tunable in the server config: cast power, bite windows and the directional hook-set, every fight number (line length, tension, fatigue, weight-endurance curve), fish AI (interest, scare ranges, schooling, habitat, predation, food attraction, cold-biome bite rates), spawning (school sizes, supplemental spawner targets), size variance and the trait lottery, net and trap balance, and per-species profiles as simple config strings (`minecraft:salmon=boldness:0.45,strength:1.2,predator:true,...`), including per-species size bands, which is how Aquaculture's tiny tuna grows to trophy size. The client config covers reel sensitivity, camera follow and shake, FOV zoom, rod animations, fish carrying, HUD hints, the hook-set yank threshold, and `requireCircularMotion` (accessibility: reel by holding instead of circling).

## Compatibility
Nice Catch never replaces rods, fish, or bobbers. It intercepts the click, invokes the rod's own `use()` logic, and steers the resulting `FishingHook`, so **any rod** providing the `fishing_rod_cast` tool action just works, and any modded fish can be whitelisted into the full AI with a config entry (Unusual Fish Mod and Upgrade Aquatic are wired up out of the box, sharks and pike as predators included).

**Aquaculture 2** gets deep, fully optional integration: bait speeds up interest and bites, hooks change nibble conversion and line tension (the double hook meaningfully doubles catches), fishing line delays the snap, higher rod tiers reel harder, worms and leeches bait the fish trap, and fish mounts keep a mounted fish's aura shimmering, show its weight and traits on hover, and display it at its true caught size. None of it is required; the mod runs fine without Aquaculture installed.

## Docs
- `instructions.md` walks through the whole reel game if you're lost on the controls.
- `needed_textures.md` lists the HUD art that's still placeholder colored bars.
- `changelog.txt` tracks everything in Keep-a-Changelog format.

🎣 Tight lines!

## License & Use
This project, Nice Catch, is licensed under the GNU General Public License v3.0 (GPLv3). This means:

* You may copy, distribute, and modify this software as long as any derivative work is also licensed under the GPLv3.
* Any software that incorporates code from this project must also be released under the GPLv3.
* The source code must be made available with any distributed version of this software or any derivatives.
