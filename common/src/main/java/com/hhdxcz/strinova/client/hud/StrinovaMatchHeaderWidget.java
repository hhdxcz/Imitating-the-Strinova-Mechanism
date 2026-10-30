package com.hhdxcz.strinova.client.hud;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * 对局顶部比分面板（对齐真实布局）：
 * 左/右各 5 个角色头像（存活带绿勾）+ 中央「回合 / 倒计时 / 双方比分」。
 * 只在有进行中的对局时显示（当前由模拟数据激活；未来由服务端对局系统同步）。
 */
public final class StrinovaMatchHeaderWidget implements StrinovaHudWidget {

    private static final int WIDTH = 408;
    private static final int HEIGHT = 48;
    private static final int PORTRAIT = 20;
    private static final int PORTRAIT_STEP = 20;
    private static final int SIDE_WIDTH = 106;
    private static final int COLOR_ALLY = 0xFFBC3B47;
    private static final int COLOR_ENEMY = 0xFF4B77DC;

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
        List<StrinovaScoreboardState.Entry> allies = StrinovaScoreboardState.allies();
        List<StrinovaScoreboardState.Entry> enemies = StrinovaScoreboardState.enemies();

        // 两侧斜切装饰底条（程序化矩形近似）
        painter.rect(x, y + 10, SIDE_WIDTH, HEIGHT - 20, 0x66BC3B47);
        painter.rect(x + SIDE_WIDTH, y + 10, 6, HEIGHT - 20, 0x33BC3B47);
        painter.rect(x + WIDTH - SIDE_WIDTH - 6, y + 10, 6, HEIGHT - 20, 0x334B77DC);
        painter.rect(x + WIDTH - SIDE_WIDTH, y + 10, SIDE_WIDTH, HEIGHT - 20, 0x664B77DC);
        // 中央主面板
        painter.rect(x + SIDE_WIDTH + 6, y, WIDTH - 2 * (SIDE_WIDTH + 6), HEIGHT, 0xB30A111C);
        painter.border(x + SIDE_WIDTH + 6, y, WIDTH - 2 * (SIDE_WIDTH + 6), HEIGHT, 0x55335F73, 1);

        // 左右头像行
        for (int i = 0; i < 5; i++) {
            int allyX = x + 8 + i * PORTRAIT_STEP;
            drawPortrait(painter, allyX, y + (HEIGHT - PORTRAIT) / 2,
                    entry(allies, i), COLOR_ALLY);
            int enemyX = x + WIDTH - 8 - PORTRAIT - (4 - i) * PORTRAIT_STEP;
            drawPortrait(painter, enemyX, y + (HEIGHT - PORTRAIT) / 2,
                    entry(enemies, i), COLOR_ENEMY);
        }

        int centerX = x + WIDTH / 2;
        int alliesScore = active ? StrinovaMatchState.getAlliesScore() : 3;
        int enemiesScore = active ? StrinovaMatchState.getEnemiesScore() : 2;
        int round = active ? StrinovaMatchState.getRound() : 1;
        int seconds = active ? StrinovaMatchState.getRoundSecondsLeft() : 83;

        String roundText = Component.translatable("hud.strinova.match.target_round", round).getString();
        painter.text(roundText, centerX - painter.textWidth(roundText) / 2, y + 2,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        float timeScale = 1.6F;
        painter.textScaled(time, centerX - painter.textWidth(time) * timeScale / 2.0F, y + 11,
                timeScale, StrinovaProgrammaticPainter.COLOR_TEXT, true);

        // 下方比分：红（我方）/ 蓝（敌方）+ 斜切小底条
        int scoreY = y + 33;
        painter.rect(centerX - 42, scoreY, 34, 12, 0x66BC3B47);
        painter.rect(centerX + 8, scoreY, 34, 12, 0x664B77DC);
        String allyScore = twoDigits(alliesScore);
        String enemyScore = twoDigits(enemiesScore);
        painter.textRightAligned(allyScore, centerX - 10, scoreY + 2, 0xFFFF8A93, true);
        painter.text(enemyScore, centerX + 10, scoreY + 2, 0xFF8FB4FF, true);
        painter.text(":", centerX - painter.textWidth(":") / 2, scoreY + 2,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
    }

    private static StrinovaScoreboardState.Entry entry(List<StrinovaScoreboardState.Entry> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }

    /** 绘制一个角色头像方块：定位色左边条 + 名字首字 + 存活绿勾。 */
    private static void drawPortrait(StrinovaHudPainter painter, int x, int y,
                                     StrinovaScoreboardState.Entry entry, int fallbackAccent) {
        int accent = fallbackAccent;
        String label = "?";
        boolean alive = false;
        if (entry != null) {
            accent = agentAccent(entry.agentName(), fallbackAccent);
            if (entry.agentName() != null && !entry.agentName().isEmpty()) {
                label = entry.agentName().substring(0, 1);
            }
            alive = entry.status() == StrinovaScoreboardState.Status.ALIVE;
        }
        painter.rect(x, y, PORTRAIT, PORTRAIT, 0xB3101820);
        painter.rect(x, y, 2, PORTRAIT, accent);
        painter.border(x, y, PORTRAIT, PORTRAIT, 0x66FFFFFF, 1);
        painter.text(label, x + (PORTRAIT - painter.textWidth(label)) / 2,
                y + (PORTRAIT - painter.textHeight()) / 2, 0xFFEAF6FF, false);
        if (alive) {
            painter.rect(x + PORTRAIT - 6, y + PORTRAIT - 6, 5, 5, 0xFF5FE08C);
            painter.border(x + PORTRAIT - 6, y + PORTRAIT - 6, 5, 5, 0xAA000000, 1);
        }
    }

    private static int agentAccent(String agentName, int fallback) {
        if (agentName == null) {
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
