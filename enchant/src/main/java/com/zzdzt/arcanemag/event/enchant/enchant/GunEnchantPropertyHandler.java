package com.zzdzt.arcanemag.event.enchant;

import java.util.LinkedList;
import java.util.function.Function;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.gun.GunPropertyContext;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * enchant 乘区统一枪属性应用处理器。
 *
 * 乘区规则：
 *   - spell 乘区（spell/ 下法术触发，如 GunEnhance/TacticalDash）：各自应用，保持现状
 *   - enchant 乘区（enchant/ 下附魔触发，如 ManaHawkEye/GuidanceAmplify）：同乘区内加法叠加
 *
 * 本处理器仅负责 enchant 乘区：汇总所有活跃 enchant perk 的 damage/rpm 百分比加成，
 * 加法累加后一次乘到 cache（damageMul = 1 + sumDamage%，rpmMul = 1 + sumRpm%）。
 * 与 spell 乘区为乘法关系（spell 已先改 cache，本处理器在 spell 改后的 cache 上再乘）。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public final class GunEnchantPropertyHandler {

    private GunEnchantPropertyHandler() {}

    @SubscribeEvent
    public static void onAttachmentPropertyEvent(AttachmentPropertyEvent event) {
        LivingEntity shooter = GunPropertyContext.getShooter();
        if (!(shooter instanceof Player player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        // 汇总 enchant 乘区所有活跃 perk 的百分比加成（加法）
        double sumDamagePercent = 0;
        double sumRpmPercent = 0;

        // 法力鹰眼：仅贡献伤害
        sumDamagePercent += ManaHawkEyeHandler.getActiveDamageBonusPercent(player);

        // 引导增幅：贡献伤害 + 射速
        sumDamagePercent += GuidanceAmplifyHandler.getActiveDamageBonusPercent(player);
        sumRpmPercent += GuidanceAmplifyHandler.getActiveRpmBonusPercent(player);

        // 充盈：贡献伤害 + 射速
        sumDamagePercent += OverflowHandler.getActiveDamageBonusPercent(player);
        sumRpmPercent += OverflowHandler.getActiveRpmBonusPercent(player);

        // 火力狂热：仅贡献伤害
        sumDamagePercent += FrenzyHandler.getFireFrenzyDamageBonusPercent(player);

        if (sumDamagePercent <= 0 && sumRpmPercent <= 0) return;

        AttachmentCacheProperty cache = event.getCacheProperty();
        if (sumDamagePercent > 0) {
            float damageMul = 1.0f + (float) (sumDamagePercent / 100.0);
            modifyDamage(cache, damageMul);
        }
        if (sumRpmPercent > 0) {
            float rpmMul = 1.0f + (float) (sumRpmPercent / 100.0);
            modifyInteger(cache, GunProperties.ROUNDS_PER_MINUTE, v -> Math.max(1, (int) (v * rpmMul)));
        }
    }

    // ==================== 工具 ====================

    private static void modifyDamage(AttachmentCacheProperty cache, float multiplier) {
        LinkedList<ExtraDamage.DistanceDamagePair> damage = cache.getCache(GunProperties.DAMAGE);
        if (damage == null || damage.isEmpty()) return;
        LinkedList<ExtraDamage.DistanceDamagePair> newDamage = new LinkedList<>();
        for (ExtraDamage.DistanceDamagePair pair : damage) {
            newDamage.add(new ExtraDamage.DistanceDamagePair(
                    pair.getDistance(),
                    pair.getDamage() * multiplier
            ));
        }
        cache.setCache(GunProperties.DAMAGE, newDamage);
    }

    private static void modifyInteger(AttachmentCacheProperty cache, com.tacz.guns.api.GunProperty<Integer> property, Function<Integer, Integer> func) {
        Integer val = cache.getCache(property);
        if (val != null) {
            cache.setCache(property, func.apply(val));
        }
    }
}
