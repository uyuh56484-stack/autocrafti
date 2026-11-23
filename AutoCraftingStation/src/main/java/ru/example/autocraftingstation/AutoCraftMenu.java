package ru.example.autocraftingstation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class AutoCraftMenu extends AbstractContainerMenu {
    private final CraftingStationBlockEntity station;
    private final BlockPos pos;
    private int selectedRecipe;
    private int clientEnabledMask;

    public AutoCraftMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(id, playerInventory, data.readBlockPos(), null);
    }

    public AutoCraftMenu(int id, Inventory playerInventory, CraftingStationBlockEntity station) {
        this(id, playerInventory, station.getBlockPos(), station);
    }

    private AutoCraftMenu(int id, Inventory playerInventory, BlockPos pos, CraftingStationBlockEntity suppliedStation) {
        super(AutoCraftingStation.AUTO_CRAFT_MENU.get(), id);
        this.pos = pos;
        this.station = suppliedStation != null ? suppliedStation : findStation(playerInventory, pos);

        IItemHandler recipeView = new IItemHandler() {
            @Override public int getSlots() { return 9; }
            @Override public ItemStack getStackInSlot(int slot) {
                return station == null || slot < 0 || slot >= 9 ? ItemStack.EMPTY : station.getRecipeSlot(selectedRecipe, slot);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (station == null || slot < 0 || slot >= 9 || stack.isEmpty()) return stack;
                ItemStack existing = getStackInSlot(slot);
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
                int room = 1 - existing.getCount();
                int accepted = Math.min(room, stack.getCount());
                if (accepted <= 0) return stack;
                if (!simulate) {
                    ItemStack updated = existing.isEmpty() ? stack.copyWithCount(accepted) : existing.copyWithCount(existing.getCount() + accepted);
                    station.setRecipeSlot(selectedRecipe, slot, updated);
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(accepted);
                return remainder;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (station == null || slot < 0 || slot >= 9 || amount <= 0) return ItemStack.EMPTY;
                ItemStack current = getStackInSlot(slot);
                if (current.isEmpty()) return ItemStack.EMPTY;
                int taken = Math.min(amount, current.getCount());
                ItemStack result = current.copyWithCount(taken);
                if (!simulate) {
                    ItemStack rest = current.copy(); rest.shrink(taken);
                    station.setRecipeSlot(selectedRecipe, slot, rest);
                }
                return result;
            }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return slot >= 0 && slot < 9; }
        };

        // Ghost/template grid for the currently selected recipe.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(recipeView, row * 3 + col, 16 + col * 18, 53 + row * 18));
            }
        }

        // Internal input buffer: 18 slots, inserted by left side/top.
        if (station != null) {
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new SlotItemHandler(station.getStorage(), row * 9 + col, 16 + col * 18, 131 + row * 18));
                }
            }
            // Output buffer: 9 slots, extracted by right side/bottom.
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    addSlot(new SlotItemHandler(station.getStorage(), 18 + row * 3 + col, 190 + col * 18, 128 + row * 18) {
                        @Override public boolean mayPlace(ItemStack stack) { return false; }
                    });
                }
            }
        } else {
            // Client fallback; the BE should normally be present when the menu arrives.
            net.neoforged.neoforge.items.ItemStackHandler dummy = new net.neoforged.neoforge.items.ItemStackHandler(27);
            for (int i = 0; i < 18; i++) addSlot(new SlotItemHandler(dummy, i, 16 + (i % 9) * 18, 131 + (i / 9) * 18));
            for (int i = 0; i < 9; i++) addSlot(new SlotItemHandler(dummy, 18 + i, 190 + (i % 3) * 18, 128 + (i / 3) * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        addStandardInventorySlots(playerInventory, 16, 199);
        addDataSlot(new DataSlot() {
            @Override public int get() { return selectedRecipe; }
            @Override public void set(int value) { selectedRecipe = Math.max(0, Math.min(4, value)); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return station == null ? 0 : station.getEnabledMask(); }
            @Override public void set(int value) { clientEnabledMask = value & 31; }
        });
    }

    private static CraftingStationBlockEntity findStation(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof CraftingStationBlockEntity station) return station;
        return null;
    }

    public int getSelectedRecipe() { return selectedRecipe; }
    public boolean isSelectedEnabled() { return isRecipeEnabled(selectedRecipe); }
    public boolean isRecipeEnabled(int recipe) { return recipe >= 0 && recipe < 5 && (clientEnabledMask & (1 << recipe)) != 0; }
    public ItemStack getPreviewResult() { return getRecipePreview(selectedRecipe); }
    public ItemStack getRecipePreview(int recipe) { return station == null ? ItemStack.EMPTY : station.getPreviewResult(recipe); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) return false;
        if (id >= 0 && id < 5) {
            selectedRecipe = id;
            return true;
        }
        if (station != null && id == 5) {
            station.toggleRecipe(selectedRecipe);
            return true;
        }
        if (station != null && id == 6) {
            station.clearRecipe(selectedRecipe);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) return true;
        return station != null && player.level().getBlockEntity(pos) == station
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        ItemStack moving = slot.getItem();

        if (index >= 36) {
            // Player inventory -> machine input only. Recipe templates are intentionally click-to-place.
            if (!moveItemStackTo(moving, 9, 27, false)) return ItemStack.EMPTY;
        } else if (index >= 9) {
            // Machine input/output -> player inventory.
            if (!moveItemStackTo(moving, 36, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, moving);
        return original;
    }
}
