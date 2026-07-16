package com.zzdzt.arcanemag.event;

import com.zzdzt.arcanemag.item.ArcaneStaffItem;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 奥术法杖伤害加成处理器
 * 
 * 当玩家主手持有奥术法杖时：
 * 1. 法术伤害不低于 攻击力 × 法术强度 × 0.5
 * 2. 所有其他伤害（副手武器、弓箭等）同样享受此下限
 */
@Mod.EventBusSubscriber(modid = com.zzdzt.arcanemag.ArcaneMag.MODID)
public class ArcaneStaffDamageHandler {

    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        SpellDamageSource spellSource = event.getSpellDamageSource();
        if (!(spellSource.getEntity() instanceof Player player)) {
            return;
        }

        if (!isHoldingArcaneStaff(player)) {
            return;
        }

        float baseAmount = event.getOriginalAmount();
        float minDamage = calculateMinDamage(player);

        if (baseAmount < minDamage) {
            event.setAmount(minDamage);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {

        if (event.getSource() instanceof SpellDamageSource) {
            return;
        }

        Player player = getAttackingPlayer(event.getSource());
        if (player == null) return;

        if (!isHoldingArcaneStaff(player)) {
            return;
        }

        float baseAmount = event.getAmount();
        float minDamage = calculateMinDamage(player);

        if (baseAmount < minDamage) {
            event.setAmount(minDamage);
        }
    }

    /**
     * 从伤害来源中提取玩家攻击者
     */
    private static Player getAttackingPlayer(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return null;

        // 直接攻击
        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof Player player) {
            return player;
        }

        // 弹射物（弓箭、三叉戟等）—— getEntity() 返回拥有者
        Entity causingEntity = source.getEntity();
        if (causingEntity instanceof Player player) {
            return player;
        }

        return null;
    }

    /**
     * 检查玩家主手是否持有奥术法杖
     */
    private static boolean isHoldingArcaneStaff(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        return mainHand.getItem() instanceof ArcaneStaffItem;
    }

    /**
     * 计算最小伤害：攻击力 × 法术强度 × 0.5
     */
    public static float calculateMinDamage(Player player) {
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double spellPower = player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        return (float) (attackDamage * spellPower * 0.5);
    }
}