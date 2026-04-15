package com.hhdxcz.wa.mixin.client;

import com.hhdxcz.wa.paper.WaPaperState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = PlayerRenderer.class, priority = 500)
public abstract class PlayerRendererMixin {
    @Unique
    private static final ConcurrentHashMap<UUID, Float> WA_PAPER_IDLE_YAW = new ConcurrentHashMap<>();

    @Unique
    private static boolean wa$isPaperIdle(AbstractClientPlayer player) {
        if (player == null) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        double h = motion.x * motion.x + motion.z * motion.z;
        return player.onGround()
                && h < 1.0E-6D
                && Math.abs(motion.y) < 1.0E-6D;
    }

    @Unique
    private static float wa$getOrUpdateIdleYaw(UUID playerId, float yaw, boolean idle) {
        if (playerId == null) {
            return yaw;
        }
        if (!idle) {
            WA_PAPER_IDLE_YAW.put(playerId, yaw);
            return yaw;
        }
        Float locked = WA_PAPER_IDLE_YAW.get(playerId);
        if (locked == null) {
            WA_PAPER_IDLE_YAW.put(playerId, yaw);
            return yaw;
        }
        return locked;
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private float wa$lockPaperYawWhenIdle(float yaw, AbstractClientPlayer player) {
        if (player == null) {
            return yaw;
        }
        UUID playerId = player.getUUID();
        if (!WaPaperState.isPaper(playerId) || WaPaperState.isWall(playerId)) {
            WA_PAPER_IDLE_YAW.remove(playerId);
            return yaw;
        }
        boolean idle = wa$isPaperIdle(player);
        return wa$getOrUpdateIdleYaw(playerId, yaw, idle);
    }
}
