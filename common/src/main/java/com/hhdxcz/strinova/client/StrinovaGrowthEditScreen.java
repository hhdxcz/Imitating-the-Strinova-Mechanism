package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;
import com.hhdxcz.strinova.client.hud.StrinovaGrowthState;
import com.hhdxcz.strinova.client.hud.StrinovaGrowthUi;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import com.hhdxcz.strinova.client.hud.StrinovaWikiData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 「弦能增幅网络」界面可视化自定义编辑器（与 TAB 计分板编辑器同一套交互习惯）。
 *
 * <ul>
 *   <li>中区按真实比例绘制界面示意图：标题条 / 满高左立绘 / 中间 2×4 增幅网络 + 底部 3 觉醒卡
 *       （含觉醒 2/3「切换激活」按钮）/ 右侧武器栏（1/2/3 整列宽 + 4/5 底部并排），
 *       并示意分类标题刷新图标、锁定灰显、武器切换图标；</li>
 *   <li>8 条可拖拽分割线（青色高亮、实时改配置、按配置类范围 clamp）：
 *       竖线 = 立绘右边界 → {@code portraitWidthRatio}、网络列分界 → {@code networkSplitRatio}、
 *       武器栏左边界 → {@code weaponsWidthRatio}；
 *       横线 = 标题栏底边 → {@code headerHeightRatio}、网络 1/2 与 2/3 与 3/4 行分隔线 →
 *       相邻两行 {@code networkRowWeights}（总和保持不变）、觉醒区上边界 → {@code awakenHeightRatio}；</li>
 *   <li>底部按钮：6 个显隐开关 + 不透明度 / 列间距 / 行间距微调 + 4 个颜色循环按钮
 *       （主色 / 升级项文字 / 价格 / 觉醒名称，点击按「默认→白→金→青→品红→绿→橙→紫→默认」循环）
 *       + 能量点 -100 / +100 / 重置购买 + 重置 / 保存 / 返回；</li>
 *   <li>能量点：参数行显示当前 {@link StrinovaGrowthState#energyPoints()}；-/+ 写回
 *       {@code growthEnergyPoints}（0~9999）并调用 {@link StrinovaGrowthState#setEnergyPoints(int)}
 *       实时生效；「重置购买」调用 {@link StrinovaGrowthState#reset()} 清空已购并恢复配置值；</li>
 *   <li>打开时用 {@link StrinovaHudConfig#growthExportJson()} 记录快照，ESC / 返回时用
 *       {@link StrinovaHudConfig#growthImportJson(String)} 还原；保存写入客户端配置。</li>
 * </ul>
 *
 * <p>常规窗口下按钮自适应排成两行（11+11），窄窗口自动换 3~5 行；示意图等比缩放并隐藏次要文字，
 * 320×240 不崩溃。</p>
 */
public class StrinovaGrowthEditScreen extends Screen {

    // ==================== 配色（与增幅网络界面一致） ====================
    private static final int COLOR_TITLE = 0xFFEAF2FA;
    private static final int COLOR_INFO = 0xFF9BAEBF;
    private static final int COLOR_HIDDEN = 0xFF8A94A0;
    private static final int COLOR_CYAN = 0xFF35D6E8;
    private static final int COLOR_GOLD = 0xFFFFD166;
    private static final int COLOR_MAGENTA = 0xFFFF4FA3;
    private static final int COLOR_TEXT = 0xFFEAF2FA;
    private static final int COLOR_TEXT_DIM = 0xFF9AA8B8;
    private static final int COLOR_TEXT_DISABLED = 0xFF5E6874;
    private static final int COLOR_DIAMOND = 0xFF4FA3FF;

    /** 增幅网络 8 个分支的示意强调色（与界面 CATEGORY_ACCENTS 一致）。 */
    private static final int[] PREVIEW_ACCENTS = {
            0xFF35D6E8, 0xFFFFD166, 0xFF54E0A0, 0xFF4FA3FF,
            0xFF9B6BFF, 0xFFFF9F43, 0xFFFF4FA3, 0xFF6ED0FF
    };
    /** wiki 数据不可用时类别标题占位。 */
    private static final String[] CATEGORY_FALLBACK_LABELS = {
            "输出", "暴击", "续航", "精准", "咒法", "踏空", "护甲", "弦化"
    };
    /** 武器槽类型（1 主武器 / 2 副武器 / 3 近战 / 4~5 战术道具）。 */
    private static final String[] WEAPON_SLOT_LABELS = {"主武器", "副武器", "近战", "战术道具", "战术道具"};
    /** 武器槽数量。 */
    private static final int WEAPON_SLOT_COUNT = 5;
    /** 每列最多 4 个成长分支（8 分支 → 2 列 × 4 行）。 */
    private static final int CATEGORY_ROWS = 4;
    /** 成长类别标题区高度（与界面一致）。 */
    private static final int ITEM_HEADER_HEIGHT = 12;

    // ==================== 颜色切换预设（点击按钮按顺序循环） ====================
    /** 预设颜色：0 表示「默认」（使用界面原有颜色）。 */
    private static final int[] COLOR_PRESETS = {
            0, 0xFFFFFFFF, 0xFFFFD166, 0xFF35D6E8, 0xFFFF4FA3, 0xFF54E0A0, 0xFFFF8A5C, 0xFF9B6BFF
    };
    /** 预设颜色名称（与 COLOR_PRESETS 一一对应）。 */
    private static final String[] COLOR_PRESET_NAMES = {
            "默认", "白", "金", "青", "品红", "绿", "橙", "紫"
    };

    // ==================== 拖拽边界（8 条可调分割线） ====================
    private static final int DRAG_NONE = 0;
    /** 竖线：立绘右边界 → portraitWidthRatio。 */
    private static final int DRAG_PORTRAIT = 1;
    /** 竖线：增幅网络左右列分隔 → networkSplitRatio。 */
    private static final int DRAG_NETWORK_SPLIT = 2;
    /** 竖线：武器栏左边界 → weaponsWidthRatio。 */
    private static final int DRAG_WEAPONS = 3;
    /** 横线：标题栏底边 → headerHeightRatio。 */
    private static final int DRAG_HEADER = 4;
    /** 横线：增幅网络第 1/2 行之间 → networkRowWeights[0]/[1]。 */
    private static final int DRAG_ROW_12 = 5;
    /** 横线：增幅网络第 2/3 行之间 → networkRowWeights[1]/[2]。 */
    private static final int DRAG_ROW_23 = 6;
    /** 横线：增幅网络第 3/4 行之间 → networkRowWeights[2]/[3]。 */
    private static final int DRAG_ROW_34 = 7;
    /** 横线：觉醒区上边界 → awakenHeightRatio。 */
    private static final int DRAG_AWAKENINGS = 8;

    private static final int BUTTON_COUNT = 22;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_ROW_GAP = 4;
    /** 颜色按钮对应的配置字段：0 主色 / 1 升级项文字 / 2 价格 / 3 觉醒名称。 */
    private static final int COLOR_BUTTON_COUNT = 4;

    private final Screen parent;
    private boolean saved;
    private boolean snapshotCaptured;
    private String snapshotJson = "";

    /** 0~5：标题 / 立绘 / 数值 / 身份 / 武器 / 觉醒（用于重置后刷新按钮文案）。 */
    private final Button[] toggleButtons = new Button[6];
    /** 0~3：主色 / 升级项文字 / 价格 / 觉醒名称（用于刷新当前色名）。 */
    private final Button[] colorButtons = new Button[COLOR_BUTTON_COUNT];

    // ==================== 编辑器布局 ====================
    private int buttonColumns = 10;
    private int buttonWidth = 64;
    private int buttonsTop;

    private int previewLeft;
    private int previewTop;
    private int previewRight;
    private int previewBottom;
    private double previewScale = 1.0D;
    private int previewOffsetX;
    private int previewOffsetY;

    private PreviewLayout preview = PreviewLayout.compute(320, 240, new StrinovaGrowthUi());
    private StrinovaAgentRoster.Agent agent;
    private StrinovaWikiData.Agent wiki;

    // ==================== 拖拽状态 ====================
    private int dragging = DRAG_NONE;
    private int hoveredBoundary = DRAG_NONE;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private float dragStartRatio;
    /** 行分隔线拖拽：起始 4 行权重备份 / 网络总权重 / 网络可用高度（虚拟 px）。 */
    private final float[] dragStartRowWeights = new float[CATEGORY_ROWS];
    private int dragStartGridUsable;
    private float dragStartTotalWeight;

    /** 底部操作反馈文案（2 秒内显示在参数行下方，如「能量 330」「已重置购买」）。 */
    private String statusText = "";
    private long statusExpireAt;

    public StrinovaGrowthEditScreen(@Nullable Screen parent) {
        super(Component.literal("弦能增幅网络 · 界面自定义"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!this.snapshotCaptured) {
            // 打开时记录快照：ESC / 返回时整体还原（与计分板编辑器「放弃修改」一致）
            this.snapshotJson = StrinovaHudConfig.get().growthExportJson();
            this.snapshotCaptured = true;
        }
        this.saved = false;
        this.dragging = DRAG_NONE;
        this.hoveredBoundary = DRAG_NONE;
        this.clearWidgets();
        this.resolveAgent();
        // 直接通过命令打开编辑器时成长状态可能尚未初始化：先让当前能量点对齐配置值，便于预览
        if (!StrinovaGrowthState.hasData()) {
            StrinovaGrowthState.applyConfiguredEnergy();
        }
        this.computeChrome();
        this.createButtons();
    }

    /** 解析当前选角（只读，不修改成长状态）。 */
    private void resolveAgent() {
        StrinovaAgentRoster.Agent selected = StrinovaAgentRoster.byId(StrinovaHudConfig.get().selectedAgentId);
        this.agent = selected != null ? selected : StrinovaAgentRoster.defaultAgent();
        this.wiki = this.agent == null ? null : StrinovaWikiData.byId(this.agent.id());
    }

    /** 计算按钮区 / 示意图区域：按钮过多或窗口过窄时自动换行，示意图等比缩放到剩余区域。 */
    private void computeChrome() {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        int usable = Math.max(60, this.width - 12);
        // 按「按钮宽度尽量 >= 58px（长文案「升级项：默认」不裁切）」挑选列数：
        // 宽屏优先两行（11+11），窄屏自动换 3~5 行；再窄时保底 3 列。
        int columns = 0;
        for (int candidate = 11; candidate >= 3; candidate--) {
            int candidateRows = (BUTTON_COUNT + candidate - 1) / candidate;
            int balanced = (BUTTON_COUNT + candidateRows - 1) / candidateRows;
            int width = (usable - BUTTON_ROW_GAP * (balanced - 1)) / balanced;
            if (width >= 58) {
                columns = balanced;
                break;
            }
        }
        if (columns == 0) {
            columns = 3;
        }
        int rows = (BUTTON_COUNT + columns - 1) / columns;
        this.buttonColumns = columns;
        this.buttonWidth = clamp((usable - BUTTON_ROW_GAP * (columns - 1)) / columns, 34, 72);
        this.buttonsTop = this.height - 6 - rows * BUTTON_HEIGHT - (rows - 1) * BUTTON_ROW_GAP;
        this.previewLeft = 6;
        this.previewRight = Math.max(this.previewLeft + 40, this.width - 6);
        // 顶部固定 4 行文字（标题 / 两行参数 / 拖动提示），状态或「已隐藏」占第 5 行
        this.previewTop = (hasHiddenParts(ui) || this.statusVisible()) ? 66 : 54;
        this.previewBottom = Math.max(this.previewTop + 10, this.buttonsTop - 6);
    }

    /** 创建底部按钮：6 个显隐开关、不透明度 ±、列间距 ±、行间距 ±、4 个颜色循环、能量点 -100/+100、重置购买、重置、保存、返回。 */
    private void createButtons() {
        java.util.Arrays.fill(this.toggleButtons, null);
        java.util.Arrays.fill(this.colorButtons, null);
        String[] labels = buttonLabels(StrinovaHudConfig.get().growthUi);
        for (int index = 0; index < BUTTON_COUNT; index++) {
            int row = index / this.buttonColumns;
            int column = index % this.buttonColumns;
            int countInRow = Math.min(this.buttonColumns, BUTTON_COUNT - row * this.buttonColumns);
            int rowWidth = countInRow * this.buttonWidth + (countInRow - 1) * BUTTON_ROW_GAP;
            int startX = (this.width - rowWidth) / 2;
            int x = startX + column * (this.buttonWidth + BUTTON_ROW_GAP);
            int y = this.buttonsTop + row * (BUTTON_HEIGHT + BUTTON_ROW_GAP);
            int actionIndex = index;
            Button button = Button.builder(Component.literal(labels[index]), b -> this.handleButton(actionIndex))
                    .bounds(x, y, this.buttonWidth, BUTTON_HEIGHT)
                    .build();
            this.addRenderableWidget(button);
            if (index < 6) {
                this.toggleButtons[index] = button;
            } else if (index >= 12 && index < 12 + COLOR_BUTTON_COUNT) {
                this.colorButtons[index - 12] = button;
            }
        }
        this.refreshDynamicLabels();
    }

    private static String[] buttonLabels(StrinovaGrowthUi ui) {
        return new String[]{
                "标题：" + onOff(ui.showHeader),
                "立绘：" + onOff(ui.showPortrait),
                "数值：" + onOff(ui.showStats),
                "身份：" + onOff(ui.showIdentity),
                "武器：" + onOff(ui.showWeapons),
                "觉醒：" + onOff(ui.showAwakenings),
                "不透明度 -", "不透明度 +",
                "列间距 -", "列间距 +",
                "行间距 -", "行间距 +",
                "主色：" + colorName(ui.accentColor),
                "升级项：" + colorName(ui.itemTextColor),
                "价格：" + colorName(ui.priceColor),
                "觉醒名：" + colorName(ui.awakenNameColor),
                "能量 -100", "能量 +100", "重置购买",
                "重置", "保存", "返回"
        };
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }

    /** 颜色名：命中预设返回中文色名，导入的自定义色值显示「其他」。 */
    private static String colorName(int color) {
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i] == color) {
                return COLOR_PRESET_NAMES[i];
            }
        }
        return "其他";
    }

    /** 点击颜色按钮：按预设顺序循环（默认 → 白 → 金 → 青 → 品红 → 绿 → 橙 → 紫 → 默认）。 */
    private static int nextColor(int current) {
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i] == current) {
                return COLOR_PRESETS[(i + 1) % COLOR_PRESETS.length];
            }
        }
        // 非预设颜色（如命令导入的自定义色）→ 先回到「默认」
        return COLOR_PRESETS[0];
    }

    private void handleButton(int index) {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        switch (index) {
            case 0 -> ui.showHeader = !ui.showHeader;
            case 1 -> ui.showPortrait = !ui.showPortrait;
            case 2 -> ui.showStats = !ui.showStats;
            case 3 -> ui.showIdentity = !ui.showIdentity;
            case 4 -> ui.showWeapons = !ui.showWeapons;
            case 5 -> ui.showAwakenings = !ui.showAwakenings;
            case 6 -> ui.opacity = clampf(ui.opacity - 0.02F, 0.20F, 1.0F);
            case 7 -> ui.opacity = clampf(ui.opacity + 0.02F, 0.20F, 1.0F);
            case 8 -> ui.columnGap = clampf(ui.columnGap - 1.0F, 0.0F, 32.0F);
            case 9 -> ui.columnGap = clampf(ui.columnGap + 1.0F, 0.0F, 32.0F);
            case 10 -> ui.rowGap = clampf(ui.rowGap - 1.0F, 0.0F, 32.0F);
            case 11 -> ui.rowGap = clampf(ui.rowGap + 1.0F, 0.0F, 32.0F);
            case 12 -> ui.accentColor = nextColor(ui.accentColor);
            case 13 -> ui.itemTextColor = nextColor(ui.itemTextColor);
            case 14 -> ui.priceColor = nextColor(ui.priceColor);
            case 15 -> ui.awakenNameColor = nextColor(ui.awakenNameColor);
            case 16 -> this.adjustEnergy(-100);
            case 17 -> this.adjustEnergy(100);
            case 18 -> {
                // 重置购买：清空已购并把当前能量恢复为配置值（界面立即刷新）
                StrinovaGrowthState.reset();
                this.showStatus("已重置购买，能量 " + StrinovaGrowthState.energyPoints());
            }
            case 19 -> ui.reset();
            case 20 -> {
                this.saveAndClose();
                return;
            }
            case 21 -> {
                this.restoreAndClose();
                return;
            }
            default -> {
                // 不存在的按钮索引
            }
        }
        ui.sanitize();
        this.refreshDynamicLabels();
        this.computeChrome();
    }

    /** 显示一条 2 秒的底部操作反馈（如「能量 330」「已重置购买」）。 */
    private void showStatus(String text) {
        this.statusText = text;
        this.statusExpireAt = System.currentTimeMillis() + 2000L;
    }

    private boolean statusVisible() {
        return this.statusText != null && !this.statusText.isEmpty()
                && System.currentTimeMillis() < this.statusExpireAt;
    }

    /** 能量点 -100 / +100：写回 config.growthEnergyPoints（0~9999）并立即更新当前能量（界面实时刷新）。 */
    private void adjustEnergy(int delta) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int value = clamp(config.growthEnergyPoints + delta, 0, StrinovaGrowthState.MAX_ENERGY);
        config.growthEnergyPoints = value;
        StrinovaGrowthState.setEnergyPoints(value);
        this.showStatus("能量 " + value);
    }

    /** 显隐开关 / 颜色按钮被点击或重置后刷新按钮文案。 */
    private void refreshDynamicLabels() {
        this.refreshToggleLabels();
        this.refreshColorLabels();
    }

    /** 刷新 6 个显隐开关的「开 / 关」文案。 */
    private void refreshToggleLabels() {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        String[] names = {"标题：", "立绘：", "数值：", "身份：", "武器：", "觉醒："};
        boolean[] values = {ui.showHeader, ui.showPortrait, ui.showStats,
                ui.showIdentity, ui.showWeapons, ui.showAwakenings};
        for (int i = 0; i < this.toggleButtons.length; i++) {
            if (this.toggleButtons[i] != null) {
                this.toggleButtons[i].setMessage(Component.literal(names[i] + onOff(values[i])));
            }
        }
    }

    /** 刷新 4 个颜色按钮的当前色名（如「主色：金」「价格：默认」）。 */
    private void refreshColorLabels() {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        String[] names = {"主色：", "升级项：", "价格：", "觉醒名："};
        int[] colors = {ui.accentColor, ui.itemTextColor, ui.priceColor, ui.awakenNameColor};
        for (int i = 0; i < this.colorButtons.length; i++) {
            if (this.colorButtons[i] != null) {
                this.colorButtons[i].setMessage(Component.literal(names[i] + colorName(colors[i])));
            }
        }
    }

    private void saveAndClose() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        config.growthUi.sanitize();
        config.save();
        this.saved = true;
        this.closeToParent();
    }

    /** 放弃修改：用打开时的快照整体还原配置，再返回父界面。 */
    private void restoreAndClose() {
        if (!this.saved && this.snapshotJson != null && !this.snapshotJson.isEmpty()) {
            StrinovaHudConfig.get().growthImportJson(this.snapshotJson);
        }
        this.closeToParent();
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.restoreAndClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        // 半透明压暗背景（与计分板编辑器一致）
        graphics.fill(0, 0, this.width, this.height, 0xC8000000);
        this.preview = PreviewLayout.compute(this.width, this.height, ui);
        // 第 5 行状态提示可能 2 秒后消失，这里逐帧重算示意图区域，避免留下空白
        this.previewTop = (hasHiddenParts(ui) || this.statusVisible()) ? 66 : 54;
        this.previewBottom = Math.max(this.previewTop + 10, this.buttonsTop - 6);
        this.updateMapping();
        this.hoveredBoundary = this.hitBoundary(mouseX, mouseY);
        this.drawPreview(graphics, ui);
        this.drawEditorText(graphics, ui);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 顶部标题 / 两行参数（含分割线比例、行权重、能量点）/ 拖动提示 / 操作反馈或已隐藏汇总。 */
    private void drawEditorText(GuiGraphics graphics, StrinovaGrowthUi ui) {
        this.drawCenteredText(graphics, "弦能增幅网络 · 界面自定义", 6, COLOR_TITLE, true);
        // 第一行：能量点 + 主要比例（能量放最前，窄窗口截断时也能看到）
        String info1 = String.format(Locale.ROOT,
                "能量 %d   标题高 %.1f%%   立绘宽 %.1f%%   武器宽 %.1f%%   觉醒高 %.1f%%",
                StrinovaGrowthState.energyPoints(),
                ui.headerHeightRatio * 100.0F, ui.portraitWidthRatio * 100.0F,
                ui.weaponsWidthRatio * 100.0F, ui.awakenHeightRatio * 100.0F);
        this.drawCenteredText(graphics, info1, 18, COLOR_INFO, false);
        // 第二行：网络列分界 / 行权重 / 间距 / 不透明度
        String info2 = String.format(Locale.ROOT,
                "网络列分界 %.0f%%   行权重 %.1f:%.1f:%.1f:%.1f   列距 %d   行距 %d   透明 %.0f%%",
                ui.networkSplitRatio * 100.0F,
                rowWeight(ui, 0), rowWeight(ui, 1), rowWeight(ui, 2), rowWeight(ui, 3),
                Math.round(ui.columnGap), Math.round(ui.rowGap), ui.opacity * 100.0F);
        this.drawCenteredText(graphics, info2, 30, COLOR_INFO, false);
        this.drawCenteredText(graphics,
                "拖动 8 条青色分割线调整布局 · 底部按钮切换显隐 / 微调 / 颜色 / 能量 · ESC 放弃还原 · 保存写入配置",
                42, COLOR_INFO, false);
        if (this.statusVisible()) {
            // 能量 +/- 与重置购买的 2 秒操作反馈
            this.drawCenteredText(graphics, this.statusText, 54, COLOR_GOLD, false);
            return;
        }
        String hidden = hiddenSummary(ui);
        if (!hidden.isEmpty()) {
            this.drawCenteredText(graphics, hidden, 54, COLOR_HIDDEN, false);
        }
    }

    private static float rowWeight(StrinovaGrowthUi ui, int index) {
        if (ui.networkRowWeights == null || index < 0 || index >= ui.networkRowWeights.length) {
            return 1.0F;
        }
        return clampf(ui.networkRowWeights[index], 0.35F, 2.5F);
    }

    /** 居中绘制一行文字，超过窗口宽度时截断。 */
    private void drawCenteredText(GuiGraphics graphics, String text, int y, int color, boolean shadow) {
        String shown = this.truncate(text, Math.max(20, this.width - 8));
        graphics.drawString(this.font, shown, (this.width - this.font.width(shown)) / 2, y, color, shadow);
    }

    private static boolean hasHiddenParts(StrinovaGrowthUi ui) {
        return !ui.showHeader || !ui.showPortrait || !ui.showStats
                || !ui.showIdentity || !ui.showWeapons || !ui.showAwakenings;
    }

    /** 灰色「已隐藏：…」汇总文案（示意图里也会在各区域标注「已隐藏」）。 */
    private static String hiddenSummary(StrinovaGrowthUi ui) {
        StringBuilder sb = new StringBuilder();
        appendHidden(sb, "标题", !ui.showHeader);
        appendHidden(sb, "立绘", !ui.showPortrait);
        appendHidden(sb, "数值", !ui.showStats);
        appendHidden(sb, "身份", !ui.showIdentity);
        appendHidden(sb, "武器", !ui.showWeapons);
        appendHidden(sb, "觉醒", !ui.showAwakenings);
        return sb.length() == 0 ? "" : "已隐藏：" + sb;
    }

    private static void appendHidden(StringBuilder sb, String name, boolean hidden) {
        if (!hidden) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("、");
        }
        sb.append(name);
    }

    // ==================== 示意图 ====================

    /** 示意画面板：深色底 + 等比缩放的真实界面布局 + 可拖拽边界 + 隐藏标注。 */
    private void drawPreview(GuiGraphics graphics, StrinovaGrowthUi ui) {
        graphics.fill(this.previewLeft, this.previewTop, this.previewRight, this.previewBottom, 0xFF04070B);
        this.gradientVirtual(graphics, 0, 0, this.width, this.height, 0xFF0A1018, 0xFF141D2B);
        int dimAlpha = (int) (ui.backgroundDim * 255.0F);
        if (dimAlpha > 0) {
            this.fillVirtual(graphics, 0, 0, this.width, this.height, Math.min(255, dimAlpha) << 24);
        }
        this.drawPreviewBorder(graphics);
        if (this.preview.showHeader) {
            this.drawHeaderPreview(graphics, ui);
        }
        if (this.preview.showPortrait) {
            this.drawPortraitPreview(graphics, ui);
        }
        this.drawCategoriesPreview(graphics, ui);
        if (this.preview.showWeapons) {
            this.drawWeaponsPreview(graphics, ui);
        }
        if (this.preview.showAwakenings) {
            this.drawAwakeningsPreview(graphics, ui);
        }
        this.drawBoundaries(graphics);
        this.drawHiddenBadges(graphics, ui);
        // 预览外框
        this.fillVirtual(graphics, 0, 0, this.width, 1, 0x5535D6E8);
        this.fillVirtual(graphics, 0, this.height - 1, this.width, this.height, 0x5535D6E8);
    }

    private void drawPreviewBorder(GuiGraphics graphics) {
        int x1 = this.mapX(0);
        int y1 = this.mapY(0);
        int x2 = Math.max(x1 + 1, this.mapX(this.width));
        int y2 = Math.max(y1 + 1, this.mapY(this.height));
        graphics.fill(x1, y1, x2, y1 + 1, 0x8035D6E8);
        graphics.fill(x1, y2 - 1, x2, y2, 0x8035D6E8);
        graphics.fill(x1, y1, x1 + 1, y2, 0x8035D6E8);
        graphics.fill(x2 - 1, y1, x2, y2, 0x8035D6E8);
    }

    private void drawHeaderPreview(GuiGraphics graphics, StrinovaGrowthUi ui) {
        PreviewLayout l = this.preview;
        int headerBottom = l.headerTop + l.headerHeight;
        this.fillVirtual(graphics, l.innerLeft, l.headerTop, l.innerRight, headerBottom,
                withOpacity(0xF0161F2C, ui.opacity));
        this.fillVirtual(graphics, l.innerLeft, l.headerTop, l.innerLeft + 3, headerBottom, COLOR_CYAN);
        this.fillVirtual(graphics, l.innerLeft, headerBottom - 1, this.width / 2, headerBottom, 0x8035D6E8);
        this.fillVirtual(graphics, this.width / 2, headerBottom - 1, l.innerRight, headerBottom, 0x60FF4FA3);
        int mappedHeight = this.mapY(headerBottom) - this.mapY(l.headerTop);
        if (mappedHeight < 9) {
            return;
        }
        int textY = this.mapY(l.headerTop + l.headerHeight / 2) - 4;
        int headerWidth = this.mapX(l.innerRight) - this.mapX(l.innerLeft);
        String title = "弦能增幅网络 · " + agentName();
        graphics.drawString(this.font, this.truncate(title, Math.max(12, headerWidth - 46)),
                this.mapX(l.innerLeft) + 8, textY, COLOR_TEXT, false);
        if (headerWidth >= 170) {
            String points = "能量 " + StrinovaGrowthState.energyPoints();
            graphics.drawString(this.font, points,
                    this.mapX(this.width / 2) - this.font.width(points) / 2, textY, COLOR_GOLD, false);
        }
        if (headerWidth >= 120) {
            String ready = StrinovaMatchState.formattedTime();
            graphics.drawString(this.font, ready,
                    this.mapX(l.innerRight) - this.font.width(ready) - 22, textY, COLOR_CYAN, false);
        }
    }

    private void drawPortraitPreview(GuiGraphics graphics, StrinovaGrowthUi ui) {
        PreviewLayout l = this.preview;
        int accent = this.accentColor(ui);
        this.gradientVirtual(graphics, l.portraitX, l.portraitY,
                l.portraitX + l.portraitWidth, l.portraitY + l.portraitHeight,
                0xFF1C2735, 0xFF0C121B);
        this.fillVirtual(graphics, l.portraitX, l.portraitY,
                l.portraitX + l.portraitWidth, l.portraitY + 2, accent);
        this.fillVirtual(graphics, l.portraitX, l.portraitY,
                l.portraitX + 1, l.portraitY + l.portraitHeight, 0x60FFFFFF);

        int portraitW = this.mapX(l.portraitX + l.portraitWidth) - this.mapX(l.portraitX);
        int portraitH = this.mapY(l.portraitY + l.portraitHeight) - this.mapY(l.portraitY);
        int centerX = (this.mapX(l.portraitX) + this.mapX(l.portraitX + l.portraitWidth)) / 2;
        // 名字首字：示意图里用自然字号，空间足够时放大 2~3 倍
        if (portraitW >= 16 && portraitH >= 24) {
            int glyphScale = clamp(portraitW / 30, 1, 3);
            graphics.pose().pushPose();
            graphics.pose().scale(glyphScale, glyphScale, 1.0F);
            graphics.drawCenteredString(this.font, initialOf(),
                    centerX / glyphScale,
                    (this.mapY(l.portraitY + l.portraitHeight / 2) - 4) / glyphScale,
                    (accent & 0x00FFFFFF) | 0x8C000000);
            graphics.pose().popPose();
        }
        int nameY = this.mapY(Math.max(l.portraitY + 14, l.portraitY + l.portraitHeight - 40));
        if (portraitW >= 44 && portraitH >= 54) {
            graphics.drawCenteredString(this.font, this.truncate(agentName(), portraitW - 4),
                    centerX, nameY, COLOR_TEXT);
        }
        if (portraitW >= 58 && portraitH >= 70) {
            graphics.drawCenteredString(this.font, this.truncate(roleName(), portraitW - 6),
                    centerX, nameY + 11, accent);
        }
        boolean roomForExtra = portraitW >= 70 && portraitH >= 88;
        if (roomForExtra) {
            if (ui.showStats) {
                graphics.drawCenteredString(this.font, this.truncate(statsLine(), portraitW - 6),
                        centerX, nameY - 24, COLOR_TEXT_DISABLED);
            } else {
                this.drawHiddenBadge(graphics, "数值 已隐藏", centerX, nameY - 22);
            }
            if (ui.showIdentity) {
                graphics.drawCenteredString(this.font, this.truncate(identityLine(), portraitW - 6),
                        centerX, nameY - 12, COLOR_TEXT_DIM);
            } else {
                this.drawHiddenBadge(graphics, "身份 已隐藏", centerX, nameY - 10);
            }
        }
    }

    /** 某行高度下升级项的行高（与真实界面一致，1~36）。 */
    private int previewItemHeight(int blockHeight) {
        return clamp((blockHeight - ITEM_HEADER_HEIGHT - 2) / 2, 1, 36);
    }

    private void drawCategoriesPreview(GuiGraphics graphics, StrinovaGrowthUi ui) {
        PreviewLayout l = this.preview;
        List<StrinovaWikiData.NetworkGroup> groups = this.wikiGroups();
        // 条目条 / 价格点用配置色示意（与界面实际配色覆盖一致）
        int itemTextOverride = ui.itemTextColor != 0 ? ui.itemTextColor : 0xFF33465C;
        int priceDotColor = ui.priceColor != 0 ? ui.priceColor : COLOR_DIAMOND;
        for (int i = 0; i < 8; i++) {
            int column = Math.min(1, i / CATEGORY_ROWS);
            int row = i % CATEGORY_ROWS;
            int blockX = l.columnX[column];
            int blockY = l.rowTops[row];
            int blockWidth = l.columnW[column];
            int blockHeight = l.rowHeights[row];
            int blockW = this.mapX(blockX + blockWidth) - this.mapX(blockX);
            int blockH = this.mapY(blockY + blockHeight) - this.mapY(blockY);
            int accent = PREVIEW_ACCENTS[i % PREVIEW_ACCENTS.length];
            // mock：第 0 类视为「本类已选」，其未选项灰显、刷新图标可用；其余类刷新图标半透明
            boolean mockLocked = i == 0;
            this.fillVirtual(graphics, blockX, blockY + 2, blockX + 10, blockY + 10, accent);
            if (blockW >= 26 && blockH >= 10) {
                String label = i < groups.size() ? groups.get(i).label() : CATEGORY_FALLBACK_LABELS[i];
                // 标题文字给右上角刷新图标让位
                graphics.drawString(this.font, this.truncate(label, Math.max(10, blockW - 38)),
                        this.mapX(blockX) + 14, this.mapY(blockY) + 1, COLOR_TEXT, false);
            }
            // 分类标题右上角刷新图标（绿色斜四边形 + 白色圆形箭头）
            if (blockW >= 24 && blockH >= 12) {
                int iconX = this.mapX(blockX + blockWidth - 20);
                int iconY = this.mapY(blockY);
                int iconW = Math.max(8, this.mapX(blockX + blockWidth) - iconX);
                int iconH = Math.max(6, this.mapY(blockY + 14) - iconY);
                this.drawMiniRefreshIcon(graphics, iconX, iconY, iconW, iconH, mockLocked);
            }
            int itemCount = i < groups.size() ? Math.min(2, groups.get(i).items().size()) : 2;
            int itemHeight = this.previewItemHeight(blockHeight);
            int itemsBlockHeight = itemHeight * itemCount + 2 * Math.max(0, itemCount - 1);
            int itemsTop = blockY + ITEM_HEADER_HEIGHT
                    + Math.max(0, (blockHeight - ITEM_HEADER_HEIGHT - itemsBlockHeight) / 2);
            for (int j = 0; j < itemCount; j++) {
                int itemY = itemsTop + j * (itemHeight + 2);
                this.fillVirtual(graphics, blockX, itemY, blockX + blockWidth,
                        itemY + itemHeight, withOpacity(mockLocked ? 0x14000000 : 0x1CFFFFFF, l.opacity));
                int itemH = this.mapY(itemY + itemHeight) - this.mapY(itemY);
                if (itemH < 2 || blockW < 18) {
                    continue;
                }
                int barTop = itemY + Math.max(1, itemHeight / 2 - 1);
                int barBottom = itemY + Math.max(2, itemHeight / 2 + 1);
                this.fillVirtual(graphics, blockX + 3, barTop,
                        blockX + Math.max(8, blockWidth * 3 / 5), barBottom,
                        mockLocked ? 0xFF46525E : withOpacity(itemTextOverride, 0.7F));
                this.fillVirtual(graphics, blockX + blockWidth - 10,
                        itemY + Math.max(1, itemHeight / 2 - 2),
                        blockX + blockWidth - 6,
                        itemY + Math.max(2, itemHeight / 2 + 2),
                        mockLocked ? 0xFF46525E : priceDotColor);
            }
        }
    }

    private void drawWeaponsPreview(GuiGraphics graphics, StrinovaGrowthUi ui) {
        PreviewLayout l = this.preview;
        this.fillVirtual(graphics, l.weaponsX - 4, l.weaponsY, l.innerRight, l.weaponsBottom,
                withOpacity(0x88101824, ui.opacity));
        this.fillVirtual(graphics, l.weaponsX - 4, l.weaponsY, l.weaponsX - 3, l.weaponsBottom, 0x4035D6E8);
        int cardLeft = l.weaponsX;
        int cardWidth = Math.max(8, l.innerRight - 4 - cardLeft);
        int slotGap = 3;
        int top = l.weaponsY + 4;
        int bottom = l.weaponsBottom - 4;
        int rowHeight = Math.max(12, (bottom - top - slotGap * 3) / 4);
        // 1 / 2 / 3：整列宽、四行等高
        for (int i = 0; i < 3; i++) {
            int slotY = top + i * (rowHeight + slotGap);
            this.drawWeaponCardPreview(graphics, i, cardLeft, slotY, cardWidth, rowHeight);
        }
        // 4 / 5：底部一行并排各半宽
        int rowY = top + 3 * (rowHeight + slotGap);
        int halfWidth = Math.max(8, (cardWidth - slotGap) / 2);
        this.drawWeaponCardPreview(graphics, 3, cardLeft, rowY, halfWidth, rowHeight);
        this.drawWeaponCardPreview(graphics, 4, cardLeft + halfWidth + slotGap, rowY, halfWidth, rowHeight);
    }

    /** 单张武器卡示意：序号 + 槽位类型、几何图标、右下角黄色「切换」双向箭头。 */
    private void drawWeaponCardPreview(GuiGraphics graphics, int index, int x, int y, int width, int height) {
        boolean selected = index == 0;
        this.fillVirtual(graphics, x, y, x + width, y + height,
                selected ? 0x66203548 : 0x2A0B121A);
        this.fillVirtual(graphics, x, y, x + width, y + 1, selected ? COLOR_CYAN : 0x22FFFFFF);
        int cardW = this.mapX(x + width) - this.mapX(x);
        int cardH = this.mapY(y + height) - this.mapY(y);
        if (cardH >= 9 && cardW >= 16) {
            graphics.drawString(this.font, String.valueOf(index + 1),
                    this.mapX(x) + 3, this.mapY(y) + 2,
                    selected ? COLOR_CYAN : COLOR_TEXT_DIM, false);
        }
        if (cardH >= 9 && cardW >= 46) {
            graphics.drawString(this.font, this.truncate(WEAPON_SLOT_LABELS[index], cardW - 18),
                    this.mapX(x) + 13, this.mapY(y) + 2, COLOR_TEXT_DISABLED, false);
        }
        if (cardH >= 16 && cardW >= 22) {
            int iconSize = Math.max(6, Math.min(Math.min(cardW - 22, cardH - 14), 18));
            int iconX = this.mapX(x) + (cardW - iconSize) / 2;
            int iconY = this.mapY(y) + 10 + Math.max(0, (cardH - 12 - iconSize) / 2);
            this.drawWeaponPlaceholder(graphics, index, iconX, iconY, iconSize,
                    selected ? COLOR_CYAN : 0xFFC8D4E0);
        }
        // 右下角黄色「切换 / 更换」双向箭头（装饰）
        this.drawMiniSwitchIcon(graphics, this.mapX(x + width) - 3, this.mapY(y + height) - 2);
    }

    private void drawAwakeningsPreview(GuiGraphics graphics, StrinovaGrowthUi ui) {
        PreviewLayout l = this.preview;
        int cardGap = l.gap;
        // 觉醒 2/3 卡片间隙加宽出「切换激活」按钮位置（与真实界面一致）
        int toggleW = clamp(Math.round(l.awakenWidth * 0.16F), 16, 60);
        int cardWidth = (l.awakenWidth - 3 * cardGap - toggleW) / 3;
        if (cardWidth < 30) {
            toggleW = Math.max(10, l.awakenWidth - 3 * cardGap - 3 * 30);
            cardWidth = Math.max(8, (l.awakenWidth - 3 * cardGap - toggleW) / 3);
        }
        int card0X = l.awakenLeft;
        int card1X = card0X + cardWidth + cardGap;
        int toggleX = card1X + cardWidth + cardGap;
        int card2X = toggleX + toggleW + cardGap;
        int[] cardXs = {card0X, card1X, card2X};
        // 觉醒名称色 / 顶条：自定义 awakenNameColor 非 0 时同步示意
        int activeNameColor = ui.awakenNameColor != 0 ? ui.awakenNameColor : COLOR_GOLD;
        int inactiveNameColor = ui.awakenNameColor != 0
                ? ((ui.awakenNameColor & 0x00FFFFFF) | 0xCC000000) : COLOR_TEXT_DIM;
        for (int i = 0; i < 3; i++) {
            int x = cardXs[i];
            this.fillVirtual(graphics, x, l.awakenTop, x + cardWidth, l.awakenTop + l.awakenHeight,
                    withOpacity(0xCC101823, ui.opacity));
            this.fillVirtual(graphics, x, l.awakenTop, x + cardWidth, l.awakenTop + 2,
                    i == 0 ? activeNameColor : 0xFF3A4756);
            this.fillVirtual(graphics, x, l.awakenTop, x + 1, l.awakenTop + l.awakenHeight,
                    i == 0 ? 0x8035D6E8 : 0x30FFFFFF);
            int cardW = this.mapX(x + cardWidth) - this.mapX(x);
            int cardH = this.mapY(l.awakenTop + l.awakenHeight) - this.mapY(l.awakenTop);
            if (cardW >= 30 && cardH >= 9) {
                String label = "觉醒 " + (i + 1);
                graphics.drawString(this.font, this.truncate(label, cardW - 6),
                        this.mapX(x) + 3, this.mapY(l.awakenTop) + 4,
                        i == 0 ? activeNameColor : inactiveNameColor, false);
            }
        }
        // 觉醒 2/3 二选一「切换激活」按钮（示意图内为可用态）
        int toggleH = clamp(l.awakenHeight - 12, 14, 18);
        int toggleY = l.awakenTop + (l.awakenHeight - toggleH) / 2;
        this.drawMiniAwakenToggle(graphics, toggleX, toggleY, toggleW, toggleH);
    }

    /** 示意图里的迷你「切换激活」按钮（⇄ 图标 + 文案，宽度不足时只画图标）。 */
    private void drawMiniAwakenToggle(GuiGraphics graphics, int x, int y, int width, int height) {
        int mappedX = this.mapX(x);
        int mappedY = this.mapY(y);
        int mappedW = Math.max(8, this.mapX(x + width) - mappedX);
        int mappedH = Math.max(6, this.mapY(y + height) - mappedY);
        graphics.fill(mappedX, mappedY, mappedX + mappedW, mappedY + mappedH, 0x99101824);
        graphics.fill(mappedX, mappedY, mappedX + mappedW, mappedY + 1, 0xFF57C785);
        graphics.fill(mappedX, mappedY + mappedH - 1, mappedX + mappedW, mappedY + mappedH, 0xFF57C785);
        graphics.fill(mappedX, mappedY, mappedX + 1, mappedY + mappedH, 0xFF57C785);
        graphics.fill(mappedX + mappedW - 1, mappedY, mappedX + mappedW, mappedY + mappedH, 0xFF57C785);
        String text = "切换激活";
        int textW = this.font.width(text);
        boolean showText = mappedW >= textW + 20;
        int iconW = 9;
        int contentW = showText ? iconW + 3 + textW : iconW;
        int startX = mappedX + Math.max(2, (mappedW - contentW) / 2);
        int centerY = mappedY + mappedH / 2;
        this.drawMiniSwapIcon(graphics, startX, centerY - 3, COLOR_CYAN);
        if (showText) {
            graphics.drawString(this.font, text, startX + iconW + 3, centerY - 4, COLOR_CYAN, false);
        } else if (mappedW >= 22) {
            // 按钮较窄时缩放字号仍显示「切换激活」文案，示意与真实界面对应
            float scale = Math.min(1.0F, (mappedW - 6.0F) / (iconW + 3 + textW));
            graphics.pose().pushPose();
            graphics.pose().translate(startX + iconW + 3.0F, centerY - 4.0F, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(this.font, text, 0, 0, COLOR_CYAN, false);
            graphics.pose().popPose();
        }
    }

    /** 迷你双向交换图标 ⇄（上右下左两个箭头）。 */
    private void drawMiniSwapIcon(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 6, y + 1, color);
        graphics.fill(x + 6, y - 1, x + 8, y + 2, color);
        graphics.fill(x + 2, y + 5, x + 8, y + 6, color);
        graphics.fill(x, y + 4, x + 2, y + 7, color);
    }

    // ==================== 边界与隐藏标注 ====================

    /** 画 8 条可拖拽分割线：3 竖（立绘右边界 / 网络列分界 / 武器栏左边界）+ 5 横（标题栏底边 / 3 条行分隔 / 觉醒上边界）。 */
    private void drawBoundaries(GuiGraphics graphics) {
        PreviewLayout l = this.preview;
        int top = this.mapY(l.contentTop);
        int bottom = this.mapY(l.contentBottom);
        if (bottom <= top) {
            bottom = top + 1;
        }
        // 竖线 1：立绘右边界
        if (l.showPortrait) {
            this.drawVerticalBoundary(graphics, this.mapX(l.portraitX + l.portraitWidth),
                    top, bottom, this.isBoundaryActive(DRAG_PORTRAIT));
        }
        // 竖线 2：增幅网络左右列分隔线（列间距中线）
        int splitX = this.mapX(l.categoriesLeft + l.columnW[0] + l.categoryColumnGap / 2);
        this.drawVerticalBoundary(graphics, splitX, top, this.mapY(l.categoriesBottom),
                this.isBoundaryActive(DRAG_NETWORK_SPLIT));
        // 竖线 3：武器栏左边界
        if (l.showWeapons) {
            this.drawVerticalBoundary(graphics, this.mapX(l.weaponsX),
                    top, bottom, this.isBoundaryActive(DRAG_WEAPONS));
        }
        // 横线 1：标题栏底边
        if (l.showHeader) {
            this.drawHorizontalBoundary(graphics, this.mapY(l.headerTop + l.headerHeight),
                    this.mapX(l.innerLeft), this.mapX(l.innerRight),
                    this.isBoundaryActive(DRAG_HEADER));
        }
        // 横线 2~4：增幅网络第 1/2、2/3、3/4 行分隔线
        for (int row = 0; row < CATEGORY_ROWS - 1; row++) {
            int y = this.mapY(l.rowTops[row] + l.rowHeights[row]);
            this.drawHorizontalBoundary(graphics, y,
                    this.mapX(l.categoriesLeft), this.mapX(l.categoriesRight),
                    this.isBoundaryActive(DRAG_ROW_12 + row));
        }
        // 横线 5：觉醒区上边界
        if (l.showAwakenings) {
            this.drawHorizontalBoundary(graphics, this.mapY(l.awakenTop),
                    this.mapX(l.awakenLeft), this.mapX(l.awakenRight),
                    this.isBoundaryActive(DRAG_AWAKENINGS));
        }
    }

    private boolean isBoundaryActive(int kind) {
        return this.dragging == kind || this.hoveredBoundary == kind;
    }

    private void drawVerticalBoundary(GuiGraphics graphics, int x, int y1, int y2, boolean active) {
        int color = active ? COLOR_CYAN : 0x66F2F7FF;
        graphics.fill(x, y1, x + 1, y2, color);
        // 顶部拖动把手
        graphics.fill(x - 3, y1, x + 4, y1 + 3, active ? 0xFF35D6E8 : 0x66FFFFFF);
        if (active) {
            graphics.fill(x - 1, y1 + 3, x + 2, y1 + 7, 0x8835D6E8);
        }
    }

    private void drawHorizontalBoundary(GuiGraphics graphics, int y, int x1, int x2, boolean active) {
        int color = active ? COLOR_CYAN : 0x66F2F7FF;
        graphics.fill(x1, y, x2, y + 1, color);
        // 左侧拖动把手
        graphics.fill(x1, y - 3, x1 + 3, y + 4, active ? 0xFF35D6E8 : 0x66FFFFFF);
        if (active) {
            graphics.fill(x1 + 3, y - 1, x1 + 7, y + 2, 0x8835D6E8);
        }
    }

    /** 被隐藏区域画灰色「已隐藏」标注（半透明底衬，保证盖在示意图上也能看清）。 */
    private void drawHiddenBadges(GuiGraphics graphics, StrinovaGrowthUi ui) {
        PreviewLayout l = this.preview;
        int contentCenterY = this.mapY((l.contentTop + l.contentBottom) / 2);
        if (!l.showHeader) {
            this.drawHiddenBadge(graphics, "标题 已隐藏", this.mapX(this.width / 2),
                    this.mapY(l.headerTop + l.controlHeight / 2));
        }
        if (!l.showPortrait) {
            this.drawHiddenBadge(graphics, "立绘 已隐藏", this.mapX(l.innerLeft) + 34, contentCenterY);
        }
        if (!l.showWeapons) {
            this.drawHiddenBadge(graphics, "武器 已隐藏", this.mapX(l.innerRight) - 34, contentCenterY);
        }
        if (!l.showAwakenings) {
            this.drawHiddenBadge(graphics, "觉醒 已隐藏",
                    this.mapX((l.awakenLeft + l.awakenRight) / 2),
                    this.mapY(l.contentBottom - 14));
        }
    }

    private void drawHiddenBadge(GuiGraphics graphics, String text, int centerX, int centerY) {
        int width = this.font.width(text) + 8;
        int x = clamp(centerX - width / 2, this.previewLeft + 2,
                Math.max(this.previewLeft + 2, this.previewRight - width - 2));
        int y = clamp(centerY - 4, this.previewTop + 2,
                Math.max(this.previewTop + 2, this.previewBottom - 10));
        graphics.fill(x, y, x + width, y + 10, 0xBB000000);
        graphics.drawString(this.font, text, x + 4, y + 1, COLOR_HIDDEN, false);
    }

    /** 示意图里的迷你刷新图标：绿色斜四边形底 + 白色圆形箭头（可用时高亮，否则半透明）。 */
    private void drawMiniRefreshIcon(GuiGraphics graphics, int x, int y, int width, int height,
                                     boolean enabled) {
        int bg = enabled ? 0xFF57C785 : 0x6657C785;
        int white = enabled ? 0xFFFFFFFF : 0x99FFFFFF;
        int slant = Math.max(1, width / 6);
        for (int row = 0; row < height; row++) {
            int offset = slant - (row * slant) / Math.max(1, height - 1);
            graphics.fill(x + offset, y + row, x + width, y + row + 1, bg);
        }
        int cx = x + width / 2;
        int cy = y + height / 2;
        int radius = Math.max(2, height / 2 - 1);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < radius - 1.0D || dist > radius + 0.2D) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(-dy, dx));
                if (angle > 25.0D && angle < 70.0D) {
                    continue;
                }
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, white);
            }
        }
        // 缺口处箭头三角
        int ax = cx + radius - 1;
        int ay = cy - radius;
        graphics.fill(ax, ay, ax + 2, ay + 1, white);
        graphics.fill(ax, ay, ax + 1, ay + 2, white);
    }

    /** 示意图里的迷你黄色「切换 / 更换」双向箭头（装饰）。 */
    private void drawMiniSwitchIcon(GuiGraphics graphics, int right, int bottom) {
        int left = right - 7;
        int top = bottom - 3;
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0x66000000);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, COLOR_GOLD);
        graphics.fill(left, top, left + 2, top + 3, COLOR_GOLD);
        graphics.fill(right - 2, top, right, top + 3, COLOR_GOLD);
    }

    // ==================== 坐标映射（示意图 = 真实界面尺寸等比缩小） ====================

    private void updateMapping() {
        int areaWidth = Math.max(8, this.previewRight - this.previewLeft);
        int areaHeight = Math.max(8, this.previewBottom - this.previewTop);
        int virtualWidth = Math.max(1, this.width);
        int virtualHeight = Math.max(1, this.height);
        this.previewScale = Math.min(areaWidth / (double) virtualWidth, areaHeight / (double) virtualHeight);
        if (!(this.previewScale > 0.0D)) {
            this.previewScale = 1.0D / Math.max(virtualWidth, virtualHeight);
        }
        int drawnWidth = (int) Math.round(virtualWidth * this.previewScale);
        int drawnHeight = (int) Math.round(virtualHeight * this.previewScale);
        this.previewOffsetX = this.previewLeft + (areaWidth - drawnWidth) / 2;
        this.previewOffsetY = this.previewTop + (areaHeight - drawnHeight) / 2;
    }

    private int mapX(double virtualX) {
        return this.previewOffsetX + (int) Math.round(virtualX * this.previewScale);
    }

    private int mapY(double virtualY) {
        return this.previewOffsetY + (int) Math.round(virtualY * this.previewScale);
    }

    private void fillVirtual(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int left = this.mapX(x1);
        int top = this.mapY(y1);
        int right = Math.max(left + 1, this.mapX(x2));
        int bottom = Math.max(top + 1, this.mapY(y2));
        graphics.fill(left, top, right, bottom, color);
    }

    private void gradientVirtual(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                 int topColor, int bottomColor) {
        int left = this.mapX(x1);
        int top = this.mapY(y1);
        int right = Math.max(left + 1, this.mapX(x2));
        int bottom = Math.max(top + 1, this.mapY(y2));
        graphics.fillGradient(left, top, right, bottom, topColor, bottomColor);
    }

    // ==================== 交互（拖拽三条边界） ====================

    /** 命中检测：鼠标靠近 8 条分割线（±5px）时返回对应拖拽类型。 */
    private int hitBoundary(double mouseX, double mouseY) {
        PreviewLayout l = this.preview;
        int top = this.mapY(l.contentTop);
        int bottom = this.mapY(l.contentBottom);
        // 竖线
        if (l.showPortrait) {
            int boundaryX = this.mapX(l.portraitX + l.portraitWidth);
            if (Math.abs(mouseX - boundaryX) <= 5.0D && mouseY >= top && mouseY <= bottom) {
                return DRAG_PORTRAIT;
            }
        }
        int splitX = this.mapX(l.categoriesLeft + l.columnW[0] + l.categoryColumnGap / 2);
        int gridBottom = this.mapY(l.categoriesBottom);
        if (Math.abs(mouseX - splitX) <= 5.0D && mouseY >= top && mouseY <= gridBottom) {
            return DRAG_NETWORK_SPLIT;
        }
        if (l.showWeapons) {
            int boundaryX = this.mapX(l.weaponsX);
            if (Math.abs(mouseX - boundaryX) <= 5.0D && mouseY >= top && mouseY <= bottom) {
                return DRAG_WEAPONS;
            }
        }
        // 横线：标题栏底边
        if (l.showHeader) {
            int boundaryY = this.mapY(l.headerTop + l.headerHeight);
            if (Math.abs(mouseY - boundaryY) <= 5.0D
                    && mouseX >= this.mapX(l.innerLeft) && mouseX <= this.mapX(l.innerRight)) {
                return DRAG_HEADER;
            }
        }
        // 横线：增幅网络 1/2、2/3、3/4 行分隔线
        for (int row = 0; row < CATEGORY_ROWS - 1; row++) {
            int boundaryY = this.mapY(l.rowTops[row] + l.rowHeights[row]);
            if (Math.abs(mouseY - boundaryY) <= 5.0D
                    && mouseX >= this.mapX(l.categoriesLeft)
                    && mouseX <= this.mapX(l.categoriesRight)) {
                return DRAG_ROW_12 + row;
            }
        }
        // 横线：觉醒区上边界
        if (l.showAwakenings) {
            int boundaryY = this.mapY(l.awakenTop);
            if (Math.abs(mouseY - boundaryY) <= 5.0D
                    && mouseX >= this.mapX(l.awakenLeft) && mouseX <= this.mapX(l.awakenRight)) {
                return DRAG_AWAKENINGS;
            }
        }
        return DRAG_NONE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        this.preview = PreviewLayout.compute(this.width, this.height, StrinovaHudConfig.get().growthUi);
        this.updateMapping();
        int hit = this.hitBoundary(mouseX, mouseY);
        if (hit == DRAG_NONE) {
            return false;
        }
        this.dragging = hit;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
        this.dragStartRatio = switch (hit) {
            case DRAG_PORTRAIT -> ui.portraitWidthRatio;
            case DRAG_NETWORK_SPLIT -> ui.networkSplitRatio;
            case DRAG_WEAPONS -> ui.weaponsWidthRatio;
            case DRAG_HEADER -> ui.headerHeightRatio;
            case DRAG_AWAKENINGS -> ui.awakenHeightRatio;
            default -> 0.0F;
        };
        if (hit >= DRAG_ROW_12 && hit <= DRAG_ROW_34) {
            // 行分隔线拖拽：备份起始权重 / 网络可用高度，拖动时相邻两行增减
            for (int i = 0; i < CATEGORY_ROWS; i++) {
                this.dragStartRowWeights[i] = weightAt(ui.networkRowWeights, i);
            }
            this.dragStartGridUsable = Math.max(1, this.preview.gridUsable);
            this.dragStartTotalWeight = this.dragStartRowWeights[0] + this.dragStartRowWeights[1]
                    + this.dragStartRowWeights[2] + this.dragStartRowWeights[3];
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging != DRAG_NONE && button == 0) {
            StrinovaGrowthUi ui = StrinovaHudConfig.get().growthUi;
            double scale = Math.max(1.0E-4D, this.previewScale);
            double deltaX = (mouseX - this.dragStartMouseX) / scale;
            double deltaY = (mouseY - this.dragStartMouseY) / scale;
            switch (this.dragging) {
                case DRAG_PORTRAIT -> ui.portraitWidthRatio = clampf(
                        (float) (this.dragStartRatio + deltaX / Math.max(1, this.width)), 0.08F, 0.32F);
                case DRAG_NETWORK_SPLIT -> {
                    double avail = Math.max(1.0D, this.preview.categoriesRight
                            - this.preview.categoriesLeft - this.preview.categoryColumnGap);
                    ui.networkSplitRatio = clampf(
                            (float) (this.dragStartRatio + deltaX / avail), 0.20F, 0.80F);
                }
                case DRAG_WEAPONS -> ui.weaponsWidthRatio = clampf(
                        (float) (this.dragStartRatio - deltaX / Math.max(1, this.width)), 0.04F, 0.18F);
                case DRAG_HEADER -> {
                    double ratio = (this.dragStartRatio * this.height + deltaY) / Math.max(1, this.height);
                    ui.headerHeightRatio = clampf((float) ratio, 0.030F, 0.120F);
                }
                case DRAG_AWAKENINGS -> {
                    double ratio = (this.dragStartRatio * this.height - deltaY) / Math.max(1, this.height);
                    ui.awakenHeightRatio = clampf((float) ratio, 0.10F, 0.35F);
                }
                case DRAG_ROW_12, DRAG_ROW_23, DRAG_ROW_34 -> {
                    int pair = this.dragging - DRAG_ROW_12;
                    float deltaWeight = (float) (deltaY * this.dragStartTotalWeight
                            / Math.max(1, this.dragStartGridUsable));
                    float pairSum = this.dragStartRowWeights[pair] + this.dragStartRowWeights[pair + 1];
                    float weightI = clampf(this.dragStartRowWeights[pair] + deltaWeight, 0.35F, 2.5F);
                    float weightJ = pairSum - weightI;
                    if (weightJ > 2.5F) {
                        weightJ = 2.5F;
                        weightI = pairSum - weightJ;
                    }
                    if (weightJ < 0.35F) {
                        weightJ = 0.35F;
                        weightI = pairSum - weightJ;
                    }
                    ui.networkRowWeights[pair] = clampf(weightI, 0.35F, 2.5F);
                    ui.networkRowWeights[pair + 1] = clampf(weightJ, 0.35F, 2.5F);
                }
                default -> {
                    return false;
                }
            }
            ui.sanitize();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = DRAG_NONE;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC：放弃修改并还原
            this.restoreAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 配色与数据辅助 ====================

    private int accentColor(StrinovaGrowthUi ui) {
        if (ui.accentColor != 0) {
            return ui.accentColor;
        }
        return this.agent == null ? COLOR_CYAN : this.agent.accentColor();
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * clampf(opacity, 0.20F, 1.0F));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private String agentName() {
        if (this.agent == null || this.agent.name() == null || this.agent.name().isEmpty()) {
            return "角色";
        }
        return this.agent.name();
    }

    private String initialOf() {
        if (this.agent == null || this.agent.name() == null || this.agent.name().isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(this.agent.name().codePointAt(0)));
    }

    private String roleName() {
        if (this.agent == null || this.agent.role() == null) {
            return "定位";
        }
        return Component.translatable(StrinovaAgentRoster.roleNameKey(this.agent.role())).getString();
    }

    private String statsLine() {
        if (this.wiki == null || this.wiki.stats() == null || this.wiki.stats().hp() <= 0) {
            return "生命 -   护甲 -   移速 -";
        }
        StrinovaWikiData.Stats stats = this.wiki.stats();
        return "生命 " + stats.hp() + "   护甲 " + stats.armor() + "   移速 " + stats.moveSpeed();
    }

    private String identityLine() {
        if (this.wiki == null || this.wiki.identity() == null || this.wiki.identity().isEmpty()) {
            return "身份示意";
        }
        return this.wiki.identity();
    }

    private List<StrinovaWikiData.NetworkGroup> wikiGroups() {
        if (this.wiki == null || this.wiki.network() == null) {
            return List.of();
        }
        return this.wiki.network();
    }

    /** 武器图标示意：主武器 / 副武器 / 近战 / 战术道具的几何占位。 */
    private void drawWeaponPlaceholder(GuiGraphics graphics, int index, int x, int y, int size, int color) {
        switch (index) {
            case 0 -> {
                graphics.fill(x + size / 8, y + size * 35 / 100, x + size * 85 / 100,
                        y + size * 55 / 100, color);
                graphics.fill(x + size * 85 / 100, y + size * 42 / 100, x + size,
                        y + size * 48 / 100, color);
            }
            case 1 -> {
                graphics.fill(x + size / 8, y + size * 32 / 100, x + size * 75 / 100,
                        y + size * 52 / 100, color);
                graphics.fill(x + size * 75 / 100, y + size * 38 / 100, x + size * 95 / 100,
                        y + size * 45 / 100, color);
            }
            case 2 -> {
                int steps = Math.max(3, size * 5 / 10);
                for (int i = 0; i < steps; i++) {
                    graphics.fill(x + size * 10 / 100 + i, y + size * 85 / 100 - i,
                            x + size * 10 / 100 + i + 2, y + size * 85 / 100 - i + 2, color);
                }
            }
            default -> {
                int centerX = x + size / 2;
                int centerY = y + size * 55 / 100;
                int radius = Math.max(2, size / 4);
                for (int dy = -radius; dy <= radius; dy++) {
                    int half = radius - Math.abs(dy);
                    graphics.fill(centerX - half, centerY + dy, centerX + half + 1,
                            centerY + dy + 1, color);
                }
            }
        }
    }

    /** 文本超宽时截断并追加“..”。 */
    private String truncate(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "..";
        int keepWidth = Math.max(0, maxWidth - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, keepWidth) + ellipsis;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampf(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 示意图布局（与 StrinovaGrowthScreen.computeLayout 保持一致） ====================

    /**
     * 示意图布局：按编辑器窗口尺寸（= 真实界面窗口尺寸）套用与
     * {@code StrinovaGrowthScreen#computeLayout()} 完全相同的比例/显隐/自适应规则，
     * 再整体等比缩放到编辑器中区，保证拖动边界时的预览与真实界面一致。
     */
    private static final class PreviewLayout {

        int width;
        int height;
        int margin;
        int gap;
        int innerLeft;
        int innerRight;
        int headerTop;
        int headerHeight;
        int closeButtonSize;
        int controlHeight;
        boolean showHeader;

        int contentTop;
        int contentBottom;

        boolean showPortrait;
        int portraitX;
        int portraitY;
        int portraitWidth;
        int portraitHeight;

        boolean showWeapons;
        int weaponsX;
        int weaponsY;
        int weaponsBottom;
        int weaponsWidth;
        boolean compactWeapons;

        int categoriesLeft;
        int categoriesTop;
        int categoriesRight;
        int categoriesBottom;
        /** 增幅网络列布局：0 左列 / 1 右列（networkSplitRatio 决定）。 */
        final int[] columnX = new int[2];
        final int[] columnW = new int[2];
        /** 增幅网络 4 行实际 Y / 高度（networkRowWeights 归一化）。 */
        final int[] rowTops = new int[CATEGORY_ROWS];
        final int[] rowHeights = new int[CATEGORY_ROWS];
        /** 行权重拖拽换算用的网络可用高度（不含行间距）。 */
        int gridUsable;
        int categoryColumnGap;
        int categoryRowGap;
        int itemRowGap;

        boolean showAwakenings;
        int awakenLeft;
        int awakenRight;
        int awakenWidth;
        int awakenTop;
        int awakenHeight;
        boolean showAwakenDescription;

        /** 配置不透明度（示意图里用于卡片 / 面板底色的 alpha 缩放）。 */
        float opacity = 1.0F;

        static PreviewLayout compute(int width, int height, StrinovaGrowthUi ui) {
            PreviewLayout l = new PreviewLayout();
            l.width = width;
            l.height = height;
            l.opacity = ui.opacity;
            int margin = clamp(width / 96, 4, 12);
            l.margin = margin;
            l.gap = height >= 300 ? 8 : 6;
            l.innerLeft = margin;
            l.innerRight = Math.max(margin + 1, width - margin);

            l.headerTop = margin;
            l.showHeader = ui.showHeader && width >= 320;
            int baseClose = height >= 300 ? 20 : 18;
            int headerRaw = clamp(Math.round(height * clampf(ui.headerHeightRatio, 0.030F, 0.120F)),
                    10, Math.max(10, height / 3));
            l.headerHeight = l.showHeader ? headerRaw : 0;
            l.closeButtonSize = l.showHeader ? clamp(l.headerHeight - 4, 10, baseClose) : baseClose;
            l.controlHeight = l.showHeader ? l.headerHeight : l.closeButtonSize;
            l.contentTop = l.headerTop + l.controlHeight + l.gap;
            // 内容区一直延伸到底部（立绘 / 武器栏满高）
            l.contentBottom = height - margin;

            int available = l.innerRight - l.innerLeft;
            int portraitRaw = Math.round(width * ui.portraitWidthRatio);
            int weaponsRaw = Math.round(width * ui.weaponsWidthRatio);
            l.showPortrait = ui.showPortrait && width >= 480 && height >= 240 && portraitRaw >= 56;
            l.portraitWidth = l.showPortrait
                    ? clamp(portraitRaw, 56, Math.max(56, available / 3))
                    : 0;
            l.portraitX = l.innerLeft;
            l.portraitY = l.contentTop;
            l.portraitHeight = Math.max(0, l.contentBottom - l.contentTop);

            l.showWeapons = ui.showWeapons && width >= 400 && weaponsRaw >= 36;
            l.weaponsWidth = l.showWeapons
                    ? clamp(weaponsRaw, 36, Math.max(36, available / 3))
                    : 0;
            l.compactWeapons = !l.showWeapons || l.weaponsWidth < 64;
            l.weaponsX = l.showWeapons ? l.innerRight - l.weaponsWidth : l.innerRight;
            l.weaponsY = l.contentTop;
            l.weaponsBottom = l.contentBottom;

            l.categoriesLeft = l.innerLeft + (l.showPortrait ? l.portraitWidth + l.gap : 0);
            l.categoriesRight = l.showWeapons ? l.weaponsX - l.gap : l.innerRight;
            l.categoriesTop = l.contentTop;
            l.categoriesBottom = l.contentBottom;

            int minGridWidth = 2 * 48 + 6;
            if (l.categoriesRight - l.categoriesLeft < minGridWidth) {
                if (l.showPortrait) {
                    l.showPortrait = false;
                    l.portraitWidth = 0;
                    l.portraitHeight = 0;
                    l.categoriesLeft = l.innerLeft;
                }
                if (l.categoriesRight - l.categoriesLeft < minGridWidth && l.showWeapons) {
                    l.showWeapons = false;
                    l.weaponsWidth = 0;
                    l.weaponsX = l.innerRight;
                    l.categoriesRight = l.innerRight;
                    l.compactWeapons = true;
                }
            }

            // 觉醒区：只在立绘右侧的中间列底部
            int gridMinHeight = CATEGORY_ROWS * 18 + (CATEGORY_ROWS - 1) * 2;
            int middleWidth = Math.max(0, l.categoriesRight - l.categoriesLeft);
            int awakenMax = Math.max(34,
                    l.contentBottom - l.contentTop - gridMinHeight - l.gap * 2);
            l.showAwakenings = ui.showAwakenings && width >= 260 && height >= 220
                    && middleWidth >= 120;
            if (l.showAwakenings) {
                l.awakenHeight = clamp(Math.round(height * ui.awakenHeightRatio),
                        34, Math.max(34, Math.min(awakenMax, height / 2)));
                l.showAwakenDescription = l.awakenHeight >= 56 && middleWidth >= 480;
            } else {
                l.awakenHeight = 0;
                l.showAwakenDescription = false;
            }
            l.awakenLeft = l.categoriesLeft;
            l.awakenRight = l.categoriesRight;
            l.awakenWidth = middleWidth;
            l.awakenTop = l.showAwakenings ? l.contentBottom - l.awakenHeight : l.contentBottom;
            l.categoriesBottom = l.showAwakenings
                    ? Math.max(l.categoriesTop + 8, l.awakenTop - l.gap)
                    : l.contentBottom;

            // 增幅网络列宽：左列 = (可用宽 - 列间距) × networkSplitRatio
            l.categoryColumnGap = clamp(Math.round(ui.columnGap), 0, 32);
            int availCol = Math.max(2, l.categoriesRight - l.categoriesLeft);
            int colInner = Math.max(2, availCol - l.categoryColumnGap);
            float split = clampf(ui.networkSplitRatio, 0.20F, 0.80F);
            int minCol = Math.min(28, Math.max(8, colInner / 2));
            int leftWidth = clamp(Math.round(colInner * split), minCol, Math.max(minCol, colInner - minCol));
            l.columnX[0] = l.categoriesLeft;
            l.columnW[0] = leftWidth;
            l.columnX[1] = l.categoriesLeft + leftWidth + l.categoryColumnGap;
            l.columnW[1] = Math.max(8, l.categoriesRight - l.columnX[1]);

            // 4 行高度：按 networkRowWeights 归一化
            int gridAvail = Math.max(8, l.categoriesBottom - l.categoriesTop);
            int rowGapWanted = clamp(Math.round(ui.rowGap), 0, 32);
            int rowGapEff = rowGapWanted;
            int usable = gridAvail - rowGapEff * (CATEGORY_ROWS - 1);
            if (usable < CATEGORY_ROWS * 24 && rowGapEff > 2) {
                rowGapEff = Math.min(rowGapEff, 2);
                usable = gridAvail - rowGapEff * (CATEGORY_ROWS - 1);
            }
            usable = Math.max(4, Math.min(usable, gridAvail));
            l.categoryRowGap = rowGapEff;
            l.gridUsable = usable;
            distributeRowHeights(usable, ui.networkRowWeights, l.rowHeights);
            int cursor = l.categoriesTop;
            for (int row = 0; row < CATEGORY_ROWS; row++) {
                l.rowTops[row] = cursor;
                cursor += l.rowHeights[row] + rowGapEff;
            }
            l.itemRowGap = 2;
            return l;
        }
    }

    /** 把可用高度按 4 行权重归一化分配（与真实界面一致）。 */
    private static void distributeRowHeights(int usable, float[] weights, int[] out) {
        float total = 0.0F;
        for (int i = 0; i < CATEGORY_ROWS; i++) {
            total += weightAt(weights, i);
        }
        int remaining = usable;
        for (int i = 0; i < CATEGORY_ROWS; i++) {
            int height;
            if (i == CATEGORY_ROWS - 1) {
                height = remaining;
            } else {
                height = Math.max(4, Math.round(usable * weightAt(weights, i) / total));
                int reserve = (CATEGORY_ROWS - 1 - i) * 4;
                height = Math.min(height, Math.max(4, remaining - reserve));
            }
            out[i] = height;
            remaining -= height;
        }
        if (out[CATEGORY_ROWS - 1] < 4) {
            int deficit = 4 - out[CATEGORY_ROWS - 1];
            out[CATEGORY_ROWS - 1] = 4;
            for (int i = CATEGORY_ROWS - 2; i >= 0 && deficit > 0; i--) {
                int take = Math.min(deficit, Math.max(0, out[i] - 4));
                out[i] -= take;
                deficit -= take;
            }
        }
        int diff = usable - (out[0] + out[1] + out[2] + out[3]);
        if (diff != 0) {
            out[CATEGORY_ROWS - 1] = Math.max(4, out[CATEGORY_ROWS - 1] + diff);
        }
    }

    private static float weightAt(float[] weights, int index) {
        if (weights == null || index < 0 || index >= weights.length) {
            return 1.0F;
        }
        return clampf(weights[index], 0.35F, 2.5F);
    }
}
