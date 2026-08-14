package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The bubble jet: a horizontal bubble column. Vanilla's soul sand and magma only ever push
 * straight up or down; this block, submerged, blows a steady sideways current of bubbles
 * through the water in front of it — up to a configured reach, stopping at the first
 * non-water block. Anything in the stream (items, fish, boats, swimmers) is pushed along.
 * Build conveyor channels for drops, herd fish toward a trap, or shove a lazy friend's
 * boat across the harbor.
 */
public class BubbleJetBlock extends BaseEntityBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BubbleJetBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BubbleJetBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.BUBBLE_JET.get(), BubbleJetBlockEntity::serverTick);
    }

    /** The per-tick current: walk the beam through open water, pushing whatever floats in it. */
    static void blow(ServerLevel level, BlockPos pos, BlockState state)
    {
        // No audience, no current worth simulating: a jet farm far from everyone idles free.
        if (level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                64.0D, false) == null) {
            return;
        }
        Direction dir = state.getValue(FACING);
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        int range = cfg.bubbleJetRange.get();
        double push = cfg.bubbleJetPush.get();
        Vec3 flow = Vec3.atLowerCornerOf(dir.getNormal());

        int reach = 0;
        BlockPos.MutableBlockPos probe = pos.mutable();
        while (reach < range) {
            probe.move(dir);
            if (!level.getFluidState(probe).is(FluidTags.WATER)) break;
            reach++;
        }
        if (reach == 0) return;

        AABB beam = new AABB(pos.relative(dir)).expandTowards(
                flow.x * (reach - 1), 0.0D, flow.z * (reach - 1)).inflate(0.05D, 0.05D, 0.05D);
        for (Entity entity : level.getEntities((Entity) null, beam, e -> !e.isSpectator() && !e.noPhysics)) {
            Vec3 v = entity.getDeltaMovement();
            // Push along the flow, capped so chained jets make a current, not a railgun.
            double along = v.x * flow.x + v.z * flow.z;
            if (along < 0.7D) {
                entity.setDeltaMovement(v.add(flow.scale(push)));
                entity.hurtMarked = true;
            }
        }

        // It sounds exactly like the vanilla upward column, offset per jet so a row of
        // them burbles as a chorus instead of a metronome.
        if ((level.getGameTime() + (pos.asLong() & 63)) % 52 == 0) {
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F,
                    0.9F + level.random.nextFloat() * 0.2F);
        }

        // The visible stream: a couple of bubbles somewhere along the beam every few ticks.
        if (level.getGameTime() % 3 == 0) {
            double t = level.random.nextDouble() * reach;
            level.sendParticles(ParticleTypes.BUBBLE,
                    pos.getX() + 0.5D + flow.x * (0.6D + t),
                    pos.getY() + 0.2D + level.random.nextDouble() * 0.6D,
                    pos.getZ() + 0.5D + flow.z * (0.6D + t),
                    2, 0.1D, 0.1D, 0.1D, flow.horizontalDistance() * 0.06D);
        }

        // The intake: the soul-sand side of the block drinks the water in — little bubbles
        // drift toward the back and sides and vanish into it, selling the pump both ways.
        if (level.getGameTime() % 4 == 0) {
            Direction intake = level.random.nextBoolean() ? dir.getOpposite()
                    : (level.random.nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise());
            BlockPos intakePos = pos.relative(intake);
            if (level.getFluidState(intakePos).is(FluidTags.WATER)) {
                double ox = intakePos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.7D;
                double oy = pos.getY() + 0.2D + level.random.nextDouble() * 0.6D;
                double oz = intakePos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.7D;
                // A single directed particle per call: velocity points INTO the block.
                level.sendParticles(ParticleTypes.BUBBLE, ox, oy, oz, 0,
                        (pos.getX() + 0.5D - ox) * 0.25D,
                        (pos.getY() + 0.5D - oy) * 0.1D,
                        (pos.getZ() + 0.5D - oz) * 0.25D, 1.0D);
            }
        }
    }
}
