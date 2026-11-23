package ru.example.autocraftingstation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CraftingStationBlockEntity extends BlockEntity {
    public static final int RECIPE_COUNT = 5;
    public static final int RECIPE_GRID_SIZE = 9;
    // Lazy preview cache avoids querying the recipe manager on every rendered frame.
    private final ItemStack[] previewCache = new ItemStack[RECIPE_COUNT];
    private final ItemStackHandler storage = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        // Internal logic may insert into all slots; public sided views and GUI slots enforce access rules.
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot >= 0 && slot < 27; }
    };
    private final ItemStackHandler templates = new ItemStackHandler(RECIPE_COUNT * RECIPE_GRID_SIZE) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) {
            previewCache[slot / RECIPE_GRID_SIZE] = null;
            setChangedAndSync();
        }
    };
    private final boolean[] enabled = new boolean[]{false, false, false, false, false};
    private int tickDelay;
    private int nextRecipe;

    public CraftingStationBlockEntity(BlockPos pos, BlockState state) {
        super(AutoCraftingStation.CRAFTING_STATION_BE.get(), pos, state);
    }

    public ItemStackHandler getStorage() { return storage; }
    public ItemStack getRecipeSlot(int recipe, int slot) {
        if (!validRecipe(recipe) || slot < 0 || slot >= RECIPE_GRID_SIZE) return ItemStack.EMPTY;
        return templates.getStackInSlot(recipe * RECIPE_GRID_SIZE + slot);
    }
    public void setRecipeSlot(int recipe, int slot, ItemStack stack) {
        if (!validRecipe(recipe) || slot < 0 || slot >= RECIPE_GRID_SIZE) return;
        ItemStack one = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        templates.setStackInSlot(recipe * RECIPE_GRID_SIZE + slot, one);
    }
    public void clearRecipe(int recipe) {
        if (!validRecipe(recipe)) return;
        for (int i = 0; i < RECIPE_GRID_SIZE; i++) templates.setStackInSlot(recipe * RECIPE_GRID_SIZE + i, ItemStack.EMPTY);
        setChangedAndSync();
    }
    public boolean isRecipeEnabled(int recipe) { return validRecipe(recipe) && enabled[recipe]; }
    public void toggleRecipe(int recipe) {
        if (validRecipe(recipe)) { enabled[recipe] = !enabled[recipe]; setChangedAndSync(); }
    }
    public int getEnabledMask() {
        int mask = 0;
        for (int i = 0; i < RECIPE_COUNT; i++) if (enabled[i]) mask |= 1 << i;
        return mask;
    }
    private static boolean validRecipe(int recipe) { return recipe >= 0 && recipe < RECIPE_COUNT; }

    public ItemStack getPreviewResult(int recipe) {
        if (level == null || !validRecipe(recipe)) return ItemStack.EMPTY;
        if (previewCache[recipe] == null) {
            CraftingInput input = makeRecipeInput(recipe);
            previewCache[recipe] = input == null ? ItemStack.EMPTY : level.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(holder -> holder.value().assemble(input, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
        }
        return previewCache[recipe].copy();
    }

    private CraftingInput makeRecipeInput(int recipe) {
        List<ItemStack> items = new ArrayList<>(RECIPE_GRID_SIZE);
        boolean any = false;
        for (int i = 0; i < RECIPE_GRID_SIZE; i++) {
            ItemStack template = getRecipeSlot(recipe, i);
            if (!template.isEmpty()) any = true;
            items.add(template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1));
        }
        return any ? CraftingInput.of(3, 3, items) : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CraftingStationBlockEntity station) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 10L == 0L) station.transferAdjacentContainers(serverLevel);
        if (station.tickDelay > 0) { station.tickDelay--; return; }
        for (int offset = 0; offset < RECIPE_COUNT; offset++) {
            int recipe = (station.nextRecipe + offset) % RECIPE_COUNT;
            if (station.enabled[recipe] && station.tryCraft(serverLevel, recipe)) {
                station.nextRecipe = (recipe + 1) % RECIPE_COUNT;
                station.tickDelay = 9;
                return;
            }
        }
    }


    /** Pulls from a directly adjacent container on the left and pushes to one on the right. */
    private void transferAdjacentContainers(ServerLevel serverLevel) {
        Direction facing = getBlockState().getValue(CraftingStationBlock.FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        IItemHandler source = getNeighbourHandler(serverLevel, worldPosition.relative(left), left.getOpposite());
        if (source != null) {
            for (int sourceSlot = 0; sourceSlot < source.getSlots(); sourceSlot++) {
                ItemStack offered = source.getStackInSlot(sourceSlot);
                if (offered.isEmpty() || !isRequestedByEnabledRecipe(offered)) continue;
                ItemStack remainder = offered.copy();
                for (int targetSlot = 0; targetSlot < 18 && !remainder.isEmpty(); targetSlot++) {
                    remainder = storage.insertItem(targetSlot, remainder, false);
                }
                int moved = offered.getCount() - remainder.getCount();
                if (moved > 0) source.extractItem(sourceSlot, moved, false);
            }
        }

        IItemHandler destination = getNeighbourHandler(serverLevel, worldPosition.relative(right), right.getOpposite());
        if (destination != null) {
            for (int outputSlot = 18; outputSlot < 27; outputSlot++) {
                ItemStack offered = storage.getStackInSlot(outputSlot);
                if (offered.isEmpty()) continue;
                ItemStack remainder = offered.copy();
                for (int targetSlot = 0; targetSlot < destination.getSlots() && !remainder.isEmpty(); targetSlot++) {
                    remainder = destination.insertItem(targetSlot, remainder, false);
                }
                int moved = offered.getCount() - remainder.getCount();
                if (moved > 0) storage.extractItem(outputSlot, moved, false);
            }
        }
    }

    private boolean isRequestedByEnabledRecipe(ItemStack offered) {
        for (int recipe = 0; recipe < RECIPE_COUNT; recipe++) {
            if (!enabled[recipe]) continue;
            for (int slot = 0; slot < RECIPE_GRID_SIZE; slot++) {
                ItemStack required = getRecipeSlot(recipe, slot);
                if (!required.isEmpty() && ItemStack.isSameItemSameComponents(required, offered)) return true;
            }
        }
        return false;
    }

    private IItemHandler getNeighbourHandler(ServerLevel level, BlockPos neighbourPos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighbourPos, side);
        if (handler != null) return handler;
        // Vanilla chests and some third-party containers implement Container but do not expose a capability.
        if (level.getBlockEntity(neighbourPos) instanceof Container container) return new InvWrapper(container);
        return null;
    }

    private boolean tryCraft(ServerLevel serverLevel, int recipeIndex) {
        CraftingInput input = makeRecipeInput(recipeIndex);
        if (input == null) return false;
        Optional<RecipeHolder<CraftingRecipe>> match = serverLevel.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
        if (match.isEmpty()) return false;

        int[] consumeFromSlots = new int[18];
        for (int gridSlot = 0; gridSlot < RECIPE_GRID_SIZE; gridSlot++) {
            ItemStack required = getRecipeSlot(recipeIndex, gridSlot);
            if (required.isEmpty()) continue;
            int remaining = 1;
            for (int inventorySlot = 0; inventorySlot < 18 && remaining > 0; inventorySlot++) {
                ItemStack available = storage.getStackInSlot(inventorySlot);
                if (!available.isEmpty() && ItemStack.isSameItemSameComponents(required, available)) {
                    int free = available.getCount() - consumeFromSlots[inventorySlot];
                    int take = Math.min(remaining, free);
                    if (take > 0) {
                        consumeFromSlots[inventorySlot] += take;
                        remaining -= take;
                    }
                }
            }
            if (remaining > 0) return false;
        }

        CraftingRecipe recipe = match.get().value();
        ItemStack result = recipe.assemble(input, serverLevel.registryAccess());
        if (result.isEmpty()) return false;
        List<ItemStack> products = new ArrayList<>();
        products.add(result.copy());
        NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);
        for (ItemStack remainder : remainders) if (!remainder.isEmpty()) products.add(remainder.copy());
        if (!canFitOutput(products)) return false;

        for (int slot = 0; slot < consumeFromSlots.length; slot++) {
            int count = consumeFromSlots[slot];
            if (count > 0) storage.extractItem(slot, count, false);
        }
        for (ItemStack product : products) insertOutput(product);
        setChangedAndSync();
        return true;
    }

    private boolean canFitOutput(List<ItemStack> products) {
        ItemStackHandler simulation = new ItemStackHandler(9);
        for (int i = 0; i < 9; i++) simulation.setStackInSlot(i, storage.getStackInSlot(18 + i).copy());
        for (ItemStack product : products) {
            ItemStack remainder = product.copy();
            for (int slot = 0; slot < 9 && !remainder.isEmpty(); slot++) {
                remainder = simulation.insertItem(slot, remainder, false);
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private void insertOutput(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 18; slot < 27 && !remainder.isEmpty(); slot++) {
            remainder = storage.insertItem(slot, remainder, false);
        }
        // Capacity is simulated first; reaching this branch with a remainder would indicate a bug.
        if (!remainder.isEmpty() && level != null) Containers.dropItemStack(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, remainder);
    }

    public IItemHandler getHandlerForSide(Direction side) {
        if (side == null) return new SidedHandler(false);
        Direction facing = getBlockState().getValue(CraftingStationBlock.FACING);
        if (side == Direction.UP || side == facing.getCounterClockWise()) return new SidedHandler(false);
        if (side == Direction.DOWN || side == facing.getClockWise()) return new SidedHandler(true);
        return null;
    }

    private final class SidedHandler implements IItemHandler {
        private final boolean output;
        private SidedHandler(boolean output) { this.output = output; }
        @Override public int getSlots() { return output ? 9 : 18; }
        private int realSlot(int slot) { return output ? 18 + slot : slot; }
        @Override public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
            return storage.getStackInSlot(realSlot(slot));
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (output || slot < 0 || slot >= getSlots()) return stack;
            return storage.insertItem(realSlot(slot), stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!output || slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
            return storage.extractItem(realSlot(slot), amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return slot < 0 || slot >= getSlots() ? 0 : 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !output && slot >= 0 && slot < getSlots(); }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void dropStoredItems() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
            storage.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Storage", storage.serializeNBT(registries));
        tag.put("Recipes", templates.serializeNBT(registries));
        for (int i = 0; i < RECIPE_COUNT; i++) tag.putBoolean("Enabled" + i, enabled[i]);
        tag.putInt("NextRecipe", nextRecipe);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Storage")) storage.deserializeNBT(registries, tag.getCompound("Storage"));
        if (tag.contains("Recipes")) templates.deserializeNBT(registries, tag.getCompound("Recipes"));
        for (int i = 0; i < RECIPE_COUNT; i++) enabled[i] = tag.getBoolean("Enabled" + i);
        nextRecipe = Math.floorMod(tag.getInt("NextRecipe"), RECIPE_COUNT);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
