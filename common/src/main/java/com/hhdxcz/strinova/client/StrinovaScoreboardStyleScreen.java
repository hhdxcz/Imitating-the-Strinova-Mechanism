package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaProgrammaticPainter;
import com.hhdxcz.strinova.client.hud.StrinovaScoreboardOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 计分板「部件样式」编辑器（类似 Word 的属性面板）：
 * 选中某个部件后，可以单独设置它的 可见性 / 字号倍率 / 文字颜色 / 背景颜色，全部实时预览。
 * <p>打开方式：/strinova_client scoreboard style，或在计分板布局编辑器里点「样式…」。</p>
 */
public class StrinovaScoreboardStyleScreen extends Screen {

    /** 色板颜色；0 表示“默认/透明”。 */
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFF9BAEBF, 0xFF5A6673, 0xFF0A0E13,
            0xFFBF3F4C, 0xFFFF7A3D, 0xFFFFD166, 0xFF5FE08C,
            0xFF35D6E8, 0xFF4B77DC, 0xFFA26BFF, 0xFFFF4FA3,
            0xFFFF9AA6, 0xFF9BB6FF, 0xFFDEB639, 0x33000000
    };

    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 3;
    private static final int ROW_HEIGHT = 16;

    private final Screen parent;
    private String selectedKey = "panel";

    /** 点击热区。action: 0=选部件 1=可见 2=字号- 3=字号+ 4=文字色 5=背景色 6=保存 7=返回 8=重置当前部件 9=重置全部 */
    private record Hit(int x, int y, int w, int h, int action, String key, int value) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public StrinovaScoreboardStyleScreen(@Nullable Screen parent) {
        super(Component.literal("计分板样式编辑器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        com.hhdxcz.strinova.client.hud.StrinovaHudMockData.ensureScoreboardPreview();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC8000000);
        StrinovaScoreboardOverlay.renderPreview(graphics, new StrinovaProgrammaticPainter(graphics));
        drawSelectionHighlight(graphics);

        hits.clear();
        drawTopBar(graphics);
        drawPropertyPanel(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ==================== 顶部：部件选择 ====================

    private void drawTopBar(GuiGraphics graphics) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        String title = "计分板样式编辑器 — 选中部件后调整 可见 / 字号 / 文字色 / 背景色";
        graphics.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 4, 0xFFEAF2FA, true);

        String[] keys = StrinovaHudConfig.SCOREBOARD_COMPONENT_KEYS;
        int columns = 8;
        int buttonW = 42;
        int buttonH = 16;
        int startX = (this.width - (buttonW * columns + SWATCH_GAP * (columns - 1))) / 2;
        int startY = 16;
        for (int i = 0; i < keys.length; i++) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * (buttonW + SWATCH_GAP);
            int y = startY + row * (buttonH + 2);
            boolean selected = keys[i].equals(selectedKey);
            StrinovaHudConfig.ScoreboardComponentStyle style = config.scoreboardComponent(keys[i]);
            int bg = selected ? 0xFF35D6E8 : 0xFF1A212A;
            int fg = selected ? 0xFF06131A : (style.visible ? 0xFFEAF2FA : 0xFF7A8794);
            graphics.fill(x, y, x + buttonW, y + buttonH, bg);
            graphics.fill(x, y, x + buttonW, y + 1, 0x33FFFFFF);
            String label = StrinovaHudConfig.scoreboardComponentLabel(keys[i]);
            graphics.drawString(this.font, trim(label, buttonW - 4), x + 2, y + 4, fg, false);
            hits.add(new Hit(x, y, buttonW, buttonH, 0, keys[i], 0));
        }
    }

    private static String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 4) {
            return text;
        }
        return text.substring(0, 4);
    }

    // ==================== 底部：属性面板 ====================

    private void drawPropertyPanel(GuiGraphics graphics) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        StrinovaHudConfig.ScoreboardComponentStyle style = config.scoreboardComponent(selectedKey);
        boolean columnKey = isColumnKey(selectedKey);
        boolean roundKey = isRoundKey(selectedKey);
        int areaTop = this.height - (92 + (columnKey ? 22 : 0) + (roundKey ? 22 : 0));
        graphics.fill(0, areaTop, this.width, this.height, 0xE60B0F14);
        graphics.fill(0, areaTop, this.width, areaTop + 1, 0x44FFFFFF);

        String label = "当前部件：" + StrinovaHudConfig.scoreboardComponentLabel(selectedKey)
                + "（" + selectedKey + "）";
        graphics.drawString(this.font, label, 8, areaTop + 5, 0xFFEAF2FA, false);

        // 第一行：可见 / 字号 / 居中 / 重置 / 保存 / 返回
        int y = areaTop + 20;
        int x = 8;
        x = button(graphics, x, y, 48, style.visible ? "可见: 开" : "可见: 关", 1, 0);
        x = button(graphics, x, y, 34, "字号-", 2, 0);
        String fontSize = String.format(java.util.Locale.ROOT, "%.2fx", style.fontScale);
        graphics.drawString(this.font, fontSize, x + 2, y + 4, 0xFFEAF2FA, false);
        x += 34;
        x = button(graphics, x, y, 34, "字号+", 3, 0);
        x = button(graphics, x, y, 34, style.centerH ? "居中: 开" : "居中: 关", 14, 0);
        x = button(graphics, x, y, 40, "重置本项", 8, 0);
        x = button(graphics, x, y, 40, "全部重置", 9, 0);
        // 保存/返回靠右
        buttonAt(graphics, this.width - 116, y, 52, "保存", 6, 0);
        buttonAt(graphics, this.width - 56, y, 48, "返回", 7, 0);

        int rowY = areaTop + 42;
        if (columnKey) {
            drawColumnWidthRow(graphics, rowY, config);
            rowY += 22;
        }
        if (roundKey) {
            drawRoundHeightRow(graphics, rowY, config);
            rowY += 22;
        }
        // 文字颜色
        drawColorRow(graphics, rowY, "文字色", 4, style.textColor);
        // 背景颜色
        rowY += 22;
        drawColorRow(graphics, rowY, "背景色", 5, style.backgroundColor);
    }

    private boolean isRoundKey(String key) {
        return "rounds".equals(key) || "roundAlly".equals(key) || "roundEnemy".equals(key);
    }

    private void drawRoundHeightRow(GuiGraphics graphics, int y, StrinovaHudConfig config) {
        graphics.drawString(this.font, "回合计行高", 8, y + 2, 0xFF9BAEBF, false);
        int x = 8 + 64;
        x = button(graphics, x, y - 2, 52, "行高-", 12, 0);
        int height = StrinovaScoreboardOverlay.effectiveRoundRowHeight();
        graphics.drawString(this.font, height + "px", x + 4, y + 2, 0xFFEAF2FA, false);
        x += 36;
        button(graphics, x, y - 2, 52, "行高+", 13, 0);
        graphics.drawString(this.font, "（也可在布局编辑器拖中间三条边界线）", x + 56, y + 2, 0xFF7A8794, false);
    }

    private boolean isColumnKey(String key) {
        for (String columnKey : StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS) {
            if (columnKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void drawColumnWidthRow(GuiGraphics graphics, int y, StrinovaHudConfig config) {
        graphics.drawString(this.font, "列宽", 8, y + 2, 0xFF9BAEBF, false);
        int x = 8 + 40;
        x = button(graphics, x, y - 2, 44, "列宽-", 10, 0);
        float weight = config.scoreboardColumnWeight(selectedKey);
        // 显示占总内容宽度的百分比
        float total = 0.0F;
        for (String key : StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS) {
            total += config.scoreboardColumnWeight(key);
        }
        float pct = total <= 0.0F ? 0.0F : weight / total * 100.0F;
        String text = String.format(java.util.Locale.ROOT, "%.1f%%", pct);
        graphics.drawString(this.font, text, x + 4, y + 2, 0xFFEAF2FA, false);
        x += 52;
        button(graphics, x, y - 2, 44, "列宽+", 11, 0);
        graphics.drawString(this.font, "（也可在布局编辑器里拖列头边界）", x + 52, y + 2, 0xFF7A8794, false);
    }

    /** 在预览上高亮当前选中的部件；数据行/列头/列会同时高亮两个阵营。 */
    private void drawSelectionHighlight(GuiGraphics graphics) {
        StrinovaScoreboardOverlay.Layout layout =
                StrinovaScoreboardOverlay.computeLayoutPublic(this.width, this.height);
        int[][] rects = selectionRects(layout);
        for (int[] rect : rects) {
            if (rect == null || rect[2] <= 0 || rect[3] <= 0) {
                continue;
            }
            int x = rect[0];
            int y = rect[1];
            int w = rect[2];
            int h = rect[3];
            graphics.fill(x, y, x + w, y + h, 0x2235D6E8);
            graphics.fill(x, y, x + w, y + 2, 0xFF35D6E8);
            graphics.fill(x, y + h - 2, x + w, y + h, 0xFF35D6E8);
            graphics.fill(x, y, x + 2, y + h, 0xFF35D6E8);
            graphics.fill(x + w - 2, y, x + w, y + h, 0xFF35D6E8);
        }
    }

    private int[][] selectionRects(StrinovaScoreboardOverlay.Layout layout) {
        int left = layout.left();
        int blockTop = layout.blockTop();
        int enemyTop = layout.enemyTop();
        int contentX = left + layout.sidebarWidth();
        int contentW = layout.width() - layout.sidebarWidth() - 1;
        int rowHeight = layout.rowHeight();
        int headerHeight = layout.headerHeight();
        int rowsTop = blockTop + headerHeight;
        int rowsHeight = 5 * rowHeight;
        int enemyRowsTop = enemyTop + headerHeight;
        int middleTop = layout.middleTop();
        int middleHeight = layout.middleHeight();
        if (selectedKey == null) {
            return new int[0][];
        }
        return switch (selectedKey) {
            case "panel" -> new int[][]{{left, blockTop, layout.width(), layout.height()}};
            case "sidebar" -> new int[][]{
                    {left, blockTop, layout.sidebarWidth(), layout.blockHeight()},
                    {left, enemyTop, layout.sidebarWidth(), layout.blockHeight()}};
            case "header" -> new int[][]{
                    {contentX, blockTop, contentW, headerHeight},
                    {contentX, enemyTop, contentW, headerHeight}};
            case "rows" -> new int[][]{
                    {contentX, rowsTop, contentW, rowsHeight},
                    {contentX, enemyRowsTop, contentW, rowsHeight}};
            case "rounds" -> new int[][]{{left, middleTop, layout.width(), middleHeight}};
            case "roundAlly" -> new int[][]{{left, middleTop + 1, layout.width(),
                    Math.max(2, middleHeight / 2 - 1)}};
            case "roundEnemy" -> new int[][]{{left, middleTop + middleHeight / 2, layout.width(),
                    Math.max(2, middleHeight / 2)}};
            default -> {
                int[] allyRect = columnRect(contentX, contentW, rowsTop, rowsHeight);
                int[] enemyRect = columnRect(contentX, contentW, enemyRowsTop, rowsHeight);
                if (allyRect == null) {
                    yield new int[0][];
                }
                yield new int[][]{allyRect, enemyRect};
            }
        };
    }

    private int[] columnRect(int contentX, int contentW, int rowsTop, int rowsHeight) {
        String[] keys = StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS;
        int[] starts = StrinovaScoreboardOverlay.columnStarts(contentW);
        for (int i = 0; i < keys.length; i++) {
            if (!keys[i].equals(selectedKey)) {
                continue;
            }
            int start = starts[i];
            int end = (i + 1 < starts.length) ? starts[i + 1] : contentW;
            return new int[]{contentX + start, rowsTop, Math.max(4, end - start), rowsHeight};
        }
        return null;
    }

    private void drawColorRow(GuiGraphics graphics, int y, String title, int action, int current) {
        graphics.drawString(this.font, title, 8, y + 2, 0xFF9BAEBF, false);
        int x = 8 + 40;
        // 默认色块
        int dx = x;
        graphics.fill(dx, y, dx + SWATCH, y + SWATCH, 0xFF1A212A);
        graphics.fill(dx, y, dx + SWATCH, y + 1, 0x66FFFFFF);
        graphics.drawString(this.font, "默", dx + 3, y + 3, 0xFFEAF2FA, false);
        if (current == 0) {
            graphics.fill(dx - 1, y - 1, dx + SWATCH + 1, y + 1, 0xFFFFD166);
        }
        hits.add(new Hit(dx, y, SWATCH, SWATCH, action, selectedKey, 0));
        x += SWATCH + SWATCH_GAP + 4;
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            graphics.fill(x, y, x + SWATCH, y + SWATCH, color);
            if (color == current) {
                graphics.fill(x - 1, y - 1, x + SWATCH + 1, y, 0xFF35D6E8);
                graphics.fill(x - 1, y + SWATCH, x + SWATCH + 1, y + SWATCH + 1, 0xFF35D6E8);
                graphics.fill(x - 1, y - 1, x, y + SWATCH + 1, 0xFF35D6E8);
                graphics.fill(x + SWATCH, y - 1, x + SWATCH + 1, y + SWATCH + 1, 0xFF35D6E8);
            }
            hits.add(new Hit(x, y, SWATCH, SWATCH, action, selectedKey, color));
            x += SWATCH + SWATCH_GAP;
        }
    }

    private int button(GuiGraphics graphics, int x, int y, int w, String text, int action, int value) {
        buttonAt(graphics, x, y, w, text, action, value);
        return x + w + 4;
    }

    private void buttonAt(GuiGraphics graphics, int x, int y, int w, String text, int action, int value) {
        graphics.fill(x, y, x + w, y + ROW_HEIGHT, 0xFF1A212A);
        graphics.fill(x, y, x + w, y + 1, 0x44FFFFFF);
        graphics.drawString(this.font, text, x + (w - this.font.width(text)) / 2, y + 4, 0xFFEAF2FA, false);
        hits.add(new Hit(x, y, w, ROW_HEIGHT, action, selectedKey, value));
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                apply(hit);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void apply(Hit hit) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        switch (hit.action()) {
            case 0 -> selectedKey = hit.key();
            case 1 -> config.scoreboardComponent(selectedKey).visible = !config.scoreboardComponent(selectedKey).visible;
            case 2 -> {
                StrinovaHudConfig.ScoreboardComponentStyle style = config.scoreboardComponent(selectedKey);
                style.fontScale = Math.max(0.5F, style.fontScale - 0.05F);
            }
            case 3 -> {
                StrinovaHudConfig.ScoreboardComponentStyle style = config.scoreboardComponent(selectedKey);
                style.fontScale = Math.min(2.0F, style.fontScale + 0.05F);
            }
            case 4 -> config.scoreboardComponent(selectedKey).textColor = hit.value();
            case 5 -> config.scoreboardComponent(selectedKey).backgroundColor = hit.value();
            case 6 -> {
                config.save();
                Minecraft.getInstance().setScreen(parent);
            }
            case 7 -> Minecraft.getInstance().setScreen(parent);
            case 8 -> {
                StrinovaHudConfig.ScoreboardComponentStyle style = config.scoreboardComponent(selectedKey);
                style.visible = true;
                style.fontScale = 1.0F;
                style.textColor = 0;
                style.backgroundColor = 0;
                if (isColumnKey(selectedKey)) {
                    config.setScoreboardColumnWeight(selectedKey, 0.10F);
                }
            }
            case 10 -> {
                float weight = config.scoreboardColumnWeight(selectedKey);
                config.setScoreboardColumnWeight(selectedKey, weight - 0.01F);
            }
            case 11 -> {
                float weight = config.scoreboardColumnWeight(selectedKey);
                config.setScoreboardColumnWeight(selectedKey, weight + 0.01F);
            }
            case 12 -> {
                int height = StrinovaScoreboardOverlay.effectiveRoundRowHeight();
                config.scoreboardRoundRowHeight = Math.max(6, height - 1);
            }
            case 13 -> {
                int height = StrinovaScoreboardOverlay.effectiveRoundRowHeight();
                config.scoreboardRoundRowHeight = Math.min(24, height + 1);
            }
            case 14 -> config.scoreboardComponent(selectedKey).centerH =
                    !config.scoreboardComponent(selectedKey).centerH;
            case 9 -> {
                for (String key : StrinovaHudConfig.SCOREBOARD_COMPONENT_KEYS) {
                    StrinovaHudConfig.ScoreboardComponentStyle style = config.scoreboardComponent(key);
                    style.visible = true;
                    style.fontScale = 1.0F;
                    style.textColor = 0;
                    style.backgroundColor = 0;
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
