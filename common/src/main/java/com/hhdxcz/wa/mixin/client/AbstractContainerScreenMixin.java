package com.hhdxcz.wa.mixin.client;

import com.hhdxcz.wa.client.WaRenderPoseLeakGuard;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // 临时：继续隔离问题，先不 drain/重置容器 Screen 的 pose 状态。
    /*
    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void wa$resetGuiStateBeforeContainerRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (guiGraphics == null) {
            return;
        }
        WaRenderPoseLeakGuard.drain(guiGraphics.pose());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void wa$resetGuiStateAfterContainerRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (guiGraphics == null) {
            return;
        }
        WaRenderPoseLeakGuard.drain(guiGraphics.pose());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    */
}
