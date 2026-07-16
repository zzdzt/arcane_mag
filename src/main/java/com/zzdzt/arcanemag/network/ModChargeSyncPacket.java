package com.zzdzt.arcanemag.network;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.utils.ModChargeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 充能数据同步包（服务端 → 客户端）
 * 同步弹匣的充能数据到客户端，用于 HUD 显示。
 */
public class ModChargeSyncPacket {

    private final CompoundTag chargeTag;

    public ModChargeSyncPacket(ItemStack magazine) {
        this.chargeTag = magazine.getOrCreateTag().copy();
    }

    public ModChargeSyncPacket(FriendlyByteBuf buf) {
        this.chargeTag = buf.readAnySizeNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(chargeTag);
    }

    public static ModChargeSyncPacket decode(FriendlyByteBuf buf) {
        return new ModChargeSyncPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端接收，更新持有枪械的弹匣 NBT
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;

            net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            ItemStack gunStack = mc.player.getItemInHand(hand);
            if (gunStack.isEmpty()) return;

            ItemStack magazine = com.zzdzt.arcanemag.utils.MagazineSpellHelper
                .getMagazineAttachment(gunStack);
            if (magazine == null) return;

            // 更新充能相关 NBT
            CompoundTag tag = magazine.getOrCreateTag();
            if (chargeTag.contains("arcanemag:charge")) {
                tag.putDouble("arcanemag:charge", chargeTag.getDouble("arcanemag:charge"));
            }
            if (chargeTag.contains("arcanemag:charge_max")) {
                tag.putDouble("arcanemag:charge_max", chargeTag.getDouble("arcanemag:charge_max"));
            }
            if (chargeTag.contains("arcanemag:charge_stacks")) {
                tag.putInt("arcanemag:charge_stacks", chargeTag.getInt("arcanemag:charge_stacks"));
            }
            if (chargeTag.contains("arcanemag:max_charge_stacks")) {
                tag.putInt("arcanemag:max_charge_stacks", chargeTag.getInt("arcanemag:max_charge_stacks"));
            }
            if (chargeTag.contains("arcanemag:overdrive_stacks")) {
                tag.putInt("arcanemag:overdrive_stacks", chargeTag.getInt("arcanemag:overdrive_stacks"));
            }
            if (chargeTag.contains("arcanemag:overdrive_expire")) {
                tag.putLong("arcanemag:overdrive_expire", chargeTag.getLong("arcanemag:overdrive_expire"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
