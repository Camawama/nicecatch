package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.registry.ModBlockEntities;
import net.camacraft.nicecatch.server.FishBehavior;
import net.camacraft.nicecatch.server.FishConversion;
import net.camacraft.nicecatch.server.goal.FoodInterestGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The trap's working parts. Every so often it rolls a catch attempt: a real fish loitering
 * inside the catch radius may be caught (converted to its item and stored), and a baited
 * trap in empty water occasionally produces a biome-appropriate fish on its own. Bait —
 * any food item — multiplies the odds, is consumed one per catch, and, via the fish AI's
 * food-interest goal, actively draws fish over to nose around the trap; that investigation
 * is exactly what puts them inside the catch radius.
 *
 * Deliberately slow: it's the lazy fisher's method and must never outfish a rod.
 */
public class FishTrapBlockEntity extends BlockEntity
{
    /** Storage stacks; a full trap stops catching until emptied. */
    private static final int STORAGE_SLOTS = 4;
    private static final int MAX_BAIT = 16;

    /** Baited traps by level, so fish AI can find them without scanning blocks. */
    private static final Map<Level, Set<BlockPos>> BAITED_TRAPS = new WeakHashMap<>();

    private final NonNullList<ItemStack> storage = NonNullList.withSize(STORAGE_SLOTS, ItemStack.EMPTY);
    private ItemStack bait = ItemStack.EMPTY;
    private int checkIn = 100;

    public FishTrapBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.FISH_TRAP.get(), pos, state);
    }

    // ---- Fish-AI queries ----

    /** Nearest baited trap within radius, for fish deciding where to nose around. */
    @Nullable
    public static BlockPos findBaitedTrapNear(Level level, BlockPos near, double radius)
    {
        Set<BlockPos> traps = BAITED_TRAPS.get(level);
        if (traps == null || traps.isEmpty()) return null;
        BlockPos best = null;
        double bestDist = radius * radius;
        for (BlockPos pos : traps) {
            double d = pos.distSqr(near);
            if (d <= bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    /** Still a baited, loaded trap? Fish stop caring the moment the bait runs out. */
    public static boolean isBaitedTrap(Level level, BlockPos pos)
    {
        Set<BlockPos> traps = BAITED_TRAPS.get(level);
        return traps != null && traps.contains(pos)
                && level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof FishTrapBlockEntity trap
                && !trap.bait.isEmpty();
    }

    private void updateRegistry()
    {
        if (level == null || level.isClientSide) return;
        Set<BlockPos> traps = BAITED_TRAPS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
        if (bait.isEmpty() || isRemoved()) {
            traps.remove(worldPosition);
        } else {
            traps.add(worldPosition);
        }
    }

    @Override
    public void onLoad()
    {
        super.onLoad();
        updateRegistry();
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            Set<BlockPos> traps = BAITED_TRAPS.get(level);
            if (traps != null) traps.remove(worldPosition);
        }
    }

    // ---- Player interaction ----

    /** Food in hand baits the trap; an empty(ish) hand collects the haul. */
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);
        if (FoodInterestGoal.isFishFood(held)) {
            int room = bait.isEmpty() ? MAX_BAIT
                    : ItemStack.isSameItemSameTags(bait, held) ? MAX_BAIT - bait.getCount() : 0;
            if (room <= 0) {
                player.displayClientMessage(Component.translatable("nicecatch.trap.bait_full"), true);
                return InteractionResult.CONSUME;
            }
            int moved = Math.min(room, held.getCount());
            if (bait.isEmpty()) {
                bait = held.copyWithCount(moved);
            } else {
                bait.grow(moved);
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(moved);
            }
            setChanged();
            updateRegistry();
            level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5F, 1.4F);
            player.displayClientMessage(Component.translatable("nicecatch.trap.baited", bait.getCount()), true);
            return InteractionResult.CONSUME;
        }

        boolean any = false;
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.get(i);
            if (stack.isEmpty()) continue;
            any = true;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            storage.set(i, ItemStack.EMPTY);
        }
        if (any) {
            setChanged();
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.1F);
            player.displayClientMessage(Component.translatable("nicecatch.trap.collected"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    bait.isEmpty() ? "nicecatch.trap.empty" : "nicecatch.trap.waiting"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Spill everything (catches and remaining bait) when the block is broken. */
    public void dropContents()
    {
        if (level == null || level.isClientSide) return;
        NonNullList<ItemStack> all = NonNullList.create();
        all.addAll(storage);
        all.add(bait);
        Containers.dropContents(level, worldPosition, all);
        for (int i = 0; i < storage.size(); i++) {
            storage.set(i, ItemStack.EMPTY); // fixed-size list: no clear()
        }
        bait = ItemStack.EMPTY;
    }

    // ---- The trap doing its job ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, FishTrapBlockEntity trap)
    {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!state.hasProperty(FishTrapBlock.WATERLOGGED) || !state.getValue(FishTrapBlock.WATERLOGGED)) return;
        if (--trap.checkIn > 0) return;

        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        RandomSource random = serverLevel.random;
        int interval = cfg.trapCheckIntervalTicks.get();
        trap.checkIn = interval - interval / 4 + random.nextInt(Math.max(1, interval / 2));

        boolean baited = !trap.bait.isEmpty();
        float chance = cfg.trapCatchChance.get().floatValue()
                * (baited ? cfg.trapBaitMultiplier.get().floatValue() : 1.0F);

        Vec3 center = Vec3.atCenterOf(pos);
        double radius = cfg.trapCatchRadius.get();
        List<PathfinderMob> near = serverLevel.getEntitiesOfClass(PathfinderMob.class,
                AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D),
                f -> f.isAlive() && f.isInWater() && FishBehavior.isFishLike(f) && !FishBehavior.isHooked(f));

        if (!near.isEmpty() && random.nextFloat() < chance) {
            PathfinderMob caught = near.get(random.nextInt(near.size()));
            if (trap.store(FishConversion.itemForFish(serverLevel, caught))) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE, caught.getX(), caught.getY() + 0.2D, caught.getZ(),
                        6, 0.15D, 0.1D, 0.15D, 0.03D);
                serverLevel.playSound(null, pos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.4F, 0.9F);
                caught.discard();
                trap.consumeBait();
            }
            return;
        }

        // Empty water, but the bait's scent still drifts: the odd fish finds its way in.
        if (baited && random.nextFloat() < cfg.trapAmbientCatchChance.get().floatValue()) {
            ItemStack ambient = ambientCatch(serverLevel, pos, random);
            if (!ambient.isEmpty() && trap.store(ambient)) {
                serverLevel.playSound(null, pos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.3F, 0.8F);
                trap.consumeBait();
            }
        }
    }

    /** A biome-appropriate fish item, by the entity-id/item-id naming convention; empty if none. */
    private static ItemStack ambientCatch(ServerLevel level, BlockPos pos, RandomSource random)
    {
        var mobs = level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.WATER_AMBIENT);
        var picked = mobs.getRandom(random);
        if (picked.isEmpty()) return ItemStack.EMPTY;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(picked.get().type);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && item != net.minecraft.world.item.Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
    }

    /** Merge a catch into storage; false (nothing taken) when the trap is stuffed full. */
    private boolean store(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        for (int i = 0; i < storage.size(); i++) {
            ItemStack slot = storage.get(i);
            if (slot.isEmpty()) {
                storage.set(i, stack);
                setChanged();
                return true;
            }
            if (ItemStack.isSameItemSameTags(slot, stack)
                    && slot.getCount() + stack.getCount() <= slot.getMaxStackSize()) {
                slot.grow(stack.getCount());
                setChanged();
                return true;
            }
        }
        return false;
    }

    private void consumeBait()
    {
        if (bait.isEmpty()) return;
        bait.shrink(1);
        setChanged();
        if (bait.isEmpty()) {
            updateRegistry();
        }
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (ItemStack stack : storage) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("Storage", list);
        tag.put("Bait", bait.save(new CompoundTag()));
        tag.putInt("CheckIn", checkIn);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        ListTag list = tag.getList("Storage", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < storage.size(); i++) {
            storage.set(i, i < list.size() ? ItemStack.of(list.getCompound(i)) : ItemStack.EMPTY);
        }
        bait = ItemStack.of(tag.getCompound("Bait"));
        checkIn = Math.max(20, tag.getInt("CheckIn"));
    }
}
