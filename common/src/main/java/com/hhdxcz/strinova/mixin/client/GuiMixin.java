package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudMockData;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 战斗 HUD 开启自定义准星时隐藏原版准星，避免屏幕上出现双重准星。
 * 条件与 HUD 渲染保持一致：HUD 关闭、准星关闭或模组未启用（且未开模拟数据）时保留原版准星。
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void strinova$hideVanillaCrosshair(GuiGraphics graphics, CallbackInfo ci) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        if (!config.hudEnabled || !config.crosshairEnabled) {
            return;
        }
        if (!StrinovaMod.isEnabled() && !StrinovaHudMockData.isEnabled()) {
            return;
        }
        ci.cancel();
    }
}
