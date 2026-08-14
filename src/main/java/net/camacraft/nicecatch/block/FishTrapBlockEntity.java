package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.menu.FishTrapMenu;
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
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 * The trap's working parts, behind a real inventory: right-click opens its dedicated GUI —
 * the bait slot alone on the left, the 2x2 haul grid on the right. Every so often a BAITED
 * trap rolls a catch attempt: a real fish loitering inside the catch radius may be caught
 * (converted to its item and stored), and a baited trap in empty water occasionally
 * produces a biome-appropriate small fish on its own. An unbaited trap catches NOTHING,
 * and the bait sets the ceiling on what it can take: plain food brings in small fry only,
 * while a caught fish used as bait takes fish up to several times its own weight — feed
 * the trap upward, crumbs to minnows to keepers to trophies. Bait is consumed one per
 * catch and, via the fish AI's food-interest goal, actively draws fish over to nose around
 * the trap; that investigation is exactly what puts them inside the catch radius.
 *
 * Deliberately slow: it's the lazy fisher's method and must never outfish a rod.
 */
public class FishTrapBlockEntity extends BlockEntity implements Container, MenuProvider
{
    public static final int BAIT_SLOT = 0;
    public static final int STORAGE_START = 1;
    public static final int SLOT_COUNT = 5;

    /** Baited traps by level, so fish AI can find them without scanning blocks. */
    private static final Map<Level, Set<BlockPos>> BAITED_TRAPS = new WeakHashMap<>();

    /** Slot 0 = bait; 1..4 = the haul. */
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
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
                && !trap.getItem(BAIT_SLOT).isEmpty();
    }

    private void updateRegistry()
    {
        if (level == null || level.isClientSide) return;
        Set<BlockPos> traps = BAITED_TRAPS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
        if (getItem(BAIT_SLOT).isEmpty() || isRemoved()) {
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

    /** Spill everything (catches and remaining bait) when the block is broken. */
    public void dropContents()
    {
        if (level == null || level.isClientSide) return;
        Containers.dropContents(level, worldPosition, items);
        items.replaceAll(stack -> ItemStack.EMPTY);
        updateRegistry();
    }

    // ---- Container (backing the menu) ----

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty()
    {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent()
    {
        items.replaceAll(stack -> ItemStack.EMPTY);
        setChanged();
    }

    /** Every inventory mutation lands here — the moment bait appears or runs out, the fish
     * AI's trap registry follows. */
    @Override
    public void setChanged()
    {
        super.setChanged();
        updateRegistry();
    }

    // ---- Menu ----

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("block.nicecatch.fish_trap");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player)
    {
        return new FishTrapMenu(windowId, playerInventory, this);
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

        // No bait, no catches, period: a trap catches because something in it was worth
        // swimming into, and the bait sets the ceiling on what bothers — plain food takes
        // only small fry; a fish in the bait slot takes fish up to several times its own
        // weight. Feed the trap upward: crumbs, then minnows, then keepers, then trophies.
        ItemStack bait = trap.getItem(BAIT_SLOT);
        if (bait.isEmpty()) return;
        float chance = cfg.trapCatchChance.get().floatValue();
        double maxLbs = baitMaxCatchLbs(bait);

        Vec3 center = Vec3.atCenterOf(pos);
        double radius = cfg.trapCatchRadius.get();
        List<PathfinderMob> near = serverLevel.getEntitiesOfClass(PathfinderMob.class,
                AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D),
                f -> f.isAlive() && f.isInWater() && FishBehavior.isFishLike(f) && !FishBehavior.isHooked(f)
                        && net.camacraft.nicecatch.server.FishSizing.weightLbs(f) <= maxLbs);

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
        if (random.nextFloat() < cfg.trapAmbientCatchChance.get().floatValue()) {
            ItemStack ambient = ambientCatch(serverLevel, pos, random);
            if (!ambient.isEmpty() && trap.store(ambient)) {
                serverLevel.playSound(null, pos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.3F, 0.8F);
                trap.consumeBait();
            }
        }
    }

    /**
     * The heaviest fish (lbs) this bait can bring into the trap. A caught fish carries its
     * stamped weight; a fresh-off-the-loot-table fish item counts as a small one; anything
     * that is merely food caps out at small fry. This is the trap's whole progression:
     * what you feed it decides what it can take.
     */
    private static double baitMaxCatchLbs(ItemStack bait)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        CompoundTag info = bait.getTagElement("NiceCatch");
        if (info != null && info.contains("Weight")) {
            return info.getFloat("Weight") * net.camacraft.nicecatch.server.FishSizing.KG_TO_LBS
                    * cfg.trapBaitWeightMultiplier.get();
        }
        if (bait.is(net.minecraft.tags.ItemTags.FISHES)) {
            return 1.5D * cfg.trapBaitWeightMultiplier.get(); // un-stamped fish: a small one
        }
        return cfg.trapFoodBaitMaxLbs.get();
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

    /** Merge a catch into the haul slots; false (nothing taken) when the trap is stuffed full. */
    private boolean store(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        for (int i = STORAGE_START; i < SLOT_COUNT; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, stack);
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
        ItemStack bait = items.get(BAIT_SLOT);
        if (bait.isEmpty()) return;
        bait.shrink(1);
        setChanged();
    }

    /** The bait slot takes what fish would eat. Used by the menu's slot filter too. */
    public static boolean isBait(ItemStack stack)
    {
        return FoodInterestGoal.isFishFood(stack);
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("CheckIn", checkIn);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        items.replaceAll(stack -> ItemStack.EMPTY);
        if (tag.contains("Items")) {
            ContainerHelper.loadAllItems(tag, items);
        } else {
            // Pre-GUI traps saved a separate Storage list and Bait stack; migrate them.
            ListTag legacy = tag.getList("Storage", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(legacy.size(), SLOT_COUNT - STORAGE_START); i++) {
                items.set(STORAGE_START + i, ItemStack.of(legacy.getCompound(i)));
            }
            items.set(BAIT_SLOT, ItemStack.of(tag.getCompound("Bait")));
        }
        checkIn = Math.max(20, tag.getInt("CheckIn"));
    }
}
