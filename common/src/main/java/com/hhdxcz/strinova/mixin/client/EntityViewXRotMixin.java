package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaRenderPoseLeakGuard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// 飘飞渲染时强制 getViewXRot/getXRot 返回固定值，使模型始终保持与地面平行
@Mixin(Entity.class)
public abstract class EntityViewXRotMixin {
    @Inject(method = "getViewXRot(F)F", at = @At("RETURN"), cancellable = true)
    private void strinova$forceFlyViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        Object self = this;
        if (!(self instanceof Entity entity)) {
            return;
        }
        UUID playerId = entity.getUUID();
        if (!StrinovaRenderPoseLeakGuard.isFlyRenderActive(playerId)) {
            return;
        }
        cir.setReturnValue(0.0F);
    }

    // 原版鞘翅（FALL_FLYING）渲染在 setupRotations 中读取 getXRot() 计算俯仰：
    //   rotationDegrees(k * (-90 - getXRot()))
    // 仅当 getXRot() == -90（视角朝正上方）时该旋转为 0，
    // 模型才能保持本模组在 EntityRenderDispatcherMixin 里已完成的躺平变换。
    // 因此飘飞渲染窗口内强制 getXRot() 返回 -90，让任意视角都等价于“面朝天空”。
    @Inject(method = "getXRot()F", at = @At("RETURN"), cancellable = true)
    private void strinova$forceFlyXRot(CallbackInfoReturnable<Float> cir) {
        Object self = this;
        if (!(self instanceof Entity entity)) {
            return;
        }
        UUID playerId = entity.getUUID();
        if (!StrinovaRenderPoseLeakGuard.isFlyRenderActive(playerId)) {
            return;
        }
        cir.setReturnValue(-90.0F);
    }
}