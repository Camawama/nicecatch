package net.camacraft.nicecatch.menu;

import net.camacraft.nicecatch.block.FishTrapBlockEntity;
import net.camacraft.nicecatch.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The fish trap's inventory, on its own dedicated GUI: the bait slot alone on the left
 * (green-tinted — accepts any food a fish would eat, INCLUDING caught fish, which raise
 * the size ceiling of what the trap can take), an arrow, and the 2x2 haul grid on the
 * right (take-out only; the trap fills it itself). Shift-clicking bait-worthy items from
 * the player's inventory loads the bait slot; shift-clicking the haul empties it.
 */
public class FishTrapMenu extends AbstractContainerMenu
{
    private final Container trap;

    /** Client-side factory: the menu syncs slot contents itself, so a blank container serves. */
    public FishTrapMenu(int windowId, Inventory playerInventory)
    {
        this(windowId, playerInventory, new SimpleContainer(FishTrapBlockEntity.SLOT_COUNT));
    }

    public FishTrapMenu(int windowId, Inventory playerInventory, Container trap)
    {
        super(ModMenus.FISH_TRAP.get(), windowId);
        checkContainerSize(trap, FishTrapBlockEntity.SLOT_COUNT);
        this.trap = trap;
        trap.startOpen(playerInventory.player);

        // Bait alone on the left; the 2x2 haul grid on the right (fish_trap.png layout).
        addSlot(new Slot(trap, FishTrapBlockEntity.BAIT_SLOT, 26, 35)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return FishTrapBlockEntity.isBait(stack);
            }
        });
        int[][] haul = { {97, 26}, {115, 26}, {97, 44}, {115, 44} };
        for (int i = FishTrapBlockEntity.STORAGE_START; i < FishTrapBlockEntity.SLOT_COUNT; i++) {
            int[] at = haul[i - FishTrapBlockEntity.STORAGE_START];
            addSlot(new Slot(trap, i, at[0], at[1])
            {
                @Override
                public boolean mayPlace(ItemStack stack)
                {
                    return false; // the trap fills these itself
                }
            });
        }

        // Player inventory, below the trap area.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, row * 18 + 70));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 128));
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return trap.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack moved = slot.getItem();
        result = moved.copy();
        if (index < FishTrapBlockEntity.SLOT_COUNT) {
            // Out of the trap, into the player's inventory.
            if (!moveItemStackTo(moved, FishTrapBlockEntity.SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From the player: food heads for the bait slot; anything else stays put.
            if (!FishTrapBlockEntity.isBait(moved)
                    || !moveItemStackTo(moved, FishTrapBlockEntity.BAIT_SLOT, FishTrapBlockEntity.BAIT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (moved.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        trap.stopOpen(player);
    }
}
