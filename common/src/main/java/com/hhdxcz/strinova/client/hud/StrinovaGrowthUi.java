package com.hhdxcz.strinova.client.hud;

/**
 * 「弦能增幅网络」界面的可视化自定义配置（布局 / 显隐 / 配色）。
 *
 * <p>入口：`/strinova_client growth edit`，或界面右上角「自定义」按钮；
 * 编辑结果保存在客户端配置 {@code config/strinova-client.json}，可用
 * `/strinova_client growth export|import` 导出/导入 JSON。</p>
 */
public final class StrinovaGrowthUi {

    // ==================== 显隐 ====================
    /** 顶部标题栏（标题 / 能量点 / 倒计时）。 */
    public boolean showHeader = true;
    /** 左侧角色立绘（占位）。 */
    public boolean showPortrait = true;
    /** 立绘区底部数值行（生命 / 护甲 / 移速）。 */
    public boolean showStats = true;
    /** 立绘区身份小字。 */
    public boolean showIdentity = true;
    /** 右侧武器栏。 */
    public boolean showWeapons = true;
    /** 底部觉醒区。 */
    public boolean showAwakenings = true;

    // ==================== 布局（比例，0 = 使用默认） ====================
    /** 立绘宽度 / 屏宽。 */
    public float portraitWidthRatio = 0.165F;
    /** 武器栏宽度 / 屏宽。 */
    public float weaponsWidthRatio = 0.082F;
    /** 觉醒区高度 / 屏高。 */
    public float awakenHeightRatio = 0.205F;
    /** 增幅网络列间距（px）。 */
    public float columnGap = 10.0F;
    /** 增幅网络行间距（px）。 */
    public float rowGap = 8.0F;

    // ==================== 外观 ====================
    /** 卡片/面板不透明度（0.2~1.0）。 */
    public float opacity = 0.97F;
    /** 背景额外压暗（0~0.8）。 */
    public float backgroundDim = 0.0F;
    /** 强调色；0 = 使用角色定位色。 */
    public int accentColor = 0;
    /** 升级项文字色；0 = 默认。 */
    public int itemTextColor = 0;
    /** 价格色；0 = 默认。 */
    public int priceColor = 0;
    /** 觉醒名称色；0 = 默认。 */
    public int awakenNameColor = 0;

    /** 恢复到默认值。 */
    public void reset() {
        StrinovaGrowthUi defaults = new StrinovaGrowthUi();
        showHeader = defaults.showHeader;
        showPortrait = defaults.showPortrait;
        showStats = defaults.showStats;
        showIdentity = defaults.showIdentity;
        showWeapons = defaults.showWeapons;
        showAwakenings = defaults.showAwakenings;
        portraitWidthRatio = defaults.portraitWidthRatio;
        weaponsWidthRatio = defaults.weaponsWidthRatio;
        awakenHeightRatio = defaults.awakenHeightRatio;
        columnGap = defaults.columnGap;
        rowGap = defaults.rowGap;
        opacity = defaults.opacity;
        backgroundDim = defaults.backgroundDim;
        accentColor = defaults.accentColor;
        itemTextColor = defaults.itemTextColor;
        priceColor = defaults.priceColor;
        awakenNameColor = defaults.awakenNameColor;
    }

    /** 读取/导入后做范围校正。 */
    public void sanitize() {
        portraitWidthRatio = clampf(portraitWidthRatio, 0.08F, 0.32F);
        weaponsWidthRatio = clampf(weaponsWidthRatio, 0.04F, 0.18F);
        awakenHeightRatio = clampf(awakenHeightRatio, 0.10F, 0.35F);
        columnGap = clampf(columnGap, 0.0F, 32.0F);
        rowGap = clampf(rowGap, 0.0F, 32.0F);
        opacity = clampf(opacity, 0.20F, 1.0F);
        backgroundDim = clampf(backgroundDim, 0.0F, 0.80F);
    }

    private static float clampf(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
