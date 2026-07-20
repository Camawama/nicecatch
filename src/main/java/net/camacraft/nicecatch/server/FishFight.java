package net.camacraft.nicecatch.server;

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
    /** 0..1, catch progress. Fish is landed at 1, escapes at 0 (after the grace period). */
    public float progress;
    /** 0..1, line tension. The line snaps at 1 if snapping is enabled. */
    public float tension;
    /** Tension gain is divided by this; >1 with Aquaculture line, hooks, and rod tiers. */
    public float tensionScale = 1.0F;
    /** Crank progress is multiplied by this; >1 with higher-tier Aquaculture rods. */
    public float reelScale = 1.0F;
    /** Chance to double the caught item (Aquaculture double hook). */
    public float doubleCatchChance;
    public int runTicks;
    public int calmTicks;
    public int graceTicks;
    public int ticks;

    // Input accumulated from ReelMessages since the last fight tick.
    public float pendingCrank;
    public float pendingLift;
    public boolean holding;
}
