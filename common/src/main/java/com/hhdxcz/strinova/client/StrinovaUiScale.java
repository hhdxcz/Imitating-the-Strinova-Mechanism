package com.hhdxcz.strinova.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 全局 UI 设计分辨率换算（HUD / 自定义界面共用）：
 * 设计高度固定 540 逻辑像素（≈1080p 下 GUI 缩放 2），再整体缩放到玩家的实际 GUI 空间，
 * 这样 GUI 缩放 1/2/3 下 HUD 与界面的相对布局、物理尺寸都一致。
 ⁠​‌​​​‍​‌​​‍​​​‍‍‌‍​​‌‍‌‌​‌‌‌‌‌​‍​‌‌‍​‍‍‍‍​​​‌‍‌‍​​‌‌‌​‌​​​‍‌​‍‍​‍​​​‌‌‌‌‌​​​‌‍​​​​​‍​‍​‍‍‍​‍‍​‌​‌​​​‍‍​​‌​‌‌​‍‍‍​​​‌‌‍‌‌​‍​‌​​​​​‌‍‌⁡*/
public final class StrinovaUiScale {

    /** 设计分辨率高度（960×540）。 */
    public static final int DESIGN_HEIGHT = 540;

    /** 当前是否已处于设计空间（界面内部再画 HUD 预览时避免重复缩放）。 */
    private static int designDepth;

    private StrinovaUiScale() {
    }

    /** 真实 GUI 空间 → 设计空间的缩放系数。 */
    public static float scale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1.0F;
        }
        return Math.max(0.25F, minecraft.getWindow().getGuiScaledHeight() / (float) DESIGN_HEIGHT);
    }

    /** 设计空间宽度（按当前窗口宽高比换算）。 */
    public static int width() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 960;
        }
        return Math.max(1, Math.round(minecraft.getWindow().getGuiScaledWidth() / scale()));
    }

    /** 设计空间高度。 */
    public static int height() {
        return DESIGN_HEIGHT;
    }

    public static boolean isDesignSpaceActive() {
        return designDepth > 0;
    }

    public static void push(GuiGraphics graphics, float scale) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        designDepth++;
    }

    public static void pop(GuiGraphics graphics) {
        designDepth = Math.max(0, designDepth - 1);
        graphics.pose().popPose();
    }
}
