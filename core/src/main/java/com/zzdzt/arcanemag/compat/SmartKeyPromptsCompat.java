package com.zzdzt.arcanemag.compat;

import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.keybind.CastKeyHandler;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * SmartKeyPrompts 可选联动。
 *
 * 当玩家主手持铭刻了法术的 TACZ 枪械时
 * 在屏幕上动态显示施法按键提示。
 * 
 * 无编译期依赖——未安装 SmartKeyPrompts 时本类完全静默。
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID, value = Dist.CLIENT)
public class SmartKeyPromptsCompat {

    private static final String GROUP = "arcane_mag_skp";
    private static final String SKP_CLASS = "com.mafuyu404.smartkeyprompts.SmartKeyPrompts";

    // 缓存反射方法，避免每 tick 查找；resolved 后 showMethod 为 null 表示 SKP 不可用
    private static boolean resolved = false;
    private static Method showMethod = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!ModList.get().isLoaded("smartkeyprompts")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.getConnection() == null) return;

        ItemStack gun = mc.player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun)) return;
        if (MagazineSpellHelper.extractSpell(gun) == null) return;

        invokeShow(GROUP, CastKeyHandler.KEY_CAST_SPELL);
    }

    private static void invokeShow(String group, String desc) {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> clazz = Class.forName(SKP_CLASS);
                showMethod = clazz.getMethod("show", String.class, String.class);
            } catch (Exception e) {
                showMethod = null;
            }
        }
        if (showMethod == null) return;
        try {
            showMethod.invoke(null, group, desc);
        } catch (Exception e) {

        }
    }
}
