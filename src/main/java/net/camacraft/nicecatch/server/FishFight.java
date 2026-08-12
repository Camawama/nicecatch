package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.FightPhase;
import net.minecraft.world.InteractionHand;

import javax.annotation.Nullable;
import java.util.UUID;

/** Server-side state of one hooked fish being fought by one player. */
public class FishFight
{
    public InteractionHand hand;
    /** The real fish on the line, or null for a loot-table fight (no fish entities around). */
    @Nullable public UUID fishId;
    /** 0..1, how hard this fish fights. Entity fights derive this from hitbox size and species. */
    public float strength;
    /** Stamina multiplier: >1 tires slower. Species profile x traits. */
    public float stamina = 1.0F;
    /** Thrash amplitude while sweeping (feisty fish). */
    public float sweepAmp = 1.0F;
    /** Force multiplier on its runs (swift/sluggish fish). */
    public float runForce = 1.0F;
    /** Weighting toward the Charge tactic (cunning fish). */
    public float chargeBias = 1.0F;
    /** Extra XP granted on landing (lucky/ancient fish). */
    public int bonusXp;
    /** Distance to the player last tick; watched to hear line being stripped off the reel. */
    public double lastDist = -1.0D;
    /** Line stripped since the last drag click; every ~third of a block clicks the drag. */
    public float dragClickAccum;
    /**
     * 0..1, line retrieved — derived from the fish's distance each tick, this is what the
     * HUD bar shows. Full means the fish is at landing range; empty means it took all the line.
     */
    public float progress;
    /** 0..1, how played-out the fish is. Cranking drains it, slack restores it; tired fish run weak. */
    public float fatigue;
    /** Blocks of line on the reel this fight; the fish escapes if it gets this far away. */
    public float lineLength = 30.0F;
    /** 0..1, line tension. The line snaps at 1 if snapping is enabled. */
    public float tension;
    /** Tension gain is divided by this; >1 with Aquaculture line, hooks, and rod tiers. */
    public float tensionScale = 1.0F;
    /** Reel-in pull speed is multiplied by this; >1 with higher-tier Aquaculture rods. */
    public float reelScale = 1.0F;
    /** Chance to double the caught item (Aquaculture double hook). */
    public float doubleCatchChance;

    /**
     * True for a line-arrow fight: no rod and no FishingHook bobber (the line runs straight to
     * the fish), no rod durability cost, and the caught fish is not added to catch-and-release.
     */
    public boolean arrow;

    /** The fish's current tactic. It cycles between these throughout the fight. */
    public FightPhase phase = FightPhase.PULL;
    /** Ticks left in the current phase; a new phase is picked when this runs out. */
    public int phaseTicks;
    public int graceTicks;
    public int ticks;

    // Erratic-swimming state: the fish's lateral heading offset (radians off the straight
    // away-from-player line), eased toward a target that gets re-picked every few ticks.
    public float veer;
    public float veerTarget;
    public int veerTicks;
    /** Vertical analogue of veer, biased downward: a fighting fish likes to sound for the deep. */
    public float dive;
    public float diveTarget;

    // Input accumulated from ReelMessages since the last fight tick.
    public float pendingCrank;
    public float pendingLift;
    /** Signed sideways swing, right-positive: the answer to dart events. */
    public float pendingSide;
    /** Line the player deliberately paid out with the scroll wheel. */
    public float pendingFeed;
    public boolean holding;

    // Dart quick-time event: the fish bolts sideways and demands a counter-pull.
    /** 0 none; 1 = PULL LEFT required (fish darting to the player's right); 2 = PULL RIGHT. */
    public byte dartDir;
    public int dartTicksLeft;
    public float dartProgress;
    /** Ticks until the next dart may begin; opens at 60 so the fight settles in first. */
    public int dartCooldown = 60;

    /** A chain bite already happened this fight (a bigger fish ate the hooked one). */
    public boolean chainHappened;
}
