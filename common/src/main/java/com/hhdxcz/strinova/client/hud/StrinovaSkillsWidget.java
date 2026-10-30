package com.hhdxcz.strinova.client.hud;

/**
 * 技能部件（底部居中，对齐真实布局）：Q / X / C 三个菱形技能图标。
 * Q = 战术技能（冷却），X = 绝招（显示点数），C = 第三技能/装备槽（冷却）。
 */
public final class StrinovaSkillsWidget implements StrinovaHudWidget {

    private static final int WIDTH = 210;
    private static final int HEIGHT = 54;
    private static final int HALF = 15;
    private static final int CY = 18;
    private static final int[] CENTERS = {35, 105, 175};

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.SKILLS;
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
        int centerY = y + CY;
        int ultPoints = StrinovaHudState.getUltimatePoints();
        int ultMax = StrinovaHudState.getUltimateMax();

        // Q：战术技能
        float qCooldown = StrinovaHudState.getSkillCooldown(0);
        boolean qReady = StrinovaHudState.isSkillReady(0) && qCooldown <= 0.0F;
        drawSkillDiamond(painter, x + CENTERS[0], centerY, qReady,
                StrinovaProgrammaticPainter.COLOR_ACCENT_CYAN, "Q");
        if (qReady) {
            painter.rect(x + CENTERS[0] - 12, centerY + HALF + 3, 24, 2,
                    StrinovaProgrammaticPainter.COLOR_WARN);
        } else {
            String seconds = String.valueOf(Math.max(1, Math.round(qCooldown * 30.0F)));
            painter.textRightAligned(seconds, x + CENTERS[0] + 12, centerY + HALF + 2,
                    StrinovaProgrammaticPainter.COLOR_WARN, false);
        }

        // X：绝招（点数 0/7）
        boolean ultReady = ultPoints >= ultMax;
        drawDiamond(painter, x + CENTERS[1], centerY, HALF + 3, ultReady
                ? StrinovaProgrammaticPainter.COLOR_ACCENT_MAGENTA : 0x88445566);
        drawDiamond(painter, x + CENTERS[1], centerY, HALF, 0x99101820);
        String points = ultPoints + "/" + ultMax;
        painter.text(points, x + CENTERS[1] - painter.textWidth(points) / 2, centerY - 4,
                ultReady ? StrinovaProgrammaticPainter.COLOR_ACCENT_MAGENTA
                        : StrinovaProgrammaticPainter.COLOR_TEXT, true);
        painter.text("X", x + CENTERS[1] - painter.textWidth("X") / 2, centerY + 6,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        // C：第三技能槽
        float cCooldown = StrinovaHudState.getSkillCooldown(2);
        boolean cReady = StrinovaHudState.isSkillReady(2) && cCooldown <= 0.0F;
        drawSkillDiamond(painter, x + CENTERS[2], centerY, cReady,
                cReady ? StrinovaProgrammaticPainter.COLOR_ACCENT_CYAN : 0xFF8899AA, "C");

        // 底部键帽
        for (int i = 0; i < 3; i++) {
            String key = switch (i) {
                case 0 -> "Q";
                case 1 -> "X";
                default -> "C";
            };
            int keyX = x + CENTERS[i] - 7;
            int keyY = centerY + HALF + 8;
            painter.rect(keyX, keyY, 14, 10, 0xAA101820);
            painter.border(keyX, keyY, 14, 10, 0x55445566, 1);
            painter.text(key, keyX + (14 - painter.textWidth(key)) / 2, keyY + 1,
                    StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        }
    }

    private static void drawSkillDiamond(StrinovaHudPainter painter, int cx, int cy,
                                         boolean ready, int color, String label) {
        drawDiamond(painter, cx, cy, HALF + 3, ready ? color : 0x88445566);
        drawDiamond(painter, cx, cy, HALF, 0x99101820);
        painter.text(label, cx - painter.textWidth(label) / 2, cy - 5,
                ready ? StrinovaProgrammaticPainter.COLOR_TEXT
                        : StrinovaProgrammaticPainter.COLOR_TEXT_DIM, true);
    }

    /** 程序化菱形填充（逐行矩形近似）。 */
    private static void drawDiamond(StrinovaHudPainter painter, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int width = radius - Math.abs(dy);
            painter.rect(cx - width, cy + dy, width * 2 + 1, 1, color);
        }
    }
}
