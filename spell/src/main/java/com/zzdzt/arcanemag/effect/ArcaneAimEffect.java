package com.zzdzt.arcanemag.effect;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class ArcaneAimEffect extends MobEffect {
    // NBT 键名：存储锁定目标的 UUID
    public static final String AIM_TARGET_TAG = ArcaneMag.MODID + ":aim_target_uuid";

    public ArcaneAimEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8A2BE2); // 蓝紫色
    }
}