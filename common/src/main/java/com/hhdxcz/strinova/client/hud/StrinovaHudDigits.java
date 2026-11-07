package com.hhdxcz.strinova.client.hud;

/**
 * 官方 7 段数码数字（DP_Num 精灵表）绘制器。
 *
 * <p>精灵表 1870×256，共 11 格，每格 170×256；第 0~9 格对应数字 0~9。
 * 仅依赖 {@link StrinovaHudPainter#textureRegion}，因此程序化/贴图版 painter 都能用。</p>
 *
 * <p>用于顶部面板的倒计时（冒号用两个小方块绘制）。</p>
 */
public final class StrinovaHudDigits {

    private static final int CELL_COUNT = 11;
    private static final int CELL_WIDTH = 170;
    private static final int CELL_HEIGHT = 256;
    private static final int TEX_WIDTH = 1870;

    private StrinovaHudDigits() {
    }

    /** 单个数字宽度（按目标高度等比缩放）。 */
    public static float digitWidth(float height) {
        return CELL_WIDTH * height / CELL_HEIGHT;
    }

    /** 文本总宽度（数字按格宽、冒号按两个点宽 + 间距估算）。 */
    public static float textWidth(String text, float height) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        float digit = digitWidth(height);
        float gap = Math.max(1.0F, height * 0.05F);
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                width += digit + gap;
            } else if (c == ':') {
                width += Math.max(2.0F, digit * 0.22F) + gap;
            } else if (c == ' ') {
                width += gap * 2;
            }
        }
        return Math.max(0.0F, width - gap);
    }

    /** 绘制数字串（支持 0~9 与冒号）。 */
    public static void draw(StrinovaHudPainter painter, String text, int x, int y,
                            float height, int tint, float alpha) {
        if (painter == null || text == null || text.isEmpty() || height <= 0.0F) {
            return;
        }
        float digit = digitWidth(height);
        float gap = Math.max(1.0F, height * 0.05F);
        int drawHeight = Math.max(1, Math.round(height));
        int drawDigit = Math.max(1, Math.round(digit));
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                int index = Math.min(CELL_COUNT - 1, c - '0');
                painter.textureRegion(StrinovaHudTextures.DIGITS,
                        Math.round(cursor), y, drawDigit, drawHeight,
                        index * CELL_WIDTH, 0, CELL_WIDTH, CELL_HEIGHT,
                        TEX_WIDTH, CELL_HEIGHT, tint, alpha);
                cursor += digit + gap;
            } else if (c == ':') {
                int dot = Math.max(2, Math.round(height * 0.1F));
                int dotX = Math.round(cursor + digit * 0.05F);
                int dot1Y = y + Math.round(height * 0.3F);
                int dot2Y = y + Math.round(height * 0.66F);
                painter.rect(dotX, dot1Y, dot, dot, tint);
                painter.rect(dotX, dot2Y, dot, dot, tint);
                cursor += Math.max(2.0F, digit * 0.22F) + gap;
            } else if (c == ' ') {
                cursor += gap * 2;
            }
        }
    }
}
