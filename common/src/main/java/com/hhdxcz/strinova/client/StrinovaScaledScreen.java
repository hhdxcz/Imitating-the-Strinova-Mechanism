package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 设计分辨率界面基类：整个界面统一按 960×540（等价于 1080p、GUI 缩放 2 的界面尺寸）排版，
 * 渲染时整体缩放到玩家实际的 GUI 空间，鼠标事件再换算回设计坐标。
 *
 * <p>效果：不管玩家 GUI 缩放是 1 / 2 / 3，界面的相对布局、占屏比例、物理尺寸都一致
 * （文字是位图字体，被 pose 缩放放大/缩小时清晰度会有差异）。</p>
 */
public abstract class StrinovaScaledScreen extends Screen {

    /** 设计分辨率高度（960×540，与 HUD 共用）。 */
    public static final int DESIGN_HEIGHT = StrinovaUiScale.DESIGN_HEIGHT;

    private float uiScale = 1.0F;
    private int designWidth;

    protected StrinovaScaledScreen(Component title) {
        super(title);
    }

    /** 子类 init() 第一行调用：把真实 GUI 尺寸换算成设计坐标。 */
    protected final void beginDesignSpace() {
        // 已经是设计坐标（重复 init / reposition）时保持原样⁠​‌​​​‍​‌​​‍​​​‍‍‌‍​​‌‍‌‌​‌‌‌‌‌​‍​‌‌‍​‍‍‍‍​​​‌‍‌‍​​‌‌‌​‌​​​‍‌​‍‍​‍​​​‌‌‌‌‌​​​‌‍​​​​​‍​​‌‍‍​​‌‌‌‌‍‌​​​‌​​‍‌‌​‍​​​‍​​​‌‍​‍‍​‌‌​‍​​‌‍‌‍​⁡
        if (this.uiScale != 1.0F && this.height == DESIGN_HEIGHT && this.width == this.designWidth) {
            return;
        }
        int realWidth = Math.max(1, this.width);
        int realHeight = Math.max(1, this.height);
        this.uiScale = Math.max(0.25F, realHeight / (float) DESIGN_HEIGHT);
        this.width = Math.max(1, Math.round(realWidth / this.uiScale));
        this.height = DESIGN_HEIGHT;
        this.designWidth = this.width;
    }

    protected final float uiScale() {
        return this.uiScale;
    }

    /** 渲染前压入设计坐标缩放（结束记得 popDesignSpace）。 */
    protected final void pushDesignSpace(GuiGraphics graphics) {
        StrinovaUiScale.push(graphics, this.uiScale);
    }

    protected final void popDesignSpace(GuiGraphics graphics) {
        StrinovaUiScale.pop(graphics);
    }

    /** 真实鼠标坐标 → 设计坐标。 */
    protected final double uiMouseX(double mouseX) {
        return mouseX / this.uiScale;
    }

    protected final double uiMouseY(double mouseY) {
        return mouseY / this.uiScale;
    }

    protected final int uiMouseX(int mouseX) {
        return Math.round(mouseX / this.uiScale);
    }

    protected final int uiMouseY(int mouseY) {
        return Math.round(mouseY / this.uiScale);
    }

    // ---- 鼠标事件：控件按设计坐标布局，先把真实坐标换算成设计坐标再分发 ----
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(uiMouseX(mouseX), uiMouseY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(uiMouseX(mouseX), uiMouseY(mouseY), button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(uiMouseX(mouseX), uiMouseY(mouseY));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(uiMouseX(mouseX), uiMouseY(mouseY), button,
                dragX / this.uiScale, dragY / this.uiScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(uiMouseX(mouseX), uiMouseY(mouseY), delta);
    }
}
