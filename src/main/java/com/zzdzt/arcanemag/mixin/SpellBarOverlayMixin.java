package com.zzdzt.arcanemag.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.config.ClientConfigs;
import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.player.ClientRenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 SpellBarOverlay.render() 末尾注入充能槽位渲染，
 * 将充能图标作为 SpellBar 网格的虚拟扩展槽位。
 * 槽位定位规则：读取 relativeSpellBarSlotLocations 最后一个槽位，
 * 按 (col+1 同行 / 换行) 规则接续。无法术时退回到锚点独立显示。
 */
@Mixin(SpellBarOverlay.class)
public class SpellBarOverlayMixin {

    private static final net.minecraft.resources.ResourceLocation TEXTURE =
            new net.minecraft.resources.ResourceLocation("irons_spellbooks", "textures/gui/icons.png");

    // UV 坐标（与 SpellBarOverlay 一致）
    private static final int UV_SLOT_BG = 66;   // 槽位底框
    private static final int UV_BORDER = 22;     // 普通边框
    private static final int UV_COOLDOWN = 47;   // 冷却遮罩（与铁魔法原生一致）
    private static final int UV_COOLDOWN_Y = 87; // 冷却遮罩 Y 行
    private static final int UV_Y = 84;          // 槽位底框/边框 Y 行
    private static final int SLOT_SIZE = 22;
    private static final int ICON_SIZE = 16;
    private static final int ICON_OFFSET = 3;

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    @SuppressWarnings("DataFlowIssue")
    private void arcaneMag$renderChargeSlot(ForgeGui gui, GuiGraphics guiGraphics,
                                             float partialTick, int screenWidth, int screenHeight,
                                             CallbackInfo ci) {
        if (guiGraphics == null) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;

        ItemStack gunStack = player.getMainHandItem();
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        SpellData spellData = MagazineSpellHelper.extractSpell(gunStack);
        if (spellData == null) return;

        try {
            var ssm = ClientMagicData.getSpellSelectionManager();
            List<Vec2> locations = ClientRenderCache.relativeSpellBarSlotLocations;

            // 读取 Iron's Spells 配置计算锚点（与 SpellBarOverlay 相同公式）
            int configOffsetY = ClientConfigs.SPELL_BAR_Y_OFFSET.get();
            int configOffsetX = ClientConfigs.SPELL_BAR_X_OFFSET.get();
            SpellBarOverlay.Anchor anchor = ClientConfigs.SPELL_BAR_ANCHOR.get();

            int centerX, centerY;
            if (anchor == SpellBarOverlay.Anchor.Hotbar) {
                centerX = screenWidth / 2 - Math.max(110, screenWidth / 4);
                centerY = screenHeight - Math.max(55, screenHeight / 8);
            } else {
                String name = anchor.name();
                centerX = name.contains("LEFT") ? 0 : screenWidth;
                centerY = name.contains("TOP") ? 0 : screenHeight;
            }
            centerX += configOffsetX;
            centerY += configOffsetY;

            int iconX, iconY;

            if (locations != null && !locations.isEmpty()) {
                int approximateWidth = locations.size() / 3;
                centerX -= approximateWidth * 5;

                Vec2 lastSlot = locations.get(locations.size() - 1);
                if (locations.size() % 3 == 0) {
                    // 当前行满，换行
                    iconX = centerX;
                    iconY = centerY + (int) lastSlot.y + SLOT_SIZE;
                } else {
                    // 同行继续（水平间距 = generateRelativeLocations 参数 20）
                    iconX = centerX + (int) lastSlot.x + 20;
                    iconY = centerY + (int) lastSlot.y;
                }
            } else {
                // 无 SpellBar 网格时，退回到锚点位置
                iconX = centerX;
                iconY = centerY;
            }

            renderChargeSlot(guiGraphics, iconX, iconY, spellData, magazine);
        } catch (Exception e) {
            ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to render charge slot: {}", e.getMessage());
        }
    }

    /**
     * 以 SpellBar 风格绘制充能槽位：底框 → 图标 → 遮罩 → 边框 → 高亮 → 文字
     */
    private void renderChargeSlot(GuiGraphics guiGraphics, int x, int y,
                                   SpellData spellData, ItemStack magazine) {
        // 设置半透明渲染（与 SpellBarOverlay.setTranslucentTexture 一致）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. 槽位底框（UV 66,84, 22×22） — 与 SpellBar 槽位背景完全一致
        guiGraphics.blit(TEXTURE, x, y, UV_SLOT_BG, UV_Y, SLOT_SIZE, SLOT_SIZE);

        // 2. 法术图标（16×16, 偏移 3px）
        try {
            var iconResource = spellData.getSpell().getSpellIconResource();
            guiGraphics.blit(iconResource, x + ICON_OFFSET, y + ICON_OFFSET,
                    0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        } catch (Exception e) {
            // 图标加载失败时静默跳过，底框仍可见
        }

        // 3. 冷却遮罩（与 Iron's Spells 原生完全一致：UV 47,87，从底部向上）
        //    未充能比例 = 1.0 - progress，映射为遮罩高度
        double progress = ModChargeData.getProgress(magazine);
        float unchargedPercent = 1.0f - (float) progress;
        if (unchargedPercent > 0.0f) {
            int pixels = (int) (ICON_SIZE * unchargedPercent + 1f);
            guiGraphics.blit(TEXTURE,
                    x + ICON_OFFSET,
                    y + ICON_OFFSET + ICON_SIZE - pixels,
                    UV_COOLDOWN, UV_COOLDOWN_Y,
                    ICON_SIZE, pixels);
        }

        // 4. 普通边框（UV 22,84, 22×22） — 与 SpellBar 非选中槽位一致
        guiGraphics.blit(TEXTURE, x, y, UV_BORDER, UV_Y, SLOT_SIZE, SLOT_SIZE);

        // 5. 满充高亮
        if (ModChargeData.isFull(magazine) && !ModChargeData.hasOverdrive(magazine)) {
            guiGraphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF00FF44);
        }

        // 6. 过载边框， 最后 3 秒（60 tick）闪烁提醒
        if (ModChargeData.hasOverdrive(magazine)) {
            long expireTick = ModChargeData.getOverdriveExpireTick(magazine);
            long currentTick = Minecraft.getInstance().level.getGameTime();
            long remaining = expireTick - currentTick;

            if (remaining > 0 && remaining < 60) {
                // 最后 3 秒闪烁
                if ((remaining / 5) % 2 == 0) {
                    guiGraphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFFCC2200);
                }
            } else {
                // 正常深橙红边框
                guiGraphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFFCC2200);
            }
        }

        // 7. 叠层计数
        int stacks = ModChargeData.getStacks(magazine);
        if (stacks > 0) {
            guiGraphics.drawString(Minecraft.getInstance().font, "x" + stacks,
                    x + SLOT_SIZE + 2, y + 4, 0xFFFFFF00);
        }
    }
}
