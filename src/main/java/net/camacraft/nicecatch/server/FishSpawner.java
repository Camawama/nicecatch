package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Supplemental fish spawning with its own accounting. Vanilla's WATER_AMBIENT cap keeps fish
 * scarce and refills slowly, which starves a fishing-focused mod; but simply raising mob caps
 * would trample every other mod's spawning. Instead, this spawner periodically counts the
 * fish around each player and, when the water runs light, drops in one school — species and
 * group size straight from the biome's own spawn list (with the school-size boost applied),
 * spawn rules respected, spawned as NATURAL so ordinary despawning still cleans them up.
 *
 * It never touches mob caps, never blocks another spawn, and never exceeds its own target,
 * so an area can't silt up with fish. Spawns land 24+ blocks out, out of the player's face.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class FishSpawner
{
    /** Surface sample attempts per spawn cycle; misses (land, shallow water) just skip the cycle. */
    private static final int PLACEMENT_TRIES = 6;
    /** Minimum water depth for a school to appear. */
    private static final int MIN_DEPTH = 2;
    /** Deepest below the surface a school will be placed. */
    private static final int MAX_SPAWN_DEPTH = 12;

    private FishSpawner() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        if (!cfg.extraSpawningEnabled.get()) return;
        if (!(event.player instanceof ServerPlayer player) || player.isSpectator()) return;

        int interval = cfg.extraSpawnIntervalTicks.get();
        if ((player.tickCount + (player.getId() & 0xFFFF)) % interval != 0) return;

        ServerLevel level = player.serverLevel();
        double radius = cfg.extraSpawnRadius.get();

        // Our own accounting: every fish-kind entity in the column around the player counts.
        AABB box = player.getBoundingBox().inflate(radius, radius * 0.75D, radius);
        int count = level.getEntitiesOfClass(PathfinderMob.class, box, FishBehavior::isFishKind).size();
        int target = cfg.extraSpawnTargetCount.get();
        if (count >= target) return;

        trySpawnSchool(level, player, radius, target - count);
    }

    private static void trySpawnSchool(ServerLevel level, ServerPlayer player, double radius, int room)
    {
        RandomSource random = level.random;
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
            // Ring placement: never closer than 24 blocks, so schools don't pop in on screen.
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 24.0D + random.nextDouble() * Math.max(1.0D, radius - 24.0D);
            int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z))) continue;

            // Find the water surface, then make sure the column is deep enough to live in.
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            BlockPos surface = new BlockPos(x, surfaceY, z);
            if (!level.getFluidState(surface).is(FluidTags.WATER)) continue;
            int depth = 0;
            while (depth < MAX_SPAWN_DEPTH
                    && level.getFluidState(surface.below(depth + 1)).is(FluidTags.WATER)) {
                depth++;
            }
            if (depth < MIN_DEPTH) continue;

            BlockPos spawnPos = surface.below(1 + random.nextInt(depth));
            if (spawnSchoolAt(level, spawnPos, room, random)) return;
        }
    }

    /** Picks a species from the biome's own water-ambient list and spawns one school of it. */
    private static boolean spawnSchoolAt(ServerLevel level, BlockPos pos, int room, RandomSource random)
    {
        var mobs = level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.WATER_AMBIENT);
        var picked = mobs.getRandom(random);
        if (picked.isEmpty()) return false;

        // The same school-size boost the natural spawner sees, so group sizes match config.
        MobSpawnSettings.SpawnerData data = FishBehavior.boostedSpawn(picked.get());
        if (!SpawnPlacements.checkSpawnRules(data.type, level, MobSpawnType.NATURAL, pos, random)) {
            return false;
        }

        int size = data.minCount + random.nextInt(Math.max(1, data.maxCount - data.minCount + 1));
        size = Math.min(size, room);
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            BlockPos memberPos = i == 0 ? pos : pos.offset(
                    random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
            if (!level.getFluidState(memberPos).is(FluidTags.WATER)) continue;

            var entity = data.type.create(level);
            if (!(entity instanceof PathfinderMob fish) || !FishBehavior.isFishKind(fish)) {
                // The biome listed something that isn't a fish (some mods do); leave it to vanilla.
                if (entity != null) entity.discard();
                return false;
            }
            fish.moveTo(memberPos.getX() + 0.5D, memberPos.getY() + 0.5D, memberPos.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            // finalizeSpawn gives species their natural variation (tropical fish patterns etc).
            fish.finalizeSpawn(level, level.getCurrentDifficultyAt(memberPos),
                    MobSpawnType.NATURAL, null, null);
            level.addFreshEntity(fish);
            spawned++;
        }
        return spawned > 0;
    }
}
