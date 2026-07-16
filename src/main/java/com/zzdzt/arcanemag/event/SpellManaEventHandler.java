package com.zzdzt.arcanemag.event;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪械施法法力系数处理器
 *
 * 当玩家通过枪械施法时，在 SpellOnCastEvent 中读取 MANA_COST_MULTIPLIER 配置，
 * 将实际法力消耗乘以系数。例如系数 0.1 时，100 法力法术只消耗 10 法力。
 *
 * 配合 SpellCastHandler 中的预检逻辑，确保法力预检和实际扣除使用一致的系数。
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class SpellManaEventHandler {

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 检测是否为枪械施法：主手持有 IGun 且弹匣铭刻了法术
        ItemStack gunStack = player.getMainHandItem();
        if (!(gunStack.getItem() instanceof com.tacz.guns.api.item.IGun)) return;

        SpellData spellData = MagazineSpellHelper.extractSpell(gunStack);
        if (spellData == null) return;

        // 应用法力系数
        float coeff = ArcaneMagConfig.MANA_COST_MULTIPLIER.get().floatValue();
        if (coeff == 1.0f) return;

        int baseCost = event.getManaCost();
        int newCost = Math.max(1, (int) (baseCost * coeff));
        event.setManaCost(newCost);

        ArcaneMag.LOGGER.debug("Gun cast mana coefficient {}: {} -> {}",
                coeff, baseCost, newCost);
    }
}
