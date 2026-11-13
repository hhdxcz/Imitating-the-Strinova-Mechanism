package com.hhdxcz.strinova.client.hud;

import net.minecraft.resources.ResourceLocation;

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

    /**
     * 绘制整张贴图（按目标宽高拉伸）。
     *
     * @param texture 贴图 ResourceLocation
     * @param x       左上角 X
     * @param y       左上角 Y
     * @param width   目标宽
     * @param height  目标高
     * @param argb    颜色混合（一般传 0xFFFFFFFF 保持原色）
     * @param alpha   整体透明度 0~1
     */
    void texture(ResourceLocation texture, int x, int y, int width, int height, int argb, float alpha);

    /**
     * 绘制贴图的一块区域（用于精灵表/九宫格/数码数字）。
     *
     * @param u         区域左上角 U（像素）
     * @param v         区域左上角 V（像素）
     * @param uWidth    区域宽（像素）
     * @param vHeight   区域高（像素）
     * @param texWidth  原图宽（像素）
     * @param texHeight 原图高（像素）
     */
    void textureRegion(ResourceLocation texture, int x, int y, int width, int height,
                       float u, float v, int uWidth, int vHeight, int texWidth, int texHeight,
                       int argb, float alpha);

    /** 以 (centerX, centerY) 为中心压入旋转/镜像变换（配对 popTransform）。 */
    void pushTransform(float centerX, float centerY, float rotationDeg,
                       boolean mirrorX, boolean mirrorY);

    /** 弹出 pushTransform 的变换。 */
    void popTransform();
}
