package net.camacraft.nicecatch.block;

import net.camacraft.nicecatch.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Holds the tank's single displayed fish and keeps clients in sync (the renderer needs it). */
public class FishTankBlockEntity extends BlockEntity
{
    private ItemStack fish = ItemStack.EMPTY;

    public FishTankBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.FISH_TANK.get(), pos, state);
    }

    public ItemStack getFish()
    {
        return fish;
    }

    public void setFish(ItemStack stack)
    {
        fish = stack;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (!fish.isEmpty()) {
            tag.put("Fish", fish.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        fish = tag.contains("Fish") ? ItemStack.of(tag.getCompound("Fish")) : ItemStack.EMPTY;
    }

    // ---- Client sync: the renderer draws from this data ----

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

    // ---- The connected tank region (client-side, for the swim path and render culling) ----

    /** Hard cap on how many joined tanks one region scan follows (a 4x4x4 build). */
    private static final int REGION_CAP = 64;

    @Nullable private java.util.List<BlockPos> cellCache;
    @Nullable private net.minecraft.world.phys.AABB boundsCache;
    private long regionCacheTime = Long.MIN_VALUE;

    // The occupant's wandering state: it glides cell-center to ADJACENT cell-center, so in
    // an L- or T-shaped build it never cuts a corner through open air. (Client-side only.)
    @Nullable public BlockPos swimFrom;
    @Nullable public BlockPos swimTo;
    public double swimProgress;
    public float swimYaw;

    /**
     * Every WATER-FILLED tank cell connected to this one — the actual rooms the occupant
     * may swim through, not a bounding box that could include open air in non-cuboid
     * builds. Rescanned every couple of seconds so edits reshape the swim room shortly.
     */
    public java.util.List<BlockPos> tankCells()
    {
        if (level == null) return java.util.List.of(worldPosition);
        long now = level.getGameTime();
        if (cellCache == null || now - regionCacheTime > 40) {
            regionCacheTime = now;
            computeRegion();
        }
        return cellCache != null ? cellCache : java.util.List.of(worldPosition);
    }

    private void computeRegion()
    {
        var block = getBlockState().getBlock();
        var visited = new java.util.LinkedHashSet<BlockPos>();
        var queue = new java.util.ArrayDeque<BlockPos>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        BlockPos min = worldPosition;
        BlockPos max = worldPosition;
        while (!queue.isEmpty() && visited.size() <= REGION_CAP) {
            BlockPos current = queue.poll();
            min = new BlockPos(Math.min(min.getX(), current.getX()),
                    Math.min(min.getY(), current.getY()), Math.min(min.getZ(), current.getZ()));
            max = new BlockPos(Math.max(max.getX(), current.getX()),
                    Math.max(max.getY(), current.getY()), Math.max(max.getZ(), current.getZ()));
            for (var dir : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.contains(next) && level != null) {
                    var neighborState = level.getBlockState(next);
                    if (neighborState.is(block) && neighborState.getValue(FishTankBlock.FILLED)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        cellCache = java.util.List.copyOf(visited);
        boundsCache = new net.minecraft.world.phys.AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    /** The fish roams the whole connected aquarium; culling must account for all of it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox()
    {
        tankCells();
        return (boundsCache != null ? boundsCache
                : new net.minecraft.world.phys.AABB(worldPosition)).inflate(1.0D);
    }
}
