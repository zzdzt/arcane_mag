package com.zzdzt.arcanemag.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.tacz.guns.entity.EntityKineticBullet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.extensions.IForgeEntity;

/**
 * 射击-子弹延迟匹配账本。
 *
 * TaCZ 的 GunFireEvent 触发时子弹实体尚未创建（异步生成），
 * 本类通过三阶段 token 匹配将"一次射击"与"其产生的子弹实体"关联：
 *
 * 阶段一（rememberShot）：GunFireEvent 时生成 UUID token，存入 per-shooter 队列。
 * 阶段二（bindBullet）：EntityJoinLevelEvent 时按 gunId 从队列匹配，
 *         将 token 写入子弹的 persistentData。
 * 阶段三（claim）：命中事件时从子弹 persistentData 读取 token，取回 payload。
 *
 * 设计为通用基础设施：payload 类型为泛型 ShotPayload，
 * 未来法术可携带任意数据（元素类型、弹丸序号、施法上下文等）。
 *
 * 当前未启用——由 ShotTrackingEvents.ENABLED 控制。
 */
public final class ShotLedger {
    private static final String TOKEN_TAG = "arcanemag_shot_token";
    private static final long SHOT_WINDOW_TICKS = 4L;
    private static final long PAYLOAD_LIFETIME_TICKS = 600L;
    private static final long PRUNE_INTERVAL_TICKS = 20L;
    private static final int MAX_PENDING_PER_PLAYER = 16;

    private final Map<UUID, Deque<PendingShot>> pendingByShooter = new HashMap<>();
    private final Map<UUID, ShotPayload> payloads = new HashMap<>();
    private long nextPruneTick;

    /**
     * 阶段一：记录一次射击。
     * 在 GunFireEvent（服务端）时调用。
     *
     * @param shooter 射击者
     * @param gunId   枪械 ResourceLocation ID
     * @param payload 本次射击携带的数据
     */
    public void rememberShot(ServerPlayer shooter, ResourceLocation gunId, ShotPayload payload) {
        long now = shooter.server.getTickCount();
        UUID token = UUID.randomUUID();
        Deque<PendingShot> queue = pendingByShooter.computeIfAbsent(
                shooter.getUUID(), ignored -> new ArrayDeque<>());
        queue.addLast(new PendingShot(token, gunId, now + SHOT_WINDOW_TICKS));

        // 超出上限时淘汰最旧的
        while (queue.size() > MAX_PENDING_PER_PLAYER) {
            PendingShot evicted = queue.removeFirst();
            payloads.remove(evicted.token());
        }
        payloads.put(token, payload.withExpiry(now + PAYLOAD_LIFETIME_TICKS));
    }

    /**
     * 阶段二：将子弹实体与待匹配的射击关联。
     * 在 EntityJoinLevelEvent（服务端）时调用。
     *
     * @param shooter 子弹的 owner（射击者）
     * @param bullet  新生成的子弹实体
     */
    public void bindBullet(ServerPlayer shooter, EntityKineticBullet bullet) {
        CompoundTag data = ((IForgeEntity) bullet).getPersistentData();
        if (data.hasUUID(TOKEN_TAG)) return; // 已绑定

        UUID shooterId = shooter.getUUID();
        Deque<PendingShot> queue = pendingByShooter.get(shooterId);
        if (queue == null || queue.isEmpty()) return;

        ResourceLocation bulletGunId = bullet.getGunId();
        if (bulletGunId == null) return;

        long now = shooter.server.getTickCount();
        ShotPayload selectedPayload = null;

        // 从队列尾部向前查找匹配（最近的射击优先）
        for (var iterator = queue.descendingIterator(); iterator.hasNext();) {
            PendingShot candidate = iterator.next();
            ShotPayload candidatePayload = payloads.get(candidate.token());

            // 清理过期条目
            if (candidate.expiresAt() <= now || candidatePayload == null) {
                payloads.remove(candidate.token());
                iterator.remove();
                continue;
            }

            // 按 gunId 匹配
            if (candidate.gunId().equals(bulletGunId)) {
                selectedPayload = candidatePayload;
                break;
            }
        }

        if (queue.isEmpty()) pendingByShooter.remove(shooterId);

        if (selectedPayload != null) {
            // 为子弹生成独立 token（同一 payload 可被多颗弹丸共享引用，如霰弹）
            UUID bulletToken = UUID.randomUUID();
            payloads.put(bulletToken, selectedPayload);
            data.putUUID(TOKEN_TAG, bulletToken);
        }
    }

    /**
     * 阶段三：从子弹实体取回 payload（一次性消费）。
     * 在命中事件（EntityHurtByGunEvent / AmmoHitBlockEvent）时调用。
     *
     * @param bullet 命中的子弹实体
     * @return 该子弹携带的 payload，如果未绑定则 empty
     */
    public Optional<ShotPayload> claim(EntityKineticBullet bullet) {
        CompoundTag data = ((IForgeEntity) bullet).getPersistentData();
        if (!data.hasUUID(TOKEN_TAG)) return Optional.empty();
        return Optional.ofNullable(payloads.remove(data.getUUID(TOKEN_TAG)));
    }

    /**
     * 查询但不消费（用于判断子弹是否携带效果）。
     */
    public Optional<ShotPayload> peek(EntityKineticBullet bullet) {
        CompoundTag data = ((IForgeEntity) bullet).getPersistentData();
        if (!data.hasUUID(TOKEN_TAG)) return Optional.empty();
        return Optional.ofNullable(payloads.get(data.getUUID(TOKEN_TAG)));
    }

    /**
     * 定期清理过期条目，由 ServerTickEvent 调用。
     */
    public void prune(long gameTick) {
        if (gameTick < nextPruneTick) return;
        nextPruneTick = gameTick + PRUNE_INTERVAL_TICKS;
        if (pendingByShooter.isEmpty() && payloads.isEmpty()) return;

        pendingByShooter.values().removeIf(queue -> {
            queue.removeIf(shot -> {
                boolean expired = shot.expiresAt() <= gameTick;
                if (expired) payloads.remove(shot.token());
                return expired;
            });
            return queue.isEmpty();
        });
        payloads.values().removeIf(payload -> payload.expiresAt() <= gameTick);
    }

    /**
     * 射击载荷：一次射击携带的数据。
     * 当前为通用骨架，未来法术可扩展字段（元素类型、弹丸序号、施法上下文等）。
     *
     * @param spellId   关联的法术 ID（可为 null 表示非法术射击）
     * @param spellLevel 法术等级
     * @param tag       自定义附加数据（可为 null）
     * @param expiresAt  过期 tick（由 withExpiry 设置）
     */
    public record ShotPayload(
            ResourceLocation spellId,
            int spellLevel,
            CompoundTag tag,
            long expiresAt
    ) {
        public ShotPayload(ResourceLocation spellId, int spellLevel, CompoundTag tag) {
            this(spellId, spellLevel, tag, Long.MAX_VALUE);
        }

        ShotPayload withExpiry(long expiresAt) {
            return new ShotPayload(spellId, spellLevel, tag, expiresAt);
        }
    }

    private record PendingShot(UUID token, ResourceLocation gunId, long expiresAt) {}
}
