package net.camacraft.nicecatch;

import net.minecraftforge.common.ForgeConfigSpec;

public class NiceCatchConfig
{
    /** Display unit for fish weights. Data is always stored metric; this is presentation only. */
    public enum WeightUnit { LBS, KG }

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
        public final ForgeConfigSpec.DoubleValue runDragTaper;
        public final ForgeConfigSpec.DoubleValue dragRunFatigue;
        public final ForgeConfigSpec.DoubleValue crankOverdrive;
        public final ForgeConfigSpec.DoubleValue reelResistance;
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
        public final ForgeConfigSpec.IntValue earlyTensionGraceTicks;
        public final ForgeConfigSpec.BooleanValue lineSnapEnabled;
        public final ForgeConfigSpec.IntValue snapRodDamage;
        public final ForgeConfigSpec.DoubleValue fishStrengthMin;
        public final ForgeConfigSpec.DoubleValue fishStrengthMax;
        public final ForgeConfigSpec.BooleanValue bonusXp;
        public final ForgeConfigSpec.DoubleValue maxRevolutionsPerTick;
        public final ForgeConfigSpec.DoubleValue reelInSpeed;
        public final ForgeConfigSpec.IntValue escapeGraceTicks;
        public final ForgeConfigSpec.DoubleValue arrowFatigueMultiplier;
        public final ForgeConfigSpec.DoubleValue weightEndurance;
        public final ForgeConfigSpec.BooleanValue dartEventsEnabled;
        public final ForgeConfigSpec.IntValue dartWindowTicks;
        public final ForgeConfigSpec.DoubleValue dartSwingRequired;
        public final ForgeConfigSpec.DoubleValue dartMinWeightLbs;
        public final ForgeConfigSpec.DoubleValue soundPhaseMinWeightLbs;
        public final ForgeConfigSpec.DoubleValue dartMaxPullPerTick;
        public final ForgeConfigSpec.DoubleValue dartJerkTensionPenalty;
        public final ForgeConfigSpec.DoubleValue chainBiteChancePerSecond;
        public final ForgeConfigSpec.DoubleValue chainBiteSizeRatio;

        // Line & reel-in (no fish on the line)
        public final ForgeConfigSpec.BooleanValue spoolDragEnabled;
        public final ForgeConfigSpec.DoubleValue spoolLength;
        public final ForgeConfigSpec.BooleanValue gradualReelEnabled;
        public final ForgeConfigSpec.DoubleValue emptyReelSpeed;
        public final ForgeConfigSpec.DoubleValue itemReelSpeed;
        public final ForgeConfigSpec.DoubleValue entityReelSpeed;
        public final ForgeConfigSpec.IntValue playerEscapeCrouchTicks;
        public final ForgeConfigSpec.DoubleValue reelCompleteDistance;
        public final ForgeConfigSpec.IntValue reelIdleTimeoutTicks;
        public final ForgeConfigSpec.DoubleValue rodRangeLimit;
        public final ForgeConfigSpec.IntValue cutLineCooldownTicks;

        // Fishing-line arrow
        public final ForgeConfigSpec.BooleanValue arrowFightEnabled;
        public final ForgeConfigSpec.DoubleValue arrowMaxLine;

        // Hook-set minigame
        public final ForgeConfigSpec.BooleanValue directionalHookSet;

        // Species profiles, food, predation, habitat, environment
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> fishProfiles;
        public final ForgeConfigSpec.BooleanValue foodAttractionEnabled;
        public final ForgeConfigSpec.DoubleValue foodAttractRadius;
        public final ForgeConfigSpec.DoubleValue foodStartleChance;
        public final ForgeConfigSpec.DoubleValue foodInterestBoost;
        public final ForgeConfigSpec.IntValue foodSatiationTicks;
        public final ForgeConfigSpec.BooleanValue predationEnabled;
        public final ForgeConfigSpec.DoubleValue predatorHuntRadius;
        public final ForgeConfigSpec.IntValue predationSatiationTicks;
        public final ForgeConfigSpec.BooleanValue habitatMovementEnabled;
        public final ForgeConfigSpec.DoubleValue coldBiteMultiplier;

        // Weight, size variance & traits
        public final ForgeConfigSpec.EnumValue<WeightUnit> weightUnit;
        public final ForgeConfigSpec.BooleanValue sizeVarianceEnabled;
        public final ForgeConfigSpec.DoubleValue sizeVarianceMin;
        public final ForgeConfigSpec.DoubleValue sizeVarianceMax;
        public final ForgeConfigSpec.DoubleValue weightPerVolume;
        public final ForgeConfigSpec.DoubleValue healthScaleExponent;
        public final ForgeConfigSpec.DoubleValue traitChance;
        public final ForgeConfigSpec.DoubleValue doubleTraitChance;
        public final ForgeConfigSpec.BooleanValue traitAurasEnabled;

        // Fishing net & fish trap
        public final ForgeConfigSpec.DoubleValue netCatchRadius;
        public final ForgeConfigSpec.DoubleValue netCatchChance;
        public final ForgeConfigSpec.IntValue netCooldownTicks;
        public final ForgeConfigSpec.IntValue trapCheckIntervalTicks;
        public final ForgeConfigSpec.DoubleValue trapCatchChance;
        public final ForgeConfigSpec.DoubleValue trapCatchRadius;
        public final ForgeConfigSpec.DoubleValue trapAttractRadius;
        public final ForgeConfigSpec.DoubleValue trapAmbientCatchChance;
        public final ForgeConfigSpec.DoubleValue trapBaitMultiplier;

        // Fish AI
        public final ForgeConfigSpec.BooleanValue entityFishingEnabled;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> fishAiBlacklist;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> fishAiWhitelist;
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
        public final ForgeConfigSpec.DoubleValue boatScareRadius;
        public final ForgeConfigSpec.DoubleValue meleeThreatRadius;
        public final ForgeConfigSpec.DoubleValue swimScareChance;
        public final ForgeConfigSpec.DoubleValue schoolPanicChance;
        public final ForgeConfigSpec.DoubleValue meleeScatterChance;
        public final ForgeConfigSpec.DoubleValue scatterSpeed;
        public final ForgeConfigSpec.IntValue scatterDurationTicks;
        public final ForgeConfigSpec.BooleanValue boidSchoolingEnabled;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> boidBlacklist;
        public final ForgeConfigSpec.DoubleValue boidNeighborRadius;
        public final ForgeConfigSpec.DoubleValue boidSpeed;
        public final ForgeConfigSpec.DoubleValue fishSwimSoundVolume;
        public final ForgeConfigSpec.BooleanValue vibrationScareEnabled;
        public final ForgeConfigSpec.DoubleValue vibrationScareRadius;

        // Spawning
        public final ForgeConfigSpec.BooleanValue schoolSpawnBoostEnabled;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> schoolSpawnFish;
        public final ForgeConfigSpec.IntValue schoolSpawnMinSize;
        public final ForgeConfigSpec.IntValue schoolSpawnMaxSize;
        public final ForgeConfigSpec.DoubleValue schoolSpawnWeightMultiplier;
        public final ForgeConfigSpec.BooleanValue extraSpawningEnabled;
        public final ForgeConfigSpec.IntValue extraSpawnIntervalTicks;
        public final ForgeConfigSpec.IntValue extraSpawnTargetCount;
        public final ForgeConfigSpec.IntValue extraSpawnRadius;

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
        public final ForgeConfigSpec.DoubleValue doubleHookChanceMultiplier;
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
            directionalHookSet = b.comment("When a real fish bites you must yank the rod (jolt the mouse) LEFT or RIGHT — the HUD shows which — inside the bite window to set the hook; the wrong way (or too slow) loses the fish. Disable to set the hook with a plain right-click instead.")
                    .define("directionalHookSet", true);
            b.pop();

            b.push("fight");
            lineLength = b.comment("Blocks of line on the reel. The bar shows line retrieved; the fish escapes if it takes it all. Capped at runtime just under line.rodRangeLimit (where the line finally gives out entirely).")
                    .defineInRange("lineLength", 30.0D, 10.0D, 126.0D);
            runReelEffectiveness = b.comment("Fraction of your reel-in pull that still applies while the fish is running (cranking through a run builds heavy tension).")
                    .defineInRange("runReelEffectiveness", 0.35D, 0.0D, 1.0D);
            liftRunResistance = b.comment("How strongly pulling the mouse up (lifting the rod) resists a running fish taking line (per lift unit). Applies to both run styles: boring straight away (Pull) and diving (Sounding) — lifting the rod is the angler's live counter while a fish strips line.")
                    .defineInRange("liftRunResistance", 1.6D, 0.0D, 10.0D);
            runDragTaper = b.comment("How much of a running fish's pull the loaded drag eats by the time the line is fully out (ramping up quadratically with line taken). At 0.6, a fish at the end of the spool runs with only 40% of its force — heavyweights stall further and further out instead of ripping straight off the end of the line. 0 disables (old behavior: a monster's run never weakens).")
                    .defineInRange("runDragTaper", 0.6D, 0.0D, 0.95D);
            dragRunFatigue = b.comment("Extra stamina a running fish burns for sprinting against the loaded drag, scaling with how much line it has taken (run fatigue x (1 + this x lineOutFraction)). This is what finally wears a monster down: its own long runs exhaust it. 0 disables.")
                    .defineInRange("dragRunFatigue", 2.0D, 0.0D, 8.0D);
            liftPumpBonus = b.comment("Extra reel-in speed per lift unit while the fish is calm — pumping the rod as you crank.")
                    .defineInRange("liftPumpBonus", 0.5D, 0.0D, 2.0D);
            fatiguePerRevolution = b.comment("Fish stamina drained per full crank revolution while it is calm. Fatigue is what weakens and shortens its runs.")
                    .defineInRange("fatiguePerRevolution", 0.04D, 0.005D, 0.5D);
            fatiguePerRunTick = b.comment("Fish stamina drained per tick while it is running (runs are exhausting).")
                    .defineInRange("fatiguePerRunTick", 0.003D, 0.0D, 0.05D);
            fatigueRecoverPerTick = b.comment("Fish stamina regained per tick while you give it slack — don't let it rest.")
                    .defineInRange("fatigueRecoverPerTick", 0.0015D, 0.0D, 0.05D);
            slackTakeFactor = b.comment("Unused since the fight-phase rework (the run phases strip line intrinsically); kept for config compatibility.")
                    .defineInRange("slackTakeFactor", 0.7D, 0.0D, 1.5D);
            chargeChance = b.comment("How strongly the Charge tactic is weighted when the fish picks its next move: it sprints back toward you, dumping slack you must crank up. 0 makes it never charge.")
                    .defineInRange("chargeChance", 0.2D, 0.0D, 1.0D);
            maxRevolutionsPerTick = b.comment("Crank speed (mouse revolutions per tick) that counts as a FULL crank — the base reel-in rate. Circling faster than this keeps helping, up to crankOverdrive times the base pull.")
                    .defineInRange("maxRevolutionsPerTick", 0.25D, 0.05D, 1.0D);
            crankOverdrive = b.comment("How far past the base full-crank rate genuinely fast circling keeps adding pull (1.5 = circling 50% faster than the base rate reels 50% harder). Working the reel furiously now visibly matters against a strong fish — at the cost of proportionally more line tension. 1.0 restores the old hard ceiling.")
                    .defineInRange("crankOverdrive", 1.5D, 1.0D, 3.0D);
            reelResistance = b.comment("How much of your reel-in speed the strongest fish can cancel while fresh (fatigue erodes it): at 0.4 a fresh monster reels in at 60% of your crank speed. Was effectively 0.5 before.")
                    .defineInRange("reelResistance", 0.4D, 0.0D, 0.9D);
            reelInSpeed = b.comment("Top speed, in blocks per second, at which full-speed cranking drags a calm fish toward the player. The strongest fish resist down to about half this.")
                    .defineInRange("reelInSpeed", 4.0D, 0.5D, 10.0D);
            escapeGraceTicks = b.comment("Ticks after hooking a fish before it is allowed to escape by taking all the line.")
                    .defineInRange("escapeGraceTicks", 50, 0, 200);
            arrowFatigueMultiplier = b.comment("How much faster a fish speared by a fishing-line arrow tires compared to a mouth-hooked fish. It still fights at first, but gives up much sooner — and it never recovers stamina on slack.")
                    .defineInRange("arrowFatigueMultiplier", 3.0D, 1.0D, 10.0D);
            weightEndurance = b.comment("How strongly a fish's WEIGHT deepens its fight. At 1.0: under 5 lbs tires quickly (easy), 5-10 lbs fights roughly up to twice as long, 10-15 lbs up to three times (very difficult), and past 15 lbs it keeps climbing toward the cap — an extreme, drawn-out battle. Heavier fish also get a strength floor. 0 disables weight-based endurance; higher values exaggerate it.")
                    .defineInRange("weightEndurance", 1.0D, 0.0D, 3.0D);
            dartEventsEnabled = b.comment("Dart events: mid-fight the fish suddenly darts sideways and the HUD demands a quick PULL LEFT / PULL RIGHT — STOP cranking (the reel gains nothing during a dart anyway), then swing the mouse that way (while still holding right-click) to muscle it back for a burst of fatigue and a yank toward you; too slow and it steals line and strains the rod. The yank only registers once the reel has been still for a beat, so circling can never answer the prompt by accident.")
                    .define("dartEventsEnabled", true);
            dartWindowTicks = b.comment("Reaction window for a dart event, in ticks. Long enough to read the prompt and swing; shorten it once the motion is muscle memory.")
                    .defineInRange("dartWindowTicks", 35, 10, 80);
            dartSwingRequired = b.comment("How much sideways mouse-swing answers a dart (roughly comparable to the rod-lift scale). Lower = easier.")
                    .defineInRange("dartSwingRequired", 1.2D, 0.2D, 5.0D);
            dartMinWeightLbs = b.comment("Fish lighter than this (in pounds) never spring dart events at all — the PULL LEFT / PULL RIGHT tests are reserved for genuine heavyweights, so everyday fish stay simple to reel and only trophies demand the advanced moves. 0 gives every fish darts.")
                    .defineInRange("dartMinWeightLbs", 75.0D, 0.0D, 100000.0D);
            soundPhaseMinWeightLbs = b.comment("Fish lighter than this (in pounds) never use the Sounding tactic (the deep dive that demands rod lifts before it can be reeled). Light fish are then a pure reel-and-crank affair; only sizeable fish make you work the rod vertically.")
                    .defineInRange("soundPhaseMinWeightLbs", 25.0D, 0.0D, 100000.0D);
            dartMaxPullPerTick = b.comment("The most dart-answering pull that counts per tick. This forces EASING into the sideways pull over a few smooth ticks — a single violent yank can't complete the prompt instantly.")
                    .defineInRange("dartMaxPullPerTick", 0.25D, 0.05D, 2.0D);
            dartJerkTensionPenalty = b.comment("Line tension added per unit of sideways swing beyond the smooth-pull cap while answering a dart — jerking the rod violently strains the line. 0 disables the penalty.")
                    .defineInRange("dartJerkTensionPenalty", 0.08D, 0.0D, 1.0D);
            chainBiteChancePerSecond = b.comment("Chance per second, while reeling a hooked fish, that a much larger fish nearby strikes it — eating your catch off the hook and taking its place on the line. The small fish becomes the bait; the fight restarts fresh against the monster. Once per fight, rod fights only. 0 disables.")
                    .defineInRange("chainBiteChancePerSecond", 0.03D, 0.0D, 1.0D);
            chainBiteSizeRatio = b.comment("How much larger (hitbox area) the striking fish must be than the hooked one for a chain bite.")
                    .defineInRange("chainBiteSizeRatio", 2.2D, 1.2D, 10.0D);
            b.pop();

            b.push("line");
            spoolDragEnabled = b.comment("As you walk away from a cast bobber the reel pays out line; once you reach the end of the spool it drags the bobber along behind you instead of the line snapping back and retracting. Disable for vanilla behavior (the line retracts when you get too far).")
                    .define("spoolDragEnabled", true);
            spoolLength = b.comment("Blocks of line on the spool for a cast (no fish) bobber. Walk past this and the bobber gets dragged along at this distance. Keep it under line.rodRangeLimit (where the line gives out entirely).")
                    .defineInRange("spoolLength", 30.0D, 8.0D, 126.0D);
            gradualReelEnabled = b.comment("Instead of a right-click instantly retracting the line, hold right-click and reel (circle the mouse) to bring the bobber in — fast on an empty line, a little slower with a loot item on the hook (no chance of the line snapping; only real fish fight). Disable for vanilla instant retrieval.")
                    .define("gradualReelEnabled", true);
            emptyReelSpeed = b.comment("Top speed, in blocks per second, at which you can reel in an empty line (nothing on the hook).")
                    .defineInRange("emptyReelSpeed", 7.0D, 1.0D, 20.0D);
            itemReelSpeed = b.comment("Top speed, in blocks per second, at which you can reel in a snagged loot item. Slightly slower than an empty line, but there is no fight and the line cannot snap.")
                    .defineInRange("itemReelSpeed", 4.5D, 1.0D, 20.0D);
            entityReelSpeed = b.comment("Top speed, in blocks per second, at which reeling drags a snagged non-fish entity (a mob, on land or in water) toward you. Heavier entities are dragged slower.")
                    .defineInRange("entityReelSpeed", 3.0D, 0.5D, 20.0D);
            playerEscapeCrouchTicks = b.comment("A player hooked by someone's rod is told on screen how to escape and can hold sneak for this many ticks to break the line and get free (sneaking also mostly anchors them against the drag). 0 disables escaping (old behavior).")
                    .defineInRange("playerEscapeCrouchTicks", 30, 0, 400);
            reelCompleteDistance = b.comment("Distance from you at which a reeled-in bobber is fully retrieved (collecting any snagged item).")
                    .defineInRange("reelCompleteDistance", 2.5D, 1.0D, 8.0D);
            reelIdleTimeoutTicks = b.comment("If you stop reeling (release right-click) for this many ticks, the reel-in is abandoned and the bobber is left sitting in the water again.")
                    .defineInRange("reelIdleTimeoutTicks", 40, 5, 400);
            rodRangeLimit = b.comment("Absolute range of the fishing line in blocks: past this the line gives out and the bobber is lost, vanilla-style (vanilla hardcodes 32). Raise it for longer casts, longer fights, and longer boat trolling; lineLength and spoolLength can then be raised toward it.")
                    .defineInRange("rodRangeLimit", 64.0D, 33.0D, 128.0D);
            cutLineCooldownTicks = b.comment("Rod cooldown (ticks) after cutting the line, simulating tying fresh terminal tackle on — the rod shows the vanilla item-cooldown sweep and cannot cast until it clears. 200 = 10 seconds. 0 disables.")
                    .defineInRange("cutLineCooldownTicks", 200, 0, 2400);
            b.pop();

            b.push("lineArrow");
            arrowFightEnabled = b.comment("A fishing-line arrow fired from a bow that hits a fish starts the reel fight for that fish, with the line running from you to the fish. Fish caught this way are NOT added to catch-and-release (the item can't be tossed back to revive them).")
                    .define("arrowFightEnabled", true);
            arrowMaxLine = b.comment("Maximum line length (blocks) for a line-arrow fight. If the fish is hit from further away than this, the line won't take hold and the arrow just behaves like a normal arrow.")
                    .defineInRange("arrowMaxLine", 40.0D, 10.0D, 120.0D);
            b.pop();

            b.push("tension");
            tensionPerRevolutionRun = b.comment("Line tension added per crank revolution while the fish is running. Scaled by the fish's strength (a weak fish strains the line far less than a monster), so this is the value for the strongest fish.")
                    .defineInRange("tensionPerRevolutionRun", 0.30D, 0.0D, 2.0D);
            tensionPerRevolutionCalm = b.comment("Line tension added per crank revolution while the fish is calm.")
                    .defineInRange("tensionPerRevolutionCalm", 0.04D, 0.0D, 2.0D);
            tensionRecoveryPerTick = b.comment("Line tension released per tick while not cranking hard.")
                    .defineInRange("tensionRecoveryPerTick", 0.012D, 0.0D, 0.5D);
            earlyTensionGraceTicks = b.comment("Tension gains ramp in from zero over this many ticks at the start of a fight, so a big fish's opening run can't flash the bar red before anyone can even react. 0 disables the grace.")
                    .defineInRange("earlyTensionGraceTicks", 40, 0, 200);
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
            fishAiWhitelist = b.comment("Water creatures that are NOT vanilla-style fish (they don't extend AbstractFish) but should be treated as fish anyway: lured, spooked, schooled, hooked, and caught. Needed for mods like Unusual Fish whose fish are custom water animals. Same entry format as fishAiBlacklist; the blacklist wins on conflicts. Defaults cover Unusual Fish's actual fish (not its snails, crabs, squids, or jellies).")
                    .defineListAllowEmpty("fishAiWhitelist",
                            java.util.List.of(
                                    "unusualfishmod:aero_mono", "unusualfishmod:amber_goby",
                                    "unusualfishmod:bark_angelfish", "unusualfishmod:beaked_herring",
                                    "unusualfishmod:blindsailfin", "unusualfishmod:blizzardfin",
                                    "unusualfishmod:celestial", "unusualfishmod:circus",
                                    "unusualfishmod:clownthorn_shark", "unusualfishmod:copperflame",
                                    "unusualfishmod:demon_herring", "unusualfishmod:drooping_gourami",
                                    "unusualfishmod:duality_damselfish", "unusualfishmod:eyelash",
                                    "unusualfishmod:forkfish", "unusualfishmod:gnasher",
                                    "unusualfishmod:hatchet_fish", "unusualfishmod:jungleshark",
                                    "unusualfishmod:mossthorn", "unusualfishmod:picklefish",
                                    "unusualfishmod:pinkfin", "unusualfishmod:rhino_tetra",
                                    "unusualfishmod:ripper", "unusualfishmod:roughback_guitarfish",
                                    "unusualfishmod:sailor_barb", "unusualfishmod:sea_pancake",
                                    "unusualfishmod:shockcat", "unusualfishmod:sneep_snorp",
                                    "unusualfishmod:snowflaketail", "unusualfishmod:spindlefish",
                                    "unusualfishmod:spoon_shark", "unusualfishmod:stout_bichir",
                                    "unusualfishmod:tiger_puffer", "unusualfishmod:triple_twirl_pleco",
                                    "unusualfishmod:volt_angler", "unusualfishmod:zebra_cornetfish",
                                    "upgrade_aquatic:pike", "upgrade_aquatic:lionfish",
                                    "upgrade_aquatic:thrasher", "upgrade_aquatic:great_thrasher"),
                            o -> o instanceof String s && s.contains(":"));
            interestRadius = b.comment("Horizontal radius in which fish notice a bobber and swim over. Depth is ignored: a fish on the ocean floor can see a bobber floating any distance above it.")
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
            boatScareRadius = b.comment("Fish scatter from a moving boat within this distance (always, like swimmers). 0 disables boat scares; a boat sitting still never scares anything.")
                    .defineInRange("boatScareRadius", 8.0D, 0.0D, 16.0D);
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
            boidBlacklist = b.comment("Fish that never school boids-style even with boidSchoolingEnabled on — loners like salmon or jellyfish. They keep vanilla's flock-following if they had it, and everything else (luring, scattering, hooking) still applies. Same entry format as fishAiBlacklist.")
                    .defineListAllowEmpty("boidBlacklist",
                            java.util.List.of(
                                    "minecraft:salmon", "minecraft:pufferfish", "aquaculture:jellyfish",
                                    "unusualfishmod:celestial", "unusualfishmod:clownthorn_shark",
                                    "unusualfishmod:gnasher", "unusualfishmod:jungleshark",
                                    "unusualfishmod:mossthorn", "unusualfishmod:pinkfin",
                                    "unusualfishmod:ripper", "unusualfishmod:roughback_guitarfish",
                                    "unusualfishmod:sea_pancake", "unusualfishmod:shockcat",
                                    "unusualfishmod:spoon_shark", "unusualfishmod:stout_bichir",
                                    "unusualfishmod:tiger_puffer", "unusualfishmod:volt_angler",
                                    "upgrade_aquatic:pike", "upgrade_aquatic:lionfish",
                                    "upgrade_aquatic:thrasher", "upgrade_aquatic:great_thrasher"),
                            o -> o instanceof String s && s.contains(":"));
            boidNeighborRadius = b.comment("Radius in which same-species fish count as schoolmates for boids steering.")
                    .defineInRange("boidNeighborRadius", 5.0D, 1.0D, 16.0D);
            boidSpeed = b.comment("Swim speed multiplier for boids schooling (regrouping after a scare is naturally faster).")
                    .defineInRange("boidSpeed", 1.0D, 0.25D, 3.0D);
            fishSwimSoundVolume = b.comment("Volume multiplier for fish swimming sounds — vanilla plays them at full blast and our fish swim fast. 0 silences them entirely.")
                    .defineInRange("fishSwimSoundVolume", 0.1D, 0.0D, 1.0D);
            b.pop();

            b.push("vibration");
            vibrationScareEnabled = b.comment("Fish hear the world the way sculk sensors do: footsteps, swimming, splashes, landing thumps, block breaking — every vanilla vibration in earshot spooks them reliably, event-driven so nothing slips past. Sneaking is silent exactly like with sculk, and wool blocks the signal; no sculk sound or particle is ever played, the fish just listen. Your own casting and reeling are exempt, thrown FOOD keeps its gentler splash-then-attract chum behavior, and land animals other than players are ambient noise fish ignore. Disable to fall back to the old proximity-only scare checks (which still run either way, covering things that make no vibration, like a drifting boat).")
                    .define("vibrationScareEnabled", true);
            vibrationScareRadius = b.comment("How far fish hear vibrations, in blocks (a sculk sensor hears 8). Loud events — explosions, lightning, goat horns, a mob dying — carry half again as far and always cut through even a habituated fish's calm.")
                    .defineInRange("vibrationScareRadius", 8.0D, 2.0D, 24.0D);
            b.pop();

            b.push("species");
            fishProfiles = b.comment("Per-species behavior overrides as 'entityid=trait:value,trait:value'. Traits: boldness (0-1, approach willingness), curiosity (interest gain mult), bite (bite chance mult), strength (fight strength mult), stamina (tires slower >1), food (thrown-food attraction mult), predator (true/false), prey_ratio (min own/prey hitbox area ratio to hunt), depth (surface|open|bottom), cover (0-1 kelp/seagrass lurking), size_min/size_max (species size band replacing the global weight.sizeVariance range — grow undersized species like Aquaculture's tuna to trophy size, or set both to 1 to opt a species out of size variance). Species not listed get sensible size-based defaults: small fish are bold, weak and quick to tire; big fish are wary, strong, and lurk near cover.")
                    .defineListAllowEmpty("fishProfiles",
                            java.util.List.of(
                                    "minecraft:cod=boldness:0.65,curiosity:1.2,depth:open",
                                    "minecraft:salmon=boldness:0.45,strength:1.2,stamina:1.15,predator:true,prey_ratio:3.5,depth:open",
                                    "minecraft:tropical_fish=boldness:0.75,curiosity:1.3,strength:0.8,stamina:0.75,depth:surface,cover:0.5",
                                    "minecraft:pufferfish=boldness:0.3,curiosity:0.7,food:0.6,cover:0.4",
                                    "aquaculture:tuna=size_min:0.9,size_max:2.6,strength:1.25,stamina:1.35,boldness:0.4,depth:open",
                                    "upgrade_aquatic:pike=predator:true,prey_ratio:2.2,strength:1.15,cover:0.7,depth:bottom",
                                    "upgrade_aquatic:lionfish=boldness:0.55,curiosity:0.9,cover:0.5",
                                    "upgrade_aquatic:thrasher=predator:true,strength:1.3,stamina:1.35,boldness:0.5",
                                    "upgrade_aquatic:great_thrasher=predator:true,strength:1.45,stamina:1.5,boldness:0.6",
                                    "unusualfishmod:clownthorn_shark=predator:true,strength:1.35,stamina:1.4,boldness:0.35",
                                    "unusualfishmod:jungleshark=predator:true,strength:1.35,stamina:1.4,boldness:0.35",
                                    "unusualfishmod:spoon_shark=predator:true,strength:1.25,stamina:1.3,boldness:0.35",
                                    "unusualfishmod:gnasher=predator:true,strength:1.2,stamina:1.2,boldness:0.45",
                                    "unusualfishmod:ripper=predator:true,strength:1.2,stamina:1.2,boldness:0.5"),
                            o -> o instanceof String s && s.contains("="));
            foodAttractionEnabled = b.comment("Food items thrown into water attract fish: the splash startles some, then hungry fish come investigate and eat it — and a fed fish's interest in bobbers rises (chumming works).")
                    .define("foodAttractionEnabled", true);
            foodAttractRadius = b.comment("Radius in which fish notice a food item floating in water.")
                    .defineInRange("foodAttractRadius", 8.0D, 2.0D, 24.0D);
            foodStartleChance = b.comment("Chance (scaled down by boldness) that a fish's first reaction to a fresh splash is to bolt briefly before curiosity can win.")
                    .defineInRange("foodStartleChance", 0.4D, 0.0D, 1.0D);
            foodInterestBoost = b.comment("Bobber interest a fish gains when it eats thrown food.")
                    .defineInRange("foodInterestBoost", 0.25D, 0.0D, 1.0D);
            foodSatiationTicks = b.comment("How long a fish that just ate ignores further food.")
                    .defineInRange("foodSatiationTicks", 1600, 0, 24000);
            predationEnabled = b.comment("Predator species (see fishProfiles) hunt, chase, and eat much smaller fish; prey flees and schools scatter.")
                    .define("predationEnabled", true);
            predatorHuntRadius = b.comment("Radius in which a hungry predator looks for prey.")
                    .defineInRange("predatorHuntRadius", 10.0D, 2.0D, 24.0D);
            predationSatiationTicks = b.comment("How long a predator that just ate a fish waits before hunting again.")
                    .defineInRange("predationSatiationTicks", 2400, 200, 24000);
            habitatMovementEnabled = b.comment("Fish idle organically per species: cruising, loitering, depth changes, pauses, and big fish lurking in kelp and seagrass (replaces vanilla's random-swim pathfinding for managed fish).")
                    .define("habitatMovementEnabled", true);
            coldBiteMultiplier = b.comment("Bite/interest multiplier in snowy-cold water (biome cold enough to snow at the bobber). Fish still bite, just noticeably slower.")
                    .defineInRange("coldBiteMultiplier", 0.5D, 0.05D, 1.0D);
            b.pop();

            b.push("weight");
            weightUnit = b.comment("Unit fish weights are shown in (tooltips and the catch message): LBS or KG. Stored data is always metric, so flipping this never changes any fish.")
                    .defineEnum("weightUnit", WeightUnit.LBS);
            sizeVarianceEnabled = b.comment("Every individual fish rolls its own body size (hitbox and model both) — no two salmon alike. Smaller fish are genuinely lighter, weaker fights; bigger ones heavier and harder. Derived from the fish's UUID, so it needs no syncing and survives saves and catch-and-release.")
                    .define("sizeVarianceEnabled", true);
            sizeVarianceMin = b.comment("Smallest body scale a fish can roll.")
                    .defineInRange("sizeVarianceMin", 0.7D, 0.3D, 1.0D);
            sizeVarianceMax = b.comment("Largest body scale a fish can roll (before the giant/dwarf traits, which multiply on top).")
                    .defineInRange("sizeVarianceMax", 1.4D, 1.0D, 2.5D);
            weightPerVolume = b.comment("Kilograms per block-volume of hitbox; weight = width x width x height x this (x density traits). Shown on every caught fish's tooltip.")
                    .defineInRange("weightPerVolume", 25.0D, 1.0D, 500.0D);
            healthScaleExponent = b.comment("Fish max health scales with body size: multiplied by bodyScale^this. At 2.0 a double-size fish has 4x the health and a half-size one a quarter — a 500 lb leviathan tuna is a genuine tank instead of a one-punch kill, and a tiny minnow is fragile. 0 disables (every fish keeps its species' base health).")
                    .defineInRange("healthScaleExponent", 2.0D, 0.0D, 4.0D);
            traitChance = b.comment("Chance a fish is born with a trait (feisty, tough, cosmic...). Traits color the fight, the fish's temperament, its size or heft — and are named on the caught item's tooltip.")
                    .defineInRange("traitChance", 0.25D, 0.0D, 1.0D);
            doubleTraitChance = b.comment("Chance a trait-bearing fish carries a second trait.")
                    .defineInRange("doubleTraitChance", 0.1D, 0.0D, 1.0D);
            traitAurasEnabled = b.comment("Special-trait fish (cosmic, glimmering, molten...) shed ambient particles so you can spot them in the water.")
                    .define("traitAurasEnabled", true);
            b.pop();

            b.push("net");
            netCatchRadius = b.comment("Radius around the struck water (or targeted fish) in which the fishing net can scoop fish.")
                    .defineInRange("netCatchRadius", 2.5D, 1.0D, 6.0D);
            netCatchChance = b.comment("Base net catch chance, for a small fish taken by surprise. The real odds scale hard with weight (under 2 lbs full, 2-5 lbs x0.6, 5-10 x0.25, 10-15 x0.08, 15+ x0.03 — trophies burst out of a hand net), drop when the fish sees the sweep coming (approach from behind, or sneak up), rise when it's distracted by someone's bobber, and all but vanish on a fleeing fish. Netting a fish another player has hooked always lands it for them.")
                    .defineInRange("netCatchChance", 0.55D, 0.0D, 1.0D);
            netCooldownTicks = b.comment("Cooldown between net sweeps.")
                    .defineInRange("netCooldownTicks", 60, 0, 400);
            b.pop();

            b.push("trap");
            trapCheckIntervalTicks = b.comment("Ticks between a placed fish trap's catch attempts. Traps are meant to be slow, passive fishing.")
                    .defineInRange("trapCheckIntervalTicks", 600, 100, 24000);
            trapCatchChance = b.comment("Chance per attempt that a real fish swimming inside the trap's catch radius is caught.")
                    .defineInRange("trapCatchChance", 0.35D, 0.0D, 1.0D);
            trapCatchRadius = b.comment("Radius around the trap in which a fish can be caught by it.")
                    .defineInRange("trapCatchRadius", 2.5D, 1.0D, 6.0D);
            trapAttractRadius = b.comment("Radius in which a baited trap draws fish in to investigate (they treat it like food).")
                    .defineInRange("trapAttractRadius", 10.0D, 2.0D, 24.0D);
            trapAmbientCatchChance = b.comment("Chance per attempt that a baited trap catches a biome-appropriate fish even with none swimming nearby. Keeps traps slowly productive without replacing rod fishing.")
                    .defineInRange("trapAmbientCatchChance", 0.05D, 0.0D, 1.0D);
            trapBaitMultiplier = b.comment("Catch chance multiplier while the trap holds bait (any food item). Each catch consumes one bait.")
                    .defineInRange("trapBaitMultiplier", 1.8D, 1.0D, 5.0D);
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
                    .defineInRange("schoolSpawnWeightMultiplier", 2.5D, 0.1D, 10.0D);
            extraSpawningEnabled = b.comment("Supplemental fish spawning: when the water around a player runs light on fish, spawn a fresh school out of sight. Uses its own accounting (the count below) and never touches vanilla mob caps or other mobs' spawning.")
                    .define("extraSpawningEnabled", true);
            extraSpawnIntervalTicks = b.comment("Ticks between supplemental spawn checks per player.")
                    .defineInRange("extraSpawnIntervalTicks", 200, 40, 6000);
            extraSpawnTargetCount = b.comment("The fish population the supplemental spawner maintains within its radius of each player. No spawns happen while at least this many fish are around.")
                    .defineInRange("extraSpawnTargetCount", 26, 4, 128);
            extraSpawnRadius = b.comment("Horizontal radius of the supplemental spawner's population count and spawn placement (schools appear 24 blocks out or further, never in your face).")
                    .defineInRange("extraSpawnRadius", 48, 32, 96);
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
            doubleHookChanceMultiplier = b.comment("Multiplier on Aquaculture hooks' double-catch chance. Their double hook reports only 10% — tuned for doubling whole loot hauls, invisible when each fight lands one fish — so 3.5 gives it a meaningful ~35% chance to double a caught fish (announced with 'x2!' on the catch line).")
                    .defineInRange("doubleHookChanceMultiplier", 3.5D, 1.0D, 10.0D);
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
        public final ForgeConfigSpec.IntValue castCooldownTicks;
        public final ForgeConfigSpec.BooleanValue bobberHookInMouth;
        public final ForgeConfigSpec.DoubleValue bobberHookRotationDegrees;
        public final ForgeConfigSpec.DoubleValue hookSetJoltDegrees;
        public final ForgeConfigSpec.BooleanValue fishPitchEnabled;
        public final ForgeConfigSpec.BooleanValue rodAnimationsEnabled;
        public final ForgeConfigSpec.BooleanValue fishCarryEnabled;
        public final ForgeConfigSpec.DoubleValue scrollReelPerNotch;

        Client(ForgeConfigSpec.Builder b)
        {
            chargeSweepTicks = b.comment("Ticks for the cast power bar to sweep from empty to full (it then falls back down).")
                    .defineInRange("chargeSweepTicks", 30, 5, 200);
            reelSensitivity = b.comment("Multiplier on how much reeling you get out of your mouse motion.")
                    .defineInRange("reelSensitivity", 1.0D, 0.1D, 5.0D);
            requireCircularMotion = b.comment("Accessibility. If true (the default), reeling ALWAYS requires circling the mouse — holding right-click alone never reels, in fights or plain retrieves. Set false if circular mouse motion is difficult: holding right-click then reels at a steady moderate speed by itself (circling still reels faster).")
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
            castCooldownTicks = b.comment("Cooldown (ticks) after a fight ends or you finish reeling your line in before you can charge another cast, so a held right-click doesn't fling the line straight back out.")
                    .defineInRange("castCooldownTicks", 12, 0, 100);
            bobberHookInMouth = b.comment("When a fish is hooked, roll the bobber so its hook clips up into the fish's mouth (cosmetic). The bobber is also slid forward to the fish's snout regardless of this setting.")
                    .define("bobberHookInMouth", true);
            bobberHookRotationDegrees = b.comment("Degrees to roll the hooked bobber in the screen plane so the hook points into the fish. 180 flips it so the hook (bottom of the bobber sprite) points up into the mouth; 0 disables the roll.")
                    .defineInRange("bobberHookRotationDegrees", 180.0D, 0.0D, 360.0D);
            hookSetJoltDegrees = b.comment("How hard you must yank the view sideways (degrees of quick yaw motion) to set the hook when a fish bites. Lower = easier.")
                    .defineInRange("hookSetJoltDegrees", 22.0D, 5.0D, 90.0D);
            fishPitchEnabled = b.comment("Fish visually pitch their nose up and down with their vertical swimming (cosmetic, smoothed).")
                    .define("fishPitchEnabled", true);
            rodAnimationsEnabled = b.comment("First-person rod animations: draw-back while charging a cast, the cast whip, bite twitches, and strain/shake/lift/crank motion during the fight.")
                    .define("rodAnimationsEnabled", true);
            fishCarryEnabled = b.comment("First-person fish carrying: a light fish held in one hand dangles by the tail; a heavy one (8+ lbs) is hefted low with both hands.")
                    .define("fishCarryEnabled", true);
            scrollReelPerNotch = b.comment("Crank revolutions added per scroll-wheel notch while working the reel (scroll DOWN = wind line in, scroll UP = deliberately pay line out, easing tension at the cost of ground — matching a real reel handle's motion). 0 disables scroll controls entirely.")
                    .defineInRange("scrollReelPerNotch", 0.12D, 0.0D, 1.0D);
        }
    }
}
