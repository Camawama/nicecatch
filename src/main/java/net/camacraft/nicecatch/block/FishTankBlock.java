package net.camacraft.nicecatch.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The fish tank: a glass water block for building aquariums. Tanks placed against each
 * other CONNECT — the shared glass walls and the water seams between them vanish (the same
 * face-culling trick vanilla glass uses), so a 2x2x2 or 3x3x3 of tanks reads as one glass
 * box holding one body of water. Right-click any tank with a LIVE fish — a caught
 * (stamped) fish, a fish bucket, or a fish spawn egg — and the actual fish swims the whole
 * connected tank, at its true stamped size (fitted if it's a monster). Empty hand takes it
 * back, NBT intact.
 */
public class FishTankBlock extends BaseEntityBlock
{
    // One connection flag per side: a connected side renders neither glass frame nor
    // interior faces, which is what makes a bank of tanks read as ONE seamless aquarium.
    public static final java.util.Map<Direction, net.minecraft.world.level.block.state.properties.BooleanProperty>
            CONNECTED = java.util.Map.of(
                    Direction.NORTH, net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH,
                    Direction.SOUTH, net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH,
                    Direction.EAST, net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,
                    Direction.WEST, net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,
                    Direction.UP, net.minecraft.world.level.block.state.properties.BlockStateProperties.UP,
                    Direction.DOWN, net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN);

    /** A tank starts EMPTY: it holds water (and then fish) only once a bucket fills it. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FILLED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("filled");

    public FishTankBlock(Properties properties)
    {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FILLED, false);
        for (var property : CONNECTED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder)
    {
        CONNECTED.values().forEach(builder::add);
        builder.add(FILLED);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context)
    {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTED.get(dir),
                    context.getLevel().getBlockState(context.getClickedPos().relative(dir)).is(this));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        return state.setValue(CONNECTED.get(direction), neighbor.is(this));
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    /**
     * Neighboring tanks merge visually: interior faces (glass AND water) are skipped —
     * but only between tanks in the SAME fill state, so an empty tank against a full one
     * still shows the water wall through the shared pane.
     */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction)
    {
        return (adjacent.is(this) && adjacent.getValue(FILLED) == state.getValue(FILLED))
                || super.skipRendering(state, adjacent, direction);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new FishTankBlockEntity(pos, state);
    }

    /**
     * Only LIVE fish go in a tank: a caught fish with its NiceCatch stamp (revivable —
     * that's what makes it alive), a fish in a bucket, or a fish spawn egg. A plain dead
     * fillet from a loot table is dinner, not a pet.
     */
    public static boolean isTankable(ItemStack stack, Level level)
    {
        if (stack.isEmpty()) return false;
        if (stack.getTagElement("NiceCatch") != null && stack.is(ItemTags.FISHES)) return true;
        if (stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem egg) {
            return net.camacraft.nicecatch.server.NiceCatchCommands.isFishType(
                    egg.getType(stack.getTag()), level);
        }
        // Buckets by the id convention (minecraft:cod_bucket -> minecraft:cod and friends).
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && id.getPath().endsWith("_bucket")) {
            var entityId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), id.getPath().substring(0, id.getPath().length() - 7));
            if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                return net.camacraft.nicecatch.server.NiceCatchCommands.isFishType(
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(entityId), level);
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof FishTankBlockEntity tank)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        // An empty tank wants water first: a bucket fills THIS pane's worth.
        if (!state.getValue(FILLED) && held.is(net.minecraft.world.item.Items.WATER_BUCKET)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(FILLED, true), 3);
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, new ItemStack(net.minecraft.world.item.Items.BUCKET));
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getValue(FILLED) && tank.getFish().isEmpty() && isTankable(held, level)) {
            if (!level.isClientSide) {
                ItemStack one = held.copyWithCount(1);
                if (!player.getAbilities().instabuild) held.shrink(1);
                tank.setFish(one);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 0.6F, 1.1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!tank.getFish().isEmpty() && held.isEmpty()) {
            if (!level.isClientSide) {
                ItemStack fish = tank.getFish();
                tank.setFish(ItemStack.EMPTY);
                if (!player.getInventory().add(fish)) {
                    player.drop(fish, false);
                }
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FishTankBlockEntity tank
                && !tank.getFish().isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), tank.getFish());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
