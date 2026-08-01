package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 魔力弹匣（mana_magazine Perk）核心逻辑
 *
 * 弹匣打空时向弹匣写入 1 发真实弹药（setCurrentAmmoCount），使 TACZ 看到"有弹"
 * 而继续正常射击 / 上膛流程，不进入换弹分支。写入的这发会被 TACZ 正常消耗，弹匣回到 0。
 *
 * 三种触发场景：
 * - 闭膛 / 开膛枪空仓扣扳机：noAmmo 判定处翻转（{@link #tryConjure}），即时击发。
 * - 连发 / 多重扳机中途打空：reduceAmmoOnce 续弹（{@link #tryConjure}），每 cycle 各补 1 发。
 * - 栓动枪（狙击）空仓扣扳机：客户端翻转 noAmmo 让 TACZ 走到自动拉栓分支，拉栓时
 *   {@link #tryConjureForBolt} 补 1 发供入膛，保持原版拉栓动画，每发烧蓝替代换弹。s
 */
public final class ManaMagazineLogic {

    private ManaMagazineLogic() {}

    /**
     * 指定等级下每发补弹的法力消耗。
     * {@link EnchantConfig#MANA_MAGAZINE_BASE_COST} / {@code MANA_MAGAZINE_COST_REDUCTION_PER_LEVEL}
     * / {@code MANA_MAGAZINE_MIN_COST}，均可在配置中调整。
     */
    public static int costForLevel(int level) {
        int base = EnchantConfig.MANA_MAGAZINE_BASE_COST.get();
        int reduction = EnchantConfig.MANA_MAGAZINE_COST_REDUCTION_PER_LEVEL.get();
        int min = EnchantConfig.MANA_MAGAZINE_MIN_COST.get();
        return Math.max(base - reduction * level, min);
    }

    /**
     * 基础校验（不含栓型过滤）：附魔存在、非背包直读、弹匣空、法力足够。
     *
     * @return 可补弹时返回每发蓝耗 cost(>=0)；否则返回 -1
     */
    private static int baseCost(LivingEntity player, ItemStack gun, float mana) {
        if (player == null || gun == null || gun.isEmpty()) return -1;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return -1;
        int level = EnchantmentRegistry.MANA_MAGAZINE.get().levelOnGun(gun);
        if (level <= 0) return -1;
        if (iGun.useInventoryAmmo(gun)) return -1;
        if (iGun.getCurrentAmmoCount(gun) >= 1) return -1;
        int cost = costForLevel(level);
        if (mana < cost) return -1;
        return cost;
    }

    private static Bolt boltOf(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return null;
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(idx -> idx.getGunData().getBolt())
                .orElse(null);
    }

    /**
     * 仅判定能否补弹（不写弹、不扣蓝、不区分栓型）。客户端 noAmmo 翻转用：
     * 闭膛/开膛 → 翻转后即时击发；栓动 → 翻转后让 TACZ 走到自动拉栓分支。
     */
    public static boolean canConjure(LivingEntity player, ItemStack gun, float mana) {
        return baseCost(player, gun, mana) >= 0;
    }

    /**
     * 仅判定栓动枪能否拉栓补弹（不写弹、不扣蓝）。客户端 / 服务端 bolt() 包裹用：
     * 先判栓型（避免 tickAutoBolt 每 tick 对非栓动枪做无谓校验），再走基础校验。
     */
    public static boolean canConjureForBolt(LivingEntity player, ItemStack gun, float mana) {
        if (gun == null || gun.isEmpty()) return false;
        if (IGun.getIGunOrNull(gun) == null) return false;
        if (boltOf(gun) != Bolt.MANUAL_ACTION) return false;
        return baseCost(player, gun, mana) >= 0;
    }

    /**
     * 闭膛 / 开膛枪补弹：校验通过后写 1 发进弹匣，服务端可选扣蓝。
     * 用于 noAmmo 即时击发与 reduceAmmoOnce 续弹；栓动枪跳过（交给拉栓路径）。
     */
    public static boolean tryConjure(LivingEntity player, ItemStack gun, float mana, boolean deductMana) {
        int cost = baseCost(player, gun, mana);
        if (cost < 0) return false;
        if (boltOf(gun) == Bolt.MANUAL_ACTION) return false;
        IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 1);
        if (deductMana) deduct(player, cost);
        return true;
    }

    /**
     * 栓动枪（狙击）拉栓补弹：校验通过后写 1 发进弹匣供拉栓上膛，服务端可选扣蓝。
     * 仅对栓动枪生效。
     */
    public static boolean tryConjureForBolt(LivingEntity player, ItemStack gun, float mana, boolean deductMana) {
        int cost = baseCost(player, gun, mana);
        if (cost < 0) return false;
        if (boltOf(gun) != Bolt.MANUAL_ACTION) return false;
        IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 1);
        if (deductMana) deduct(player, cost);
        return true;
    }

    private static void deduct(LivingEntity player, int cost) {
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData != null) {
            magicData.setMana(Math.max(0f, magicData.getMana() - cost));
        }
    }
}
