package com.hhdxcz.strinova.client.hud;

/**
 * 准星部件：动态扩散准星 + 命中标记（hitmarker）。
 * 绘制参数来自客户端配置 {@link StrinovaHudConfig}，扩散值来自 {@link StrinovaHudState#getCrosshairSpread()}。
 */
public final class StrinovaCrosshairWidget implements StrinovaHudWidget {

    private static final int WIDTH = 72;
    private static final int HEIGHT = 72;

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.CROSSHAIR;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        return WIDTH;
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        return HEIGHT;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int centerX = x + WIDTH / 2;
        int centerY = y + HEIGHT / 2;
        int thickness = config.crosshairThickness;
        int length = config.crosshairLength;
        int gap = config.crosshairGap
                + (config.crosshairDynamic ? Math.round(StrinovaHudState.getCrosshairSpread() * 8.0F) : 0);
        int color = config.crosshairColor;

        // 上
        painter.rect(centerX - thickness / 2, centerY - gap - length, thickness, length, color);
        // 下
        painter.rect(centerX - thickness / 2, centerY + gap, thickness, length, color);
        // 左
        painter.rect(centerX - gap - length, centerY - thickness / 2, length, thickness, color);
        // 右
        painter.rect(centerX + gap, centerY - thickness / 2, length, thickness, color);
        if (config.crosshairDot) {
            painter.rect(centerX - 1, centerY - 1, 2, 2, color);
        }

        // 命中标记
        float hit = StrinovaHudState.hitMarkerProgress();
        if (hit >= 0.0F) {
            int alpha = Math.max(0, Math.round((1.0F - hit) * 255.0F));
            int markerColor = (alpha << 24) | (StrinovaHudState.isHitMarkerKill()
                    ? (StrinovaProgrammaticPainter.COLOR_ACCENT_MAGENTA & 0x00FFFFFF)
                    : 0x00FFFFFF);
            int inner = 6;
            int outer = 11;
            painter.line(centerX - inner, centerY - inner, centerX - outer, centerY - outer, markerColor);
            painter.line(centerX + inner, centerY - inner, centerX + outer, centerY - outer, markerColor);
            painter.line(centerX - inner, centerY + inner, centerX - outer, centerY + outer, markerColor);
            painter.line(centerX + inner, centerY + inner, centerX + outer, centerY + outer, markerColor);
        }
    }
}
