package com.zzdzt.arcanemag.event.enchant;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * arcane_focus（奥术聚焦）触发处理器。
 *
 * 两段逻辑：
 * 1. 施法成功 → {@link #onSpellCast} 写入玩家 NBT 层数（= 枪上 arcane_focus 等级）。
 * 2. 子弹命中实体（{@link EntityHurtByGunEvent.Pre}）→ 消耗一层；
 *    若为爆头命中，再叠加爆头倍率加成。
 *
 * 命中即消耗（无论爆头与否），但加成仅爆头生效，故非爆头命中浪费层数，
 * 以此抬高瞄准门槛。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public final class ArcaneFocusHandler {

    /** 玩家持久化 NBT 中的层数键 */
    private static final String KEY_CHARGES = "arcanemag:arcane_focus_charges";

    private ArcaneFocusHandler() {}

    /**
     * 施法成功后调用：若枪上刻有 arcane_focus，将层数设为其等级。
     * 由 SpellCastHandler 在瞬时/长施法成功后触发。
     */
    public static void onSpellCast(ServerPlayer player, ItemStack gunStack) {
        if (gunStack == null || !(gunStack.getItem() instanceof IGun)) return;
        int level = EnchantmentRegistry.ARCANE_FOCUS.get().levelOnGun(gunStack);
        if (level <= 0) return;
        player.getPersistentData().putInt(KEY_CHARGES, level);
    }

    @SubscribeEvent
    public static void onEntityHurtByGunPre(EntityHurtByGunEvent.Pre event) {
        // 仅服务端：伤害与层数均以服务端为准
        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        int charges = player.getPersistentData().getInt(KEY_CHARGES);
        if (charges <= 0) return;

        // 命中即消耗一层（无论是否爆头）
        player.getPersistentData().putInt(KEY_CHARGES, charges - 1);

        // 仅爆头命中给予倍率加成
        if (event.isHeadShot()) {
            float bonus = EnchantConfig.ARCANE_FOCUS_HEADSHOT_BONUS.get().floatValue();
            event.setHeadshotMultiplier(event.getHeadshotMultiplier() + bonus);
        }
    }
}
