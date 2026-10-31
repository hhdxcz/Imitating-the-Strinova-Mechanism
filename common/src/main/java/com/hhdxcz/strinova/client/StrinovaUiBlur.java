package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 打开本模组 UI 时给游戏画面加一层轻量高斯模糊（使用自定义 post shader，半径 8）。
 * 只在【本模组的界面】打开时启用，关闭界面后恢复；可在客户端配置关闭 uiBlurEnabled。
 */
public final class StrinovaUiBlur {

    private static final ResourceLocation BLUR_EFFECT =
            new ResourceLocation(StrinovaMod.MOD_ID, "ui_blur");

    private static boolean active;

    private StrinovaUiBlur() {
    }

    /** 界面打开时调用（仅当 uiBlurEnabled 且处于游戏世界内）。 */
    public static void begin() {
        if (active || !StrinovaHudConfig.get().uiBlurEnabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null) {
            return;
        }
        try {
            ((com.hhdxcz.strinova.mixin.client.GameRendererAccessor) minecraft.gameRenderer)
                    .strinova$loadEffect(BLUR_EFFECT);
            active = true;
        } catch (Throwable throwable) {
            StrinovaMod.LOGGER.warn("Strinova: UI 模糊启用失败: {}", throwable.toString());
        }
    }

    /** 界面关闭时调用，恢复清晰画面。 */
    public static void end() {
        if (!active) {
            return;
        }
        active = false;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameRenderer != null) {
                minecraft.gameRenderer.shutdownEffect();
            }
        } catch (Throwable throwable) {
            StrinovaMod.LOGGER.warn("Strinova: UI 模糊关闭失败: {}", throwable.toString());
        }
    }
}
