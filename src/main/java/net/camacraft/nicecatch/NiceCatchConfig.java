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
        public final ForgeConfigSpec.DoubleValue runReelEffectiveness;
        public final ForgeConfigSpec.DoubleValue liftRunResistance;
        public final ForgeConfigSpec.DoubleValue liftPumpBonus;
        public final ForgeConfigSpec.DoubleValue lineLength;
        public final ForgeConfigSpec.DoubleValue fatiguePerRevolution;
        public final ForgeConfigSpec.DoubleValue fatiguePerRunTick;
        public final ForgeConfigSpec.DoubleValue fatigueRecoverPerTick;
        public final ForgeConfigSpec.DoubleValue slackTakeFactor;
        public final ForgeConfigSpec.DoubleValue chargeChance;
        public final ForgeConfigSpec.DoubleValue tensionPerRevolutionRun;
        public final ForgeConfigSpec.DoubleValue tensionPerRevolutionCalm;
        public final ForgeConfigSpec.DoubleValue tensionRecoveryPerTick;
        public final ForgeConfigSpec.BooleanValue lineSnapEnabled;
        public final ForgeConfigSpec.IntValue snapRodDamage;
        public final ForgeConfigSpec.DoubleValue fishStrengthMin;
        public final ForgeConfigSpec.DoubleValue fishStrengthMax;
        public final ForgeConfigSpec.BooleanValue bonusXp;
        public final ForgeConfigSpec.DoubleValue maxRevolutionsPerTick;
        public final ForgeConfigSpec.DoubleValue reelInSpeed;
        public final ForgeConfigSpec.IntValue escapeGraceTicks;

        // Fish AI
        public final ForgeConfigSpec.BooleanValue entityFishingEnabled;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> fishAiBlacklist;
        public final ForgeConfigSpec.DoubleValue interestRadius;
        public final ForgeConfigSpec.IntValue maxFishPerBobber;
        public final ForgeConfigSpec.DoubleValue interestChance;
        public final ForgeConfigSpec.DoubleValue interestGainPerCheck;
        public final ForgeConfigSpec.DoubleValue nibbleChancePerSecond;
        public final ForgeConfigSpec.DoubleValue nibbleToBiteChance;
        public final ForgeConfigSpec.DoubleValue followFollowerChance;
        public final ForgeConfigSpec.DoubleValue followFollowerRadius;
        public final ForgeConfigSpec.DoubleValue biteChancePerSecond;
        public final ForgeConfigSpec.DoubleValue biteRange;
        public final ForgeConfigSpec.IntValue biteWindowTicks;
        public final ForgeConfigSpec.IntValue fishBiteCooldownTicks;
        public final ForgeConfigSpec.DoubleValue scatterOnHookChance;
        public final ForgeConfigSpec.DoubleValue scatterRadius;
        public final ForgeConfigSpec.DoubleValue swimScareRadius;
        public final ForgeConfigSpec.DoubleValue meleeThreatRadius;
        public final ForgeConfigSpec.DoubleValue swimScareChance;
        public final ForgeConfigSpec.DoubleValue schoolPanicChance;
        public final ForgeConfigSpec.DoubleValue meleeScatterChance;
        public final ForgeConfigSpec.DoubleValue scatterSpeed;
        public final ForgeConfigSpec.IntValue scatterDurationTicks;
        public final ForgeConfigSpec.BooleanValue boidSchoolingEnabled;
        public final ForgeConfigSpec.DoubleValue boidNeighborRadius;
        public final ForgeConfigSpec.DoubleValue boidSpeed;
        public final ForgeConfigSpec.DoubleValue fishSwimSoundVolume;

        // Spawning
        public final ForgeConfigSpec.BooleanValue schoolSpawnBoostEnabled;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> schoolSpawnFish;
        public final ForgeConfigSpec.IntValue schoolSpawnMinSize;
        public final ForgeConfigSpec.IntValue schoolSpawnMaxSize;
        public final ForgeConfigSpec.DoubleValue schoolSpawnWeightMultiplier;

        // Entity catches
        public final ForgeConfigSpec.DoubleValue sizeStrengthExponent;
        public final ForgeConfigSpec.DoubleValue sizeReferenceArea;
        public final ForgeConfigSpec.IntValue entityCatchRodDamage;
        public final ForgeConfigSpec.DoubleValue convertDistance;
        public final ForgeConfigSpec.DoubleValue landDistance;
        public final ForgeConfigSpec.IntValue pullTimeoutTicks;
        public final ForgeConfigSpec.IntValue releaseWindowTicks;

        // Loot
        public final ForgeConfigSpec.BooleanValue removeFishFromLoot;
        public final ForgeConfigSpec.DoubleValue treasureReplacementChance;
        public final ForgeConfigSpec.DoubleValue luckTreasureBonus;

        // Aquaculture
        public final ForgeConfigSpec.DoubleValue baitInterestPerLureLevel;
        public final ForgeConfigSpec.DoubleValue baitBitePerLureLevel;
        public final ForgeConfigSpec.DoubleValue lineTensionScale;
        public final ForgeConfigSpec.DoubleValue rodTierTensionBonus;
        public final ForgeConfigSpec.DoubleValue rodTierReelBonus;
        public final ForgeConfigSpec.DoubleValue defaultHookTensionScale;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> hookTensionScales;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> hookNibbleScales;

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
            lineLength = b.comment("Blocks of line on the reel. The bar shows line retrieved; the fish escapes if it takes it all. Must stay under 32 (vanilla breaks the line there).")
                    .defineInRange("lineLength", 30.0D, 10.0D, 31.0D);
            runReelEffectiveness = b.comment("Fraction of your reel-in pull that still applies while the fish is running (cranking through a run builds heavy tension).")
                    .defineInRange("runReelEffectiveness", 0.35D, 0.0D, 1.0D);
            liftRunResistance = b.comment("How strongly pulling the mouse up (lifting the rod) resists a running fish taking line (per lift unit).")
                    .defineInRange("liftRunResistance", 1.6D, 0.0D, 10.0D);
            liftPumpBonus = b.comment("Extra reel-in speed per lift unit while the fish is calm — pumping the rod as you crank.")
                    .defineInRange("liftPumpBonus", 0.5D, 0.0D, 2.0D);
            fatiguePerRevolution = b.comment("Fish stamina drained per full crank revolution while it is calm. Fatigue is what weakens and shortens its runs.")
                    .defineInRange("fatiguePerRevolution", 0.04D, 0.005D, 0.5D);
            fatiguePerRunTick = b.comment("Fish stamina drained per tick while it is running (runs are exhausting).")
                    .defineInRange("fatiguePerRunTick", 0.003D, 0.0D, 0.05D);
            fatigueRecoverPerTick = b.comment("Fish stamina regained per tick while you give it slack — don't let it rest.")
                    .defineInRange("fatigueRecoverPerTick", 0.0015D, 0.0D, 0.05D);
            slackTakeFactor = b.comment("How hard the fish strips line off a slack reel whenever you release right-click, as a fraction of its run strength.")
                    .defineInRange("slackTakeFactor", 0.7D, 0.0D, 1.5D);
            chargeChance = b.comment("Chance that a run is a charge: the fish sprints back toward you, dumping slack you must crank up.")
                    .defineInRange("chargeChance", 0.2D, 0.0D, 1.0D);
            maxRevolutionsPerTick = b.comment("Server-side cap on crank speed, in mouse revolutions per tick.")
                    .defineInRange("maxRevolutionsPerTick", 0.25D, 0.05D, 1.0D);
            reelInSpeed = b.comment("Top speed, in blocks per second, at which full-speed cranking drags a calm fish toward the player. The strongest fish resist down to about half this.")
                    .defineInRange("reelInSpeed", 4.0D, 0.5D, 10.0D);
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

            b.push("fishAI");
            entityFishingEnabled = b.comment("Real fish entities are lured to bobbers and are what you actually catch. Disable for pure loot-table fishing.")
                    .define("entityFishingEnabled", true);
            fishAiBlacklist = b.comment("Fish-type entities the overhaul must leave entirely alone: they keep their vanilla AI and are never lured, spooked, schooled, or hooked. Entries are entity ids like 'aquaculture:jellyfish', or 'somemod:*' for every fish from that mod.")
                    .defineListAllowEmpty("fishAiBlacklist",
                            java.util.List.of("aquaculture:jellyfish"),
                            o -> o instanceof String s && s.contains(":"));
            interestRadius = b.comment("Radius in which fish notice a bobber and swim over.")
                    .defineInRange("interestRadius", 12.0D, 2.0D, 32.0D);
            maxFishPerBobber = b.comment("How many fish can crowd around one bobber at a time.")
                    .defineInRange("maxFishPerBobber", 5, 1, 20);
            interestChance = b.comment("Base chance (per check, roughly every half second) that an eligible fish decides to approach a bobber; scaled by that fish's accumulated interest.")
                    .defineInRange("interestChance", 0.35D, 0.0D, 1.0D);
            interestGainPerCheck = b.comment("Interest (0..1) a fish gains each time it notices a bobber. Aquaculture bait multiplies this; getting spooked costs 0.35.")
                    .defineInRange("interestGainPerCheck", 0.06D, 0.0D, 1.0D);
            nibbleChancePerSecond = b.comment("Chance per second that a fish at the bobber gives it a teasing nibble (bobber dips, but no hookable bite).")
                    .defineInRange("nibbleChancePerSecond", 0.45D, 0.0D, 1.0D);
            nibbleToBiteChance = b.comment("Chance that a nibble turns into a real, hookable bite.")
                    .defineInRange("nibbleToBiteChance", 0.15D, 0.0D, 1.0D);
            followFollowerChance = b.comment("Chance that a fish outside the interest radius follows a nearby fish that is already interested.")
                    .defineInRange("followFollowerChance", 0.25D, 0.0D, 1.0D);
            followFollowerRadius = b.comment("How close a fish must be to an interested fish to consider following it.")
                    .defineInRange("followFollowerRadius", 6.0D, 1.0D, 16.0D);
            biteChancePerSecond = b.comment("Chance per second that a fish milling at the bobber commits to a bite.")
                    .defineInRange("biteChancePerSecond", 0.22D, 0.005D, 1.0D);
            biteRange = b.comment("How close to the bobber a fish must be to bite.")
                    .defineInRange("biteRange", 1.8D, 0.5D, 5.0D);
            biteWindowTicks = b.comment("How long a biting fish stays on before you must set the hook.")
                    .defineInRange("biteWindowTicks", 50, 10, 200);
            fishBiteCooldownTicks = b.comment("How long a fish that escaped or got spooked waits before it can bite again.")
                    .defineInRange("fishBiteCooldownTicks", 400, 0, 6000);
            scatterOnHookChance = b.comment("Chance for each nearby fish to scatter when another fish is hooked. Kept low so a bite doesn't empty the spot.")
                    .defineInRange("scatterOnHookChance", 0.2D, 0.0D, 1.0D);
            scatterRadius = b.comment("Radius of the scatter shockwave around a hooked or attacked fish.")
                    .defineInRange("scatterRadius", 6.0D, 1.0D, 16.0D);
            swimScareRadius = b.comment("Fish scatter from non-fish entities swimming within this distance (always, no chance roll).")
                    .defineInRange("swimScareRadius", 6.0D, 0.5D, 16.0D);
            meleeThreatRadius = b.comment("Fish also scatter from any player (in water or not) who is moving or swinging within this distance.")
                    .defineInRange("meleeThreatRadius", 4.5D, 0.5D, 16.0D);
            swimScareChance = b.comment("Chance (per check, ~4x a second) that a fish scatters from a player looming in melee reach. Swimmers nearby always scatter fish regardless of this.")
                    .defineInRange("swimScareChance", 0.75D, 0.0D, 1.0D);
            schoolPanicChance = b.comment("When a fish spooks from a threat, chance for each schoolmate within scatterRadius to bolt with it.")
                    .defineInRange("schoolPanicChance", 0.8D, 0.0D, 1.0D);
            meleeScatterChance = b.comment("Chance for each nearby fish to scatter when a player attacks any fish.")
                    .defineInRange("meleeScatterChance", 0.9D, 0.0D, 1.0D);
            scatterSpeed = b.comment("Swim speed multiplier while scattering.")
                    .defineInRange("scatterSpeed", 1.9D, 1.0D, 4.0D);
            scatterDurationTicks = b.comment("How long a scatter lasts.")
                    .defineInRange("scatterDurationTicks", 90, 20, 600);
            boidSchoolingEnabled = b.comment("Same-species fish school together boids-style and regroup after a scare (replaces vanilla flock-following).")
                    .define("boidSchoolingEnabled", true);
            boidNeighborRadius = b.comment("Radius in which same-species fish count as schoolmates for boids steering.")
                    .defineInRange("boidNeighborRadius", 5.0D, 1.0D, 16.0D);
            boidSpeed = b.comment("Swim speed multiplier for boids schooling (regrouping after a scare is naturally faster).")
                    .defineInRange("boidSpeed", 1.0D, 0.25D, 3.0D);
            fishSwimSoundVolume = b.comment("Volume multiplier for fish swimming sounds — vanilla plays them at full blast and our fish swim fast. 0 silences them entirely.")
                    .defineInRange("fishSwimSoundVolume", 0.1D, 0.0D, 1.0D);
            b.pop();

            b.push("spawning");
            schoolSpawnBoostEnabled = b.comment("Spawn the fish listed below in much bigger natural groups, so boids schools actually have members.")
                    .define("schoolSpawnBoostEnabled", true);
            schoolSpawnFish = b.comment("Fish that spawn in boosted school-sized groups. Entity ids; pufferfish are left out by default because they are loners.")
                    .defineListAllowEmpty("schoolSpawnFish",
                            java.util.List.of("minecraft:tropical_fish", "minecraft:cod", "minecraft:salmon"),
                            o -> o instanceof String s && s.contains(":"));
            schoolSpawnMinSize = b.comment("Smallest boosted spawn group.")
                    .defineInRange("schoolSpawnMinSize", 12, 1, 64);
            schoolSpawnMaxSize = b.comment("Largest boosted spawn group. Note the vanilla water-ambient mob cap still limits totals: a big school spawns whole, then spawning pauses until fish despawn.")
                    .defineInRange("schoolSpawnMaxSize", 32, 1, 64);
            schoolSpawnWeightMultiplier = b.comment("Multiplier on the listed fishes' spawn weight (how often the spawner picks them over other water mobs).")
                    .defineInRange("schoolSpawnWeightMultiplier", 1.0D, 0.1D, 10.0D);
            b.pop();

            b.push("entityCatch");
            sizeStrengthExponent = b.comment("Exponent on (hitbox area / reference area) when computing fight strength. Below 1.0 makes small size differences matter a lot.")
                    .defineInRange("sizeStrengthExponent", 0.6D, 0.1D, 3.0D);
            sizeReferenceArea = b.comment("Hitbox area (width * height) that maps to maximum fight strength. Salmon is ~0.28, cod ~0.15.")
                    .defineInRange("sizeReferenceArea", 0.45D, 0.05D, 4.0D);
            entityCatchRodDamage = b.comment("Rod durability cost for landing a real fish.")
                    .defineInRange("entityCatchRodDamage", 2, 0, 16);
            convertDistance = b.comment("Distance from the player at which the reeled-in fish turns into its item.")
                    .defineInRange("convertDistance", 2.2D, 0.5D, 8.0D);
            landDistance = b.comment("The fight cannot be won until the fish has been reeled to within this distance of the player.")
                    .defineInRange("landDistance", 5.0D, 1.5D, 16.0D);
            pullTimeoutTicks = b.comment("Failsafe: convert the flying fish to an item after this many ticks even if it never got close.")
                    .defineInRange("pullTimeoutTicks", 40, 10, 200);
            releaseWindowTicks = b.comment("Catch-and-release: for this many ticks after a catch, tossing the fish item into water revives the fish.")
                    .defineInRange("releaseWindowTicks", 1200, 0, 24000);
            b.pop();

            b.push("loot");
            removeFishFromLoot = b.comment("Remove fish items from rod loot; fish only come from real fish entities. Loot-table fishing still yields junk and treasure.")
                    .define("removeFishFromLoot", true);
            treasureReplacementChance = b.comment("When a loot-table fish is filtered out, chance the replacement roll uses the treasure table instead of junk.")
                    .defineInRange("treasureReplacementChance", 0.15D, 0.0D, 1.0D);
            luckTreasureBonus = b.comment("Extra treasure chance per level of Luck of the Sea on the replacement roll.")
                    .defineInRange("luckTreasureBonus", 0.04D, 0.0D, 0.5D);
            b.pop();

            b.push("aquaculture");
            baitInterestPerLureLevel = b.comment("Extra fish-interest gain per lure-speed level of the equipped Aquaculture bait (interest multiplier = 1 + this * level).")
                    .defineInRange("baitInterestPerLureLevel", 0.35D, 0.0D, 3.0D);
            baitBitePerLureLevel = b.comment("Extra bite chance per lure-speed level of the equipped Aquaculture bait (bite multiplier = 1 + this * level).")
                    .defineInRange("baitBitePerLureLevel", 0.25D, 0.0D, 3.0D);
            lineTensionScale = b.comment("Tension is divided by this while a fishing line is equipped on the Aquaculture rod (the line snaps much later).")
                    .defineInRange("lineTensionScale", 1.5D, 1.0D, 5.0D);
            rodTierTensionBonus = b.comment("Additional snap protection per rod material tier level (scale += this * tier).")
                    .defineInRange("rodTierTensionBonus", 0.08D, 0.0D, 1.0D);
            rodTierReelBonus = b.comment("Extra crank progress per rod material tier level (reel multiplier = 1 + this * tier).")
                    .defineInRange("rodTierReelBonus", 0.05D, 0.0D, 1.0D);
            defaultHookTensionScale = b.comment("Tension divisor for equipped hooks not listed below.")
                    .defineInRange("defaultHookTensionScale", 1.15D, 1.0D, 5.0D);
            hookTensionScales = b.comment("Per-hook tension scales as 'hookname=scale', e.g. heavy=1.8.")
                    .defineListAllowEmpty("hookTensionScales",
                            java.util.List.of("heavy=1.8", "double=1.2", "gold=1.3", "diamond=1.6", "nether_star=2.2"),
                            o -> o instanceof String s && s.contains("="));
            hookNibbleScales = b.comment("Per-hook multipliers on nibble-to-bite conversion as 'hookname=multiplier'; better hooks set themselves when a fish tastes the bait.")
                    .defineListAllowEmpty("hookNibbleScales",
                            java.util.List.of("iron=1.25", "gold=1.5", "diamond=1.75", "light=1.4", "heavy=1.1",
                                    "double=1.25", "redstone=1.2", "note=1.2", "nether_star=2.5"),
                            o -> o instanceof String s && s.contains("="));
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
        public final ForgeConfigSpec.BooleanValue cameraFollowFish;
        public final ForgeConfigSpec.DoubleValue cameraFollowStrength;
        public final ForgeConfigSpec.BooleanValue showHints;
        public final ForgeConfigSpec.IntValue mouseRampTicks;

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
            cameraFollowFish = b.comment("While reeling (mouse captured), smoothly pan the camera to keep the fish in view.")
                    .define("cameraFollowFish", true);
            cameraFollowStrength = b.comment("How quickly the follow camera catches up to the fish, as a fraction of the remaining angle per tick.")
                    .defineInRange("cameraFollowStrength", 0.18D, 0.02D, 1.0D);
            showHints = b.comment("Show contextual hint text above the fishing bar (release to cast, set the hook, ease off...).")
                    .define("showHints", true);
            mouseRampTicks = b.comment("Ticks over which camera sensitivity ramps back up after leaving reel mode, so a spinning hand doesn't whip the camera.")
                    .defineInRange("mouseRampTicks", 10, 0, 60);
        }
    }
}
