package com.hhdxcz.strinova.client.hud;

/**
 * 单个 HUD 部件。部件只负责“在给定左上角坐标处按测量尺寸画自己”，
 * 位置由 {@link StrinovaHudLayout} 决定，数据由 {@link StrinovaHudState} 提供，
 * 绘制由 {@link StrinovaHudPainter} 负责——三者解耦，方便后续换贴图实现。
 */
public interface StrinovaHudWidget {

    /** 该部件对应的布局元素 ID。 */
    StrinovaHudLayout.Element element();

    /** 测量宽度（像素），用于锚点对齐与布局编辑器命中框。 */
    int measureWidth(StrinovaHudPainter painter);

    /** 测量高度（像素）。 */
    int measureHeight(StrinovaHudPainter painter);

    /** 在 (x, y) 处绘制部件。 */
    void render(StrinovaHudPainter painter, int x, int y);
}
