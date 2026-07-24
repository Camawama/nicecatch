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
    /** 0..1, how hard this fish fights. Entity fights derive this from hitbox size. */
    public float strength;
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
    public boolean holding;
}
