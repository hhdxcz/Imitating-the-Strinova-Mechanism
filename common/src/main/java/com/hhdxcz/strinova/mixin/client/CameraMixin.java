package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 相机 Mixin，用于控制贴墙玩家的第三人称视角缩放行为
@Mixin(Camera.class)
public abstract class CameraMixin {

    // 相机绑定的实体
    @Shadow
    private Entity entity;

    // 禁用贴墙玩家的第三人称穿墙缩放，将相机距离锁定为期望值
    @Inject(method = "getMaxZoom(D)D", at = @At("HEAD"), cancellable = true)
    private void strinova$disableThirdPersonWallClip(double desiredCameraDistance, CallbackInfoReturnable<Double> cir) {
        Entity cameraEntity = this.entity;
        if (!(cameraEntity instanceof Player player)) {
            return;
        }
        if (!WaPaperState.isWall(player.getUUID())) {
            return;
        }
        cir.setReturnValue(desiredCameraDistance);
    }
}