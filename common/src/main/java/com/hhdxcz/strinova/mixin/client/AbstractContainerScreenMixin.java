package com.hhdxcz.strinova.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // 临时：继续隔离问题，先不 drain/重置容器 Screen 的 pose 状态。
    /*
    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void strinova$resetGuiStateBeforeContainerRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (guiGraphics == null) {
            return;
        }
        StrinovaRenderPoseLeakGuard.drain(guiGraphics.pose());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void strinova$resetGuiStateAfterContainerRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (guiGraphics == null) {
            return;
        }
        StrinovaRenderPoseLeakGuard.drain(guiGraphics.pose());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    */
}
