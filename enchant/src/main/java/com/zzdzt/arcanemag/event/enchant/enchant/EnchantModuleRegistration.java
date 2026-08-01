package com.zzdzt.arcanemag.event.enchant;

import com.zzdzt.arcanemag.api.ModifierRegistry;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.api.SpellCastHook;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * enchant 模块注册入口。
 *
 * 在 FMLCommonSetupEvent（HIGH 优先级）将充能附魔注册为 {@link com.zzdzt.arcanemag.api.ChargeModifier}，
 * 将 ArcaneFocus/Brace Handler 注册为 {@link SpellCastHook}，让 core 通过注册表遍历调用。
 *
 * 注册顺序 = 溢出处理优先级：charge_stacks 先于 charge_overdrive。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class EnchantModuleRegistration {

    private EnchantModuleRegistration() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 充能 modifier（顺序 = 优先级：stacks 先于 overdrive）
            ModifierRegistry.registerChargeModifier(EnchantmentRegistry.CHARGE_STACKS.get());
            ModifierRegistry.registerChargeModifier(EnchantmentRegistry.CHARGE_OVERDRIVE.get());
            ModifierRegistry.registerChargeModifier(EnchantmentRegistry.CHARGE_PASSIVE.get());
            ModifierRegistry.registerChargeModifier(EnchantmentRegistry.UNSTABLE_CHARGE.get());

            // Brace 蓄势：施法前消耗 stacks 加成，施法结束移除
            ModifierRegistry.registerSpellCastHook(new SpellCastHook() {
                @Override
                public void onPreCast(ServerPlayer player, ItemStack gunStack) {
                    BraceHandler.applySpellCastBonus(player, gunStack);
                }

                @Override
                public void onCastEnd(ServerPlayer player) {
                    BraceHandler.removeSpellCastBonus(player);
                }
            });

            // ArcaneFocus 奥术聚焦：施法成功后标记爆头增强
            ModifierRegistry.registerSpellCastHook(new SpellCastHook() {
                @Override
                public void onPostCast(ServerPlayer player, ItemStack gunStack) {
                    ArcaneFocusHandler.onSpellCast(player, gunStack);
                }
            });
        });
    }
}
