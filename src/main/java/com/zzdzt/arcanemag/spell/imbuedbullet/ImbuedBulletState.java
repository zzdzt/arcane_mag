package com.zzdzt.arcanemag.spell.imbuedbullet;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 管理玩家的注魔子弹状态 
 * 
 * - 持续时间
 * - 等级（amplifier）
 * - 客户端同步
 * - 死亡/下线清理
 * 
 */
public final class ImbuedBulletState {

    private ImbuedBulletState() {}

    /**
     * 检查玩家是否有任何注魔效果激活。
     */
    public static boolean isImbued(ServerPlayer player) {
        for (var entry : com.zzdzt.arcanemag.registry.SpellRegistry.SPELLS.getEntries()) {
            if (entry.get() instanceof ImbuedBulletSpell imbuedSpell) {
                if (player.hasEffect(imbuedSpell.getImbuedEffect())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取玩家当前激活的注魔法术（如果存在）。
     */
    public static ImbuedBulletSpell getActiveSpell(ServerPlayer player) {
        for (var entry : com.zzdzt.arcanemag.registry.SpellRegistry.SPELLS.getEntries()) {
            if (entry.get() instanceof ImbuedBulletSpell imbuedSpell) {
                if (player.hasEffect(imbuedSpell.getImbuedEffect())) {
                    return imbuedSpell;
                }
            }
        }
        return null;
    }

    /**
     * 清除玩家的所有注魔效果。
     * 在死亡、下线、切换维度时调用。
     */
    public static void clearAll(ServerPlayer player) {
        for (var entry : com.zzdzt.arcanemag.registry.SpellRegistry.SPELLS.getEntries()) {
            if (entry.get() instanceof ImbuedBulletSpell imbuedSpell) {
                MobEffect effect = imbuedSpell.getImbuedEffect();
                if (player.hasEffect(effect)) {
                    player.removeEffect(effect);
                }
                // 清理子类存储的NBT数据
                imbuedSpell.clearPersistentData(player);
            }
        }
    }
}
