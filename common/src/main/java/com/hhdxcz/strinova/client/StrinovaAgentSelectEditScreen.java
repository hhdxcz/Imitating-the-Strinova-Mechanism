package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;
import com.hhdxcz.strinova.client.hud.StrinovaAgentSelectUi;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaWikiData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「选角界面」可视化自定义编辑器（与 TAB 计分板编辑器同类的交互体验）。
 *
 * <p>打开方式：超弦体选角界面右上角「自定义」按钮，或客户端命令
 * {@code /strinova_client agent edit}。</p>
 *
 * <p>界面结构：</p>
 * <ul>
 *   <li>顶部：标题「选角界面 · 自定义」+ 参数行（队友列宽 / 右栏宽 / 锁定条高 / 透明度）+ 操作提示；</li>
 *   <li>中区：按当前配置实时绘制的选角界面示意图（顶部阶段条、左队友列、中央立绘 + 简介/语录、
 *       右侧信息面板（头衔 / 4 技能卡 / 武器区 / 页签）、底部 2×12 头像网格、锁定条、限制提示），
 *       被关闭的部件用灰色「已隐藏」虚线占位；</li>
 *   <li>可拖拽边界：队友列右边界 → {@link StrinovaAgentSelectUi#teamWidthRatio}，
 *       右侧面板左边界 → {@link StrinovaAgentSelectUi#rightWidthRatio}，
 *       锁定条上边界 → {@link StrinovaAgentSelectUi#lockHeight}；悬停/拖拽时高亮青色；</li>
 *   <li>底部两行按钮：12 个显隐开关 + 不透明度 -/+、重置、保存、返回；
 *       另有 4 个颜色按钮（主色 / 中央角色名 / 右侧头衔名 / 头像格底色），
 *       宽度够时接在动作按钮同一行末尾，不够时自动拆成第三行；</li>
 * </ul>
 *
 * <p>颜色按钮点击循环预设色：默认(0) → 白 → 金 → 青 → 品红 → 绿 → 橙 → 紫 → 默认，
 * 按钮文字显示当前色名（如「主色：金」），改动实时反映到中区示意图。</p>
 *
 * <p>保存写入 {@code config/strinova-client.json}；ESC / 返回放弃修改（用打开界面时的 JSON 快照还原）。
 * 编辑过程中所有改动都直接作用于 {@link StrinovaHudConfig#agentSelectUi}，因此示意图与真实界面共用同一份配置。</p>
 */
public class StrinovaAgentSelectEditScreen extends Screen {

    // ==================== 配色（与选角界面保持一致） ====================
    private static final int COLOR_TEXT = 0xFFEAF6FF;
    private static final int COLOR_DIM = 0xFF9AA8B8;
    private static final int COLOR_HINT = 0xFF74838F;
    private static final int COLOR_BORDER = 0x55335F73;
    private static final int COLOR_PANEL = 0xCC0A111C;
    private static final int COLOR_PANEL_DARK = 0xE00A111C;
    /** 边界悬停 / 拖拽高亮色（与计分板编辑器一致）。 */
    private static final int COLOR_HIGHLIGHT = 0xFF35D6E8;
    /** 隐藏部件占位色。 */
    private static final int COLOR_HIDDEN = 0xFF5A6672;
    private static final int COLOR_GOLD = 0xFFFFD24A;
    private static final int COLOR_ALLY = 0xFFBC3B47;
    private static final int COLOR_ENEMY = 0xFF4B77DC;

    /** 底部头像网格列数（2 行 × 12 列 = 24）。 */
    private static final int GRID_COLUMNS = 12;
    /** 底部头像网格行数。 */
    private static final int GRID_ROWS = 2;

    // ==================== 底部按钮行尺寸 ====================
    private static final int BUTTON_H = 18;
    private static final int BUTTON_GAP = 2;
    private static final int BUTTON_ROW_GAP = 4;
    private static final int BOTTOM_PAD = 6;
    /** 底部按钮行左右留白。 */
    private static final int BUTTON_MARGIN = 6;

    /**
     * 颜色循环预设：默认（0 = 跟随角色定位色 / 默认文字色）→ 白 → 金 → 青 → 品红 → 绿 → 橙 → 紫 → 回默认。
     * 与 {@link #PRESET_NAMES} 一一对应。
     */
    private static final int[] PRESET_COLORS = {
            0, 0xFFFFFFFF, 0xFFFFD166, 0xFF35D6E8, 0xFFFF4FA3, 0xFF54E0A0, 0xFFFF8A5C, 0xFF9B6BFF
    };
    /** 预设颜色名称（按钮文字用）。 */
    private static final String[] PRESET_NAMES = {
            "默认", "白", "金", "青", "品红", "绿", "橙", "紫"
    };

    /** 显隐开关条目（底部第一行按钮，顺序即按钮顺序）。 */
    private enum Toggle {
        TOP_BAR("顶部阶段条", "顶栏"),
        TEAM("队友列", "队友"),
        PORTRAIT("中央立绘", "立绘"),
        INTRO("角色简介", "简介"),
        QUOTE("个性语录", "语录"),
        RIGHT_PANEL("右侧面板", "右栏"),
        SKILLS("技能卡", "技能"),
        WEAPON("武器区", "武器"),
        TABS("面板页签", "页签"),
        GRID("头像网格", "网格"),
        LOCK("锁定条", "锁定"),
        NOTICE("限制提示", "提示");

        private final String label;
        private final String shortLabel;

        Toggle(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        /** 读取当前显隐状态。 */
        public boolean get(StrinovaAgentSelectUi ui) {
            return switch (this) {
                case TOP_BAR -> ui.showTopBar;
                case TEAM -> ui.showTeam;
                case PORTRAIT -> ui.showPortrait;
                case INTRO -> ui.showIntro;
                case QUOTE -> ui.showQuote;
                case RIGHT_PANEL -> ui.showRightPanel;
                case SKILLS -> ui.showSkills;
                case WEAPON -> ui.showWeaponPanel;
                case TABS -> ui.showTabs;
                case GRID -> ui.showGrid;
                case LOCK -> ui.showLockButton;
                case NOTICE -> ui.showNotice;
            };
        }

        /** 写入显隐状态。 */
        public void set(StrinovaAgentSelectUi ui, boolean value) {
            switch (this) {
                case TOP_BAR -> ui.showTopBar = value;
                case TEAM -> ui.showTeam = value;
                case PORTRAIT -> ui.showPortrait = value;
                case INTRO -> ui.showIntro = value;
                case QUOTE -> ui.showQuote = value;
                case RIGHT_PANEL -> ui.showRightPanel = value;
                case SKILLS -> ui.showSkills = value;
                case WEAPON -> ui.showWeaponPanel = value;
                case TABS -> ui.showTabs = value;
                case GRID -> ui.showGrid = value;
                case LOCK -> ui.showLockButton = value;
                case NOTICE -> ui.showNotice = value;
            }
        }
    }

    /** 4 个可循环切换的颜色项（对应 StrinovaAgentSelectUi 的 4 个颜色字段）。 */
    private enum ColorTarget {
        ACCENT("主色", "主色"),
        NAME("角色名", "名字"),
        HEADER("头衔", "头衔"),
        GRID("头像格", "格子");

        private final String label;
        private final String shortLabel;

        ColorTarget(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        /** 读取当前颜色（0 = 默认）。 */
        public int get(StrinovaAgentSelectUi ui) {
            return switch (this) {
                case ACCENT -> ui.accentColor;
                case NAME -> ui.nameColor;
                case HEADER -> ui.headerNameColor;
                case GRID -> ui.gridCellColor;
            };
        }

        /** 写入颜色（0 = 默认）。 */
        public void set(StrinovaAgentSelectUi ui, int value) {
            switch (this) {
                case ACCENT -> ui.accentColor = value;
                case NAME -> ui.nameColor = value;
                case HEADER -> ui.headerNameColor = value;
                case GRID -> ui.gridCellColor = value;
            }
        }
    }

    private final Screen parent;
    /** 打开界面时的配置快照（ESC / 返回时还原）。 */
    private String snapshotJson;
    /** 是否已保存（保存后关闭不再还原快照）。 */
    private boolean saved;

    /** 全部超弦体（示意图网格/队友列用）。 */
    private final List<StrinovaAgentRoster.Agent> agents = StrinovaAgentRoster.agents();

    private int row1Y;
    private int row2Y;
    /** 颜色按钮独立行 Y；-1 表示颜色按钮与第二行动作按钮同一行。 */
    private int row3Y = -1;
    /** 动作按钮宽度 / 颜色按钮宽度 / 行起始 X（computeLayout 计算）。 */
    private int actionButtonW = 60;
    private int colorButtonW = 60;
    private int row2StartX;
    private int row3StartX;
    /** 颜色按钮是否与动作按钮同一行（宽度不够时自动拆成第三行）。 */
    private boolean colorSameRow = true;

    /** 拖拽状态：0 无 / 1 队友列右边界 / 2 右侧面板左边界 / 3 锁定条高度。 */
    private int dragMode;

    /** 底部第一行显隐按钮（用于更新文字）。 */
    private final List<Button> toggleButtons = new ArrayList<>();
    /** 底部颜色按钮（用于刷新文字与悬停提示）。 */
    private final List<Button> colorButtons = new ArrayList<>();

    // ---- 示意图区域 ----
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private int pInnerX;
    private int pInnerY;
    private int pInnerW;
    private int pInnerH;
    private int pMargin;
    private int pGap;
    private float previewScale = 1.0F;

    private int pBarX;
    private int pBarY;
    private int pBarW;
    private int pBarH;
    private int pGhostBarH;

    private int pLeftX;
    private int pLeftY;
    private int pLeftW;
    private int pLeftH;

    private int pCenterX;
    private int pCenterY;
    private int pCenterW;
    private int pCenterH;

    private int pRightX;
    private int pRightY;
    private int pRightW;
    private int pRightH;

    private int pGridX;
    private int pGridY;
    private int pGridW;
    private int pGridH;
    private int pCellW;
    private int pCellH;
    private int pCellGap;
    private int pGhostGridH;

    private int pLockX;
    private int pLockY;
    private int pLockW;
    private int pLockH;
    private int pLockBottom;
    private int pGhostLockH;

    private int pNoticeX;
    private int pNoticeY;
    private int pNoticeW;
    private int pNoticeH;
    private int pGhostNoticeH;

    private int pGhostLeftW;
    private int pGhostRightW;

    /** 可拖拽边界（-1 = 对应部件隐藏，不参与拖拽）。 */
    private int pTeamEdgeX = -1;
    private int pRightEdgeX = -1;
    private int pLockEdgeY = -1;

    private boolean pShowBar;
    private boolean pShowTeam;
    private boolean pShowPortrait;
    private boolean pShowIntro;
    private boolean pShowQuote;
    private boolean pShowRightPanel;
    private boolean pShowSkills;
    private boolean pShowWeapon;
    private boolean pShowTabs;
    private boolean pShowGrid;
    private boolean pShowLock;
    private boolean pShowNotice;

    public StrinovaAgentSelectEditScreen(@Nullable Screen parent) {
        super(Component.literal("选角界面 · 自定义"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // 初始化 / 底部按钮
    // ------------------------------------------------------------------

    /** 初始化：记录配置快照、计算底部按钮与示意图区域，并创建显隐 / 动作 / 颜色三组按钮。 */
    @Override
    protected void init() {
        this.clearWidgets();
        this.toggleButtons.clear();
        this.colorButtons.clear();
        this.dragMode = 0;
        if (this.snapshotJson == null) {
            this.snapshotJson = StrinovaHudConfig.get().agentSelectExportJson();
        }
        this.computeLayout();
        this.buildVisibilityButtons();
        this.buildActionButtons();
        this.buildColorButtons();
    }

    /**
     * 计算编辑器自身布局：预览区 + 底部按钮行。
     * 前两行固定（显隐开关 / 动作按钮）；4 个颜色按钮优先接在动作按钮同一行末尾，
     * 宽度不足时自动拆成第三行，预览区随之收缩。
     */
    private void computeLayout() {
        int margin = this.width < 480 ? 4 : 8;
        int available = Math.max(140, this.width - 2 * BUTTON_MARGIN);
        int perNine = (available - 8 * BUTTON_GAP) / 9;
        this.actionButtonW = Math.max(44, Math.min(84, perNine));
        this.colorButtonW = Math.max(46, Math.min(76, perNine));
        int sameRowTotal = 5 * this.actionButtonW + 4 * this.colorButtonW + 8 * BUTTON_GAP;
        this.colorSameRow = sameRowTotal <= available;

        if (this.colorSameRow) {
            this.row3Y = -1;
            this.row2Y = this.height - BOTTOM_PAD - BUTTON_H;
            this.row1Y = this.row2Y - BUTTON_ROW_GAP - BUTTON_H;
            this.row2StartX = BUTTON_MARGIN + Math.max(0, (available - sameRowTotal) / 2);
            this.row3StartX = 0;
        } else {
            this.row3Y = this.height - BOTTOM_PAD - BUTTON_H;
            this.row2Y = this.row3Y - BUTTON_ROW_GAP - BUTTON_H;
            this.row1Y = this.row2Y - BUTTON_ROW_GAP - BUTTON_H;
            int actionTotal = 5 * this.actionButtonW + 4 * BUTTON_GAP;
            this.row2StartX = BUTTON_MARGIN + Math.max(0, (available - actionTotal) / 2);
            int colorTotal = 4 * this.colorButtonW + 3 * BUTTON_GAP;
            this.row3StartX = BUTTON_MARGIN + Math.max(0, (available - colorTotal) / 2);
        }

        this.previewX = margin;
        this.previewY = this.height < 300 ? 38 : 42;
        this.previewW = Math.max(20, this.width - 2 * margin);
        this.previewH = Math.max(24, this.row1Y - 6 - this.previewY);
    }

    /** 第一行：12 个显隐开关按钮，按可用宽度均分（窄窗口自动缩短按钮文字）。 */
    private void buildVisibilityButtons() {
        Toggle[] toggles = Toggle.values();
        int margin = 6;
        int available = Math.max(120, this.width - 2 * margin);
        int buttonW = Math.max(12, (available - (toggles.length - 1) * BUTTON_GAP) / toggles.length);
        int totalW = toggles.length * buttonW + (toggles.length - 1) * BUTTON_GAP;
        int startX = margin + Math.max(0, (available - totalW) / 2);
        for (int i = 0; i < toggles.length; i++) {
            final Toggle toggle = toggles[i];
            int x = startX + i * (buttonW + BUTTON_GAP);
            Button button = Button.builder(Component.literal(this.toggleLabel(toggle, buttonW)),
                            b -> {
                                StrinovaAgentSelectUi ui = ui();
                                toggle.set(ui, !toggle.get(ui));
                                this.refreshToggleLabels();
                            })
                    .bounds(x, this.row1Y, buttonW, BUTTON_H)
                    .build();
            this.toggleButtons.add(button);
            this.addRenderableWidget(button);
        }
    }

    /** 第二行：不透明度 -/+、重置、保存、返回（颜色按钮同行时接在返回按钮之后）。 */
    private void buildActionButtons() {
        int buttonW = this.actionButtonW;
        int startX = this.row2StartX;
        int y = this.row2Y;
        this.addRenderableWidget(Button.builder(Component.literal(this.actionLabel("不透明度 -", "透明-", buttonW)),
                        b -> this.changeOpacity(-0.05F))
                .bounds(startX, y, buttonW, BUTTON_H).build());
        this.addRenderableWidget(Button.builder(Component.literal(this.actionLabel("不透明度 +", "透明+", buttonW)),
                        b -> this.changeOpacity(0.05F))
                .bounds(startX + buttonW + BUTTON_GAP, y, buttonW, BUTTON_H).build());
        this.addRenderableWidget(Button.builder(Component.literal(this.actionLabel("重置", "重置", buttonW)),
                        b -> this.resetConfig())
                .bounds(startX + 2 * (buttonW + BUTTON_GAP), y, buttonW, BUTTON_H).build());
        this.addRenderableWidget(Button.builder(Component.literal(this.actionLabel("保存", "保存", buttonW)),
                        b -> this.saveAndClose())
                .bounds(startX + 3 * (buttonW + BUTTON_GAP), y, buttonW, BUTTON_H).build());
        this.addRenderableWidget(Button.builder(Component.literal(this.actionLabel("返回", "返回", buttonW)),
                        b -> this.cancelAndClose())
                .bounds(startX + 4 * (buttonW + BUTTON_GAP), y, buttonW, BUTTON_H).build());
    }

    /** 颜色按钮行：主色 / 中央角色名 / 右侧头衔名 / 头像格底色，点击循环预设色，实时改配置并刷新文字。 */
    private void buildColorButtons() {
        ColorTarget[] targets = ColorTarget.values();
        int y = this.colorSameRow ? this.row2Y : this.row3Y;
        int startX = this.colorSameRow
                ? this.row2StartX + 5 * (this.actionButtonW + BUTTON_GAP)
                : this.row3StartX;
        for (int i = 0; i < targets.length; i++) {
            final ColorTarget target = targets[i];
            Button button = Button.builder(Component.literal(this.colorLabel(target, this.colorButtonW)),
                            b -> {
                                this.cycleColor(target);
                                this.refreshColorLabels();
                            })
                    .bounds(startX + i * (this.colorButtonW + BUTTON_GAP), y,
                            this.colorButtonW, BUTTON_H)
                    .build();
            this.colorButtons.add(button);
            this.addRenderableWidget(button);
        }
    }

    /** 动作按钮文字：宽度够时用完整文字，否则退化为短文字。 */
    private String actionLabel(String full, String shortText, int buttonW) {
        if (this.font != null && this.font.width(full) + 4 <= buttonW) {
            return full;
        }
        if (this.font != null && this.font.width(shortText) + 4 <= buttonW) {
            return shortText;
        }
        return shortText.substring(0, Math.min(1, shortText.length()));
    }

    /** 显隐按钮文字：优先「名称：开/关」，宽度不足时逐级缩短。 */
    private String toggleLabel(Toggle toggle, int buttonW) {
        boolean on = toggle.get(ui());
        String state = on ? "开" : "关";
        String[] candidates = {
                toggle.label + "：" + state,
                toggle.shortLabel + "：" + state,
                toggle.label + state,
                toggle.shortLabel + state,
                toggle.shortLabel,
                state
        };
        if (this.font == null) {
            return toggle.label + "：" + state;
        }
        for (String candidate : candidates) {
            if (this.font.width(candidate) + 4 <= buttonW) {
                return candidate;
            }
        }
        return state;
    }

    /** 切换显隐后刷新第一行按钮文字。 */
    private void refreshToggleLabels() {
        Toggle[] toggles = Toggle.values();
        for (int i = 0; i < this.toggleButtons.size() && i < toggles.length; i++) {
            Button button = this.toggleButtons.get(i);
            button.setMessage(Component.literal(this.toggleLabel(toggles[i], button.getWidth())));
        }
    }

    /** 颜色按钮文字：优先「颜色项：色名」，宽度不足时逐级缩短（至少保留色名）。 */
    private String colorLabel(ColorTarget target, int buttonW) {
        String name = colorName(target.get(ui()));
        String[] candidates = {
                target.label + "：" + name,
                target.shortLabel + "：" + name,
                name,
                target.shortLabel
        };
        if (this.font == null) {
            return target.label + "：" + name;
        }
        for (String candidate : candidates) {
            if (this.font.width(candidate) + 2 <= buttonW) {
                return candidate;
            }
        }
        return name;
    }

    /** 点击颜色按钮：按预设顺序循环（默认 → 白 → 金 → 青 → 品红 → 绿 → 橙 → 紫 → 默认）。 */
    private void cycleColor(ColorTarget target) {
        StrinovaAgentSelectUi ui = ui();
        int current = target.get(ui);
        int next = PRESET_COLORS[0];
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == current) {
                next = PRESET_COLORS[(i + 1) % PRESET_COLORS.length];
                break;
            }
        }
        target.set(ui, next);
    }

    /** 取颜色对应的预设名称；不在预设内（如从 JSON 导入的自定义色）显示「自定义」。 */
    private static String colorName(int color) {
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == color) {
                return PRESET_NAMES[i];
            }
        }
        return "自定义";
    }

    /** 切换颜色后刷新 4 个颜色按钮文字。 */
    private void refreshColorLabels() {
        ColorTarget[] targets = ColorTarget.values();
        for (int i = 0; i < this.colorButtons.size() && i < targets.length; i++) {
            Button button = this.colorButtons.get(i);
            button.setMessage(Component.literal(this.colorLabel(targets[i], button.getWidth())));
        }
    }

    /** 调整面板不透明度（0.20 ~ 1.00）。 */
    private void changeOpacity(float delta) {
        StrinovaAgentSelectUi ui = ui();
        ui.opacity = clampf(ui.opacity + delta, 0.20F, 1.0F);
    }

    /** 恢复默认配置（尚未保存，ESC 仍可放弃）。 */
    private void resetConfig() {
        ui().reset();
        this.refreshToggleLabels();
        this.refreshColorLabels();
    }

    /** 保存配置并返回选角界面。 */
    private void saveAndClose() {
        StrinovaHudConfig.get().save();
        this.saved = true;
        this.closeToParent();
    }

    /** 放弃修改：用打开界面时的快照还原，然后返回。 */
    private void cancelAndClose() {
        if (!this.saved && this.snapshotJson != null) {
            StrinovaHudConfig.get().agentSelectImportJson(this.snapshotJson);
        }
        this.closeToParent();
    }

    /** 返回父界面（parent 可能为 null，表示回到游戏）。 */
    private void closeToParent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.cancelAndClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC：放弃修改并返回
            this.cancelAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------
    // 渲染主流程
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明压暗背景
        graphics.fill(0, 0, this.width, this.height, 0xC8000000);
        this.layoutPreview();

        // 顶部标题 / 参数行 / 提示行
        String title = "选角界面 · 自定义";
        graphics.drawString(this.font, this.truncate(title, this.width - 8),
                (this.width - this.font.width(this.truncate(title, this.width - 8))) / 2, 4, COLOR_TEXT, true);
        StrinovaAgentSelectUi ui = ui();
        String params = String.format(Locale.ROOT,
                "队友列宽 %.1f%%   右栏宽 %.1f%%   锁定条高 %d px   不透明度 %d%%",
                ui.teamWidthRatio * 100.0F, ui.rightWidthRatio * 100.0F,
                Math.round(ui.lockHeight), Math.round(ui.opacity * 100.0F));
        String fittedParams = this.truncate(params, this.width - 8);
        graphics.drawString(this.font, fittedParams, (this.width - this.font.width(fittedParams)) / 2, 15,
                COLOR_DIM, false);
        String hint = "拖动青色边界调整比例 · 点击下方按钮切换显隐 · 保存后生效 · ESC 放弃";
        String fittedHint = this.truncate(hint, this.width - 8);
        graphics.drawString(this.font, fittedHint, (this.width - this.font.width(fittedHint)) / 2, 26,
                COLOR_HINT, false);

        // 预览外框
        if (this.previewW > 0 && this.previewH > 0) {
            graphics.fill(this.previewX - 1, this.previewY - 1, this.previewX + this.previewW + 1,
                    this.previewY + this.previewH + 1, 0xFF1B2634);
            graphics.fill(this.previewX, this.previewY, this.previewX + this.previewW,
                    this.previewY + this.previewH, 0xE6050A12);

            // 示意图内容（裁剪到预览区内，保证小窗口也不越界）
            graphics.enableScissor(this.previewX, this.previewY,
                    this.previewX + this.previewW, this.previewY + this.previewH);
            this.drawPreview(graphics);
            graphics.disableScissor();
        }

        // 可拖拽边界
        this.drawBoundaries(graphics, mouseX, mouseY);

        // 底部按钮
        super.render(graphics, mouseX, mouseY, partialTick);
        // 显隐按钮底部状态指示条（窄窗口文字被缩短时也能看出开/关）
        this.drawToggleIndicators(graphics);
        // 颜色按钮底部颜色条
        this.drawColorIndicators(graphics);

        // 显隐 / 颜色按钮的完整名称提示（窄窗口按钮文字被缩短时尤其有用）
        this.drawToggleTip(graphics, mouseX, mouseY);
        this.drawColorTip(graphics, mouseX, mouseY);
    }

    /** 在每个颜色按钮底部画一条实际颜色条（默认色画灰色），直观显示当前配色。 */
    private void drawColorIndicators(GuiGraphics graphics) {
        ColorTarget[] targets = ColorTarget.values();
        for (int i = 0; i < this.colorButtons.size() && i < targets.length; i++) {
            Button button = this.colorButtons.get(i);
            int color = targets[i].get(ui());
            int barColor = color == 0 ? 0x665A6672 : (0xFF000000 | (color & 0x00FFFFFF));
            graphics.fill(button.getX() + 3, button.getY() + button.getHeight() - 3,
                    button.getX() + button.getWidth() - 3,
                    button.getY() + button.getHeight() - 1, barColor);
        }
    }

    /** 在每个显隐按钮底部画一条状态指示条：青色 = 显示，灰色 = 隐藏。 */
    private void drawToggleIndicators(GuiGraphics graphics) {
        Toggle[] toggles = Toggle.values();
        for (int i = 0; i < this.toggleButtons.size() && i < toggles.length; i++) {
            Button toggleButton = this.toggleButtons.get(i);
            boolean on = toggles[i].get(ui());
            int color = on ? 0xFF35D6E8 : 0x665A6672;
            graphics.fill(toggleButton.getX() + 2, toggleButton.getY() + toggleButton.getHeight() - 3,
                    toggleButton.getX() + toggleButton.getWidth() - 2,
                    toggleButton.getY() + toggleButton.getHeight() - 1, color);
        }
    }

    // ------------------------------------------------------------------
    // 示意图布局
    // ------------------------------------------------------------------

    /** 按当前配置计算示意图内部各区域位置（每帧重算，拖动时实时反映）。 */
    private void layoutPreview() {
        StrinovaAgentSelectUi ui = ui();
        this.pInnerX = this.previewX + 3;
        this.pInnerY = this.previewY + 3;
        this.pInnerW = Math.max(20, this.previewW - 6);
        this.pInnerH = Math.max(20, this.previewH - 6);
        this.previewScale = clampf(this.pInnerH / (float) Math.max(120, this.height), 0.30F, 1.0F);

        this.pShowBar = ui.showTopBar;
        this.pShowTeam = ui.showTeam;
        this.pShowPortrait = ui.showPortrait;
        this.pShowIntro = ui.showIntro;
        this.pShowQuote = ui.showQuote;
        this.pShowRightPanel = ui.showRightPanel;
        this.pShowSkills = ui.showSkills;
        this.pShowWeapon = ui.showWeaponPanel;
        this.pShowTabs = ui.showTabs;
        this.pShowGrid = ui.showGrid;
        this.pShowLock = ui.showLockButton;
        this.pShowNotice = ui.showNotice;

        this.pMargin = clamp(2, 4, this.pInnerW / 300);
        this.pGap = Math.min(4, Math.max(2, this.pMargin + 1));
        int leftAnchorX = this.pInnerX + this.pMargin;
        int rightAnchorX = this.pInnerX + this.pInnerW - this.pMargin;
        int barTop = this.pInnerY + this.pMargin;

        this.pGhostBarH = clamp(10, 26, Math.round(this.pInnerH * 0.085F));
        this.pBarH = this.pShowBar ? this.pGhostBarH : 0;
        this.pBarX = leftAnchorX;
        this.pBarY = barTop;
        this.pBarW = Math.max(20, rightAnchorX - leftAnchorX);

        int mainTop = barTop + (this.pShowBar ? this.pBarH + this.pGap : 0);
        int bottomLimit = this.pInnerY + this.pInnerH - this.pMargin;

        this.pGhostNoticeH = Math.max(8, Math.min(11, this.pInnerH / 16));
        this.pNoticeH = this.pShowNotice ? this.pGhostNoticeH : 0;
        this.pNoticeX = leftAnchorX;
        this.pNoticeW = this.pBarW;
        this.pNoticeY = bottomLimit - this.pNoticeH;

        this.pLockBottom = bottomLimit - this.pNoticeH;
        this.pGhostLockH = clamp(6, 24, Math.round(ui.lockHeight * this.previewScale));
        this.pLockH = this.pShowLock ? this.pGhostLockH : 0;
        this.pLockY = this.pLockBottom - this.pLockH;
        int gridBottom = (this.pShowLock ? this.pLockY : this.pLockBottom) - this.pGap;

        int rightW = this.pShowRightPanel
                ? clamp(30, Math.round(this.pInnerW * 0.55F),
                Math.round(this.pInnerW * ui.rightWidthRatio)) : 0;
        int teamW = this.pShowTeam
                ? clamp(14, Math.round(this.pInnerW * 0.35F),
                Math.round(this.pInnerW * ui.teamWidthRatio)) : 0;
        int gridRegionW = Math.max(30, this.pInnerW - 2 * this.pMargin
                - (this.pShowRightPanel ? rightW + this.pGap : 0));

        // 头像格尺寸（无论显隐都先算一份「隐藏占位」尺寸）
        int cellGap = clamp(1, 2, Math.max(1, gridRegionW / 220));
        int cellW = clamp(6, 40, (gridRegionW - (GRID_COLUMNS - 1) * cellGap) / GRID_COLUMNS);
        int cellH = clamp(6, 16, cellW);
        this.pGhostGridH = GRID_ROWS * cellH + (GRID_ROWS - 1) * cellGap;
        this.pCellGap = cellGap;
        this.pCellW = cellW;
        this.pCellH = cellH;
        this.pGridH = this.pShowGrid ? this.pGhostGridH : 0;
        this.pGridY = gridBottom - this.pGridH;
        this.pGridX = leftAnchorX;
        this.pGridW = Math.max(20, gridRegionW);

        int mainBottom = (this.pShowGrid ? this.pGridY : gridBottom) - this.pGap;
        int mainH = Math.max(16, mainBottom - mainTop);

        this.pLeftW = teamW;
        this.pLeftX = leftAnchorX;
        this.pLeftY = mainTop;
        this.pLeftH = mainH;
        this.pRightW = rightW;
        this.pRightX = rightAnchorX - rightW;
        this.pRightY = mainTop;
        this.pRightH = mainH;
        this.pCenterX = this.pLeftX + (this.pShowTeam ? this.pLeftW + this.pGap : 0);
        int centerRight = this.pRightX - (this.pShowRightPanel ? this.pGap : 0);
        this.pCenterW = Math.max(16, centerRight - this.pCenterX);
        this.pCenterY = mainTop;
        this.pCenterH = mainH;

        this.pLockW = Math.max(16, this.pCenterW);
        this.pLockX = this.pCenterX + Math.max(0, (this.pCenterW - this.pLockW) / 2);

        // 拖拽边界（部件隐藏时不可拖）
        this.pTeamEdgeX = this.pShowTeam ? this.pLeftX + this.pLeftW : -1;
        this.pRightEdgeX = this.pShowRightPanel ? this.pRightX : -1;
        this.pLockEdgeY = this.pShowLock ? this.pLockY : -1;

        // 隐藏部件的占位尺寸（只用于画灰色「已隐藏」示意，不参与真实布局）
        this.pGhostLeftW = clamp(14, Math.round(this.pInnerW * 0.35F),
                Math.round(this.pInnerW * ui.teamWidthRatio));
        this.pGhostRightW = clamp(30, Math.round(this.pInnerW * 0.55F),
                Math.round(this.pInnerW * ui.rightWidthRatio));
    }

    // ------------------------------------------------------------------
    // 示意图绘制
    // ------------------------------------------------------------------

    /** 绘制整张示意图：阶段条 / 队友列 / 中央立绘 / 右侧面板 / 网格 / 锁定条 / 限制提示。 */
    private void drawPreview(GuiGraphics graphics) {
        if (this.pShowBar) {
            this.drawPreviewTopBar(graphics);
        } else {
            this.drawHiddenGhost(graphics, this.pBarX, this.pBarY, this.pBarW, this.pGhostBarH, "顶部阶段条");
        }
        if (this.pShowTeam) {
            this.drawPreviewTeam(graphics);
        } else {
            this.drawHiddenGhost(graphics, this.pInnerX + this.pMargin, this.pLeftY,
                    this.pGhostLeftW, this.pLeftH, "队友列");
        }
        this.drawPreviewPortrait(graphics);
        if (this.pShowRightPanel) {
            this.drawPreviewRightPanel(graphics);
        } else {
            this.drawHiddenGhost(graphics,
                    this.pInnerX + this.pInnerW - this.pMargin - this.pGhostRightW,
                    this.pRightY, this.pGhostRightW, this.pRightH, "右侧面板");
        }
        if (this.pShowGrid) {
            this.drawPreviewGrid(graphics);
        } else {
            this.drawHiddenGhost(graphics, this.pGridX, this.pGridY - this.pGhostGridH,
                    this.pGridW, this.pGhostGridH, "头像网格");
        }
        if (this.pShowLock) {
            this.drawPreviewLock(graphics);
        } else {
            this.drawHiddenGhost(graphics, this.pLockX, this.pLockBottom - this.pGhostLockH,
                    this.pLockW, this.pGhostLockH, "锁定条");
        }
        if (this.pShowNotice) {
            this.drawPreviewNotice(graphics);
        } else {
            this.drawHiddenGhost(graphics, this.pNoticeX, this.pNoticeY - this.pGhostNoticeH,
                    this.pNoticeW, this.pGhostNoticeH, "限制提示");
        }
    }

    /** 顶部阶段条占位：左右阵营斜切条 + 中央「选择角色 / 比分 / 倒计时」。 */
    private void drawPreviewTopBar(GuiGraphics graphics) {
        int h = this.pBarH;
        if (h <= 0) {
            return;
        }
        graphics.fill(this.pBarX, this.pBarY, this.pBarX + this.pBarW, this.pBarY + h,
                this.previewColor(COLOR_PANEL_DARK));
        this.border(graphics, this.pBarX, this.pBarY, this.pBarW, h, COLOR_BORDER);
        int sideW = clamp(18, 80, Math.round(this.pBarW * 0.24F));
        graphics.fill(this.pBarX, this.pBarY + 1, this.pBarX + sideW, this.pBarY + h - 1,
                withAlpha(COLOR_ALLY, 0xAA));
        graphics.fill(this.pBarX + this.pBarW - sideW, this.pBarY + 1,
                this.pBarX + this.pBarW, this.pBarY + h - 1, withAlpha(COLOR_ENEMY, 0xAA));
        int textY = this.pBarY + (h - 9) / 2;
        if (sideW >= 32) {
            this.drawCentered(graphics, this.truncate("我方 进攻", sideW - 4),
                    this.pBarX + sideW / 2, textY, COLOR_TEXT, false);
            this.drawCentered(graphics, this.truncate("敌方 防守", sideW - 4),
                    this.pBarX + this.pBarW - sideW / 2, textY, COLOR_TEXT, false);
        }
        int centerLeft = this.pBarX + sideW + 4;
        int centerRight = this.pBarX + this.pBarW - sideW - 4;
        if (centerRight - centerLeft >= 30) {
            String center = this.truncate("选择角色   3 : 2   01:02", centerRight - centerLeft);
            this.drawCentered(graphics, center, (centerLeft + centerRight) / 2, textY, COLOR_TEXT, false);
        }
    }

    /** 左侧队友列：标题 + 5 张队友卡（本人金色描边）。 */
    private void drawPreviewTeam(GuiGraphics graphics) {
        if (this.pLeftW <= 0 || this.pLeftH <= 0) {
            return;
        }
        this.drawPanel(graphics, this.pLeftX, this.pLeftY, this.pLeftW, this.pLeftH);
        int headerH = Math.min(10, Math.max(8, this.pLeftH / 12));
        if (this.pLeftW >= 28) {
            this.drawText(graphics, "队友", this.pLeftX + 2, this.pLeftY + 1, COLOR_DIM);
        }
        graphics.fill(this.pLeftX + 1, this.pLeftY + headerH,
                this.pLeftX + this.pLeftW - 1, this.pLeftY + headerH + 1, COLOR_BORDER);
        int cardGap = 2;
        int areaTop = this.pLeftY + headerH + 2;
        int areaBottom = this.pLeftY + this.pLeftH - 2;
        int areaH = Math.max(10, areaBottom - areaTop);
        int cardH = clamp(8, 34, (areaH - 4 * cardGap) / 5);
        int totalH = 5 * cardH + 4 * cardGap;
        int startY = areaTop + Math.max(0, (areaH - totalH) / 2);
        int cardX = this.pLeftX + 2;
        int cardW = Math.max(6, this.pLeftW - 4);
        for (int i = 0; i < 5 && i < this.agents.size(); i++) {
            int y = startY + i * (cardH + cardGap);
            if (y + cardH > areaBottom) {
                break;
            }
            StrinovaAgentRoster.Agent agent = this.agents.get(i);
            boolean self = i == 0;
            graphics.fill(cardX, y, cardX + cardW, y + cardH, self ? 0x44FFD24A : 0x33101820);
            this.border(graphics, cardX, y, cardW, cardH, self ? COLOR_GOLD : 0x33FFFFFF);
            int avatar = clamp(3, Math.max(3, cardH - 2), Math.max(3, cardW / 3));
            int avatarX = cardX + 2;
            int avatarY = y + Math.max(0, (cardH - avatar) / 2);
            if (avatar >= 5) {
                graphics.fill(avatarX, avatarY, avatarX + avatar, avatarY + avatar, 0xCC05070C);
                graphics.fill(avatarX, avatarY, avatarX + 1, avatarY + avatar, this.configAccent(agent.accentColor()));
                if (avatar >= 9) {
                    this.drawCentered(graphics, this.firstChar(agent.name()), avatarX + avatar / 2,
                            avatarY + (avatar - 9) / 2, COLOR_TEXT, false);
                }
            }
            int textX = avatarX + (avatar >= 5 ? avatar + 3 : 2);
            int textW = cardX + cardW - 2 - textX;
            if (textW >= 12 && cardH >= 11) {
                int nameY = cardH <= 12 ? y + 1 : y + cardH / 2 - 9;
                this.drawText(graphics, this.truncate("玩家" + (i + 1), textW), textX, nameY,
                        self ? COLOR_GOLD : COLOR_TEXT);
            }
            if (textW >= 12 && cardH >= 22) {
                this.drawText(graphics, this.truncate(agent.name(), textW), textX, y + cardH / 2 + 1,
                        this.configAccent(agent.accentColor()));
            }
        }
    }

    /** 中央立绘：渐变 + 巨型首字 + 底部名字条 + 简介/语录占位；关闭时画灰色「已隐藏」。 */
    private void drawPreviewPortrait(GuiGraphics graphics) {
        if (this.pCenterW <= 0 || this.pCenterH <= 0) {
            return;
        }
        this.drawPanel(graphics, this.pCenterX, this.pCenterY, this.pCenterW, this.pCenterH);
        StrinovaAgentRoster.Agent agent = this.previewAgent();
        int accent = this.previewAccent();
        int cx = this.pCenterX + this.pCenterW / 2;
        int nameBlockH = clamp(12, 30, this.pCenterH / 5);
        int charTop = this.pCenterY;
        int charBottom = this.pCenterY + this.pCenterH - nameBlockH;

        if (this.pShowPortrait) {
            if (charBottom - charTop > 8) {
                graphics.fillGradient(this.pCenterX + 1, charTop, this.pCenterX + this.pCenterW - 1,
                        charBottom, withAlpha(accent, 0x99), 0xFF05070C);
                float scale = Math.min(Math.min(this.pCenterW * 0.50F,
                        (charBottom - charTop) * 0.55F) / 9.0F, 9.0F);
                if (scale >= 1.0F) {
                    this.drawGiantChar(graphics, this.firstChar(agent.name()), cx,
                            (charTop + charBottom) / 2, scale, withAlpha(COLOR_TEXT, 0xE0));
                }
            }
            int nameTop = charBottom;
            graphics.fill(this.pCenterX, nameTop, this.pCenterX + this.pCenterW,
                    this.pCenterY + this.pCenterH, this.previewColor(COLOR_PANEL_DARK));
            graphics.fill(this.pCenterX, nameTop, this.pCenterX + this.pCenterW, nameTop + 1, accent);
            int nameColor = ui().nameColor != 0 ? ui().nameColor : COLOR_TEXT;
            float nameScale = Math.max(1.0F,
                    Math.min(1.8F, (this.pCenterW - 8.0F) / Math.max(1, this.font.width(agent.name()))));
            this.drawScaledCentered(graphics, agent.name(), cx, nameTop + 1.5F, nameScale, nameColor, true);
            if (nameBlockH >= 20 && this.pCenterW >= 50) {
                String roleLine = Component.translatable(StrinovaAgentRoster.roleNameKey(agent.role())).getString()
                        + " · "
                        + Component.translatable(StrinovaAgentRoster.factionNameKey(agent.faction())).getString();
                this.drawCentered(graphics, this.truncate(roleLine, this.pCenterW - 8), cx,
                        this.pCenterY + this.pCenterH - 10, accent, false);
            }
        } else {
            this.drawHiddenGhost(graphics, this.pCenterX + 2, this.pCenterY + 2,
                    Math.max(8, this.pCenterW - 4), Math.max(8, this.pCenterH - 4), "立绘");
        }

        // 简介 / 语录占位（可独立显隐；立绘隐藏时仍可显示）
        int infoBottom = (this.pShowPortrait ? charBottom : this.pCenterY + this.pCenterH) - 4;
        int textW = Math.max(10, this.pCenterW - 8);
        if (this.pShowQuote) {
            if (infoBottom - 10 > this.pCenterY + 2) {
                this.drawCentered(graphics, this.truncate("「" + this.previewQuote() + "」", textW),
                        cx, infoBottom - 10, 0xFFAFC3D4, false);
                infoBottom -= 11;
            }
        } else if (infoBottom - 10 > this.pCenterY + 2) {
            this.drawCentered(graphics, this.truncate("语录 · 已隐藏", textW),
                    cx, infoBottom - 10, COLOR_HIDDEN, false);
            infoBottom -= 11;
        }
        if (this.pShowIntro) {
            if (infoBottom - 10 > this.pCenterY + 2) {
                this.drawCentered(graphics, this.truncate(this.previewIntro(), textW),
                        cx, infoBottom - 10, 0xFFD8E4EE, false);
            }
        } else if (infoBottom - 10 > this.pCenterY + 2) {
            this.drawCentered(graphics, this.truncate("简介 · 已隐藏", textW),
                    cx, infoBottom - 10, COLOR_HIDDEN, false);
        }
    }

    /** 右侧信息面板：头衔块 + 身份 + 技能卡 + 武器区 + 页签（子项按配置显隐）。 */
    private void drawPreviewRightPanel(GuiGraphics graphics) {
        if (this.pRightW <= 0 || this.pRightH <= 0) {
            return;
        }
        this.drawPanel(graphics, this.pRightX, this.pRightY, this.pRightW, this.pRightH);
        StrinovaAgentRoster.Agent agent = this.previewAgent();
        int accent = this.previewAccent();
        int pad = clamp(2, 4, this.pRightW / 40);
        int x = this.pRightX + pad;
        int w = Math.max(10, this.pRightW - 2 * pad);
        int bottom = this.pRightY + this.pRightH - pad;
        int y = this.pRightY + pad;
        if (w < 12 || bottom - y < 10) {
            return;
        }

        // 1) 头衔块：定位色竖条 + 定位/阵营 + 角色名
        int headerH = clamp(10, 26, Math.round(this.pRightH * 0.10F));
        if (y + headerH <= bottom) {
            graphics.fill(this.pRightX + 1, y, this.pRightX + this.pRightW - 1, y + headerH,
                    this.previewColor(0xCC1A222E));
            graphics.fill(this.pRightX + 1, y, this.pRightX + 3, y + headerH, accent);
            int nameColor = ui().headerNameColor != 0 ? ui().headerNameColor : COLOR_TEXT;
            if (headerH >= 12 && w >= 30) {
                String roleName = Component.translatable(
                        StrinovaAgentRoster.roleNameKey(agent.role())).getString();
                this.drawText(graphics, this.truncate(roleName, w / 2), x + 4, y + 1, accent);
                String name = this.truncate(agent.name(), Math.max(10, w - 4));
                this.drawText(graphics, name,
                        this.pRightX + this.pRightW - pad - this.font.width(name), y + 1, nameColor);
            } else if (w >= 14) {
                this.drawText(graphics, this.truncate(agent.name(), w), x + 2,
                        y + (headerH - 9) / 2, nameColor);
            }
            y += headerH + 2;
        }

        // 2) 身份行
        if (y + 10 <= bottom && w >= 30) {
            String identity = Component.translatable(
                    StrinovaAgentRoster.roleDescKey(agent.role())).getString();
            this.drawText(graphics, this.truncate("身份 · " + identity, w), x, y, COLOR_DIM);
            y += 11;
        }

        // 3) 技能卡（Q/P/X/E）+ 技能名 + 描述；隐藏时画灰色占位
        if (this.pShowSkills) {
            int icon = clamp(7, 16, Math.min(w / 6, this.pRightH / 14));
            int rowTop = y;
            if (rowTop + icon <= bottom) {
                String[] keys = {"Q", "P", "X", "E"};
                for (int i = 0; i < keys.length; i++) {
                    int iconX = x + i * (icon + 2);
                    if (iconX + icon > x + w) {
                        break;
                    }
                    boolean selected = i == 0;
                    graphics.fill(iconX, rowTop, iconX + icon, rowTop + icon,
                            selected ? withAlpha(accent, 0x88) : 0xCC05070C);
                    this.border(graphics, iconX, rowTop, icon, icon,
                            selected ? accent : withAlpha(accent, 0x55));
                    if (icon >= 9) {
                        this.drawCentered(graphics, keys[i], iconX + icon / 2,
                                rowTop + (icon - 9) / 2, selected ? 0xFF05070C : COLOR_DIM, false);
                    }
                }
                y = rowTop + icon + 2;
            }
            if (y + 10 <= bottom && w >= 20) {
                this.drawText(graphics, this.truncate("Q 主动 · 技能名称", w), x, y, COLOR_TEXT);
                y += 11;
            }
            for (int i = 0; i < 2; i++) {
                if (y + 10 > bottom || w < 20) {
                    break;
                }
                this.drawText(graphics, this.truncate(
                        i == 0 ? "技能描述占位：自动攻击一定范围内的敌人" : "并使其减速，持续 3 秒", w), x, y, COLOR_DIM);
                y += 11;
            }
        } else {
            int ghostH = clamp(12, 24, Math.max(10, (bottom - y) / 3));
            if (ghostH >= 8) {
                this.drawHiddenGhost(graphics, x, y, w, ghostH, "技能卡");
                y += ghostH + 2;
            }
        }

        // 4) 武器区 + 底部页签（页签固定在面板底部）
        int tabH = this.pShowTabs ? clamp(8, 18, Math.round(this.pRightH * 0.09F)) : 0;
        int tabY = bottom - tabH;
        int weaponBottom = this.pShowTabs ? tabY - 2 : bottom;
        int weaponTop = Math.max(y + 1,
                weaponBottom - clamp(20, 66, Math.round(this.pRightH * 0.30F)));
        if (this.pShowWeapon) {
            this.drawPreviewWeapon(graphics, x, weaponTop, w, weaponBottom, accent);
        } else if (weaponBottom - weaponTop >= 10) {
            this.drawHiddenGhost(graphics, x, weaponTop, w, weaponBottom - weaponTop, "武器区");
        }
        if (this.pShowTabs && tabH >= 8 && w >= 24) {
            int tabW = Math.max(10, (w - 2) / 2);
            this.drawPreviewTab(graphics, x, tabY, tabW, tabH, "武器选择", true, accent);
            this.drawPreviewTab(graphics, x + tabW + 2, tabY,
                    Math.max(8, Math.min(tabW, x + w - (x + tabW + 2))), tabH, "外观选择", false, accent);
        } else if (!this.pShowTabs) {
            int ghostH = Math.max(8, Math.min(14, bottom - tabY));
            this.drawHiddenGhost(graphics, x, bottom - ghostH, w, ghostH, "页签");
        }
    }

    /** 武器区占位：主武器大卡 + 2×2 小卡（副武器/近战/战术道具×2）。 */
    private void drawPreviewWeapon(GuiGraphics graphics, int x, int top, int w, int bottom, int accent) {
        int areaH = bottom - top;
        if (areaH < 12 || w < 12) {
            return;
        }
        int mainH = Math.max(8, (areaH - 6) / 2);
        int mainBottom = top + mainH;
        graphics.fill(x, top, x + w, mainBottom, 0x40000000);
        this.border(graphics, x, top, w, mainH, withAlpha(accent, 0x88));
        if (w >= 30 && mainH >= 11) {
            this.drawText(graphics, this.truncate("1 主武器", w - 6), x + 3, top + 1, COLOR_DIM);
        }
        if (mainH >= 10) {
            int lineY = top + mainH - 5;
            graphics.fill(x + w / 4, lineY, x + w - w / 4, lineY + 2, withAlpha(accent, 0xCC));
        }
        int smallTop = mainBottom + 2;
        int gap = 2;
        String[] labels = {"2 副武器", "3 近战武器", "4 战术道具", "5 战术道具"};
        int cellW = Math.max(6, (w - gap) / 2);
        int cellH = Math.max(5, (bottom - smallTop - gap) / 2);
        for (int i = 0; i < labels.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int cellX = x + col * (cellW + gap);
            int cellY = smallTop + row * (cellH + gap);
            if (cellY + cellH > bottom) {
                break;
            }
            graphics.fill(cellX, cellY, cellX + cellW, cellY + cellH, 0x33000000);
            this.border(graphics, cellX, cellY, cellW, cellH, 0x33FFFFFF);
            if (cellW >= 20 && cellH >= 10) {
                this.drawText(graphics, this.truncate(labels[i], cellW - 4), cellX + 2, cellY + 1, COLOR_DIM);
            }
        }
    }

    /** 底部页签示意图。 */
    private void drawPreviewTab(GuiGraphics graphics, int x, int y, int w, int h,
                                String label, boolean active, int accent) {
        if (w <= 2 || h <= 2) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, active ? withAlpha(accent, 0xB0) : 0x55000000);
        this.border(graphics, x, y, w, h, active ? accent : 0x44FFFFFF);
        if (w >= 24) {
            this.drawCentered(graphics, this.truncate(label, w - 4), x + w / 2,
                    y + (h - 9) / 2, active ? 0xFF05070C : COLOR_DIM, false);
        }
    }

    /** 底部 2×12 头像网格。 */
    private void drawPreviewGrid(GuiGraphics graphics) {
        if (!this.pShowGrid || this.pGridH <= 0 || this.pGridW <= 0) {
            return;
        }
        this.drawPanel(graphics, this.pGridX, this.pGridY, this.pGridW, this.pGridH);
        int selectedIndex = this.previewSelectedIndex();
        int realGridW = GRID_COLUMNS * this.pCellW + (GRID_COLUMNS - 1) * this.pCellGap;
        int startX = this.pGridX + Math.max(0, (this.pGridW - realGridW) / 2);
        int gridColor = ui().gridCellColor;
        for (int i = 0; i < this.agents.size(); i++) {
            int row = i / GRID_COLUMNS;
            int col = i % GRID_COLUMNS;
            if (row >= GRID_ROWS) {
                break;
            }
            int cellX = startX + col * (this.pCellW + this.pCellGap);
            int cellY = this.pGridY + 1 + row * (this.pCellH + this.pCellGap);
            if (cellY + this.pCellH > this.pGridY + this.pGridH) {
                break;
            }
            StrinovaAgentRoster.Agent agent = this.agents.get(i);
            int cellAccent = gridColor != 0 ? gridColor : agent.accentColor();
            boolean selected = i == selectedIndex;
            graphics.fill(cellX, cellY, cellX + this.pCellW, cellY + this.pCellH,
                    selected ? withAlpha(cellAccent, 0xCC) : withAlpha(cellAccent, 0x66));
            this.border(graphics, cellX, cellY, this.pCellW, this.pCellH,
                    selected ? 0xFFFFFFFF : withAlpha(cellAccent, 0xBB));
            if (this.pCellW >= 8 && this.pCellH >= 11) {
                this.drawCentered(graphics, this.firstChar(agent.name()),
                        cellX + this.pCellW / 2, cellY + (this.pCellH - 9) / 2,
                        selected ? 0xFF05070C : COLOR_TEXT, false);
            }
        }
    }

    /** 锁定条示意图（红底 + 定位色描边 + 「锁定角色」）。 */
    private void drawPreviewLock(GuiGraphics graphics) {
        if (!this.pShowLock || this.pLockW <= 0 || this.pLockH <= 0) {
            return;
        }
        graphics.fill(this.pLockX, this.pLockY, this.pLockX + this.pLockW, this.pLockY + this.pLockH,
                withAlpha(COLOR_ALLY, 0xCC));
        this.border(graphics, this.pLockX, this.pLockY, this.pLockW, this.pLockH, this.previewAccent());
        if (this.pLockW >= 30 && this.pLockH >= 11) {
            this.drawCentered(graphics, this.truncate("锁定角色", this.pLockW - 6),
                    this.pLockX + this.pLockW / 2, this.pLockY + (this.pLockH - 9) / 2, 0xFFFFF2F2, false);
        }
    }

    /** 底部限制提示占位。 */
    private void drawPreviewNotice(GuiGraphics graphics) {
        if (!this.pShowNotice || this.pNoticeH < 8) {
            return;
        }
        String text = "限制提示：进攻方无法选择欧泊角色";
        this.drawCentered(graphics, this.truncate(text, this.pNoticeW - 4),
                this.pNoticeX + this.pNoticeW / 2, this.pNoticeY + (this.pNoticeH - 9) / 2, COLOR_DIM, false);
    }

    // ------------------------------------------------------------------
    // 隐藏占位 / 拖拽边界
    // ------------------------------------------------------------------

    /** 隐藏部件：灰色虚线框 + 居中「已隐藏」文字（空间不足时自动省略名称）。 */
    private void drawHiddenGhost(GuiGraphics graphics, int x, int y, int w, int h, String label) {
        if (w < 8 || h < 8) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, 0x1A808A96);
        int borderColor = 0x665A6672;
        for (int dx = 0; dx < w; dx += 4) {
            int x2 = Math.min(x + dx + 2, x + w);
            graphics.fill(x + dx, y, x2, y + 1, borderColor);
            graphics.fill(x + dx, y + h - 1, x2, y + h, borderColor);
        }
        for (int dy = 0; dy < h; dy += 4) {
            int y2 = Math.min(y + dy + 2, y + h);
            graphics.fill(x, y + dy, x + 1, y2, borderColor);
            graphics.fill(x + w - 1, y + dy, x + w, y2, borderColor);
        }
        if (h < 11) {
            return;
        }
        String text = null;
        String[] candidates = {label + " · 已隐藏", label + " 已隐藏", "已隐藏"};
        for (String candidate : candidates) {
            if (this.font.width(candidate) <= w - 4) {
                text = candidate;
                break;
            }
        }
        if (text != null) {
            int textX = x + (w - this.font.width(text)) / 2;
            int textY = y + (h - 9) / 2;
            graphics.fill(textX - 2, textY - 1, textX + this.font.width(text) + 2, textY + 10, 0x99050A12);
            graphics.drawString(this.font, text, textX, textY, COLOR_HIDDEN, false);
        }
    }

    /** 绘制三条可拖拽边界：平时淡显，鼠标靠近/拖拽时高亮青色。 */
    private void drawBoundaries(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.pShowTeam) {
            this.drawVerticalBoundary(graphics, this.pTeamEdgeX, this.pLeftY, this.pLeftH,
                    mouseX, mouseY, 1);
        }
        if (this.pShowRightPanel) {
            this.drawVerticalBoundary(graphics, this.pRightEdgeX, this.pRightY, this.pRightH,
                    mouseX, mouseY, 2);
        }
        if (this.pShowLock) {
            this.drawHorizontalBoundary(graphics, this.pLockX, this.pLockW, this.pLockEdgeY,
                    mouseX, mouseY);
        }
    }

    /** 竖向拖拽边界（队友列右边界 / 右侧面板左边界）。 */
    private void drawVerticalBoundary(GuiGraphics graphics, int x, int y, int h,
                                      int mouseX, int mouseY, int mode) {
        if (x < 0 || h <= 0) {
            return;
        }
        boolean hover = Math.abs(mouseX - x) <= 5 && mouseY >= y && mouseY <= y + h;
        boolean active = this.dragMode == mode;
        int color = (hover || active) ? COLOR_HIGHLIGHT : 0x44FFFFFF;
        graphics.fill(x, y, x + 1, y + h, color);
        int handle = (hover || active) ? 0xAA35D6E8 : 0x33FFFFFF;
        graphics.fill(x - 2, y + 2, x + 3, y + 9, handle);
        graphics.fill(x - 2, y + h - 9, x + 3, y + h - 2, handle);
    }

    /** 横向拖拽边界（锁定条上边界）。 */
    private void drawHorizontalBoundary(GuiGraphics graphics, int x, int w, int y,
                                        int mouseX, int mouseY) {
        if (y < 0 || w <= 0) {
            return;
        }
        boolean hover = Math.abs(mouseY - y) <= 5 && mouseX >= x && mouseX <= x + w;
        boolean active = this.dragMode == 3;
        int color = (hover || active) ? COLOR_HIGHLIGHT : 0x44FFFFFF;
        graphics.fill(x, y, x + w, y + 1, color);
        int handle = (hover || active) ? 0xAA35D6E8 : 0x33FFFFFF;
        graphics.fill(x + 2, y - 2, x + 9, y + 3, handle);
        graphics.fill(x + w - 9, y - 2, x + w - 2, y + 3, handle);
    }

    // ------------------------------------------------------------------
    // 鼠标交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        this.layoutPreview();
        // 队友列右边界
        if (this.pTeamEdgeX >= 0 && Math.abs(mouseX - this.pTeamEdgeX) <= 5
                && mouseY >= this.pLeftY && mouseY <= this.pLeftY + this.pLeftH) {
            this.dragMode = 1;
            return true;
        }
        // 右侧面板左边界
        if (this.pRightEdgeX >= 0 && Math.abs(mouseX - this.pRightEdgeX) <= 5
                && mouseY >= this.pRightY && mouseY <= this.pRightY + this.pRightH) {
            this.dragMode = 2;
            return true;
        }
        // 锁定条上边界
        if (this.pLockEdgeY >= 0 && Math.abs(mouseY - this.pLockEdgeY) <= 5
                && mouseX >= this.pLockX && mouseX <= this.pLockX + this.pLockW) {
            this.dragMode = 3;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragMode == 0 || button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        StrinovaAgentSelectUi ui = ui();
        this.layoutPreview();
        int innerWidth = Math.max(1, this.pInnerW);
        switch (this.dragMode) {
            case 1 -> ui.teamWidthRatio = clampf(
                    (float) ((mouseX - this.pLeftX) / innerWidth), 0.06F, 0.24F);
            case 2 -> ui.rightWidthRatio = clampf(
                    (float) ((this.pInnerX + this.pInnerW - mouseX) / innerWidth), 0.16F, 0.36F);
            case 3 -> ui.lockHeight = clampf(
                    (float) ((this.pLockBottom - mouseY) / Math.max(0.001F, this.previewScale)),
                    18.0F, 40.0F);
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragMode = 0;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 悬停显隐按钮时在按钮上方显示完整名称与状态。 */
    private void drawToggleTip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.toggleButtons.isEmpty()) {
            return;
        }
        if (mouseY < this.row1Y || mouseY >= this.row1Y + BUTTON_H) {
            return;
        }
        Toggle[] toggles = Toggle.values();
        for (int i = 0; i < this.toggleButtons.size() && i < toggles.length; i++) {
            Button toggleButton = this.toggleButtons.get(i);
            if (mouseX < toggleButton.getX() || mouseX >= toggleButton.getX() + toggleButton.getWidth()) {
                continue;
            }
            boolean on = toggles[i].get(ui());
            String text = toggles[i].label + "：" + (on ? "开" : "关") + "（点击切换）";
            this.drawTip(graphics, text, mouseX, this.row1Y);
            return;
        }
    }

    /** 悬停颜色按钮时在按钮上方显示完整说明（颜色项 + 当前色名 + 循环切换提示）。 */
    private void drawColorTip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.colorButtons.isEmpty()) {
            return;
        }
        ColorTarget[] targets = ColorTarget.values();
        for (int i = 0; i < this.colorButtons.size() && i < targets.length; i++) {
            Button colorButton = this.colorButtons.get(i);
            if (mouseX < colorButton.getX() || mouseX >= colorButton.getX() + colorButton.getWidth()
                    || mouseY < colorButton.getY() || mouseY >= colorButton.getY() + colorButton.getHeight()) {
                continue;
            }
            String text = targets[i].label + "：" + colorName(targets[i].get(ui())) + "（点击循环切换）";
            this.drawTip(graphics, text, mouseX, colorButton.getY());
            return;
        }
    }

    /** 在鼠标附近（按钮上方）画一行提示框；贴边时自动向内偏移。 */
    private void drawTip(GuiGraphics graphics, String text, int mouseX, int buttonY) {
        int boxW = this.font.width(text) + 8;
        int boxX = clamp(4, Math.max(4, this.width - boxW - 4), mouseX + 8);
        int boxY = Math.max(4, buttonY - 14);
        graphics.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + 11, 0xFF2A3542);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 10, 0xF00C121A);
        graphics.drawString(this.font, text, boxX + 4, boxY + 1, COLOR_TEXT, false);
    }

    // ------------------------------------------------------------------
    // 数据查询与小工具
    // ------------------------------------------------------------------

    /** 当前可视化自定义配置（导入后会替换对象，因此每次现取）。 */
    private static StrinovaAgentSelectUi ui() {
        return StrinovaHudConfig.get().agentSelectUi;
    }

    /** 示意图当前选中角色（与真实界面一致：读取配置 selectedAgentId）。 */
    private int previewSelectedIndex() {
        StrinovaAgentRoster.Agent selected = StrinovaAgentRoster.byId(StrinovaHudConfig.get().selectedAgentId);
        int index = selected == null ? -1 : this.agents.indexOf(selected);
        return index < 0 ? 0 : index;
    }

    private StrinovaAgentRoster.Agent previewAgent() {
        if (this.agents.isEmpty()) {
            return StrinovaAgentRoster.defaultAgent();
        }
        return this.agents.get(this.previewSelectedIndex());
    }

    /** 示意图强调色：accentColor 覆盖时统一使用该色，否则取选中角色定位色。 */
    private int previewAccent() {
        int override = ui().accentColor;
        if (override != 0) {
            return override;
        }
        StrinovaAgentRoster.Agent agent = this.previewAgent();
        return agent == null ? COLOR_ENEMY : agent.accentColor();
    }

    /** 指定颜色是否被 accentColor 覆盖。 */
    private int configAccent(int base) {
        int override = ui().accentColor;
        return override != 0 ? override : base;
    }

    private String previewIntro() {
        StrinovaWikiData.Agent wiki = StrinovaWikiData.byId(this.previewAgent().id());
        if (wiki != null && wiki.intro() != null && !wiki.intro().isEmpty()) {
            return wiki.intro();
        }
        return "简介占位：守护 · 欧泊 · 为队伍构筑防线";
    }

    private String previewQuote() {
        StrinovaWikiData.Agent wiki = StrinovaWikiData.byId(this.previewAgent().id());
        if (wiki != null && wiki.quote() != null && !wiki.quote().isEmpty()) {
            return wiki.quote();
        }
        return "语录占位：打起精神来，游戏还没有结束呢！";
    }

    /** 取字符串首字（头像方块文字）；空串返回 "?"。 */
    private static String firstChar(String text) {
        if (text == null || text.isEmpty()) {
            return "?";
        }
        return text.substring(0, 1);
    }

    /** 文本超宽时截断并追加 ".."。 */
    private String truncate(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "..";
        int keep = Math.max(0, maxWidth - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, keep) + ellipsis;
    }

    /** 预览面板颜色按配置不透明度缩放 alpha（示意图实时反映不透明度）。 */
    private int previewColor(int color) {
        float opacity = Math.max(0.20F, Math.min(1.0F, ui().opacity));
        if (opacity >= 0.999F) {
            return color;
        }
        int alpha = Math.round(((color >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** 替换颜色 alpha 通道（保留 RGB）。 */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampf(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    // 通用绘制辅助
    // ------------------------------------------------------------------

    /** 面板：半透明深底 + 描边（alpha 随配置不透明度变化）。 */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + h, this.previewColor(COLOR_PANEL));
        this.border(graphics, x, y, w, h, COLOR_BORDER);
    }

    /** 1px 矩形描边。 */
    private void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 左对齐文本（无阴影）。 */
    private void drawText(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color, false);
    }

    /** 水平居中文本。 */
    private void drawCentered(GuiGraphics graphics, String text, int centerX, int y,
                              int color, boolean shadow) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, shadow);
    }

    /** 缩放文本（左上角对齐 x/y）。 */
    private void drawScaledText(GuiGraphics graphics, String text, float x, float y, float scale,
                                int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    /** 缩放文本（以 centerX 水平居中，y 为顶部）。 */
    private void drawScaledCentered(GuiGraphics graphics, String text, int centerX, float y,
                                    float scale, int color, boolean shadow) {
        float width = this.font.width(text) * scale;
        this.drawScaledText(graphics, text, centerX - width / 2.0F, y, scale, color, shadow);
    }

    /** 巨型首字（中央立绘占位）：以 (centerX, centerY) 为中心绘制缩放单字。 */
    private void drawGiantChar(GuiGraphics graphics, String text, int centerX, int centerY,
                               float scale, int color) {
        int width = this.font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, -width / 2, -4, color, false);
        graphics.pose().popPose();
    }
}
