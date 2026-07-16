package com.zzdzt.arcanemag.utils;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.attachment.MeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunMeleeData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.UUID;

/**
 * 武器类型系数管理器
 * 
 * 根据 TACZ GunTabType 返回对应的充能系数。
 * 支持配置热重载，自动降级到默认值 1.0。
 */
public final class WeaponTypeCoefManager {

    private static final double DEFAULT_COEF = 1.0;
    private static final UUID MELEE_DAMAGE_MODIFIER_UUID = UUID.fromString("6c5042b2-28f3-4b73-b181-cee3d93a5067");

    private WeaponTypeCoefManager() {}

    /**
     * 根据枪支 ID 获取武器类型系数
     */
    public static double getCoef(String gunTypeString) {
        if (gunTypeString == null || gunTypeString.isEmpty()) {
            return DEFAULT_COEF;
        }

        String key = gunTypeString.toLowerCase();

        // 从配置中读取
        Map<String, Double> configMap = ArcaneMagConfig.getWeaponTypeCoefMap();
        if (configMap != null && configMap.containsKey(key)) {
            return configMap.get(key);
        }

        // 配置中没有，打印警告并使用默认值
        ArcaneMag.LOGGER.warn(
            "[ArcaneMag] Gun type '{}' not found in config, using default coefficient {}. " +
            "Add '{} = <value>' to 'mod_charge.weapon_type_coefficients' to customize.",
            key, DEFAULT_COEF, key
        );
        return DEFAULT_COEF;
    }

    /**
     * 从 TACZ 事件中获取枪支类型并查表返回系数
     */
    public static double getCoefFromEvent(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Post event) {
        try {
            com.tacz.guns.resource.index.CommonGunIndex index = 
                com.tacz.guns.api.TimelessAPI.getCommonGunIndex(event.getGunId()).orElse(null);
            if (index == null) return DEFAULT_COEF;
            return getCoef(index.getType());
        } catch (Exception e) {
            ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to get weapon type coefficient: {}", e.getMessage());
            return DEFAULT_COEF;
        }
    }

    /**
     * 从枪械物品栈获取武器类型系数
     */
    public static double getCoefFromGunStack(ItemStack gunStack) {
        if (!(gunStack.getItem() instanceof IGun iGun)) return DEFAULT_COEF;
        try {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            com.tacz.guns.resource.index.CommonGunIndex index = 
                TimelessAPI.getCommonGunIndex(gunId).orElse(null);
            if (index == null) return DEFAULT_COEF;
            return getCoef(index.getType());
        } catch (Exception e) {
            ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to get weapon type coefficient from gun stack: {}", e.getMessage());
            return DEFAULT_COEF;
        }
    }

    /**
     * 提取并计算近战伤害
     * 模拟 TACZ ModernKineticGunItem.doMelee() 的伤害计算逻辑
     * 
     * @param attacker 攻击者（玩家）
     * @param gunStack 枪械物品栈
     * @return 实际近战伤害值，无配置时返回 0
     */
    public static float getMeleeDamage(LivingEntity attacker, ItemStack gunStack) {
        if (!(gunStack.getItem() instanceof IGun iGun)) return 0f;

        ResourceLocation gunId = iGun.getGunId(gunStack);
        return TimelessAPI.getCommonGunIndex(gunId).map(gunIndex -> {
            GunMeleeData meleeData = gunIndex.getGunData().getMeleeData();

            ResourceLocation muzzleId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
            MeleeData muzzleMeleeData = getMeleeData(muzzleId);
            if (muzzleMeleeData != null) {
                return calculateFinalDamage(attacker, muzzleMeleeData.getDamage());
            }

            ResourceLocation stockId = iGun.getAttachmentId(gunStack, AttachmentType.STOCK);
            MeleeData stockMeleeData = getMeleeData(stockId);
            if (stockMeleeData != null) {
                return calculateFinalDamage(attacker, stockMeleeData.getDamage());
            }

            GunDefaultMeleeData defaultMeleeData = meleeData.getDefaultMeleeData();
            if (defaultMeleeData != null) {
                return calculateFinalDamage(attacker, defaultMeleeData.getDamage());
            }

            return 0f;
        }).orElse(0f);
    }

    /**
     * 应用玩家 ATTACK_DAMAGE 属性加成，模拟 TACZ 的伤害计算方式
     */
    private static float calculateFinalDamage(LivingEntity attacker, float baseDamage) {
        if (baseDamage <= 0) return baseDamage;

        AttributeInstance instance = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return baseDamage;
        }

        double oldBase = instance.getBaseValue();
        AttributeModifier modifier = new AttributeModifier(
            MELEE_DAMAGE_MODIFIER_UUID, "ArcaneMagMeleeDamage", baseDamage,
            AttributeModifier.Operation.ADDITION
        );
        try {
            instance.setBaseValue(0);
            instance.addTransientModifier(modifier);
            return (float) instance.getValue();
        } finally {
            instance.setBaseValue(oldBase);
            instance.removeModifier(modifier);
        }
    }

    /**
     * 从配件 ID 获取近战数据
     */
    @javax.annotation.Nullable
    private static MeleeData getMeleeData(ResourceLocation attachmentId) {
        if (DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
            .map(index -> index.getData().getMeleeData())
            .orElse(null);
    }
}
