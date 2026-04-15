package com.hhdxcz.wa.mixin.client;

import com.hhdxcz.wa.paper.WaPaperState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Entity entity;

    @Inject(method = "getMaxZoom(D)D", at = @At("HEAD"), cancellable = true)
    private void wa$disableThirdPersonWallClip(double desiredCameraDistance, CallbackInfoReturnable<Double> cir) {
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