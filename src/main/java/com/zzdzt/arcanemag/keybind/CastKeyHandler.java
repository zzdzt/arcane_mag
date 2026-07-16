package com.zzdzt.arcanemag.keybind;

import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.network.SpellCastMessage;
import com.zzdzt.arcanemag.network.StopCastMessage;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CastKeyHandler {
    public static final String KEY_CATEGORY = "key.categories.arcanemag";
    public static final String KEY_CAST_SPELL = "key.arcanemag.cast_spell";
    public static KeyMapping CAST_KEY;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        CAST_KEY = new KeyMapping(KEY_CAST_SPELL, KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, KEY_CATEGORY);
        event.register(CAST_KEY);
    }

    @Mod.EventBusSubscriber(modid = ArcaneMag.MODID, value = Dist.CLIENT)
    public static class KeyPoller {
        private static boolean wasPressed = false;
        private static boolean isChanneling = false;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (CAST_KEY == null) return;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            Player player = mc.player;
            ItemStack mainHand = player.getMainHandItem();

            boolean hasValidGun = mainHand.getItem() instanceof IGun && MagazineSpellHelper.hasSpellMagazine(mainHand);
            boolean pressed = CAST_KEY.isDown();

            // 如果正在持续施法，检测松手事件
            if (isChanneling) {
                if (!hasValidGun) {
                    StopCastMessage.sendToServer();
                    isChanneling = false;
                    wasPressed = false;
                    return;
                }
                // 持续施法：松开按键时发送停止施法
                if (wasPressed && !pressed) {
                    StopCastMessage.sendToServer();
                    isChanneling = false;
                }
                wasPressed = pressed;
                return;
            }

            // 如果没有有效枪械，重置状态
            if (!hasValidGun) {
                wasPressed = false;
                return;
            }

            SpellData spellData = MagazineSpellHelper.extractSpell(mainHand);
            if (spellData == null) {
                wasPressed = false;
                return;
            }

            CastType castType = spellData.getSpell().getCastType();

            // 客户端充能检查（仅提示，最终检查在服务端）
            ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(mainHand);
            if (magazine != null) {
                boolean canCast = ModChargeData.hasStacks(magazine) || ModChargeData.isFull(magazine);
                if (!canCast) {
                    // 充能不足，显示提示（不发送网络包）
                    if (!wasPressed && pressed) {
                        mc.player.displayClientMessage(
                            Component.literal("§c充能不足"),
                            true
                        );
                    }
                    wasPressed = pressed;
                    return;
                }
            }

            if (castType == CastType.CONTINUOUS) {
                // 仅持续施法需要按下开始、松手结束
                if (!wasPressed && pressed) {
                    SpellCastMessage.sendToServer(spellData);
                    isChanneling = true;
                }
            } else {
                // 瞬发(INSTANT)和长施法(LONG)统一使用边缘检测，按下即触发
                if (!wasPressed && pressed) {
                    SpellCastMessage.sendToServer(spellData);
                }
            }
            
            wasPressed = pressed;
        }
    }
}
