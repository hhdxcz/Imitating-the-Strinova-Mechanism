package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaRenderPoseLeakGuard;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 屏幕 Mixin，在界面渲染前清理 PoseStack 泄漏并重置渲染状态
@Mixin(Screen.class)
public abstract class ScreenMixin {

    // 渲染屏幕前排空 PoseStack 泄漏，重置颜色和混合模式为默认状态
    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void strinova$drainLeakedPoseStackBeforeScreenRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (guiGraphics == null) {
            return;
        }
        StrinovaRenderPoseLeakGuard.drain(guiGraphics.pose());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // 打开本模组界面时给游戏画面加轻量高斯模糊
    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("HEAD"), require = 0)
    private void strinova$beginUiBlur(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        if (((Object) this) instanceof Screen screen
                && screen.getClass().getName().startsWith("com.hhdxcz.strinova")) {
            com.hhdxcz.strinova.client.StrinovaUiBlur.begin();
        }
    }

    // 关闭界面时恢复清晰画面
    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void strinova$endUiBlur(CallbackInfo ci) {
        if (((Object) this) instanceof Screen screen
                && screen.getClass().getName().startsWith("com.hhdxcz.strinova")) {
            com.hhdxcz.strinova.client.StrinovaUiBlur.end();
        }
    }
}