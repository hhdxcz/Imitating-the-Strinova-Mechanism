package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * TAB 计分板（对齐真实布局）：单块居中大面板，上下两个队伍区块。
 * 列：名字 / 击败·阵亡·助攻 / 绝招 / 成长等级 / 主武器 / 输出分 / 技能分 / 协作分 / PING。
 * 按住 TAB 显示；激活时由 PlayerTabOverlayMixin 隐藏原版玩家列表。
 */
public final class StrinovaScoreboardOverlay {

    private static final int ACCENT_ALLY = 0xFFBC3B47;
    private static final int ACCENT_ENEMY = 0xFF4B77DC;
    private static final int ROW_HEIGHT = 22;
    private static final int PANEL_WIDTH = 1020;

    private StrinovaScoreboardOverlay() {
    }

    /** 当前是否应显示自定义计分板（TAB 按住 + 有进行中对局 + HUD 开启）。 */
    public static boolean isActive(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.options == null) {
            return false;
        }
        if (minecraft.screen != null || minecraft.options.hideGui) {
            return false;
        }
        StrinovaHudConfig config = StrinovaHudConfig.get();
        if (!config.hudEnabled || !config.scoreboardEnabled) {
            return false;
        }
        if (!StrinovaMatchState.isActive()) {
            return false;
        }
        if (!com.hhdxcz.strinova.StrinovaMod.isEnabled() && !StrinovaHudMockData.isEnabled()) {
            return false;
        }
        return minecraft.options.keyPlayerList.isDown();
    }

    /** 绘制整个计分板面板。 */
    public static void render(GuiGraphics graphics, StrinovaHudPainter painter) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        graphics.fill(0, 0, screenWidth, screenHeight, 0xE6000000);

        int panelWidth = Math.min(PANEL_WIDTH, screenWidth - 32);
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = Math.max(14, screenHeight / 2 - 200);

        String title = Component.translatable("hud.strinova.scoreboard.title").getString();
        painter.text(title, panelLeft + (panelWidth - painter.textWidth(title)) / 2, panelTop,
                StrinovaProgrammaticPainter.COLOR_TEXT, true);
        String detail = Component.translatable(StrinovaMatchState.getModeKey()).getString()
                + " · " + Component.translatable("hud.strinova.match.round").getString()
                + " " + StrinovaMatchState.getRound()
                + " · " + StrinovaMatchState.formattedTime();
        painter.text(detail, panelLeft + (panelWidth - painter.textWidth(detail)) / 2, panelTop + 12,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        int blockTop = panelTop + 30;
        int blockHeight = 22 + 18 + 5 * ROW_HEIGHT + 8;
        renderTeam(painter, panelLeft, blockTop, panelWidth, true);
        renderTeam(painter, panelLeft, blockTop + blockHeight, panelWidth, false);
    }

    private static void renderTeam(StrinovaHudPainter painter, int panelLeft, int top,
                                   int panelWidth, boolean ally) {
        List<StrinovaScoreboardState.Entry> entries =
                ally ? StrinovaScoreboardState.allies() : StrinovaScoreboardState.enemies();
        int accent = ally ? ACCENT_ALLY : ACCENT_ENEMY;
        String teamKey = ally ? StrinovaMatchState.getAlliesTeamKey() : StrinovaMatchState.getEnemiesTeamKey();
        String teamName = teamKey == null || teamKey.isEmpty()
                ? Component.translatable(ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString()
                : Component.translatable(teamKey).getString();
        int score = ally ? StrinovaMatchState.getAlliesScore() : StrinovaMatchState.getEnemiesScore();

        // 队伍头：徽章 + 队伍名 + 比分 + 存活数
        painter.rect(panelLeft, top, panelWidth, 22, withAlpha(accent, 0x66));
        painter.rect(panelLeft, top, 3, 22, accent);
        drawCrest(painter, panelLeft + 8, top + 2, 18, accent);
        painter.text(teamName, panelLeft + 32, top + 7, StrinovaProgrammaticPainter.COLOR_TEXT, true);
        String scoreText = String.valueOf(score);
        painter.textScaled(scoreText, panelLeft + 150, top + 2, 1.4F,
                StrinovaProgrammaticPainter.COLOR_TEXT, true);
        int alive = StrinovaScoreboardState.aliveCount(ally);
        String count = Component.translatable("hud.strinova.scoreboard.status").getString()
                + " " + alive + "/" + entries.size();
        painter.textRightAligned(count, panelLeft + panelWidth - 8, top + 7,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        // 列头
        int headerY = top + 24;
        String kdaHeader = Component.translatable("hud.strinova.scoreboard.kills").getString() + "/"
                + Component.translatable("hud.strinova.scoreboard.deaths").getString() + "/"
                + Component.translatable("hud.strinova.scoreboard.assists").getString();
        painter.text(Component.translatable("hud.strinova.scoreboard.agent").getString(),
                colNameX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(kdaHeader, colKdaX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.ultimate").getString(),
                colUltX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.growth").getString(),
                colGrowthX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.weapon").getString(),
                colWeaponX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.damage_score").getString(),
                colDamageX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.skill_score").getString(),
                colSkillX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.assist_score").getString(),
                colAssistX(panelLeft), headerY, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        painter.textRightAligned("PING", panelLeft + panelWidth - 10, headerY,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        // 玩家行
        int rowY = top + 22 + 18;
        for (StrinovaScoreboardState.Entry entry : entries) {
            boolean dead = entry.status() == StrinovaScoreboardState.Status.DEAD;
            int rowBg = entry.self() ? withAlpha(0xFFFFD166, 0x33)
                    : (dead ? 0x33101518 : withAlpha(accent, 0x22));
            painter.rect(panelLeft, rowY, panelWidth, ROW_HEIGHT - 1, rowBg);
            painter.rect(panelLeft, rowY, 2, ROW_HEIGHT - 1, entry.self() ? 0xFFFFD166 : accent);

            int avatarX = colNameX(panelLeft);
            int accentColor = agentAccent(entry.agentName(), accent);
            painter.rect(avatarX, rowY + 3, 16, 16, 0xAA101820);
            painter.rect(avatarX, rowY + 3, 2, 16, accentColor);
            painter.border(avatarX, rowY + 3, 16, 16, 0x66FFFFFF, 1);
            String initial = entry.agentName() == null || entry.agentName().isEmpty()
                    ? "?" : entry.agentName().substring(0, 1);
            painter.text(initial, avatarX + (16 - painter.textWidth(initial)) / 2, rowY + 7,
                    StrinovaProgrammaticPainter.COLOR_TEXT, false);
            if (entry.status() == StrinovaScoreboardState.Status.ALIVE) {
                painter.rect(avatarX + 11, rowY + 14, 4, 4, 0xFF5FE08C);
            }
            painter.text(entry.name(), avatarX + 22, rowY + 3,
                    entry.self() ? StrinovaProgrammaticPainter.COLOR_WARN
                            : dead ? StrinovaProgrammaticPainter.COLOR_TEXT_DIM
                            : StrinovaProgrammaticPainter.COLOR_TEXT, false);
            painter.text(entry.agentName(), avatarX + 22, rowY + 12,
                    StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

            String kda = entry.kills() + " / " + entry.deaths() + " / " + entry.assists();
            painter.text(kda, colKdaX(panelLeft), rowY + 7, StrinovaProgrammaticPainter.COLOR_TEXT, false);
            painter.text(entry.ultimatePoints() + "/" + entry.ultimateMax(),
                    colUltX(panelLeft), rowY + 7, 0xFF8FD8FF, false);

            // 成长等级：蓝/金色小方块 + 数字
            int growthColor = entry.growthLevel() >= 2 ? 0xFFDEB639 : 0xFF4B77DC;
            painter.rect(colGrowthX(panelLeft), rowY + 6, 14, 11, growthColor);
            painter.text(String.valueOf(entry.growthLevel()), colGrowthX(panelLeft) + 4, rowY + 7,
                    0xFF0A111C, false);

            painter.text(entry.weaponName(), colWeaponX(panelLeft), rowY + 7,
                    StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
            painter.text(String.valueOf(entry.damageScore()), colDamageX(panelLeft), rowY + 7,
                    StrinovaProgrammaticPainter.COLOR_TEXT, false);
            painter.text(String.valueOf(entry.skillScore()), colSkillX(panelLeft), rowY + 7,
                    StrinovaProgrammaticPainter.COLOR_TEXT, false);
            painter.text(String.valueOf(entry.assistScore()), colAssistX(panelLeft), rowY + 7,
                    StrinovaProgrammaticPainter.COLOR_TEXT, false);
            painter.textRightAligned(String.valueOf(entry.ping()), panelLeft + panelWidth - 10, rowY + 7,
                    entry.ping() > 100 ? StrinovaProgrammaticPainter.COLOR_WARN
                            : StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

            rowY += ROW_HEIGHT;
        }
    }

    // 列偏移（相对面板左边）
    private static int colNameX(int panelLeft) {
        return panelLeft + 10;
    }

    private static int colKdaX(int panelLeft) {
        return panelLeft + 190;
    }

    private static int colUltX(int panelLeft) {
        return panelLeft + 300;
    }

    private static int colGrowthX(int panelLeft) {
        return panelLeft + 365;
    }

    private static int colWeaponX(int panelLeft) {
        return panelLeft + 450;
    }

    private static int colDamageX(int panelLeft) {
        return panelLeft + 560;
    }

    private static int colSkillX(int panelLeft) {
        return panelLeft + 640;
    }

    private static int colAssistX(int panelLeft) {
        return panelLeft + 720;
    }

    /** 队伍徽章（程序化几何占位）。 */
    private static void drawCrest(StrinovaHudPainter painter, int x, int y, int size, int accent) {
        painter.rect(x, y, size, size, withAlpha(accent, 0x88));
        painter.border(x, y, size, size, accent, 1);
        int half = size / 2;
        for (int dy = -half + 3; dy <= half - 3; dy++) {
            int width = (half - 3) - Math.abs(dy);
            painter.rect(x + half - width, y + half + dy, width * 2 + 1, 1, 0xCCFFFFFF);
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

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** 供 mixin 判断：自定义计分板激活时需要隐藏原版玩家列表。 */
    public static boolean shouldHideVanillaPlayerList() {
        return isActive(Minecraft.getInstance());
    }
}
