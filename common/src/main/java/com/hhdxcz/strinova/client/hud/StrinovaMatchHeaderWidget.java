package com.hhdxcz.strinova.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * 对局顶部面板（对齐真实截图）：
 * 中间「目标 N 回合」+ 大号倒计时，左右是我的/敌方比分斜切面板；
 * 两侧各 5 个玩家头像（官方 T_Dynamic_RoleScoreboard_*，中途加入/未选角用 HalfEnterEmpty），
 * 外侧用银色斜切装饰翼收边。
 *
 * <p>所有元素仍走 {@link StrinovaHudPainter}，因此支持 HUD 编辑器自由拖动/缩放。</p>
 */
public final class StrinovaMatchHeaderWidget implements StrinovaHudWidget {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 60;
    private static final int PORTRAIT = 24;
    private static final int PORTRAIT_STEP = 24;

    private static final int COLOR_ALLY = 0xFFBC3B47;
    private static final int COLOR_ENEMY = 0xFF4B77DC;
    private static final int COLOR_ALLY_TEXT = 0xFFF27C88;
    private static final int COLOR_ENEMY_TEXT = 0xFF8FB4FF;
    private static final int COLOR_PLATE = 0xB3101620;
    private static final int COLOR_CENTER = 0xB30A111C;
    private static final int COLOR_SILVER = 0x99C8D2DE;
    private static final int COLOR_SILVER_DIM = 0x55C8D2DE;

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.MATCH_HEADER;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        return StrinovaMatchState.isActive() || StrinovaHudRenderer.isEditorRendering() ? WIDTH : 0;
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        return StrinovaMatchState.isActive() || StrinovaHudRenderer.isEditorRendering() ? HEIGHT : 0;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        boolean active = StrinovaMatchState.isActive();
        int centerX = x + WIDTH / 2;

        // 两侧银色斜切装饰翼
        drawWing(painter, x, y + HEIGHT / 2, 130, false);
        drawWing(painter, x + WIDTH - 130, y + HEIGHT / 2, 130, true);

        // 中央斜切暗底
        slanted(painter, centerX - 150, y + 3, 300, HEIGHT - 6, 12, COLOR_CENTER);
        painter.rect(x + 130, y + 1, WIDTH - 260, 1, COLOR_SILVER_DIM);
        painter.rect(x + 150, y + HEIGHT - 2, WIDTH - 300, 1, COLOR_SILVER_DIM);

        // 左右各 5 个头像（先画底，再画中央文字）
        List<StrinovaScoreboardState.Entry> allies = StrinovaScoreboardState.allies();
        List<StrinovaScoreboardState.Entry> enemies = StrinovaScoreboardState.enemies();
        int portraitY = y + (HEIGHT - PORTRAIT) / 2;
        for (int i = 0; i < 5; i++) {
            drawTeamPortrait(painter, x + 4 + i * PORTRAIT_STEP, portraitY, entry(allies, i), false);
            drawTeamPortrait(painter, x + WIDTH - 4 - PORTRAIT - (4 - i) * PORTRAIT_STEP, portraitY,
                    entry(enemies, i), true);
        }

        int alliesScore = active ? StrinovaMatchState.getAlliesScore() : 3;
        int enemiesScore = active ? StrinovaMatchState.getEnemiesScore() : 2;
        int round = active ? StrinovaMatchState.getRound() : 9;
        int seconds = active ? StrinovaMatchState.getRoundSecondsLeft() : 18;
        if (seconds < 0) {
            seconds = 0;
        }

        // 目标回合
        String roundText = Component.translatable("hud.strinova.match.target_round", round).getString();
        painter.text(roundText, centerX - painter.textWidth(roundText) / 2, y + 2,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        // 中央倒计时：官方 DP_Num 七段数码数字
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        float digitHeight = 22.0F;
        float timeWidth = StrinovaHudDigits.textWidth(time, digitHeight);
        StrinovaHudDigits.draw(painter, time, Math.round(centerX - timeWidth / 2.0F), y + 12,
                digitHeight, 0xFFFFFFFF, 1.0F);

        // 左右比分板（斜切）
        drawScorePlate(painter, centerX - 132, y + 20, 74, 22, 10, COLOR_PLATE, COLOR_ALLY,
                twoDigits(alliesScore), COLOR_ALLY_TEXT, true);
        drawScorePlate(painter, centerX + 58, y + 20, 74, 22, -10, COLOR_PLATE, COLOR_ENEMY,
                twoDigits(enemiesScore), COLOR_ENEMY_TEXT, false);

        // 中间比分连接线
        painter.rect(centerX - 40, y + 30, 80, 1, COLOR_SILVER_DIM);
        painter.rect(centerX - 2, y + 27, 4, 7, 0x99E8F0FA);
    }

    /** 斜切矩形（逐行偏移，模拟平行四边形）。 */
    private static void slanted(StrinovaHudPainter painter, int x, int y, int width, int height,
                                int skew, int argb) {
        if (height <= 0 || width <= 0) {
            return;
        }
        for (int i = 0; i < height; i++) {
            float t = height <= 1 ? 0.5F : (float) i / (height - 1);
            int offset = Math.round((t - 0.5F) * skew);
            painter.rect(x + offset, y + i, width, 1, argb);
        }
    }

    /** 两侧装饰翼：淡银色斜切块 + 亮边线。 */
    private static void drawWing(StrinovaHudPainter painter, int x, int centerY, int length, boolean flip) {
        int dir = flip ? -1 : 1;
        int startX = flip ? x + length : x;
        for (int i = 0; i < 18; i++) {
            float t = i / 17.0F;
            int w = Math.round(22 + 26 * t);
            int oy = Math.round((t - 0.5F) * 14);
            int px = flip ? startX - w - Math.round(6 * t) : startX + Math.round(6 * t);
            painter.rect(px, centerY + oy - 4, w, 8, 0x38C8D2DE);
        }
        painter.line(startX, centerY - 10, startX + dir * (length - 34), centerY - 10, COLOR_SILVER);
        painter.line(startX, centerY + 10, startX + dir * (length - 34), centerY + 10, COLOR_SILVER);
        int tx = startX + dir * (length - 28);
        painter.line(tx, centerY - 10, tx + dir * 8, centerY, COLOR_SILVER);
        painter.line(tx, centerY + 10, tx + dir * 8, centerY, COLOR_SILVER);
    }

    /** 比分斜切板：左侧红色、右侧蓝色。 */
    private static void drawScorePlate(StrinovaHudPainter painter, int x, int y, int width, int height,
                                       int skew, int bg, int edge, String score, int scoreColor,
                                       boolean allySide) {
        slanted(painter, x, y, width, height, skew, bg);
        for (int i = 0; i < height; i++) {
            float t = height <= 1 ? 0.5F : (float) i / (height - 1);
            int offset = Math.round((t - 0.5F) * skew);
            if (i == 0 || i == height - 1) {
                painter.rect(x + offset, y + i, width, 1, edge);
            }
        }
        int textY = y + (height - painter.textHeight()) / 2;
        if (allySide) {
            painter.textRightAligned(score, x + width - 10, textY, scoreColor, true);
            painter.rect(x + width - 6, y + 4, 2, height - 8, edge);
        } else {
            painter.text(score, x + 10, textY, scoreColor, true);
            painter.rect(x + 4, y + 4, 2, height - 8, edge);
        }
    }

    /** 顶部面板玩家头像：官方 RoleScoreboard 贴图；未选角/中途加入用 HalfEnterEmpty。 */
    private static void drawTeamPortrait(StrinovaHudPainter painter, int x, int y,
                                         StrinovaScoreboardState.Entry entry, boolean enemy) {
        painter.rect(x, y, PORTRAIT, PORTRAIT, 0xB3101820);
        ResourceLocation texture = entry == null ? null
                : StrinovaHudTextures.avatarByAgentName(entry.agentName());
        if (texture == null) {
            texture = StrinovaHudTextures.SCOREBOARD_EMPTY;
        }
        // 头像原图 56×32，按原比例缩到方框内（宽度占满、上下留暗底）
        int fitHeight = Math.max(4, Math.round(PORTRAIT * 32.0F / 56.0F));
        int fitY = y + (PORTRAIT - fitHeight) / 2;
        painter.textureRegion(texture, x, fitY, PORTRAIT, fitHeight,
                0.0F, 0.0F, 56, 32, 56, 32, 0xFFFFFFFF, 1.0F);
        // 边框：白色细框（本人金色、死亡压暗），不再用队伍色粗框
        int border;
        if (entry != null && entry.self()) {
            border = 0xFFFFD166;
        } else if (entry != null && entry.status() == StrinovaScoreboardState.Status.ALIVE) {
            border = 0xCCFFFFFF;
        } else {
            border = 0x55FFFFFF;
        }
        painter.border(x, y, PORTRAIT, PORTRAIT, border, 1);
        // 底部细高光，贴近真实 UI 的玻璃质感
        painter.rect(x + 1, y + PORTRAIT - 2, PORTRAIT - 2, 1, 0x33FFFFFF);
        if (entry != null && entry.status() == StrinovaScoreboardState.Status.ALIVE) {
            painter.rect(x + PORTRAIT - 4, y + PORTRAIT - 4, 3, 3, 0xFF5FE08C);
        }
    }

    private static StrinovaScoreboardState.Entry entry(List<StrinovaScoreboardState.Entry> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }
}
