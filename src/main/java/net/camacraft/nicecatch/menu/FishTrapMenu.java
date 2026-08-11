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
 * The fish trap's inventory: a hopper-style row — the bait slot on the left (accepts any
 * food a fish would eat), the four catch slots to its right (take-out only; the trap fills
 * them itself). Shift-clicking food from the player's inventory loads the bait slot;
 * shift-clicking the haul empties it.
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

        // The trap row (hopper coordinates): bait far left, then the haul.
        addSlot(new Slot(trap, FishTrapBlockEntity.BAIT_SLOT, 44, 20)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return FishTrapBlockEntity.isBait(stack);
            }
        });
        for (int i = FishTrapBlockEntity.STORAGE_START; i < FishTrapBlockEntity.SLOT_COUNT; i++) {
            addSlot(new Slot(trap, i, 44 + i * 18, 20)
            {
                @Override
                public boolean mayPlace(ItemStack stack)
                {
                    return false; // the trap fills these itself
                }
            });
        }

        // Player inventory, hopper-screen layout.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, row * 18 + 51));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 109));
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
