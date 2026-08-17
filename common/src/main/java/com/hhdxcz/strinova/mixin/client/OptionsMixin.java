package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 游戏选项 Mixin，防止贴墙时切换到第一人称视角
@Mixin(Options.class)
public abstract class OptionsMixin {
    // 贴墙玩家禁止切换到第一人称，避免视角穿模
    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void strinova$preventFirstPersonOnWall(CameraType cameraType, CallbackInfo ci) {
        if (cameraType == CameraType.FIRST_PERSON) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && WaPaperState.isWall(mc.player.getUUID())) {
                ci.cancel();
            }
        }
    }
}