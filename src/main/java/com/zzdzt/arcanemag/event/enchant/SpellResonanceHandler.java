package com.zzdzt.arcanemag.event.enchant;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.SpellResonanceEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;

import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * spell_resonance（法术共鸣）触发处理器。
 *
 * 结算前（EntityHurtByGunEvent.Pre）将「附魔等级 × 弹匣法术等级 × 系数」加到 baseAmount，
 * 走 TACZ 正常结算（按穿甲比例分割 + 护甲减伤）。
 *
 * 仅服务端：伤害结算以服务端为准。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class SpellResonanceHandler {

    private SpellResonanceHandler() {}

    @SubscribeEvent
    public static void onEntityHurtByGunPre(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        int enchantLevel = EnchantmentRegistry.SPELL_RESONANCE.get().levelOnGun(gunStack);
        if (enchantLevel <= 0) return;

        SpellData spellData = MagazineSpellHelper.extractSpell(gunStack);
        if (spellData == null || spellData.getLevel() <= 0) return;

        float bonus = SpellResonanceEnchantment.calculateBonus(enchantLevel, spellData.getLevel());
        if (bonus <= 0) return;

        event.setBaseAmount(event.getBaseAmount() + bonus);
    }
}
