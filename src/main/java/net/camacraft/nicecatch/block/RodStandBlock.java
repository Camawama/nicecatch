package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.RodUtil;
import net.camacraft.nicecatch.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The rod stand: cast a line, then right-click the stand with the rod to park it — the
 * bobber stays out, the line stays live, and your hands are free to cast ANOTHER rod.
 * Run a whole row of stands and manage several lines at once. When a fish takes a parked
 * line, the bobber dips (and the stand's bell rings, if one was added with a right-click);
 * right-click the stand to snatch the rod back — with a fish on, the grab sets the hook
 * and the reel fight starts on the spot. Stand bites are patient: the window is long
 * enough to walk over from the next stand down.
 */
public class RodStandBlock extends BaseEntityBlock
{
    public static final BooleanProperty HAS_ROD = BooleanProperty.create("has_rod");
    public static final BooleanProperty HAS_BELL = BooleanProperty.create("has_bell");
    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 14.0D, 11.0D);

    public RodStandBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HAS_ROD, false).setValue(HAS_BELL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(HAS_ROD, HAS_BELL);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RodStandBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ROD_STAND.get(), RodStandBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof RodStandBlockEntity stand)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        // A bell upgrade: the stand rings out when a fish takes the line. Rendered on the
        // stand, and snipped back off with shears.
        if (held.is(Items.BELL) && !stand.hasBell()) {
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                stand.setBell(true);
                level.setBlock(pos, level.getBlockState(pos).setValue(HAS_BELL, true), 3);
                level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.getItem() instanceof net.minecraft.world.item.ShearsItem && stand.hasBell()) {
            if (!level.isClientSide) {
                stand.setBell(false);
                level.setBlock(pos, level.getBlockState(pos).setValue(HAS_BELL, false), 3);
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D,
                        pos.getZ() + 0.5D, new ItemStack(Items.BELL));
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Parking a rod: the held rod must have its line out (that's the whole point).
        if (!stand.hasRod() && RodUtil.isRod(held) && player.fishing != null) {
            if (!level.isClientSide) {
                stand.parkRod(player, hand);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Taking the rod back — with a fish on the line, the grab starts the fight.
        if (stand.hasRod()) {
            if (!level.isClientSide) {
                stand.takeRod(player, hand);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RodStandBlockEntity stand) {
            stand.spill();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
