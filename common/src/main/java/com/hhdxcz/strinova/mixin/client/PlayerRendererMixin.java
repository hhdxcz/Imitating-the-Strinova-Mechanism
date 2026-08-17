package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 玩家渲染器 Mixin，在纸片化玩家静止时锁定其朝向，防止微小抖动
@Mixin(value = PlayerRenderer.class, priority = 500)
public abstract class PlayerRendererMixin {
    // 纸片化玩家静止时的朝向锁定缓存
    @Unique
    private static final ConcurrentHashMap<UUID, Float> STRINOVA_PAPER_IDLE_YAW = new ConcurrentHashMap<>();

    // 判断纸片化玩家是否处于静止状态
    @Unique
    private static boolean strinova$isPaperIdle(AbstractClientPlayer player) {
        if (player == null) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        double h = motion.x * motion.x + motion.z * motion.z;
        return player.onGround()
                && h < 1.0E-6D
                && Math.abs(motion.y) < 1.0E-6D;
    }

    // 获取或更新静止朝向：非静止时更新缓存，静止时返回缓存值
    @Unique
    private static float strinova$getOrUpdateIdleYaw(UUID playerId, float yaw, boolean idle) {
        if (playerId == null) {
            return yaw;
        }
        if (!idle) {
            STRINOVA_PAPER_IDLE_YAW.put(playerId, yaw);
            return yaw;
        }
        Float locked = STRINOVA_PAPER_IDLE_YAW.get(playerId);
        if (locked == null) {
            STRINOVA_PAPER_IDLE_YAW.put(playerId, yaw);
            return yaw;
        }
        return locked;
    }

    // 拦截渲染 yaw 参数，在纸片化玩家静止时锁定朝向
    @ModifyVariable(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private float strinova$lockPaperYawWhenIdle(float yaw, AbstractClientPlayer player) {
        if (player == null) {
            return yaw;
        }
        UUID playerId = player.getUUID();
        if (!WaPaperState.isPaper(playerId) || WaPaperState.isWall(playerId)) {
            STRINOVA_PAPER_IDLE_YAW.remove(playerId);
            return yaw;
        }
        boolean idle = strinova$isPaperIdle(player);
        return strinova$getOrUpdateIdleYaw(playerId, yaw, idle);
    }
}