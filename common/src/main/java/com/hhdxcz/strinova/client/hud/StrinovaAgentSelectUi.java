package com.hhdxcz.strinova.client.hud;

/**
 * 「选角界面」的可视化自定义配置（布局 / 显隐 / 配色）。
 *
 * <p>入口：`/strinova_client agent edit`，或界面右上角「自定义」按钮；
 * 编辑结果保存在客户端配置 {@code config/strinova-client.json}，可用
 * `/strinova_client agent export|import` 导出/导入 JSON。</p>
 */
public final class StrinovaAgentSelectUi {

    // ==================== 显隐 ====================
    /** 顶部阶段条（阵营 / 选择角色 / 比分 / 倒计时）。 */
    public boolean showTopBar = true;
    /** 左侧队友列。 */
    public boolean showTeam = true;
    /** 中央立绘区。 */
    public boolean showPortrait = true;
    /** 中央角色简介。 */
    public boolean showIntro = true;
    /** 中央个性语录。 */
    public boolean showQuote = true;
    /** 右侧信息面板整体。 */
    public boolean showRightPanel = true;
    /** 右侧技能卡 + 技能详情。 */
    public boolean showSkills = true;
    /** 右侧武器面板。 */
    public boolean showWeaponPanel = true;
    /** 右侧「武器选择 / 外观选择」页签。 */
    public boolean showTabs = true;
    /** 底部 2×12 头像网格。 */
    public boolean showGrid = true;
    /** 底部「锁定角色」按钮。 */
    public boolean showLockButton = true;
    /** 底部阵营限制提示。 */
    public boolean showNotice = true;

    // ==================== 布局（比例） ====================
    /** 队友列宽度 / 屏宽。 */
    public float teamWidthRatio = 0.1225F;
    /** 右侧信息面板宽度 / 屏宽。 */
    public float rightWidthRatio = 0.24375F;
    /** 锁定按钮高度（像素，18~40）。 */
    public float lockHeight = 26.0F;

    // ==================== 外观 ====================
    /** 面板不透明度（0.2~1.0）。 */
    public float opacity = 0.97F;
    /** 背景额外压暗（0~0.8）。 */
    public float backgroundDim = 0.0F;
    /** 强调色；0 = 使用角色定位色。 */
    public int accentColor = 0;
    /** 中央角色名颜色；0 = 默认。 */
    public int nameColor = 0;
    /** 右侧头衔块角色名颜色；0 = 默认（金色）。 */
    public int headerNameColor = 0;
    /** 头像格背景色；0 = 默认。 */
    public int gridCellColor = 0;

    /** 恢复到默认值。 */
    public void reset() {
        StrinovaAgentSelectUi defaults = new StrinovaAgentSelectUi();
        showTopBar = defaults.showTopBar;
        showTeam = defaults.showTeam;
        showPortrait = defaults.showPortrait;
        showIntro = defaults.showIntro;
        showQuote = defaults.showQuote;
        showRightPanel = defaults.showRightPanel;
        showSkills = defaults.showSkills;
        showWeaponPanel = defaults.showWeaponPanel;
        showTabs = defaults.showTabs;
        showGrid = defaults.showGrid;
        showLockButton = defaults.showLockButton;
        showNotice = defaults.showNotice;
        teamWidthRatio = defaults.teamWidthRatio;
        rightWidthRatio = defaults.rightWidthRatio;
        lockHeight = defaults.lockHeight;
        opacity = defaults.opacity;
        backgroundDim = defaults.backgroundDim;
        accentColor = defaults.accentColor;
        nameColor = defaults.nameColor;
        headerNameColor = defaults.headerNameColor;
        gridCellColor = defaults.gridCellColor;
    }

    /** 读取/导入后做范围校正。 */
    public void sanitize() {
        teamWidthRatio = clampf(teamWidthRatio, 0.06F, 0.24F);
        rightWidthRatio = clampf(rightWidthRatio, 0.16F, 0.36F);
        lockHeight = clampf(lockHeight, 18.0F, 40.0F);
        opacity = clampf(opacity, 0.20F, 1.0F);
        backgroundDim = clampf(backgroundDim, 0.0F, 0.80F);
    }

    private static float clampf(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
