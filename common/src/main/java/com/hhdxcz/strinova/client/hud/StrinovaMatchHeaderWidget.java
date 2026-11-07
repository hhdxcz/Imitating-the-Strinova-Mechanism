package com.hhdxcz.strinova.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Locale;

/**
 * 对局顶部面板（对齐真实截图）：
 * 中间「目标 N 回合」+ 大号倒计时，左右是我的/敌方比分斜切面板，
 * 左侧为本机角色头像 + 段位徽记，两侧用银色斜切装饰翼收边。
 *
 * <p>所有元素仍走 {@link StrinovaHudPainter}，因此支持 HUD 编辑器自由拖动/缩放，
 * 也兼容后续贴图版 painter。</p>
 */
public final class StrinovaMatchHeaderWidget implements StrinovaHudWidget {

    private static final int WIDTH = 520;
    private static final int HEIGHT = 58;

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
        drawWing(painter, x, y + HEIGHT / 2, 150, false);
        drawWing(painter, x + WIDTH - 150, y + HEIGHT / 2, 150, true);

        // 中央斜切暗底
        slanted(painter, centerX - 160, y + 3, 320, HEIGHT - 6, 12, COLOR_CENTER);
        painter.rect(x + 70, y + 1, WIDTH - 140, 1, COLOR_SILVER_DIM);
        painter.rect(x + 90, y + HEIGHT - 2, WIDTH - 180, 1, COLOR_SILVER_DIM);

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

        // 中央倒计时：官方 DP_Num 七段数码数字（冒号用两个方块绘制）
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        float digitHeight = 22.0F;
        float timeWidth = StrinovaHudDigits.textWidth(time, digitHeight);
        StrinovaHudDigits.draw(painter, time, Math.round(centerX - timeWidth / 2.0F), y + 12,
                digitHeight, 0xFFFFFFFF, 1.0F);

        // 左右比分板（斜切）
        drawScorePlate(painter, centerX - 132, y + 18, 74, 24, 10, COLOR_PLATE, COLOR_ALLY,
                twoDigits(alliesScore), COLOR_ALLY_TEXT, true);
        drawScorePlate(painter, centerX + 58, y + 18, 74, 24, -10, COLOR_PLATE, COLOR_ENEMY,
                twoDigits(enemiesScore), COLOR_ENEMY_TEXT, false);

        // 中间比分连接线
        painter.rect(centerX - 40, y + 29, 80, 1, COLOR_SILVER_DIM);
        painter.rect(centerX - 2, y + 26, 4, 7, 0x99E8F0FA);

        // 左侧本机角色头像 + 段位徽记
        drawSelfPortrait(painter, centerX - 238, y + 10, 36);
        drawRankBadge(painter, centerX - 220, y + 50);
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
        // 斜切淡底
        for (int i = 0; i < 18; i++) {
            float t = i / 17.0F;
            int w = Math.round(22 + 26 * t);
            int oy = Math.round((t - 0.5F) * 14);
            int px = flip ? startX - w - Math.round(6 * t) : startX + Math.round(6 * t);
            painter.rect(px, centerY + oy - 4, w, 8, 0x38C8D2DE);
        }
        // 亮边斜线
        painter.line(startX, centerY - 10, startX + dir * (length - 40), centerY - 10, COLOR_SILVER);
        painter.line(startX, centerY + 10, startX + dir * (length - 40), centerY + 10, COLOR_SILVER);
        // 端点小三角
        int tx = startX + dir * (length - 34);
        painter.line(tx, centerY - 10, tx + dir * 8, centerY, COLOR_SILVER);
        painter.line(tx, centerY + 10, tx + dir * 8, centerY, COLOR_SILVER);
    }

    /** 比分斜切板：左侧红色、右侧蓝色，数字右/左对齐。 */
    private static void drawScorePlate(StrinovaHudPainter painter, int x, int y, int width, int height,
                                       int skew, int bg, int edge, String score, int scoreColor,
                                       boolean allySide) {
        slanted(painter, x, y, width, height, skew, bg);
        // 顶/底亮边
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

    /** 本机角色头像：定位色左边条 + 首字 + 存活绿点。 */
    private static void drawSelfPortrait(StrinovaHudPainter painter, int x, int y, int size) {
        StrinovaScoreboardState.Entry self = null;
        for (StrinovaScoreboardState.Entry entry : StrinovaScoreboardState.allies()) {
            if (entry.self()) {
                self = entry;
                break;
            }
        }
        String agentName = self == null ? null : self.agentName();
        if (agentName == null || agentName.isEmpty()) {
            agentName = selectedAgentName();
        }
        String label = agentName == null || agentName.isEmpty() ? "?" : agentName.substring(0, 1);
        int accent = agentAccent(agentName, COLOR_ALLY);
        painter.rect(x, y, size, size, 0xB3101820);
        ResourceLocation avatarTexture = StrinovaHudTextures.avatarByAgentName(agentName);
        if (avatarTexture != null) {
            painter.textureRegion(avatarTexture, x, y, size, size,
                    16.0F, 0.0F, 32, 32, 56, 32, 0xFFFFFFFF, 1.0F);
        } else {
            painter.text(label, x + (size - painter.textWidth(label)) / 2,
                    y + (size - painter.textHeight()) / 2, 0xFFEAF6FF, true);
        }
        painter.rect(x, y, 2, size, accent);
        painter.border(x, y, size, size, 0x88FFFFFF, 1);
        if (self == null || self.status() == StrinovaScoreboardState.Status.ALIVE) {
            painter.rect(x + size - 7, y + size - 7, 6, 6, 0xFF5FE08C);
            painter.border(x + size - 7, y + size - 7, 6, 6, 0xAA000000, 1);
        }
    }

    /** 段位徽记（简化的三角金徽）。 */
    private static void drawRankBadge(StrinovaHudPainter painter, int centerX, int y) {
        painter.line(centerX - 9, y + 2, centerX, y - 4, 0xFFE8C56A);
        painter.line(centerX, y - 4, centerX + 9, y + 2, 0xFFE8C56A);
        painter.line(centerX - 9, y + 2, centerX + 9, y + 2, 0xFFE8C56A);
        painter.rect(centerX - 1, y + 4, 2, 2, 0xFFE8C56A);
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }

    private static String selectedAgentName() {
        try {
            String id = StrinovaHudConfig.get().selectedAgentId;
            if (id == null || id.isEmpty()) {
                return "";
            }
            StrinovaAgentRoster.Agent agent = StrinovaAgentRoster.byId(id);
            return agent == null ? "" : agent.name();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int agentAccent(String agentName, int fallback) {
        if (agentName == null || agentName.isEmpty()) {
            return fallback;
        }
        for (StrinovaAgentRoster.Agent agent : StrinovaAgentRoster.agents()) {
            if (agent.name().equals(agentName)) {
                return agent.accentColor();
            }
        }
        return fallback;
    }
}
