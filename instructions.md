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

Keep still! Moving or swinging within a few blocks of fish scares them off, as does
swimming near them or hurting one. A spooked fish bolts and won't bite again for a while.

## 3. The bite

- **A real fish bites** — the bobber tugs, bubbles rise, the bar flashes **red**:
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
- **Aquaculture bait** — more bites while equipped.
- **Aquaculture hooks** — tension builds slower (heavier hooks = much slower), so the line
  snaps later. The double hook sometimes doubles the caught item.

Almost every number above lives in `nicecatch-server.toml` / `-client.toml`.
