package com.zzdzt.arcanemag.event.enchant;

import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.KuangYanEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * kuang_yan（狂宴）触发处理器。
 *
 * 爆头击杀回蓝：订阅 {@link EntityKillByGunEvent}，服务端爆头击杀且攻击者玩家
 * 主手枪上刻有 kuang_yan 时，按附魔等级回复法力。
 *
 * 该事件服务端（EntityKineticBullet 命中结算）与客户端（ServerMessageGunKill）
 * 均会派发，故以 {@link LogicalSide#SERVER} 过滤，回蓝以服务端权威为准，
 * 客户端预测值由 IS 自身同步。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class KuangYanHandler {

    private KuangYanHandler() {}

    @SubscribeEvent
    public static void onEntityKillByGun(EntityKillByGunEvent event) {
        // 仅服务端：回蓝以服务端权威为准
        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        // 必须爆头击杀
        if (!event.isHeadShot()) return;

        // 攻击者必须是玩家
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        // 主手必须是枪
        ItemStack gun = player.getMainHandItem();
        if (gun.isEmpty() || !(gun.getItem() instanceof IGun)) return;

        int level = EnchantmentRegistry.KUANG_YAN.get().levelOnGun(gun);
        if (level <= 0) return;

        float percent = KuangYanEnchantment.restorePercentOnKill(level);
        if (percent <= 0f) return;

        // 回蓝 = 最大法力（IS MAX_MANA 属性）* 百分比；setMana 服务端自动 clamp 到上限
        float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float restore = maxMana * percent;
        if (restore <= 0f) return;

        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData != null) {
            magicData.setMana(magicData.getMana() + restore);
        }
    }
}
