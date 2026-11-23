package ru.example.autocraftingstation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

public class CraftingStationBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CraftingStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AutoCraftingStation.CRAFTING_STATION_BE.get(),
                CraftingStationBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        openStationMenu(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        // The station takes priority over the held item so right-click works with a full hand as well.
        openStationMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openStationMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return;
        if (player.isShiftKeyDown()) {
            if (level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new AutoCraftMenu(id, inventory, station),
                        Component.translatable("screen.autocraftingstation.auto_craft")),
                        buffer -> buffer.writeBlockPos(pos));
            }
        } else {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new CraftingMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.crafting")));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station) {
            station.dropStoredItems();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
