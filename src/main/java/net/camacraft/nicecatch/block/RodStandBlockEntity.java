package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.registry.ModBlockEntities;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The rod stand's working parts. The one deep trick: a parked line's FishingHook stays
 * alive with nobody holding the rod — a mixin suppresses vanilla's give-up check for
 * hooks registered here — and the owner's {@code player.fishing} slot is freed so they
 * can cast another rod entirely. The stand runs its own patient bite courting on the
 * parked line (fish are attracted, claim it, and strike it exactly as they would a held
 * one), rings its bell when something takes it, and hands the fight over the instant the
 * rod is snatched back.
 */
public class RodStandBlockEntity extends BlockEntity
{
    /** Every stand-held hook, server-side; the FishingHook mixin consults this to stay alive. */
    private static final Map<FishingHook, BlockPos> HELD =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ItemStack rod = ItemStack.EMPTY;
    private boolean bell;
    @Nullable private UUID hookUuid;
    @Nullable private UUID ownerUuid;
    /** Runtime entity id of the hook, for the client's line rendering; -1 = none. */
    private int hookId = -1;
    @Nullable private UUID biteFishUuid;
    private long biteUntil;
    private int courtIn = 20;

    public RodStandBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.ROD_STAND.get(), pos, state);
    }

    /** Whether this hook belongs to a rod stand (its line must not self-retract). */
    public static boolean isStandHeld(FishingHook hook)
    {
        return HELD.containsKey(hook);
    }

    public boolean hasRod()
    {
        return !rod.isEmpty();
    }

    /** The parked rod, for the stand's renderer (the ACTUAL item, glint and all). */
    public ItemStack getRod()
    {
        return rod;
    }

    /** The parked line's hook entity id as the client knows it; -1 when no line. */
    public int clientHookId()
    {
        return hookId;
    }

    public boolean hasBell()
    {
        return bell;
    }

    public void setBell(boolean value)
    {
        bell = value;
        sync();
    }

    // ---- Parking & taking ----

    void parkRod(Player player, InteractionHand hand)
    {
        FishingHook hook = player.fishing;
        if (hook == null || level == null) return;
        rod = player.getItemInHand(hand).copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        hookUuid = hook.getUUID();
        hookId = hook.getId();
        ownerUuid = player.getUUID();
        HELD.put(hook, worldPosition);
        // Free the owner's hands: with the fishing slot cleared they can cast another rod.
        player.fishing = null;
        level.setBlock(worldPosition, getBlockState().setValue(RodStandBlock.HAS_ROD, true), 3);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 0.9F);
        sync();
    }

    void takeRod(Player player, InteractionHand hand)
    {
        if (level == null) return;
        ItemStack taken = rod;
        rod = ItemStack.EMPTY;
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, taken);
        } else if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }

        FishingHook hook = resolveHook();
        PathfinderMob biter = resolveBiteFish();
        releaseHook();
        if (hook != null && hook.isAlive() && player instanceof ServerPlayer serverPlayer) {
            // The line follows the rod back into the owner's hands.
            player.fishing = hook;
            if (biter != null && biteUntil > level.getGameTime()) {
                // Something is ON: grabbing the rod is the hook-set; fight, immediately.
                ServerFishingManager.startStandFight(serverPlayer, hook, biter);
            }
        }
        biteFishUuid = null;
        biteUntil = 0L;
        level.setBlock(worldPosition, getBlockState().setValue(RodStandBlock.HAS_ROD, false), 3);
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        sync();
    }

    /** Break/pop: rod and bell drop; the line (if any) is simply gone with its stand. */
    void spill()
    {
        if (level == null || level.isClientSide) return;
        if (!rod.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), rod);
            rod = ItemStack.EMPTY;
        }
        if (bell) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    new ItemStack(Items.BELL));
            bell = false;
        }
        FishingHook hook = resolveHook();
        releaseHook();
        if (hook != null) hook.discard();
    }

    private void releaseHook()
    {
        FishingHook hook = resolveHook();
        if (hook != null) HELD.remove(hook);
        hookUuid = null;
        hookId = -1;
    }

    @Nullable
    private FishingHook resolveHook()
    {
        if (hookUuid == null || !(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getEntity(hookUuid) instanceof FishingHook hook && hook.isAlive() ? hook : null;
    }

    @Nullable
    private PathfinderMob resolveBiteFish()
    {
        if (biteFishUuid == null || !(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getEntity(biteFishUuid) instanceof PathfinderMob fish && fish.isAlive() ? fish : null;
    }

    // ---- The stand fishing on its own ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, RodStandBlockEntity stand)
    {
        if (!(level instanceof ServerLevel serverLevel) || stand.rod.isEmpty()) return;
        FishingHook hook = stand.resolveHook();
        if (hook == null) return; // hook unloaded or long gone: the stand just holds a rod

        // Keep the stand registered even across a server restart's lost runtime map.
        HELD.putIfAbsent(hook, pos);
        if (stand.hookId != hook.getId()) {
            stand.hookId = hook.getId();
            stand.sync();
        }
        // No vanilla loot bites on a parked line; real fish only.
        hook.timeUntilHooked = 0;
        if (hook.timeUntilLured < 100) hook.timeUntilLured = 100;

        long now = level.getGameTime();
        if (stand.biteFishUuid != null) {
            PathfinderMob biter = stand.resolveBiteFish();
            if (biter == null || now >= stand.biteUntil) {
                // Spat the hook; nobody came. The fish sulks off with a normal bite cooldown.
                if (biter != null) {
                    FishBehavior.state(biter).biteBobber = null;
                    FishBehavior.scatter(biter, hook.position(), 40, FishBehavior.LIGHT_SCARE_COOLDOWN);
                }
                stand.biteFishUuid = null;
                stand.biteUntil = 0L;
            } else if ((now & 15) == 0) {
                // The bobber worries the whole window: unmissable from a distance.
                hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.1D, 0.0D));
                serverLevel.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY(), hook.getZ(),
                        3, 0.15D, 0.05D, 0.15D, 0.0D);
            }
            return;
        }

        if (--stand.courtIn > 0) return;
        stand.courtIn = 20;
        if (hook.currentState != FishingHook.FishHookState.BOBBING) return;

        // The same fish that were drawn to the line pick their moment. A parked line bites
        // a little less often than a worked one — nobody is teasing the bait.
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        List<PathfinderMob> candidates = FishBehavior.biteCandidates(hook, Math.max(cfg.biteRange.get(), 5.0D));
        if (candidates.isEmpty()) return;
        float chance = cfg.biteChancePerSecond.get().floatValue() * 0.7F
                * FishBehavior.coldFactor(level, hook.blockPosition())
                * FishBehavior.weatherFactor(level, hook.blockPosition());
        if (serverLevel.random.nextFloat() >= chance) return;

        PathfinderMob biter = candidates.get(serverLevel.random.nextInt(candidates.size()));
        stand.biteFishUuid = biter.getUUID();
        stand.biteUntil = now + cfg.standBiteWindowTicks.get();
        FishBehavior.state(biter).biteBobber = hook;
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -0.2D, 0.0D));
        serverLevel.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY(), hook.getZ(),
                6, 0.2D, 0.1D, 0.2D, 0.0D);
        serverLevel.playSound(null, hook.getX(), hook.getY(), hook.getZ(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.5F, 0.9F);
        if (stand.bell) {
            serverLevel.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 1.0F);
        }
    }

    // ---- NBT & sync ----

    private void sync()
    {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (!rod.isEmpty()) tag.put("Rod", rod.save(new CompoundTag()));
        tag.putBoolean("Bell", bell);
        if (hookUuid != null) tag.putUUID("Hook", hookUuid);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("HookId", hookId);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        rod = tag.contains("Rod") ? ItemStack.of(tag.getCompound("Rod")) : ItemStack.EMPTY;
        bell = tag.getBoolean("Bell");
        hookUuid = tag.hasUUID("Hook") ? tag.getUUID("Hook") : null;
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        hookId = tag.contains("HookId") ? tag.getInt("HookId") : -1;
        if (level != null && level.isClientSide) {
            clientApply();
        }
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet)
    {
        if (packet.getTag() != null) {
            load(packet.getTag());
        }
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    net.camacraft.nicecatch.client.StandLines.clear(worldPosition));
        }
    }

    /** Client side of the sync: the line renderer's stand registry and the owner's hands. */
    private void clientApply()
    {
        int id = hookId;
        UUID owner = ownerUuid;
        boolean held = !rod.isEmpty() && id >= 0;
        BlockPos pos = worldPosition;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                net.camacraft.nicecatch.client.StandLines.update(pos, id, owner, held));
    }
}
