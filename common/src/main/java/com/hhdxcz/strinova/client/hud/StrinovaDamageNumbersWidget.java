package com.hhdxcz.strinova.client.hud;

import java.util.List;
import java.util.Locale;

/**
 * 伤害反馈部件：浮动伤害数字 + 受击方向指示。
 * 伤害数字颜色区分：普通命中白色、爆头橙色、被弦化减伤灰色。
 */
public final class StrinovaDamageNumbersWidget implements StrinovaHudWidget {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 90;
    private static final float RISE_PIXELS = 34.0F;

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.DAMAGE_NUMBERS;
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
        int centerX = x + WIDTH / 2;
        int centerY = y + HEIGHT / 2;
        long now = StrinovaHudState.now();

        // 伤害数字
        List<StrinovaHudState.DamageNumber> numbers = StrinovaHudState.damageNumbers();
        for (int i = 0; i < numbers.size(); i++) {
            StrinovaHudState.DamageNumber number = numbers.get(i);
            float progress = Math.max(0.0F, Math.min(1.0F,
                    (now - number.timeMs()) / (float) StrinovaHudState.DAMAGE_NUMBER_LIFETIME_MS));
            float alpha = 1.0F - progress;
            int alphaBits = Math.max(0, Math.min(255, Math.round(alpha * 255.0F))) << 24;
            int color = number.headshot()
                    ? (alphaBits | (StrinovaProgrammaticPainter.COLOR_WARN & 0x00FFFFFF))
                    : number.mitigated()
                    ? (alphaBits | (StrinovaProgrammaticPainter.COLOR_TEXT_DIM & 0x00FFFFFF))
                    : (alphaBits | (StrinovaProgrammaticPainter.COLOR_TEXT & 0x00FFFFFF));
            String text = String.format(Locale.ROOT, "%.0f", number.amount());
            int offsetX = ((i * 13) % 29) - 14;
            int offsetY = Math.round(-progress * RISE_PIXELS);
            float scale = number.headshot() ? 1.75F : 1.5F;
            int textWidth = Math.round(painter.textWidth(text) * scale);
            painter.textScaled(text, centerX + offsetX - textWidth / 2.0F, centerY + offsetY,
                    scale, color, true);
        }

        // 受击方向指示
        for (StrinovaHudState.DamageIndicator indicator : StrinovaHudState.damageIndicators()) {
            float progress = Math.max(0.0F, Math.min(1.0F,
                    (now - indicator.timeMs()) / (float) StrinovaHudState.DAMAGE_INDICATOR_LIFETIME_MS));
            int alpha = Math.max(0, Math.round((1.0F - progress) * 255.0F));
            if (alpha <= 0) {
                continue;
            }
            double angle = Math.toRadians(indicator.yawDegrees());
            double dirX = Math.sin(angle);
            double dirY = -Math.cos(angle);
            int innerRadius = 44;
            int outerRadius = 64;
            int color = (alpha << 24) | (StrinovaProgrammaticPainter.COLOR_HP_LOW & 0x00FFFFFF);
            int x1 = centerX + (int) Math.round(dirX * innerRadius);
            int y1 = centerY + (int) Math.round(dirY * innerRadius);
            int x2 = centerX + (int) Math.round(dirX * outerRadius);
            int y2 = centerY + (int) Math.round(dirY * outerRadius);
            painter.line(x1, y1, x2, y2, color);
            painter.line(x1 + 1, y1, x2 + 1, y2, color);
            painter.line(x1, y1 + 1, x2, y2 + 1, color);
        }
    }
}
