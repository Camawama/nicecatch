package net.camacraft.nicecatch;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Kills the oldest foot-gun in the project: Forge server configs live per-world, and an
 * existing world keeps whatever value a key had when it was first written — so every time
 * a shipped default was retuned, old worlds silently played on stale numbers until someone
 * hand-edited a toml. Now each config file carries a version stamp; when a file from an
 * older generation loads, any value STILL SITTING ON ITS OLD DEFAULT is moved to the new
 * one. A value the user customized never matches the old default, so it is never touched.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigMigrations
{
    private ConfigMigrations() {}

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event)
    {
        if (event.getConfig().getSpec() != NiceCatchConfig.SERVER_SPEC) return;
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        int version = cfg.configVersion.get();
        if (version >= NiceCatchConfig.Server.CURRENT_CONFIG_VERSION) return;

        if (version < 2) {
            // Every default retuned since these keys first shipped.
            migrate(cfg.biteChancePerSecond, 0.10D, 0.22D);
            migrate(cfg.scatterOnHookChance, 0.35D, 0.2D);
            migrate(cfg.dartWindowTicks, 25, 35);
            migrate(cfg.netCooldownTicks, 40, 60);
            migrate(cfg.schoolSpawnWeightMultiplier, 1.0D, 2.5D);
            migrate(cfg.soundPhaseMinWeightLbs, 25.0D, 5.0D);
            migrate(cfg.tensionPerRevolutionRun, 0.30D, 0.24D);
            migrate(cfg.tensionRecoveryPerTick, 0.012D, 0.016D);
        }

        cfg.configVersion.set(NiceCatchConfig.Server.CURRENT_CONFIG_VERSION);
    }

    private static void migrate(ForgeConfigSpec.DoubleValue value, double oldDefault, double newDefault)
    {
        if (Math.abs(value.get() - oldDefault) < 1.0E-9D) {
            value.set(newDefault);
        }
    }

    private static void migrate(ForgeConfigSpec.IntValue value, int oldDefault, int newDefault)
    {
        if (value.get() == oldDefault) {
            value.set(newDefault);
        }
    }
}
