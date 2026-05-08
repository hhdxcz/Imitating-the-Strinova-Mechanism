package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaRenderPoseLeakGuard;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(
            method = "checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void strinova$drainLeakedPoseStackInVanillaCheck(PoseStack poseStack, CallbackInfo ci) {
        StrinovaRenderPoseLeakGuard.resetPaperTransform();
        StrinovaRenderPoseLeakGuard.drain(poseStack);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void strinova$drainLeakedPoseStackBeforeVanillaCheck(PoseStack poseStack, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        StrinovaRenderPoseLeakGuard.drain(poseStack);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void strinova$drainLeakedPoseStack(PoseStack poseStack, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        StrinovaRenderPoseLeakGuard.drain(poseStack);
    }
}
