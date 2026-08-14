package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exists only to give the bubble jet a per-tick server heartbeat for its current. */
public class BubbleJetBlockEntity extends BlockEntity
{
    public BubbleJetBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.BUBBLE_JET.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BubbleJetBlockEntity be)
    {
        if (level instanceof ServerLevel serverLevel) {
            BubbleJetBlock.blow(serverLevel, pos, state);
        }
    }
}
