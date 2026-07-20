package net.camacraft.nicecatch.server;

import net.minecraft.world.InteractionHand;

/** Server-side state of one hooked fish being fought by one player. */
public class FishFight
{
    public InteractionHand hand;
    /** 0..1, how hard this fish fights. */
    public float strength;
    /** 0..1, catch progress. Fish is landed at 1, escapes at 0 (after the grace period). */
    public float progress;
    /** 0..1, line tension. The line snaps at 1 if snapping is enabled. */
    public float tension;
    public int runTicks;
    public int calmTicks;
    public int graceTicks;
    public int ticks;

    // Input accumulated from ReelMessages since the last fight tick.
    public float pendingCrank;
    public float pendingLift;
    public boolean holding;
}
