package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 生物实体 Mixin，在预览飞行时强制返回滑翔 tick 计数
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallFlyingTicksPreviewMixin {
    // 在碰撞预览飞行时强制返回滑翔 tick 数为 1，触发滑翔相关渲染
    @Inject(method = "getFallFlyingTicks()I", at = @At("RETURN"), cancellable = true)
    private void strinova$forceFallFlyingTicksInPreview(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() > 0) {
            return;
        }
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        if (!StrinovaCollisionPreviewFlyContext.isPreviewFly(self.getUUID())) {
            return;
        }
        cir.setReturnValue(1);
    }
}