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
- **A loot nibble** (no fish around) — the bar flashes **gold**: **hold right-click and reel**
  to bring the item in. There's no fight and the line can't snap, so reel at full speed (a
  touch slower than an empty line). See *Reeling in without a fish* below.

## 4. The fight (reel mode)

You're now locked on: mouse movement drives the rod, not the camera — while you hold the
line, the camera pans by itself to keep the fish in frame.

**The bar is your line.** Full means the fish has been hauled to your feet; empty means
it has taken every block of line off the reel — and **rips free**. Setting the hook always
triggers a panicked first run, so expect the bar to drop before you can gain an inch.

**Your two inputs, both while holding right-click:**

| Motion | Meaning | Effect |
|---|---|---|
| **Circle the mouse** (either direction) | Cranking the reel | Physically drags the fish toward you, retrieving line — and wears the fish down |
| **Pull the mouse straight up** | Lifting the rod | Lifts a **sounding** (diving) fish so it can be reeled at all, braces against other runs so they take less line, and "pumps the rod" for extra reel-in speed while the fish holds |

(Straight back-and-forth shaking is rejected; only real circular motion cranks.)

**The fish cycles through five tactics** — the hint text names the one it's using, so you
always know the right response:

- **Holding** — it sits still. Your clean window: reel it straight in.
- **Thrashing** — it whips from side to side. Keep reeling; it fights the crank a little and
  the camera swings along with it.
- **Pulling** (bar shakes, splashing, camera rumbles) — it bores straight away, tearing off
  line. You **can't out-crank it**: ease off, keep some line, and let it wear itself out.
- **Sounding** — it dives for the bottom. Cranking barely budges a sounding fish — **pull the
  rod up** to lift it back toward the surface first, then reel.
- **Charging** — it rushes straight back at you, dumping slack. **Crank fast** to take the
  slack up; if you don't answer a charge, the fish gets to rest and **recover its fight**.

Cranking through any run (pull/sound/charge) still winches only a fraction and **builds
tension fast**, so pick your moments.

**Fatigue is the real war.** Every crank and every run drains the fish's stamina: fresh
fish rip back most of what you gain, but their runs get shorter, rarer, and weaker as they
tire. Big fish have far deeper reserves. When you see "It's worn out — reel it home!", the
fight is yours to lose. Giving slack lets the fish **rest and recover** — don't.

**The bar's color shows tension** (green → gold → red). Tension maxes out → **the line
snaps**: bobber, fish, and some rod durability gone. Stop cranking and tension recovers.

**Letting go of right-click** gives slack: tension drains fast, but you stop gaining line,
the fish recovers stamina, and during a run it keeps tearing line off. Let that go on and it
spools you. Slack is a breather for the fish, not for you.

**Landing**: crank the fish all the way in (~5 blocks) while it's holding or thrashing (not
mid-run) and it launches out of the water, becoming its item in your inventory — bigger fish
(by actual hitbox size) pull harder and last longer the whole way.

## 4b. Reeling in without a fish

Right-click no longer snaps the line straight back. Whether the hook is empty or has a loot
item on it, **hold right-click and reel** — the same reel mode as a fight (camera locks to
the bobber, slight zoom), just with no tension and no snapping:

- **Empty line** — reel at full speed. Circle the mouse to reel faster; simply holding reels
  at a steady baseline.
- **Loot item** (the gold bite) — a little slower, but there's no fight and it can't get
  away once you're reeling it. Reel it home to collect it.

Let go and the reel-in pauses (the camera pans back); hold again to resume from where the
bobber is. Reel it to within a couple of blocks and it's fully retrieved.

**Boat / walking away**: as you move away from a cast bobber the reel pays out line. Once you
reach the end of the spool (~30 blocks) it **drags the bobber along behind you** instead of
the line retracting — so trolling a lure behind a moving boat works. (A moving boat still
spooks nearby fish, so they may scatter.)

## 4c. The fishing-line arrow

Craft a **Fishing Line Arrow** from an arrow + string, and fire it from any bow. If it strikes
a fish, the line takes hold and the reel fight starts for that fish — you'll see the line
running from you out to it. Reel it in exactly like a rod fight (hold right-click and circle
the mouse; your bow won't draw while you're reeling). A fish landed with the arrow is **not**
added to catch-and-release, so its item can't be tossed back to revive it. Hit the fish from
too far away (beyond the arrow's line length) and the line won't take — it just behaves like a
normal arrow.

## 5. Catch and release

For **one minute** after a rod catch, tossing that fish item (Q) back into water revives the
very same fish, which darts off scared. After the minute it's just an item. (Arrow-caught fish
are excluded — see above.)

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
