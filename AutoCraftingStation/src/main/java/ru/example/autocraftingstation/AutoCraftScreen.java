package ru.example.autocraftingstation;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class AutoCraftScreen extends AbstractContainerScreen<AutoCraftMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AutoCraftingStation.MOD_ID, "textures/gui/autocraft_screen.png");

    public AutoCraftScreen(AutoCraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 282;
        titleLabelX = 10;
        titleLabelY = 8;
        inventoryLabelX = 16;
        inventoryLabelY = 188;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 5; i++) {
            final int recipe = i;
            addRenderableWidget(Button.builder(Component.empty(), button ->
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, recipe))
                    .bounds(leftPos + 15 + i * 27, topPos + 27, 23, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.autocraftingstation.toggle"), button ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 5))
                .bounds(leftPos + 150, topPos + 52, 91, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.autocraftingstation.clear"), button ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 6))
                .bounds(leftPos + 150, topPos + 78, 91, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 282);
        ItemStack preview = menu.getPreviewResult();
        if (!preview.isEmpty()) {
            graphics.renderItem(preview, leftPos + 126, topPos + 68);
            graphics.renderItemDecorations(font, preview, leftPos + 126, topPos + 68);
        }
        graphics.drawString(font, "→", leftPos + 96, topPos + 72, 0xF4C77A, false);
        graphics.drawString(font, menu.isSelectedEnabled() ? "● АКТИВЕН" : "○ ВЫКЛЮЧЕН",
                leftPos + 155, topPos + 101, menu.isSelectedEnabled() ? 0x72E6BA : 0xC1A18A, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.autocraftingstation.auto_craft"), 10, 8, 0xF2D5A0, false);
        graphics.drawString(font, "ШАБЛОН 3×3", 16, 43, 0xC9D5D9, false);
        graphics.drawString(font, "РЕЗУЛЬТАТ", 118, 43, 0xC9D5D9, false);
        graphics.drawString(font, "ВХОДНОЙ БУФЕР · 18 СЛОТОВ", 15, 118, 0xC9D5D9, false);
        graphics.drawString(font, "ВЫХОД", 190, 116, 0xC9D5D9, false);
        graphics.drawString(font, "ИНВЕНТАРЬ", 16, 188, 0xC9D5D9, false);
        graphics.drawString(font, "Рецепт " + (menu.getSelectedRecipe() + 1) + "/5", 16, 101, 0xE8B86C, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // The five profile tabs double as a compact recipe list: each tab previews its result.
        for (int i = 0; i < 5; i++) {
            ItemStack tabResult = menu.getRecipePreview(i);
            int x = leftPos + 18 + i * 27;
            int y = topPos + 28;
            if (tabResult.isEmpty()) graphics.drawCenteredString(font, Integer.toString(i + 1), x + 7, y + 4, 0xE8C18A);
            else graphics.renderItem(tabResult, x + 3, y);
            if (menu.isRecipeEnabled(i)) graphics.fill(x + 17, y + 1, x + 21, y + 5, 0xFF63D49B);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
