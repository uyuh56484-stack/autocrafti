package ru.example.autocraftingstation;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AutoCraftingStation.MOD_ID)
public final class AutoCraftingStation {
    public static final String MOD_ID = "autocraftingstation";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<Block, CraftingStationBlock> CRAFTING_STATION = BLOCKS.register("crafting_station", () ->
            new CraftingStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, Item> CRAFTING_STATION_ITEM = ITEMS.register("crafting_station", () ->
            new net.minecraft.world.item.BlockItem(CRAFTING_STATION.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStationBlockEntity>> CRAFTING_STATION_BE =
            BLOCK_ENTITIES.register("crafting_station", () -> BlockEntityType.Builder
                    .of(CraftingStationBlockEntity::new, CRAFTING_STATION.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<AutoCraftMenu>> AUTO_CRAFT_MENU = MENUS.register("auto_craft",
            () -> IMenuTypeExtension.create(AutoCraftMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = TABS.register("main", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.autocraftingstation"))
                    .icon(() -> new ItemStack(CRAFTING_STATION_ITEM.get()))
                    .displayItems((parameters, output) -> output.accept(CRAFTING_STATION_ITEM.get())).build());

    public AutoCraftingStation(IEventBus modBus, ModContainer modContainer) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CRAFTING_STATION_BE.get(),
                CraftingStationBlockEntity::getHandlerForSide);
    }
}
