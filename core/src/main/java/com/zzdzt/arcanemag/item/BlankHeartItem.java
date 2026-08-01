package com.zzdzt.arcanemag.item;

import com.zzdzt.arcanemag.ArcaneMag;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlankHeartItem extends HeartItem {

    public static final int USE_DURATION_TICKS = 20;
    public static final int BASE_COOLDOWN_TICKS = 1200;
    public static final float PERCENT_REGEN = 0.5f;

    public BlankHeartItem(Properties properties) {
        super(properties, USE_DURATION_TICKS, BASE_COOLDOWN_TICKS);
    }

    @Override
    protected void applyHeartEffect(ServerPlayer player) {
        float maxHealth = player.getMaxHealth();
        float healAmount = maxHealth * PERCENT_REGEN;
        player.heal(healAmount);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.arcane_mag.blank_heart.tooltip.heal", PERCENT_REGEN * 100)
                .withStyle(net.minecraft.ChatFormatting.RED));

        if (level != null && level.isClientSide) {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            int effectiveCooldown = player != null ? Utils.applyCooldownReduction(BASE_COOLDOWN_TICKS, player) : BASE_COOLDOWN_TICKS;
            tooltip.add(Component.translatable("item.arcane_mag.blank_heart.tooltip.cooldown", effectiveCooldown / 20)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("item.arcane_mag.blank_heart.tooltip.reusable")
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.arcane_mag.heart_item.tooltip.cooldowntip")
                .withStyle(net.minecraft.ChatFormatting.GREEN));
    }
}