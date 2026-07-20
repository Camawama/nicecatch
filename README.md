# Nice Catch

A fishing overhaul for Forge 1.20.1. No minigame screen. You cast, real fish in the water
notice your bobber and swim over, and you reel them in with the mouse.

## How it plays

### Casting

Tap right click for a short flick cast right in front of you. Hold right click and a power
bar (in the XP bar slot, like the horse jump bar) sweeps up and down. Release at any point
to cast at that strength. A fully charged cast flies well past vanilla range.

### Fish actually swim over

Nearby fish notice your bobber and swim in to check it out, and fish that see a school mate
heading for the bobber will sometimes tag along too. The more curious a fish gets about your
bait, the more likely it is to bite. Aquaculture bait speeds that up.

Fish aren't dumb about danger either. Swim near a group and they scatter, fast, and the whole
school panics with them. Swing a sword at one and good luck, they're gone before the hit
lands. That's on purpose: melee fishing should be miserable and the rod should be the answer.

### The bite

Sometimes a fish just tugs the bobber a couple times without actually taking it, a plain
tease. When it does commit, you'll see the vanilla style wake trailing toward the bobber
first, then the bar flashes red and you get a window to set the hook.

### The fight

Hold right click to set the hook. Your view locks onto the water and zooms in slightly, the
mouse now works the reel instead of the camera.

Spin the mouse in circles to crank the reel. Direction doesn't matter, but shaking it back
and forth won't fool it. Pulling the mouse straight up braces the rod against a running fish.

The fish alternates between resting and running, you'll see it splash and drag the bobber
along with it. Cranking hard during a run builds line tension (the bar goes green to gold to
red). Max tension snaps the line and you lose the fish, the bobber, and some rod durability.

Bigger fish fight harder, and the difficulty ramps up fast even for small size differences.
Landing the fish also isn't just about filling the bar, you need to actually reel it in close
before it counts as caught.

### The catch

No fish item pops out of thin air. You reel the actual fish in and it gets pulled toward you,
then quietly becomes its item right as it clears the water. No death animation, it just looks
like the fish went in your bag. If you catch one and change your mind, toss the item back into
water within a minute and that exact fish comes back to life and swims off.

Fishing in water with no fish still works, you'll just pull up junk and treasure instead of
fish, same as casting into a puddle in vanilla.

Retrieving an empty bobber is still an instant right click, and right clicking doors, chests,
and villagers while holding a rod works exactly like vanilla.

## Compatibility

Nice Catch never replaces rods or bobbers. It intercepts the click, then invokes the rod's own
`use()` logic and steers the resulting `FishingHook` entity, so any rod that provides the
`fishing_rod_cast` tool action just works.

Aquaculture 2 gets full optional support if it's installed. Bait speeds up fish interest and
bite chance, hooks change how easily a nibble turns into a bite and how much tension the line
can take, fishing line makes the line snap much later, and higher tier rods reel more
effectively. None of it is required, the mod runs fine with or without Aquaculture.

## Docs

- `instructions.md` walks through the whole reel game if you're lost on the controls.
- `needed_textures.md` lists the HUD art that's still placeholder colored bars.

## Config

- `nicecatch-server.toml`: casting, bite windows, fight difficulty, fish AI (interest, scare
  ranges, scatter), entity catching, loot, Aquaculture tuning.
- `nicecatch-client.toml`: charge bar speed, reel sensitivity, FOV zoom, camera shake, hints,
  reel click sounds, and `requireCircularMotion` (turn off to reel by just holding right click,
  an accessibility option).

## License

[GPL-3.0](LICENSE)
