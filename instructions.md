# Nice Catch — How Fishing Works

A cast-to-catch walkthrough of every phase, what your inputs do, and why.

## 1. Casting

Hold **right-click** with a rod while aiming at nothing. The bar above your hotbar sweeps
up and down — **release to cast**, and the bar's position at that moment sets the cast
distance (a quick tap flicks the bobber at your feet; releasing at the top launches it far).

## 2. Attracting fish

Real fish notice a bobber from ~12 blocks (further with Lure) and swim over to circle
beneath it. Fish that see other fish heading over may tag along. If nothing lives in the
water, you're loot-fishing: vanilla wait times apply and you can pull up junk or treasure
(enchanted books etc.) — **but never fish items**. Fish only come from actual fish.

Each fish tracks its own **interest**: every time it notices a bobber its curiosity grows
(faster with Aquaculture bait), and interested fish approach sooner and bite far more often.
Getting spooked knocks interest back down.

Keep still! Moving or swinging within a few blocks of fish scares them off, as does
swimming near them or hurting one. Startled fish sit out briefly; genuinely hurt or
escaped fish avoid biting much longer.

## 3. The bite

- **A tease** — sometimes the bobber dips a couple of times with soft bubbles but nothing
  is there to hook: a fish is just nibbling. Hold your nerve; a nibble occasionally turns
  into the real thing (and makes that fish more interested).
- **A real fish bites** — you'll first see the vanilla V-shaped wake streaking toward your
  bobber as the fish closes in. Then the bobber tugs, bubbles rise, the bar flashes **red**:
  **right-click once to set the hook** before the window (~2.5 s) closes, or the fish
  spooks and flees. Setting the hook starts the fight (section 4).
- **A loot nibble** (no fish around) — the bar flashes **gold**: right-click to reel it in
  instantly, exactly like vanilla. Items don't fight back.

## 4. The fight (reel mode)

You're now locked on: mouse movement drives the rod, not the camera.

**Your two inputs, both while holding right-click:**

| Motion | Meaning | Effect |
|---|---|---|
| **Circle the mouse** (either direction) | Cranking the reel | Main source of catch progress; also pulls the fish toward you |
| **Pull the mouse straight up** | Lifting the rod | During a **run**: braces against the fish and cancels most of its pull. While **calm**: a small "pumping the rod" progress bonus |

Yes, upward pulls reel a little on purpose — that's the rod-pump. The crank is still where
most of your progress comes from. (Straight back-and-forth shaking is rejected; only real
circular motion cranks.)

**The fish alternates between two states:**

- **Calm** — crank away. Progress climbs, tension drains.
- **Running** (bar shakes, splashing, camera rumbles) — the fish is sprinting away.
  Cranking now barely helps and **builds tension fast**. Ease off the crank and **lift**
  to hold your ground until the run ends.

**The bar shows progress; its color shows tension** (green → gold → red). Tension maxes
out → **the line snaps**: bobber, fish, and some rod durability gone. Stop cranking and
tension recovers.

**Letting go of right-click** gives slack: tension drains fast, but progress bleeds away —
and a running fish takes back much more. If progress hits zero, the fish throws the hook.

**Landing**: even at full progress the fish isn't yours until you've cranked it **close to
you** (~5 blocks). "It's worn out — crank it in close!" means exactly that. Then the fish
launches out of the water and becomes its item in your inventory — bigger fish (by actual
hitbox size) fight harder the whole way.

## 5. Catch and release

For **one minute** after a catch, tossing that fish item (Q) back into water revives the
very same fish, which darts off scared. After the minute it's just an item.

## Gear that helps

- **Lure** — fish notice the bobber from further away and bite more often.
- **Luck of the Sea** — bites skew toward the biggest fish around; better treasure when loot-fishing.

### Aquaculture (optional, full integration)

- **Bait** — graded by its lure speed: better bait builds fish interest faster and draws
  more bites while equipped.
- **Hooks** — better hooks convert nibbles into real bites far more often, ease tension
  build-up, carry their own luck bonus (gold, nether star) into bite selection and
  treasure, use their real double-catch chance, and can spare the rod's durability on a
  catch (diamond and friends).
- **Fishing line** — with a line equipped, tension builds much slower: the line snaps
  far later.
- **Rod tier** — higher-tier rods (iron → neptunium) reel more effectively and add extra
  snap protection on top of everything above.

Almost every number above lives in `nicecatch-server.toml` / `-client.toml`.
