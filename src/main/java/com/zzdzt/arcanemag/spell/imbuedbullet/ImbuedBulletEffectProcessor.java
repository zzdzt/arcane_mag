package com.zzdzt.arcanemag.spell.imbuedbullet;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 注魔子弹学派特效处理器接口。
 * 
 * 每个学派实现此接口，定义：
 * 1. 施法时的视觉效果/音效
 * 2. 命中时的学派特定效果（点燃、减速、治疗等）
 * 3. Tooltip描述
 */
public interface ImbuedBulletEffectProcessor {

    //获取特效描述（用于法术Tooltip）
    MutableComponent getDescriptionComponent(int spellLevel);

    //施法时的效果（粒子、音效等）
    default void onCast(ServerPlayer caster, int spellLevel) {}

    //命中时的核心效果
    void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel);

    //持续期间的效果（可选，每tick调用）
    default void onTick(ServerPlayer caster, int spellLevel) {}


}