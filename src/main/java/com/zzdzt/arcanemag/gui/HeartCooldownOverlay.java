package com.zzdzt.arcanemag.gui;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.item.HeartItem;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HeartCooldownOverlay {

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        for (Slot slot : containerScreen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (!(stack.getItem() instanceof HeartItem heartItem)) {
                continue;
            }

            renderCooldownOverlay(guiGraphics, containerScreen.getGuiLeft() + slot.x, containerScreen.getGuiTop() + slot.y, heartItem);
        }
    }

    @SubscribeEvent
    public static void onHudRender(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui instanceof ForgeGui forgeGui)) {
            return;
        }

        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int hotbarX = (screenWidth - 182) / 2 + 3;
        int hotbarY = screenHeight - 22 + 3;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof HeartItem heartItem)) {
                continue;
            }

            int slotX = hotbarX + i * 20;
            int slotY = hotbarY;

            renderCooldownOverlay(guiGraphics, slotX, slotY, heartItem);
        }
    }

    private static void renderCooldownOverlay(GuiGraphics guiGraphics, int x, int y, HeartItem heartItem) {
        var cooldowns = ClientMagicData.getCooldowns();
        var cooldownMap = cooldowns.getSpellCooldowns();

        if (!cooldownMap.containsKey(heartItem.getCooldownId())) {
            return;
        }

        var cooldownInstance = cooldownMap.get(heartItem.getCooldownId());
        int remaining = cooldownInstance.getCooldownRemaining();
        int total = cooldownInstance.getSpellCooldown();

        if (remaining <= 0 || total <= 0) {
            return;
        }

        float cooldownPercent = (float) remaining / total;
        if (cooldownPercent <= 0.01f) {
            return;
        }

        int pixels = (int) (16 * cooldownPercent);

        int topY = y + 16 - pixels;
        int bottomY = y + 16;

        guiGraphics.fill(x, topY, x + 16, bottomY, 0x80FFFFFF);
    }
}