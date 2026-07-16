package com.zzdzt.arcanemag.spell.jammingwaves.handler;

import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesContext;
import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 干扰波纹法力消耗管理器。
 * 
 * 负责拦截时的额外法力消耗，以及持续施法期间的基础 tick 消耗。
 */
public final class JammingWavesManaDrain {

    private JammingWavesManaDrain() {}

    /**
     * 每次成功拦截时消耗的法力。
     */
    public static void onIntercept(LivingEntity caster, JammingWavesContext context) {
        float drainMana = JammingWavesSpell.getDrainManaPerHit(context.spellLevel, caster);
        if (drainMana <= 0f) return;

        MagicData magicData = context.magicData;
        float currentMana = magicData.getMana();
        float newMana = Math.max(0f, currentMana - drainMana);
        magicData.setMana(newMana);


        if (newMana <= 0f && caster instanceof ServerPlayer serverPlayer) {
            io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(serverPlayer);
        }
    }

    /**
     * 持续施法期间每 tick 的基础法力消耗。
     */
    public static void perTick(LivingEntity caster, int spellLevel, MagicData magicData) {
        if (magicData == null || spellLevel <= 0) return;

        float tickDrain = JammingWavesSpell.getDrainManaPerHit(spellLevel, caster) * 0.05f;
        if (tickDrain <= 0f) return;

        float currentMana = magicData.getMana();
        float newMana = Math.max(0f, currentMana - tickDrain);
        magicData.setMana(newMana);

        if (newMana <= 0f && caster instanceof ServerPlayer serverPlayer) {
            io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(serverPlayer);
        }
    }
}
