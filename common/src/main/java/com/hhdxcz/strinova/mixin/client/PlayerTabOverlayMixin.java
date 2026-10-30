package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.hud.StrinovaScoreboardOverlay;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TAB 计分板接管：当自定义计分板激活（按住 TAB 且有进行中对局）时，隐藏原版玩家列表，
 * 避免两套列表同时显示。
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void strinova$hideVanillaPlayerList(CallbackInfo ci) {
        if (StrinovaScoreboardOverlay.shouldHideVanillaPlayerList()) {
            ci.cancel();
        }
    }
}
