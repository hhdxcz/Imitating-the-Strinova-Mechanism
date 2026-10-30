package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 战斗 HUD 设置界面。
 * 提供 HUD 总开关、各元素显示开关（含顶部比分面板）、TAB 计分板开关
 * 与准星参数（颜色/间隙/长度/粗细/动态）调节，并在底部提供 HUD 布局编辑器入口。
 * 所有修改实时写回 {@link StrinovaHudConfig}，关闭界面或进入编辑器前统一落盘。
 */
public class StrinovaCombatSettingsScreen extends Screen {

    /** 单行按钮高度。 */
    private static final int ROW_HEIGHT = 20;
    /**
     * 相邻两行的纵向步进（行高 + 行间距）。
     * 由 22 压缩到 21：开关项由 8 项增加到 10 项后，保证 240 逻辑高度窗口内面板不压住底部按钮。
     */
    private static final int ROW_STEP = 21;
    /** 两列之间的间距。 */
    private static final int COLUMN_GAP = 12;
    /** 面板左右内边距。 */
    private static final int PANEL_PADDING = 12;
    /** 面板顶部标题区高度（由 28 压缩到 22，给多出的一行开关让位）。 */
    private static final int TITLE_HEIGHT = 22;
    /** 显示开关组的行数（5 行 × 2 列 = HUD 总开关 + 8 个元素开关 + 计分板开关，共 10 项）。 */
    private static final int TOGGLE_ROWS = 5;
    /** 准星调节组的行数（颜色+动态 / 间隙+长度 / 粗细）。 */
    private static final int CROSSHAIR_ROWS = 3;
    /** 显示开关组与准星调节组之间的额外间距。 */
    private static final int GROUP_GAP = 6;
    /** 界面底部为“布局编辑/返回”按钮预留的高度。 */
    private static final int BOTTOM_RESERVED = 48;
    /** 底部按钮宽度。 */
    private static final int BOTTOM_BUTTON_WIDTH = 100;

    /** 准星预设颜色（ARGB）：白、青、品红、黄、绿、红。 */
    private static final int[] CROSSHAIR_COLORS = {
            0xFFFFFFFF, 0xFF35D6E8, 0xFFFF4FA3, 0xFFFFFF00, 0xFF00FF00, 0xFFFF0000
    };

    private final Screen parent;
    private final Map<StrinovaHudLayout.Element, Button> elementToggles = new EnumMap<>(StrinovaHudLayout.Element.class);
    private final List<Button> crosshairButtons = new ArrayList<>();
    private final List<NumberRow> numberRows = new ArrayList<>();

    private Button hudToggle;
    private Button scoreboardToggle;
    private Button crosshairColorButton;
    private Button crosshairDynamicButton;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int column1X;
    private int column2X;
    private int columnWidth;
    private int rowStartY;

    public StrinovaCombatSettingsScreen(Screen parent) {
        super(Component.translatable("config.strinova.hud.title"));
        this.parent = parent;
    }

    /** 初始化界面：上方 5 行显示开关（HUD 总开关 + 8 个元素 + 计分板）、下方 3 行准星调节，底部为布局编辑与返回。 */
    @Override
    protected void init() {
        this.clearWidgets();
        this.elementToggles.clear();
        this.crosshairButtons.clear();
        this.numberRows.clear();

        int panelWidth = Math.min(440, this.width - 16);
        int panelHeight = TITLE_HEIGHT + TOGGLE_ROWS * ROW_STEP + GROUP_GAP
                + (CROSSHAIR_ROWS - 1) * ROW_STEP + ROW_HEIGHT + 8;
        this.panelLeft = this.width / 2 - panelWidth / 2;
        this.panelRight = this.panelLeft + panelWidth;
        this.panelTop = 4 + Math.max(0, (this.height - BOTTOM_RESERVED - 4 - panelHeight) / 2);
        this.panelBottom = this.panelTop + panelHeight;

        int contentLeft = this.panelLeft + PANEL_PADDING;
        int contentWidth = panelWidth - PANEL_PADDING * 2;
        this.columnWidth = (contentWidth - COLUMN_GAP) / 2;
        this.column1X = contentLeft;
        this.column2X = contentLeft + this.columnWidth + COLUMN_GAP;
        this.rowStartY = this.panelTop + TITLE_HEIGHT;

        // 第 0~3 行：HUD 总开关 + 7 个 HUD 元素显示开关
        this.hudToggle = this.addTextButton(this.column1X, this.rowY(0),
                toggleMessage("config.strinova.hud.enable", StrinovaHudConfig.get().hudEnabled), () -> {
                    StrinovaHudConfig config = StrinovaHudConfig.get();
                    config.hudEnabled = !config.hudEnabled;
                    this.refreshWidgetState();
                });
        this.addElementToggle(StrinovaHudLayout.Element.HEALTH_ARMOR, this.column2X, this.rowY(0));
        this.addElementToggle(StrinovaHudLayout.Element.AMMO, this.column1X, this.rowY(1));
        this.addElementToggle(StrinovaHudLayout.Element.SKILLS, this.column2X, this.rowY(1));
        this.addElementToggle(StrinovaHudLayout.Element.STATE, this.column1X, this.rowY(2));
        this.addElementToggle(StrinovaHudLayout.Element.CROSSHAIR, this.column2X, this.rowY(2));
        this.addElementToggle(StrinovaHudLayout.Element.DAMAGE_NUMBERS, this.column1X, this.rowY(3));
        this.addElementToggle(StrinovaHudLayout.Element.KILL_FEED, this.column2X, this.rowY(3));

        // 第 4 行：顶部比分面板（HUD 元素开关） + TAB 计分板（独立配置项，不占用 Element）
        this.addElementToggle(StrinovaHudLayout.Element.MATCH_HEADER, this.column1X, this.rowY(4));
        this.scoreboardToggle = this.addTextButton(this.column2X, this.rowY(4),
                toggleMessage("config.strinova.hud.scoreboard", StrinovaHudConfig.get().scoreboardEnabled), () -> {
                    StrinovaHudConfig config = StrinovaHudConfig.get();
                    config.scoreboardEnabled = !config.scoreboardEnabled;
                    this.refreshWidgetState();
                });

        // 第 5~7 行：准星颜色、动态准星、间隙 / 长度 / 粗细
        this.crosshairColorButton = this.addTextButton(this.column1X, this.rowY(5),
                Component.translatable("config.strinova.hud.crosshair.color"), this::cycleCrosshairColor);
        this.crosshairButtons.add(this.crosshairColorButton);
        this.crosshairDynamicButton = this.addTextButton(this.column2X, this.rowY(5),
                toggleMessage("config.strinova.hud.crosshair.dynamic", StrinovaHudConfig.get().crosshairDynamic), () -> {
                    StrinovaHudConfig config = StrinovaHudConfig.get();
                    config.crosshairDynamic = !config.crosshairDynamic;
                    this.refreshWidgetState();
                });
        this.crosshairButtons.add(this.crosshairDynamicButton);
        this.addNumberRow(this.column1X, this.rowY(6), "config.strinova.hud.crosshair.gap",
                () -> StrinovaHudConfig.get().crosshairGap,
                value -> StrinovaHudConfig.get().crosshairGap = value, 0, 12);
        this.addNumberRow(this.column2X, this.rowY(6), "config.strinova.hud.crosshair.length",
                () -> StrinovaHudConfig.get().crosshairLength,
                value -> StrinovaHudConfig.get().crosshairLength = value, 4, 16);
        this.addNumberRow(this.column1X, this.rowY(7), "config.strinova.hud.crosshair.thickness",
                () -> StrinovaHudConfig.get().crosshairThickness,
                value -> StrinovaHudConfig.get().crosshairThickness = value, 1, 4);

        // 底部：布局编辑入口 + 返回
        int bottomY = this.height - 28;
        int bottomCenterX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.hud.editor.entry"),
                        button -> this.openLayoutEditor())
                .bounds(bottomCenterX - BOTTOM_BUTTON_WIDTH - 4, bottomY, BOTTOM_BUTTON_WIDTH, ROW_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.hud.back"),
                        button -> this.onClose())
                .bounds(bottomCenterX + 4, bottomY, BOTTOM_BUTTON_WIDTH, ROW_HEIGHT)
                .build());

        this.refreshWidgetState();
    }

    /** 计算第 row 行按钮的 Y 坐标（显示开关组之后有一段额外分组间距）。 */
    private int rowY(int row) {
        if (row < TOGGLE_ROWS) {
            return this.rowStartY + row * ROW_STEP;
        }
        return this.rowStartY + TOGGLE_ROWS * ROW_STEP + GROUP_GAP + (row - TOGGLE_ROWS) * ROW_STEP;
    }

    /** 创建一个占满整列的文本按钮。 */
    private Button addTextButton(int x, int y, Component message, Runnable action) {
        return this.addRenderableWidget(Button.builder(message, button -> action.run())
                .bounds(x, y, this.columnWidth, ROW_HEIGHT)
                .build());
    }

    /** 添加一个 HUD 元素显示开关按钮，点击后切换该元素的显示状态。 */
    private void addElementToggle(StrinovaHudLayout.Element element, int x, int y) {
        Button button = this.addTextButton(x, y,
                toggleMessage(elementLabelKey(element), StrinovaHudConfig.get().isElementEnabled(element)), () -> {
                    StrinovaHudConfig config = StrinovaHudConfig.get();
                    config.setElementEnabled(element, !config.isElementEnabled(element));
                    this.refreshWidgetState();
                });
        this.elementToggles.put(element, button);
    }

    /** 添加一行数值调节项：标签在 render 中实时绘制，行尾为“-”“+”两个按钮。 */
    private void addNumberRow(int x, int y, String labelKey, IntSupplier getter, IntConsumer setter, int min, int max) {
        Button minus = this.addRenderableWidget(Button.builder(Component.literal("-"),
                        button -> setter.accept(clampNumber(getter.getAsInt() - 1, min, max)))
                .bounds(x + this.columnWidth - 42, y, 20, ROW_HEIGHT)
                .build());
        Button plus = this.addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> setter.accept(clampNumber(getter.getAsInt() + 1, min, max)))
                .bounds(x + this.columnWidth - 20, y, 20, ROW_HEIGHT)
                .build());
        this.crosshairButtons.add(minus);
        this.crosshairButtons.add(plus);
        this.numberRows.add(new NumberRow(labelKey, getter, x, y));
    }

    /** 根据当前配置刷新所有按钮的文案与可用状态。 */
    private void refreshWidgetState() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        if (this.hudToggle != null) {
            this.hudToggle.setMessage(toggleMessage("config.strinova.hud.enable", config.hudEnabled));
        }
        for (Map.Entry<StrinovaHudLayout.Element, Button> entry : this.elementToggles.entrySet()) {
            StrinovaHudLayout.Element element = entry.getKey();
            entry.getValue().setMessage(toggleMessage(elementLabelKey(element), config.isElementEnabled(element)));
            // HUD 总开关关闭时，元素开关不可用（先打开总开关再单独配置）
            entry.getValue().active = config.hudEnabled;
        }
        if (this.crosshairDynamicButton != null) {
            this.crosshairDynamicButton.setMessage(
                    toggleMessage("config.strinova.hud.crosshair.dynamic", config.crosshairDynamic));
        }
        if (this.scoreboardToggle != null) {
            // 计分板是独立于 HUD 元素的 TAB 覆盖层，因此不受 HUD 总开关联动禁用
            this.scoreboardToggle.setMessage(
                    toggleMessage("config.strinova.hud.scoreboard", config.scoreboardEnabled));
        }
        boolean crosshairUsable = config.hudEnabled && config.crosshairEnabled;
        for (Button button : this.crosshairButtons) {
            button.active = crosshairUsable;
        }
    }

    /** 在预设准星颜色之间循环切换。 */
    private void cycleCrosshairColor() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int next = CROSSHAIR_COLORS[0];
        for (int i = 0; i < CROSSHAIR_COLORS.length; i++) {
            if (CROSSHAIR_COLORS[i] == config.crosshairColor) {
                next = CROSSHAIR_COLORS[(i + 1) % CROSSHAIR_COLORS.length];
                break;
            }
        }
        config.crosshairColor = next;
    }

    /** 打开 HUD 布局编辑器；进入子界面前先保存，避免本次修改丢失。 */
    private void openLayoutEditor() {
        StrinovaHudConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new StrinovaHudEditorScreen(this));
        }
    }

    /** 渲染界面：背景、面板、分组分隔线、数值标签，最后绘制控件与准星颜色预览块。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(this.panelLeft, this.panelTop, this.panelRight, this.panelBottom, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelTop + 8, 0xFFFFFF);

        // 显示开关组与准星调节组之间的分隔线
        int separatorY = this.rowY(TOGGLE_ROWS) - GROUP_GAP / 2;
        graphics.fill(this.panelLeft + PANEL_PADDING, separatorY,
                this.panelRight - PANEL_PADDING, separatorY + 1, 0x50FFFFFF);

        // 数值行：左侧标签（超宽时截断），右侧对齐显示当前值；数值变化时无需重建控件
        for (NumberRow row : this.numberRows) {
            String value = String.valueOf(row.getter.getAsInt());
            int valueX = row.columnX + this.columnWidth - 46 - this.font.width(value);
            int labelX = row.columnX + 4;
            int labelMaxWidth = Math.max(8, valueX - labelX - 6);
            String fullLabel = Component.translatable(row.labelKey).getString();
            String labelText = fullLabel;
            if (this.font.width(fullLabel) > labelMaxWidth) {
                labelText = this.font.plainSubstrByWidth(fullLabel,
                        Math.max(0, labelMaxWidth - this.font.width(".."))) + "..";
            }
            graphics.drawString(this.font, labelText, labelX, row.y + 6, 0xE0E0E0, false);
            graphics.drawString(this.font, value, valueX, row.y + 6, 0xFFFFFF, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderCrosshairColorSwatch(graphics);
    }

    /** 在“准星颜色”按钮右侧绘制当前颜色小方块，避免为每种颜色单独新增语言键。 */
    private void renderCrosshairColorSwatch(GuiGraphics graphics) {
        if (this.crosshairColorButton == null) {
            return;
        }
        int size = 10;
        int x = this.crosshairColorButton.getX() + this.crosshairColorButton.getWidth() - size - 6;
        int y = this.crosshairColorButton.getY() + (this.crosshairColorButton.getHeight() - size) / 2;
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        graphics.fill(x, y, x + size, y + size, StrinovaHudConfig.get().crosshairColor);
    }

    /** 关闭界面：保存配置后返回父界面。 */
    @Override
    public void onClose() {
        StrinovaHudConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** 设置界面不暂停游戏。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 开关类按钮文案：标签 + 开/关（使用原版 options.on/off，中英文均适用）。 */
    private static Component toggleMessage(String labelKey, boolean enabled) {
        return Component.translatable(labelKey).append(": ")
                .append(Component.translatable(enabled ? "options.on" : "options.off"));
    }

    /** HUD 元素与设置界面标签键的对应关系。 */
    private static String elementLabelKey(StrinovaHudLayout.Element element) {
        return switch (element) {
            case HEALTH_ARMOR -> "config.strinova.hud.vitals";
            case AMMO -> "config.strinova.hud.ammo";
            case SKILLS -> "config.strinova.hud.skills";
            case STATE -> "config.strinova.hud.state";
            case CROSSHAIR -> "config.strinova.hud.crosshair";
            case KILL_FEED -> "config.strinova.hud.kill_feed";
            case DAMAGE_NUMBERS -> "config.strinova.hud.damage_numbers";
            case MATCH_HEADER -> "config.strinova.hud.match_header";
        };
    }

    /** 将数值限制在 [min, max] 区间内。 */
    private static int clampNumber(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 准星数值行：标签在 render 中实时绘制（附带当前值），数值的读写走配置字段。 */
    private static final class NumberRow {
        private final String labelKey;
        private final IntSupplier getter;
        private final int columnX;
        private final int y;

        private NumberRow(String labelKey, IntSupplier getter, int columnX, int y) {
            this.labelKey = labelKey;
            this.getter = getter;
            this.columnX = columnX;
            this.y = y;
        }
    }
}
