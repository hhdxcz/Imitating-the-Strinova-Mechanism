package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaProgrammaticPainter;
import com.hhdxcz.strinova.client.hud.StrinovaScoreboardOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 计分板可视化编辑器：
 * <ul>
 *   <li>拖动面板任意位置移动（保存偏移）；</li>
 *   <li>按钮调整 宽度 / 高度 / 字体缩放 / 行高；</li>
 *   <li>鼠标滚轮在面板上快速调字体，方向键微调位置；</li>
 *   <li>实时预览，保存写入 config/strinova-client.json，返回/ESC 放弃修改。</li>
 * </ul>
 * 打开方式：/strinova_client scoreboard
 */
public class StrinovaScoreboardEditorScreen extends Screen {

    private final Screen parent;
    private boolean saved;
    private boolean dragging;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private float dragStartOffsetX;
    private float dragStartOffsetY;
    private boolean capturedOriginals;
    private float origWidthRatio;
    private float origHeightRatio;
    private float origOffsetX;
    private float origOffsetY;
    private float origTextScale;
    private int origRowHeight;

    public StrinovaScoreboardEditorScreen(@Nullable Screen parent) {
        super(Component.literal("计分板布局编辑器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        captureOriginals();
        // 编辑器里没有真实对局数据时，用模拟计分板数据做预览
        com.hhdxcz.strinova.client.hud.StrinovaHudMockData.ensureScoreboardPreview();

        StrinovaHudConfig config = StrinovaHudConfig.get();
        int buttonHeight = 18;
        int gap = 5;
        int buttonWidth = 64;
        int row1Y = this.height - 50;
        int row2Y = this.height - 28;
        int row1Count = 6;
        int row2Count = 5;
        int row1Start = (this.width - (buttonWidth * row1Count + gap * (row1Count - 1))) / 2;
        int row2Start = (this.width - (buttonWidth * row2Count + gap * (row2Count - 1))) / 2;

        addRenderableWidget(Button.builder(Component.literal("宽度 -"), b -> {
            config.scoreboardWidthRatio = Math.max(0.30F, config.scoreboardWidthRatio - 0.02F);
        }).bounds(row1Start, row1Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("宽度 +"), b -> {
            config.scoreboardWidthRatio = Math.min(1.00F, config.scoreboardWidthRatio + 0.02F);
        }).bounds(row1Start + buttonWidth + gap, row1Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("高度 -"), b -> {
            config.scoreboardHeightRatio = Math.max(0.25F, config.scoreboardHeightRatio - 0.02F);
        }).bounds(row1Start + (buttonWidth + gap) * 2, row1Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("高度 +"), b -> {
            config.scoreboardHeightRatio = Math.min(0.95F, config.scoreboardHeightRatio + 0.02F);
        }).bounds(row1Start + (buttonWidth + gap) * 3, row1Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("字体 -"), b -> {
            config.scoreboardTextScale = Math.max(0.6F, config.scoreboardTextScale - 0.05F);
        }).bounds(row1Start + (buttonWidth + gap) * 4, row1Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("字体 +"), b -> {
            config.scoreboardTextScale = Math.min(1.8F, config.scoreboardTextScale + 0.05F);
        }).bounds(row1Start + (buttonWidth + gap) * 5, row1Y, buttonWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(Component.literal("行高 -"), b -> {
            config.scoreboardRowHeight = Math.max(14, config.scoreboardRowHeight - 1);
        }).bounds(row2Start, row2Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("行高 +"), b -> {
            config.scoreboardRowHeight = Math.min(30, config.scoreboardRowHeight + 1);
        }).bounds(row2Start + buttonWidth + gap, row2Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("重置"), b -> resetToDefaults())
                .bounds(row2Start + (buttonWidth + gap) * 2, row2Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> saveAndClose())
                .bounds(row2Start + (buttonWidth + gap) * 3, row2Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> restoreAndClose())
                .bounds(row2Start + (buttonWidth + gap) * 4, row2Y, buttonWidth, buttonHeight).build());
    }

    private void captureOriginals() {
        if (capturedOriginals) {
            return;
        }
        capturedOriginals = true;
        StrinovaHudConfig config = StrinovaHudConfig.get();
        origWidthRatio = config.scoreboardWidthRatio;
        origHeightRatio = config.scoreboardHeightRatio;
        origOffsetX = config.scoreboardOffsetX;
        origOffsetY = config.scoreboardOffsetY;
        origTextScale = config.scoreboardTextScale;
        origRowHeight = config.scoreboardRowHeight;
    }

    private void resetToDefaults() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        config.scoreboardWidthRatio = 0.66F;
        config.scoreboardHeightRatio = 0.515F;
        config.scoreboardOffsetX = 0.0F;
        config.scoreboardOffsetY = 0.0F;
        config.scoreboardTextScale = 1.0F;
        config.scoreboardRowHeight = 20;
    }

    private void saveAndClose() {
        StrinovaHudConfig.get().save();
        saved = true;
        closeToParent();
    }

    private void restoreAndClose() {
        if (!saved) {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            config.scoreboardWidthRatio = origWidthRatio;
            config.scoreboardHeightRatio = origHeightRatio;
            config.scoreboardOffsetX = origOffsetX;
            config.scoreboardOffsetY = origOffsetY;
            config.scoreboardTextScale = origTextScale;
            config.scoreboardRowHeight = origRowHeight;
        }
        closeToParent();
    }

    private void closeToParent() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景压暗
        graphics.fill(0, 0, this.width, this.height, 0xC8000000);

        // 计分板实时预览（不压暗）
        StrinovaScoreboardOverlay.renderPreview(graphics, new StrinovaProgrammaticPainter(graphics));

        // 顶部标题与参数
        String title = "计分板布局编辑器";
        graphics.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 6, 0xFFEAF2FA, true);
        StrinovaHudConfig config = StrinovaHudConfig.get();
        String info = String.format(java.util.Locale.ROOT,
                "宽度 %.0f%%   高度 %.0f%%   字体 %.2fx   行高 %d   偏移 %d, %d",
                config.scoreboardWidthRatio * 100.0F, config.scoreboardHeightRatio * 100.0F,
                config.scoreboardTextScale, config.scoreboardRowHeight,
                Math.round(config.scoreboardOffsetX), Math.round(config.scoreboardOffsetY));
        graphics.drawString(this.font, info, (this.width - this.font.width(info)) / 2, 18, 0xFF9BAEBF, false);
        String hint = "拖动面板移动 · 滚轮调字体 · 方向键微调位置 · ESC 放弃修改";
        graphics.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2, 30, 0xFF9BAEBF, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int[] bounds = StrinovaScoreboardOverlay.panelBounds(this.width, this.height);
            if (mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2]
                    && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                dragging = true;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                StrinovaHudConfig config = StrinovaHudConfig.get();
                dragStartOffsetX = config.scoreboardOffsetX;
                dragStartOffsetY = config.scoreboardOffsetY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            config.scoreboardOffsetX = clampf((float) (dragStartOffsetX + mouseX - dragStartMouseX), -600.0F, 600.0F);
            config.scoreboardOffsetY = clampf((float) (dragStartOffsetY + mouseY - dragStartMouseY), -400.0F, 400.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int[] bounds = StrinovaScoreboardOverlay.panelBounds(this.width, this.height);
        if (mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            config.scoreboardTextScale = clampf(
                    config.scoreboardTextScale + (delta > 0 ? 0.05F : -0.05F), 0.6F, 1.8F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int step = Math.max(1, (int) (2 * config.scoreboardTextScale));
        switch (keyCode) {
            case 263 -> { // LEFT
                config.scoreboardOffsetX = clampf(config.scoreboardOffsetX - step, -600.0F, 600.0F);
                return true;
            }
            case 262 -> { // RIGHT
                config.scoreboardOffsetX = clampf(config.scoreboardOffsetX + step, -600.0F, 600.0F);
                return true;
            }
            case 265 -> { // UP
                config.scoreboardOffsetY = clampf(config.scoreboardOffsetY - step, -400.0F, 400.0F);
                return true;
            }
            case 264 -> { // DOWN
                config.scoreboardOffsetY = clampf(config.scoreboardOffsetY + step, -400.0F, 400.0F);
                return true;
            }
            default -> {
            }
        }
        if (keyCode == 256) { // ESC
            restoreAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        restoreAndClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float clampf(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
