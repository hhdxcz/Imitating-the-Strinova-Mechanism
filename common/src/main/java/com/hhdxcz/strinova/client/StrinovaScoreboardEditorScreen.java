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

    // 类似 Excel 的列宽/行高拖拽状态
    private int resizingColumn = -1;
    private boolean resizingSidebar;
    private float resizeStartSidebarRatio;
    private int resizePanelWidth;
    private double resizeStartX;
    private float resizeStartLeftWeight;
    private float resizeStartRightWeight;
    private int resizeContentW;
    private boolean resizingRow;
    private double resizeStartY;
    private int resizeStartRowHeight;
    private boolean resizingRoundRow;
    private int resizeStartRoundRowHeight;

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
        int row2Count = 6;
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
        addRenderableWidget(Button.builder(Component.literal("样式…"), b -> Minecraft.getInstance().setScreen(
                        new StrinovaScoreboardStyleScreen(this)))
                .bounds(row2Start + (buttonWidth + gap) * 3, row2Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> saveAndClose())
                .bounds(row2Start + (buttonWidth + gap) * 4, row2Y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> restoreAndClose())
                .bounds(row2Start + (buttonWidth + gap) * 5, row2Y, buttonWidth, buttonHeight).build());
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
        config.scoreboardOffsetX = -0.5F;
        config.scoreboardOffsetY = -0.5F;
        config.scoreboardTextScale = 1.0F;
        config.scoreboardRowHeight = 18;
        config.scoreboardRoundRowHeight = 24;
        config.scoreboardNameLeftPadding = 0;
        config.scoreboardSidebarWidthRatio = 0.0903F;
        config.resetScoreboardColumnDefaults();
        config.resetScoreboardComponentDefaults();
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
        // 列/行边界参考线（靠近时高亮，方便拖拽）
        drawAlignmentGuides(graphics, mouseX, mouseY);

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
        String hint = "拖动面板移动 · 列/行边界靠近会高亮，按住拖动即可改列宽/行高 · 滚轮调字体 · ESC 放弃";
        graphics.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2, 30, 0xFF9BAEBF, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 绘制列/行边界参考线：平时淡显，鼠标靠近时变青色高亮，表头处加拖动把手。 */
    private void drawAlignmentGuides(GuiGraphics graphics, int mouseX, int mouseY) {
        StrinovaScoreboardOverlay.Layout layout =
                StrinovaScoreboardOverlay.computeLayoutPublic(this.width, this.height);
        int contentX = layout.left() + layout.sidebarWidth();
        int contentW = layout.width() - layout.sidebarWidth() - 1;
        int[] starts = StrinovaScoreboardOverlay.columnStarts(contentW);
        int[] blockTops = {layout.blockTop(), layout.enemyTop()};

        // 列边界：头像左侧留白 + name|kda …… assist|ping（共 9 条，不含面板右边缘）
        for (int i = 0; i <= 8; i++) {
            int boundary = contentX + starts[i];
            boolean hover = Math.abs(mouseX - boundary) <= 6;
            boolean active = i == 0 ? resizingSidebar : resizingColumn == i - 1;
            int color = (hover || active) ? 0xFF35D6E8 : 0x33FFFFFF;
            for (int blockTop : blockTops) {
                int top = blockTop;
                int bottom = blockTop + layout.blockHeight() - 1;
                graphics.fill(boundary, top, boundary + 1, bottom, color);
                // 表头拖动把手
                int handleColor = (hover || active) ? 0x8835D6E8 : 0x22FFFFFF;
                graphics.fill(boundary - 2, top + 1, boundary + 3, top + layout.headerHeight() - 1, handleColor);
            }
        }
        // 行边界
        for (int blockTop : blockTops) {
            int rowsTop = blockTop + layout.headerHeight();
            for (int k = 1; k < 5; k++) {
                int rowY = rowsTop + k * layout.rowHeight();
                boolean hover = Math.abs(mouseY - rowY) <= 5;
                int color = hover ? 0xFF35D6E8 : 0x22FFFFFF;
                graphics.fill(contentX, rowY, contentX + contentW, rowY + 1, color);
            }
        }
        // 中间回合记录带边界（上边/中线/下边，拖动能改我方/敌方行高）
        int midTop = layout.middleTop();
        int midH = layout.middleHeight();
        int[] midLines = {midTop + 1, midTop + midH / 2, midTop + midH - 1};
        for (int lineY : midLines) {
            boolean hover = Math.abs(mouseY - lineY) <= 5;
            graphics.fill(layout.left(), lineY, layout.left() + layout.width(), lineY + 1,
                    hover ? 0xFF35D6E8 : 0x33FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            StrinovaScoreboardOverlay.Layout layout =
                    StrinovaScoreboardOverlay.computeLayoutPublic(this.width, this.height);
            int contentX = layout.left() + layout.sidebarWidth();
            int contentW = layout.width() - layout.sidebarWidth() - 1;
            int[] starts = StrinovaScoreboardOverlay.columnStarts(contentW);
            boolean inHeader = (mouseY >= layout.blockTop()
                    && mouseY <= layout.blockTop() + layout.headerHeight())
                    || (mouseY >= layout.enemyTop() && mouseY <= layout.enemyTop() + layout.headerHeight());
            if (inHeader) {
                // 头像左侧边界：左右拖动调整左侧竖栏宽度（竖栏变窄 → 头像列变宽）
                if (Math.abs(mouseX - (contentX + starts[0])) <= 6.0D) {
                    resizingSidebar = true;
                    resizeStartX = mouseX;
                    resizeStartSidebarRatio = config.scoreboardSidebarWidthRatio;
                    resizePanelWidth = Math.max(1, layout.width());
                    return true;
                }
                // 列边界：name|kda …… assist|ping（共 8 条）
                String[] keys = StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS;
                for (int i = 1; i <= 8; i++) {
                    int boundary = contentX + starts[i];
                    if (Math.abs(mouseX - boundary) <= 6.0D) {
                        resizingColumn = i - 1;
                        resizeStartX = mouseX;
                        resizeContentW = Math.max(1, contentW);
                        resizeStartLeftWeight = config.scoreboardColumnWeight(keys[i - 1]);
                        resizeStartRightWeight = config.scoreboardColumnWeight(keys[i]);
                        return true;
                    }
                }
            }
            // 行边界：在我方/敌方区块的数据行之间拖动，调整全局行高
            int[] blockRowTops = {layout.blockTop() + layout.headerHeight(),
                    layout.enemyTop() + layout.headerHeight()};
            for (int blockRowTop : blockRowTops) {
                for (int k = 1; k < 5; k++) {
                    int boundaryY = blockRowTop + k * layout.rowHeight();
                    if (Math.abs(mouseY - boundaryY) <= 5.0D
                            && mouseX >= contentX && mouseX <= contentX + contentW) {
                        resizingRow = true;
                        resizeStartY = mouseY;
                        resizeStartRowHeight = layout.rowHeight();
                        return true;
                    }
                }
            }
            // 中间回合记录带：上边/中线/下边都能拖动，调整我方/敌方单行高度
            int middleTop = layout.middleTop();
            int middleHeight = layout.middleHeight();
            if (mouseX >= layout.left() && mouseX <= layout.left() + layout.width()
                    && (Math.abs(mouseY - (middleTop + 1)) <= 5
                    || Math.abs(mouseY - (middleTop + middleHeight / 2)) <= 5
                    || Math.abs(mouseY - (middleTop + middleHeight - 1)) <= 5)) {
                resizingRoundRow = true;
                resizeStartY = mouseY;
                resizeStartRoundRowHeight = StrinovaScoreboardOverlay.effectiveRoundRowHeight();
                return true;
            }
            int[] bounds = StrinovaScoreboardOverlay.panelBounds(this.width, this.height);
            if (mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2]
                    && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                dragging = true;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartOffsetX = config.scoreboardOffsetX;
                dragStartOffsetY = config.scoreboardOffsetY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        if (resizingSidebar && button == 0) {
            float deltaRatio = (float) ((mouseX - resizeStartX) / Math.max(1, resizePanelWidth));
            config.scoreboardSidebarWidthRatio = clampf(
                    resizeStartSidebarRatio + deltaRatio, 0.05F, 0.25F);
            return true;
        }
        if (resizingColumn >= 0 && button == 0) {
            String[] keys = StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS;
            float frac = (float) ((mouseX - resizeStartX) / resizeContentW);
            float left = resizeStartLeftWeight + frac;
            float right = resizeStartRightWeight - frac;
            if (left >= 0.02F && right >= 0.02F) {
                config.setScoreboardColumnWeight(keys[resizingColumn], left);
                config.setScoreboardColumnWeight(keys[resizingColumn + 1], right);
            }
            return true;
        }
        if (resizingRow && button == 0) {
            int newHeight = (int) Math.round(resizeStartRowHeight + (mouseY - resizeStartY));
            config.scoreboardRowHeight = Math.max(14, Math.min(30, newHeight));
            return true;
        }
        if (resizingRoundRow && button == 0) {
            int newHeight = (int) Math.round(resizeStartRoundRowHeight + (mouseY - resizeStartY));
            config.scoreboardRoundRowHeight = Math.max(6, Math.min(24, newHeight));
            return true;
        }
        if (dragging && button == 0) {
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
            resizingColumn = -1;
            resizingSidebar = false;
            resizingRow = false;
            resizingRoundRow = false;
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
