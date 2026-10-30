package com.hhdxcz.strinova.client.hud;

/**
 * HUD 绘制抽象层。
 *
 * <p>当前只有程序化实现 {@link StrinovaProgrammaticPainter}（几何图形 + 系统字体，零贴图资源）。
 * 后续要换成贴图图集版本时，只需新增一个实现类（图集 + 九宫格 + 自定义字体），
 * 布局模型、数据契约、各 HUD 部件与界面代码都不用改。</p>
 */
public interface StrinovaHudPainter {

    /** 文本宽度（像素）。 */
    int textWidth(String text);

    /** 字体行高（像素）。 */
    int textHeight();

    /** 绘制普通文本。 */
    void text(String text, int x, int y, int argb, boolean shadow);

    /** 绘制缩放文本（以左上角为原点）。 */
    void textScaled(String text, float x, float y, float scale, int argb, boolean shadow);

    /** 右对齐绘制文本，rightX 为文本右边界。 */
    void textRightAligned(String text, int rightX, int y, int argb, boolean shadow);

    /** 实心矩形。 */
    void rect(int x, int y, int width, int height, int argb);

    /** 主题面板：半透明底 + 细边框 + 左侧强调条。 */
    void panel(int x, int y, int width, int height, int accentArgb);

    /** 矩形描边。 */
    void border(int x, int y, int width, int height, int argb, int thickness);

    /** 进度条：ratio 0..1。 */
    void bar(int x, int y, int width, int height, float ratio, int fillArgb, int backgroundArgb);

    /** 图标占位：贴图版实现会替换为图集图标。 */
    void icon(String iconId, int x, int y, int size, int argb);

    /** 直线（程序化实现会逐像素填充）。 */
    void line(int x1, int y1, int x2, int y2, int argb);
}
