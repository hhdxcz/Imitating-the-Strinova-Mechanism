package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 公告中心界面：左侧分类页签（全部 / 更新公告 / 游玩说明 / 版本说明 / 活动公告…），
 * 右侧可滚动查看公告正文。进入世界自动弹出，关闭时把当前公告记为已读
 * （之后只有新增 id 的公告才会再次弹出）。
 *
 * <p>界面同样走 960×540 设计分辨率（{@link StrinovaScaledScreen}），
 * 任何 GUI 缩放下布局与点击位置一致。</p>
 */
public class StrinovaAnnouncementScreen extends StrinovaScaledScreen {

    private static final int TAB_HEIGHT = 24;
    private static final int TAB_GAP = 4;
    private static final int BODY_LINE_HEIGHT = 10;
    private static final int SCROLL_STEP = 26;

    /** 父界面（关闭后返回；null = 回到游戏）。 */
    private final Screen parent;
    /** 自动弹出时关闭即记为已读；手动查看（设置/命令）关闭不消耗首次弹出。 */
    private final boolean acknowledgeOnClose;
    /** 打开界面时的公告快照。 */
    private final List<StrinovaAnnouncements.Entry> entries;

    private final List<Button> tabButtons = new ArrayList<>();
    private final List<int[]> tabRects = new ArrayList<>();
    private final List<String> tabs = new ArrayList<>();

    /** null = 「全部」页签。 */
    private String selectedCategory;
    private int scroll;
    private int maxScroll;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int footerY;

    public StrinovaAnnouncementScreen(Screen parent, boolean acknowledgeOnClose) {
        super(Component.translatable("config.strinova.announcement.title"));
        this.parent = parent;
        this.acknowledgeOnClose = acknowledgeOnClose;
        this.entries = StrinovaAnnouncements.currentEntries();
    }

    @Override
    protected void init() {
        // 统一设计分辨率（960×540），鼠标事件会换算回设计坐标
        this.beginDesignSpace();
        this.clearWidgets();
        this.tabButtons.clear();
        this.tabRects.clear();
        this.tabs.clear();

        this.panelLeft = 36;
        this.panelTop = 26;
        this.panelRight = this.width - 36;
        this.panelBottom = this.height - 30;
        int navWidth = Math.min(148, Math.max(110, (this.panelRight - this.panelLeft) / 6));
        int navLeft = this.panelLeft + 14;
        this.contentTop = this.panelTop + 62;
        this.contentBottom = this.panelBottom - 46;
        this.contentLeft = navLeft + navWidth + 16;
        this.contentRight = this.panelRight - 14;
        this.footerY = this.panelBottom - 30;

        // 页签：null = 全部，其余按固定顺序 + 自定义分类
        List<String> categories = collectCategories();
        if (this.selectedCategory != null && !categories.contains(this.selectedCategory)) {
            this.selectedCategory = null;
        }
        this.tabs.add(null);
        this.tabs.addAll(categories);

        int y = this.contentTop + 2;
        for (String category : this.tabs) {
            String label = tabLabel(category) + " (" + countFor(category) + ")";
            Button button = Button.builder(Component.literal(label), b -> {
                this.selectedCategory = category;
                this.scroll = 0;
            }).bounds(navLeft, y, navWidth, TAB_HEIGHT).build();
            this.addRenderableWidget(button);
            this.tabButtons.add(button);
            this.tabRects.add(new int[]{navLeft, y, navWidth, TAB_HEIGHT});
            y += TAB_HEIGHT + TAB_GAP;
        }

        // 底部按钮：设置 + 我已阅读
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.strinova.announcement.settings_entry"),
                        b -> Minecraft.getInstance().setScreen(new StrinovaCombatSettingsScreen(this)))
                .bounds(this.panelLeft + 14, this.footerY, 80, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.strinova.announcement.acknowledge"),
                        b -> this.acknowledgeAndClose())
                .bounds(this.contentRight - 116, this.footerY, 116, 20)
                .build());
    }

    /** 收集所有出现过的分类（固定顺序优先，其余按公告顺序）。 */
    private List<String> collectCategories() {
        Set<String> present = new LinkedHashSet<>();
        for (StrinovaAnnouncements.Entry entry : this.entries) {
            if (entry.category != null && !entry.category.isBlank()) {
                present.add(entry.category);
            }
        }
        List<String> result = new ArrayList<>();
        for (String known : new String[]{"update", "guide", "version", "event", "notice"}) {
            if (present.remove(known)) {
                result.add(known);
            }
        }
        result.addAll(present);
        return result;
    }

    private int countFor(String category) {
        int count = 0;
        for (StrinovaAnnouncements.Entry entry : this.entries) {
            if (category == null || category.equals(entry.category)) {
                count++;
            }
        }
        return count;
    }

    private List<StrinovaAnnouncements.Entry> visibleEntries() {
        List<StrinovaAnnouncements.Entry> result = new ArrayList<>();
        for (StrinovaAnnouncements.Entry entry : this.entries) {
            if (this.selectedCategory == null || this.selectedCategory.equals(entry.category)) {
                result.add(entry);
            }
        }
        return result;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.pushDesignSpace(graphics);
        mouseX = this.uiMouseX(mouseX);
        mouseY = this.uiMouseY(mouseY);
        this.renderBackground(graphics);

        // 主面板
        graphics.fill(this.panelLeft, this.panelTop, this.panelRight, this.panelBottom, 0xD0101014);
        graphics.fill(this.panelLeft, this.panelTop, this.panelRight, this.panelTop + 1, 0x40FFFFFF);
        graphics.fill(this.panelLeft, this.panelBottom - 1, this.panelRight, this.panelBottom, 0x40FFFFFF);
        // 标题 + 提示
        graphics.drawCenteredString(this.font, this.title,
                (this.panelLeft + this.panelRight) / 2, this.panelTop + 16, 0xFFFFFF);
        String hint = Component.translatable("config.strinova.announcement.hint").getString();
        graphics.drawString(this.font, hint,
                this.panelRight - 14 - this.font.width(hint), this.panelTop + 18, 0x8A8A8A, false);
        graphics.fill(this.contentLeft - 8, this.contentTop - 6,
                this.panelRight - 14, this.contentTop - 5, 0x30FFFFFF);

        this.drawActiveTab(graphics);
        this.drawContent(graphics);
        this.drawFooter(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
        this.popDesignSpace(graphics);
    }

    /** 当前页签高亮（按钮是原版样式，这里在按钮下面垫一层高亮）。 */
    private void drawActiveTab(GuiGraphics graphics) {
        int selected = this.tabs.indexOf(this.selectedCategory);
        if (selected < 0 || selected >= this.tabRects.size()) {
            return;
        }
        int[] rect = this.tabRects.get(selected);
        graphics.fill(rect[0] - 2, rect[1] - 2, rect[0] + rect[2] + 2, rect[1] + rect[3] + 2, 0x45FFFFFF);
        int color = categoryColor(this.tabs.get(selected));
        graphics.fill(rect[0] - 2, rect[1] - 2, rect[0] - 1, rect[1] + rect[3] + 2, color);
    }

    /** 公告正文（按当前页签过滤、可滚动，超出内容区不绘制）。 */
    private void drawContent(GuiGraphics graphics) {
        List<StrinovaAnnouncements.Entry> list = this.visibleEntries();
        int bodyWidth = this.contentRight - this.contentLeft - 22;
        int viewHeight = this.contentBottom - this.contentTop;
        int total = 8;
        for (StrinovaAnnouncements.Entry entry : list) {
            total += entryHeight(entry, bodyWidth);
        }
        this.maxScroll = Math.max(0, total - viewHeight);
        this.scroll = clamp(this.scroll, 0, this.maxScroll);

        if (list.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("config.strinova.announcement.empty"),
                    (this.contentLeft + this.contentRight) / 2, this.contentTop + viewHeight / 2, 0x9A9A9A);
            return;
        }

        int y = this.contentTop + 4 - this.scroll;
        for (StrinovaAnnouncements.Entry entry : list) {
            int height = this.drawEntry(graphics, entry, y, bodyWidth);
            y += height;
            if (y > this.contentBottom + 20) {
                break;
            }
        }

        // 滚动条
        if (this.maxScroll > 0) {
            int trackX = this.contentRight - 5;
            int trackHeight = viewHeight;
            int thumbHeight = Math.max(18, trackHeight * viewHeight / (viewHeight + this.maxScroll));
            int thumbY = this.contentTop + (trackHeight - thumbHeight) * this.scroll / this.maxScroll;
            graphics.fill(trackX, this.contentTop, trackX + 3, this.contentBottom, 0x25FFFFFF);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0x90FFFFFF);
        }
    }

    private int entryHeight(StrinovaAnnouncements.Entry entry, int bodyWidth) {
        return 8 + 12 + 4 + bodyLines(entry, bodyWidth).size() * BODY_LINE_HEIGHT + 14;
    }

    /** 绘制一条公告，返回它的总高度。 */
    private int drawEntry(GuiGraphics graphics, StrinovaAnnouncements.Entry entry, int y, int bodyWidth) {
        List<FormattedCharSequence> lines = bodyLines(entry, bodyWidth);
        int height = 8 + 12 + 4 + lines.size() * BODY_LINE_HEIGHT + 14;
        int headerY = y + 8;
        int textX = this.contentLeft + 14;

        // 「全部」页签下显示分类小标签
        if (this.selectedCategory == null) {
            String label = tabLabel(entry.category);
            int color = categoryColor(entry.category);
            if (this.visible(headerY, 12)) {
                graphics.fill(textX, headerY - 1, textX + 2, headerY + 10, color);
                graphics.drawString(this.font, label, textX + 6, headerY, color, false);
            }
            textX += 6 + this.font.width(label) + 8;
        }

        // 标题（过长截断，给右侧元信息留位置）
        String meta = entry.metaLine();
        int metaWidth = meta.isEmpty() ? 0 : this.font.width(meta) + 10;
        int titleMax = Math.max(10, this.contentRight - 12 - textX - metaWidth);
        String title = this.font.plainSubstrByWidth(entry.title, titleMax);
        if (this.visible(headerY, 12)) {
            graphics.drawString(this.font, title, textX, headerY, 0xFFFFFF, false);
            if (!meta.isEmpty()) {
                graphics.drawString(this.font, meta,
                        this.contentRight - 12 - this.font.width(meta), headerY, 0x8A8A8A, false);
            }
        }

        // 正文
        int bodyY = headerY + 12 + 4;
        for (FormattedCharSequence line : lines) {
            if (this.visible(bodyY, BODY_LINE_HEIGHT) && line != null) {
                graphics.drawString(this.font, line, this.contentLeft + 14, bodyY, 0xE0E0E0, false);
            }
            bodyY += BODY_LINE_HEIGHT;
        }

        // 分隔线
        int dividerY = y + height - 6;
        if (this.visible(dividerY, 2)) {
            graphics.fill(this.contentLeft + 14, dividerY, this.contentRight - 12, dividerY + 1, 0x25FFFFFF);
        }
        return height;
    }

    /** 正文按宽度换行；空行保留为 null（只占高度）。 */
    private List<FormattedCharSequence> bodyLines(StrinovaAnnouncements.Entry entry, int width) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : entry.body) {
            if (paragraph == null || paragraph.isEmpty()) {
                lines.add(null);
                continue;
            }
            List<FormattedCharSequence> split = this.font.split(Component.literal(paragraph), width);
            if (split.isEmpty()) {
                lines.add(null);
            } else {
                lines.addAll(split);
            }
        }
        return lines;
    }

    private boolean visible(int y, int height) {
        return y + height >= this.contentTop && y <= this.contentBottom;
    }

    private void drawFooter(GuiGraphics graphics) {
        String source = Component.translatable(StrinovaAnnouncements.sourceLabelKey()).getString();
        graphics.drawString(this.font, source, this.panelLeft + 104, this.footerY + 6, 0x8A8A8A, false);
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double designX = this.uiMouseX(mouseX);
        double designY = this.uiMouseY(mouseY);
        if (designX >= this.contentLeft && designX <= this.contentRight
                && designY >= this.contentTop && designY <= this.contentBottom) {
            this.scroll = clamp(this.scroll - (int) Math.round(delta * SCROLL_STEP), 0, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 我已阅读：记录已读并关闭。 */
    private void acknowledgeAndClose() {
        StrinovaAnnouncements.acknowledgeAll();
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void onClose() {
        if (this.acknowledgeOnClose) {
            StrinovaAnnouncements.acknowledgeAll();
        }
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 文案 / 颜色 ====================

    /** 分类显示名：null = 全部；未知分类回退原文。 */
    private String tabLabel(String category) {
        if (category == null) {
            return Component.translatable("config.strinova.announcement.category.all").getString();
        }
        String key = "config.strinova.announcement.category." + category;
        String translated = Component.translatable(key).getString();
        if (translated.equals(key)) {
            return category.isBlank()
                    ? Component.translatable("config.strinova.announcement.category.other").getString()
                    : category;
        }
        return translated;
    }

    private static int categoryColor(String category) {
        if (category == null) {
            return 0xFFE8EDF4;
        }
        return switch (category) {
            case "update" -> 0xFFD24A55;
            case "guide" -> 0xFF5B87EC;
            case "version" -> 0xFFF0B34A;
            case "event" -> 0xFFB86BFF;
            case "notice" -> 0xFF33C1A8;
            default -> 0xFFB9C4D0;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
