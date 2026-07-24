package net.camacraft.nicecatch;

import net.minecraft.util.Mth;

/**
 * Which way a hooked fish is fighting at this moment. During a fight the fish cycles through
 * these at random, and each one changes what the angler has to do — hold on through a straight
 * run, pump a sounding fish back up before it can be reeled, crank fast to keep a charging fish
 * from throwing the hook, or simply reel while it holds still or thrashes side to side.
 *
 * Shared between client and server (sent in {@code FightTickMessage}) so the HUD can coach the
 * player on the current phase.
 */
public enum FightPhase
{
    /** Sitting still: the angler's clean window to reel it steadily home. */
    HOLD,
    /** Sporadic left-right thrashing: reel while you track it, but it fights the crank a little. */
    SWEEP,
    /** Boring straight away and tearing off line: hold on and let it wear itself out. */
    PULL,
    /** Sounding for the bottom: pull the rod up to lift it before it will come in. */
    SOUND,
    /** Charging back at the angler and dumping slack: crank fast or it may spit the hook. */
    CHARGE;

    private static final FightPhase[] VALUES = values();

    public byte id()
    {
        return (byte) ordinal();
    }

    public static FightPhase byId(int id)
    {
        return VALUES[Mth.clamp(id, 0, VALUES.length - 1)];
    }

    /**
     * Runs are phases where the fish is taking or dictating line; the fight can never be landed
     * mid-run, the HUD shakes, and cranking through builds heavy tension.
     */
    public boolean isRun()
    {
        return this == PULL || this == SOUND || this == CHARGE;
    }
}
