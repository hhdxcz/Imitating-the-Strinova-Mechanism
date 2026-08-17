package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 行走动画状态 Mixin，在碰撞预览时禁用行走动画
@Mixin(WalkAnimationState.class)
public abstract class WalkAnimationStatePreviewMixin {

    // 预览时行走速度归零
    @Inject(method = "speed(F)F", at = @At("RETURN"), cancellable = true)
    private void strinova$zeroSpeed(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (StrinovaCollisionPreviewFlyContext.isPreviewActive()) {
            cir.setReturnValue(0.0F);
        }
    }

    // 预览时行走位移归零
    @Inject(method = "position(F)F", at = @At("RETURN"), cancellable = true)
    private void strinova$zeroPosition(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (StrinovaCollisionPreviewFlyContext.isPreviewActive()) {
            cir.setReturnValue(0.0F);
        }
    }

    // 预览时行走速度归零（无参版本）
    @Inject(method = "speed()F", at = @At("RETURN"), cancellable = true)
    private void strinova$zeroSpeedNoArg(CallbackInfoReturnable<Float> cir) {
        if (StrinovaCollisionPreviewFlyContext.isPreviewActive()) {
            cir.setReturnValue(0.0F);
        }
    }

    // 预览时行走位移归零（无参版本）
    @Inject(method = "position()F", at = @At("RETURN"), cancellable = true)
    private void strinova$zeroPositionNoArg(CallbackInfoReturnable<Float> cir) {
        if (StrinovaCollisionPreviewFlyContext.isPreviewActive()) {
            cir.setReturnValue(0.0F);
        }
    }

    // 预览时始终标记为未移动
    @Inject(method = "isMoving()Z", at = @At("RETURN"), cancellable = true)
    private void strinova$notMoving(CallbackInfoReturnable<Boolean> cir) {
        if (StrinovaCollisionPreviewFlyContext.isPreviewActive()) {
            cir.setReturnValue(false);
        }
    }
}