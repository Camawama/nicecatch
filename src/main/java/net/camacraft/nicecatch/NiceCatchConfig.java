package net.camacraft.nicecatch;

import net.minecraftforge.common.ForgeConfigSpec;

public class NiceCatchConfig
{
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        var serverPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = serverPair.getKey();
        SERVER_SPEC = serverPair.getValue();
        var clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getKey();
        CLIENT_SPEC = clientPair.getValue();
    }

    public static class Server
    {
        public final ForgeConfigSpec.DoubleValue castPowerMin;
        public final ForgeConfigSpec.DoubleValue castPowerMax;
        public final ForgeConfigSpec.DoubleValue biteWindowMultiplier;
        public final ForgeConfigSpec.DoubleValue progressPerRevolution;
        public final ForgeConfigSpec.DoubleValue runReelEffectiveness;
        public final ForgeConfigSpec.DoubleValue slackLossPerTick;
        public final ForgeConfigSpec.DoubleValue runPullPerTick;
        public final ForgeConfigSpec.DoubleValue liftRunResistance;
        public final ForgeConfigSpec.DoubleValue liftProgressBonus;
        public final ForgeConfigSpec.DoubleValue tensionPerRevolutionRun;
        public final ForgeConfigSpec.DoubleValue tensionPerRevolutionCalm;
        public final ForgeConfigSpec.DoubleValue tensionRecoveryPerTick;
        public final ForgeConfigSpec.BooleanValue lineSnapEnabled;
        public final ForgeConfigSpec.IntValue snapRodDamage;
        public final ForgeConfigSpec.DoubleValue fishStrengthMin;
        public final ForgeConfigSpec.DoubleValue fishStrengthMax;
        public final ForgeConfigSpec.BooleanValue bonusXp;
        public final ForgeConfigSpec.DoubleValue maxRevolutionsPerTick;
        public final ForgeConfigSpec.IntValue escapeGraceTicks;

        Server(ForgeConfigSpec.Builder b)
        {
            b.push("casting");
            castPowerMin = b.comment("Velocity multiplier of the weakest (instant-click) cast, relative to vanilla.")
                    .defineInRange("castPowerMin", 0.30D, 0.05D, 1.0D);
            castPowerMax = b.comment("Velocity multiplier of a fully charged cast, relative to vanilla.")
                    .defineInRange("castPowerMax", 1.65D, 1.0D, 3.0D);
            b.pop();

            b.push("bite");
            biteWindowMultiplier = b.comment("How much longer a fish stays on the line before you hook it, compared to vanilla (1.0 = vanilla 1-2 seconds).")
                    .defineInRange("biteWindowMultiplier", 2.0D, 1.0D, 5.0D);
            b.pop();

            b.push("fight");
            progressPerRevolution = b.comment("Catch progress gained per full crank of the mouse while the fish is calm.")
                    .defineInRange("progressPerRevolution", 0.09D, 0.01D, 1.0D);
            runReelEffectiveness = b.comment("Fraction of normal reeling progress gained while the fish is running.")
                    .defineInRange("runReelEffectiveness", 0.35D, 0.0D, 1.0D);
            slackLossPerTick = b.comment("Catch progress lost per tick while not holding the reel (scaled up during runs).")
                    .defineInRange("slackLossPerTick", 0.0045D, 0.0D, 0.1D);
            runPullPerTick = b.comment("Catch progress the fish takes back per tick during a run (before rod-lift resistance).")
                    .defineInRange("runPullPerTick", 0.0075D, 0.0D, 0.1D);
            liftRunResistance = b.comment("How strongly pulling the mouse up resists a running fish (per lift unit).")
                    .defineInRange("liftRunResistance", 1.6D, 0.0D, 10.0D);
            liftProgressBonus = b.comment("Extra catch progress per lift unit while the fish is calm.")
                    .defineInRange("liftProgressBonus", 0.02D, 0.0D, 0.5D);
            maxRevolutionsPerTick = b.comment("Server-side cap on crank speed, in mouse revolutions per tick.")
                    .defineInRange("maxRevolutionsPerTick", 0.25D, 0.05D, 1.0D);
            escapeGraceTicks = b.comment("Ticks after hooking a fish before it is allowed to escape by taking all the line.")
                    .defineInRange("escapeGraceTicks", 50, 0, 200);
            b.pop();

            b.push("tension");
            tensionPerRevolutionRun = b.comment("Line tension added per crank revolution while the fish is running.")
                    .defineInRange("tensionPerRevolutionRun", 0.30D, 0.0D, 2.0D);
            tensionPerRevolutionCalm = b.comment("Line tension added per crank revolution while the fish is calm.")
                    .defineInRange("tensionPerRevolutionCalm", 0.04D, 0.0D, 2.0D);
            tensionRecoveryPerTick = b.comment("Line tension released per tick while not cranking hard.")
                    .defineInRange("tensionRecoveryPerTick", 0.012D, 0.0D, 0.5D);
            lineSnapEnabled = b.comment("Whether the line can snap (losing the fish and bobber) when tension maxes out.")
                    .define("lineSnapEnabled", true);
            snapRodDamage = b.comment("Extra rod durability damage when the line snaps.")
                    .defineInRange("snapRodDamage", 3, 0, 64);
            b.pop();

            b.push("fish");
            fishStrengthMin = b.comment("Minimum fish strength (affects run frequency/length and pull).")
                    .defineInRange("fishStrengthMin", 0.35D, 0.0D, 1.0D);
            fishStrengthMax = b.comment("Maximum fish strength.")
                    .defineInRange("fishStrengthMax", 1.0D, 0.0D, 1.0D);
            bonusXp = b.comment("Grant a little bonus XP for landing strong fish.")
                    .define("bonusXp", true);
            b.pop();
        }
    }

    public static class Client
    {
        public final ForgeConfigSpec.IntValue chargeSweepTicks;
        public final ForgeConfigSpec.DoubleValue reelSensitivity;
        public final ForgeConfigSpec.BooleanValue requireCircularMotion;
        public final ForgeConfigSpec.DoubleValue reelFovMultiplier;
        public final ForgeConfigSpec.BooleanValue reelClickSounds;
        public final ForgeConfigSpec.BooleanValue cameraShake;

        Client(ForgeConfigSpec.Builder b)
        {
            chargeSweepTicks = b.comment("Ticks for the cast power bar to sweep from empty to full (it then falls back down).")
                    .defineInRange("chargeSweepTicks", 30, 5, 200);
            reelSensitivity = b.comment("Multiplier on how much reeling you get out of your mouse motion.")
                    .defineInRange("reelSensitivity", 1.0D, 0.1D, 5.0D);
            requireCircularMotion = b.comment("If true, you must spin the mouse in circles to reel. If false, simply holding right-click reels (accessibility).")
                    .define("requireCircularMotion", true);
            reelFovMultiplier = b.comment("FOV multiplier while focused on reeling a fish (lower = more zoom).")
                    .defineInRange("reelFovMultiplier", 0.87D, 0.5D, 1.0D);
            reelClickSounds = b.comment("Play soft reel-ratchet clicks while cranking.")
                    .define("reelClickSounds", true);
            cameraShake = b.comment("Subtle camera shake while a hooked fish is running.")
                    .define("cameraShake", true);
        }
    }
}
