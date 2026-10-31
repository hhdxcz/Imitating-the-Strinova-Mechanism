package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * TAB 计分板（对齐真实布局与填色风格）：
 * 一整块不透明深色大面板，左侧队伍徽章 + 大比分竖栏，右侧上下两个队伍区块
 * （队名色带 / 列头 / 5 行玩家数据）；本人行金色高亮、阵亡行灰显。
 *
 * <p>所有尺寸按面板宽度比例计算，面板宽度 = min(1020, GUI宽 - 16)，
 * 因此在任何 GUI 缩放下列宽都会均分铺满，不会出现列挤在右边、左侧大片空白的问题。</p>
 */
public final class StrinovaScoreboardOverlay {

    // ==================== 颜色（填色风格，参考真实截图） ====================
    private static final int DIM = 0xB3000000;
    private static final int PANEL_BG = 0xF20C1016;
    private static final int PANEL_BORDER = 0x55FFFFFF;
    private static final int TITLE_BAR = 0xFF11161D;
    private static final int TEAM_ALLY = 0xFFBF3F4C;
    private static final int TEAM_ENEMY = 0xFF3F6AD8;
    private static final int HEADER_ROW = 0xFF0F141A;
    private static final int ROW_BG = 0xFF171D25;
    private static final int ROW_ALT = 0xFF1A212A;
    private static final int ROW_DEAD = 0xFF12161B;
    private static final int ROW_SELF = 0xFF3B3320;
    private static final int TEXT = 0xFFEAF2FA;
    private static final int TEXT_DIM = 0xFF9BAEBF;
    private static final int GOLD = 0xFFFFD166;
    private static final int GREEN = 0xFF5FE08C;

    // ==================== 尺寸 ====================
    private static final int MAX_PANEL_WIDTH = 1020;
    private static final int TITLE_HEIGHT = 26;
    private static final int SIDEBAR_WIDTH = 96;
    private static final int TEAM_HEADER_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 14;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_COUNT = 5;
    private static final int BLOCK_PADDING_BOTTOM = 6;

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
        // 背景压暗，让不透明面板更清晰
        graphics.fill(0, 0, screenWidth, screenHeight, DIM);

        int panelWidth = Math.max(360, Math.min(MAX_PANEL_WIDTH, screenWidth - 16));
        int blockHeight = TEAM_HEADER_HEIGHT + COLUMN_HEADER_HEIGHT + ROW_COUNT * ROW_HEIGHT + BLOCK_PADDING_BOTTOM;
        int panelHeight = TITLE_HEIGHT + blockHeight * 2 + 2;
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = Math.max(8, (screenHeight - panelHeight) / 2);

        // 面板底色（不透明填充）+ 细边框
        painter.rect(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BG);
        painter.border(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BORDER, 1);

        // 顶部标题条
        painter.rect(panelLeft + 1, panelTop + 1, panelWidth - 2, TITLE_HEIGHT - 1, TITLE_BAR);
        String title = Component.translatable("hud.strinova.scoreboard.title").getString();
        painter.text(title, panelLeft + (panelWidth - painter.textWidth(title)) / 2, panelTop + 7, TEXT, true);
        String detail = Component.translatable(StrinovaMatchState.getModeKey()).getString()
                + " · " + Component.translatable("hud.strinova.match.round").getString()
                + " " + StrinovaMatchState.getRound()
                + " · " + StrinovaMatchState.formattedTime();
        painter.textRightAligned(detail, panelLeft + panelWidth - 10, panelTop + 8, TEXT_DIM, false);

        int blockTop = panelTop + TITLE_HEIGHT;
        renderTeam(painter, panelLeft, blockTop, panelWidth, blockHeight, true);
        renderTeam(painter, panelLeft, blockTop + blockHeight + 1, panelWidth, blockHeight, false);
    }

    // ==================== 单个队伍区块 ====================

    private static void renderTeam(StrinovaHudPainter painter, int panelLeft, int top,
                                   int panelWidth, int blockHeight, boolean ally) {
        List<StrinovaScoreboardState.Entry> entries =
                ally ? StrinovaScoreboardState.allies() : StrinovaScoreboardState.enemies();
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        String teamKey = ally ? StrinovaMatchState.getAlliesTeamKey() : StrinovaMatchState.getEnemiesTeamKey();
        String teamName = teamKey == null || teamKey.isEmpty()
                ? Component.translatable(ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString()
                : Component.translatable(teamKey).getString();
        int score = ally ? StrinovaMatchState.getAlliesScore() : StrinovaMatchState.getEnemiesScore();

        int sidebarW = SIDEBAR_WIDTH;
        int contentX = panelLeft + sidebarW;
        int contentW = panelWidth - sidebarW - 2;

        // ---- 左侧竖栏：队伍徽章 + 大比分（队伍色填充） ----
        painter.rect(panelLeft + 1, top, sidebarW - 1, blockHeight - 1, withAlpha(accent, 0x66));
        painter.rect(panelLeft + 1, top, 2, blockHeight - 1, accent);
        int crestSize = 30;
        drawCrest(painter, panelLeft + (sidebarW - crestSize) / 2, top + 14, crestSize, accent);
        String scoreText = String.valueOf(score);
        float scoreScale = 2.4F;
        float scoreWidth = painter.textWidth(scoreText) * scoreScale;
        painter.textScaled(scoreText,
                panelLeft + (sidebarW - scoreWidth) / 2,
                top + 14 + crestSize + 10, scoreScale, TEXT, true);

        // ---- 队伍名色带 ----
        painter.rect(contentX, top, contentW, TEAM_HEADER_HEIGHT, withAlpha(accent, 0xB8));
        painter.text(teamName, contentX + 8, top + 6, TEXT, true);
        int alive = StrinovaScoreboardState.aliveCount(ally);
        String aliveText = Component.translatable("hud.strinova.scoreboard.status").getString()
                + " " + alive + "/" + Math.max(entries.size(), ROW_COUNT);
        painter.textRightAligned(aliveText, contentX + contentW - 8, top + 6, 0xDDEEF7FF, false);

        // ---- 列头（深色填充条） ----
        int headerY = top + TEAM_HEADER_HEIGHT;
        painter.rect(contentX, headerY, contentW, COLUMN_HEADER_HEIGHT, HEADER_ROW);
        int[] anchors = columnAnchors(contentW);
        int headerTextY = headerY + 3;
        painter.text(Component.translatable("hud.strinova.scoreboard.agent").getString(),
                contentX + anchors[0], headerTextY, TEXT_DIM, false);
        painter.text(kdaHeader(), contentX + anchors[1], headerTextY, TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.ultimate").getString(),
                contentX + anchors[2], headerTextY, TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.growth").getString(),
                contentX + anchors[3], headerTextY, TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.weapon").getString(),
                contentX + anchors[4], headerTextY, TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.damage_score").getString(),
                contentX + anchors[5], headerTextY, TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.skill_score").getString(),
                contentX + anchors[6], headerTextY, TEXT_DIM, false);
        painter.text(Component.translatable("hud.strinova.scoreboard.assist_score").getString(),
                contentX + anchors[7], headerTextY, TEXT_DIM, false);
        painter.textRightAligned("PING", contentX + contentW - 6, headerTextY, TEXT_DIM, false);

        // ---- 玩家行（固定 5 行，空位也填色，保持布局稳定） ----
        int rowTop = headerY + COLUMN_HEADER_HEIGHT;
        for (int i = 0; i < ROW_COUNT; i++) {
            int rowY = rowTop + i * ROW_HEIGHT;
            boolean has = i < entries.size();
            StrinovaScoreboardState.Entry entry = has ? entries.get(i) : null;
            boolean dead = has && entry.status() == StrinovaScoreboardState.Status.DEAD;
            boolean self = has && entry.self();

            int rowBg;
            if (self) {
                rowBg = ROW_SELF;
            } else if (dead) {
                rowBg = ROW_DEAD;
            } else {
                rowBg = (i % 2 == 0) ? ROW_BG : ROW_ALT;
            }
            painter.rect(contentX, rowY, contentW, ROW_HEIGHT - 1, rowBg);
            // 左侧细线：本人金色 / 队伍色
            painter.rect(contentX, rowY, 2, ROW_HEIGHT - 1, self ? GOLD : withAlpha(accent, 0xCC));

            if (!has) {
                painter.text("—", contentX + anchors[0], rowY + 8, withAlpha(TEXT_DIM, 0x66), false);
                continue;
            }

            int textY = rowY + 4;
            int subY = rowY + 13;
            int avatarX = contentX + anchors[0];
            int accentColor = agentAccent(entry.agentName(), accent);
            painter.rect(avatarX, rowY + 4, 16, 16, 0xFF0D1218);
            painter.rect(avatarX, rowY + 4, 2, 16, accentColor);
            painter.border(avatarX, rowY + 4, 16, 16, 0x66FFFFFF, 1);
            String initial = entry.agentName() == null || entry.agentName().isEmpty()
                    ? "?" : entry.agentName().substring(0, 1);
            painter.text(initial, avatarX + (16 - painter.textWidth(initial)) / 2, rowY + 7, TEXT, false);
            if (entry.status() == StrinovaScoreboardState.Status.ALIVE) {
                painter.rect(avatarX + 11, rowY + 15, 4, 4, GREEN);
            }

            int nameX = avatarX + 22;
            String name = entry.name() == null ? "" : entry.name();
            if (self) {
                name = name + " · 你";
            }
            painter.text(name, nameX, textY,
                    self ? GOLD : dead ? TEXT_DIM : TEXT, false);
            painter.text(entry.agentName() == null ? "" : entry.agentName(), nameX, subY,
                    withAlpha(TEXT_DIM, dead ? 0x88 : 0xCC), false);

            int kdaY = rowY + 8;
            painter.text(entry.kills() + " / " + entry.deaths() + " / " + entry.assists(),
                    contentX + anchors[1], kdaY, TEXT, false);
            painter.text(entry.ultimatePoints() + "/" + entry.ultimateMax(),
                    contentX + anchors[2], kdaY, 0xFF8FD8FF, false);

            int growthColor = entry.growthLevel() >= 2 ? 0xFFDEB639 : 0xFF4B77DC;
            painter.rect(contentX + anchors[3], rowY + 7, 13, 11, growthColor);
            painter.text(String.valueOf(entry.growthLevel()), contentX + anchors[3] + 4, rowY + 8,
                    0xFF0A111C, false);

            painter.text(entry.weaponName() == null ? "" : entry.weaponName(),
                    contentX + anchors[4], kdaY, TEXT_DIM, false);
            painter.text(String.valueOf(entry.damageScore()), contentX + anchors[5], kdaY, TEXT, false);
            painter.text(String.valueOf(entry.skillScore()), contentX + anchors[6], kdaY, TEXT, false);
            painter.text(String.valueOf(entry.assistScore()), contentX + anchors[7], kdaY, TEXT, false);
            painter.textRightAligned(String.valueOf(entry.ping()), contentX + contentW - 6, kdaY,
                    entry.ping() > 100 ? GOLD : TEXT_DIM, false);
        }
    }

    // ==================== 列位置（按内容宽度比例均分） ====================

    private static int[] columnAnchors(int contentW) {
        return new int[]{
                6,
                Math.round(contentW * 0.28F),
                Math.round(contentW * 0.40F),
                Math.round(contentW * 0.50F),
                Math.round(contentW * 0.60F),
                Math.round(contentW * 0.71F),
                Math.round(contentW * 0.78F),
                Math.round(contentW * 0.86F)
        };
    }

    private static String kdaHeader() {
        return Component.translatable("hud.strinova.scoreboard.kills").getString() + "/"
                + Component.translatable("hud.strinova.scoreboard.deaths").getString() + "/"
                + Component.translatable("hud.strinova.scoreboard.assists").getString();
    }

    /** 队伍徽章（程序化几何占位）。 */
    private static void drawCrest(StrinovaHudPainter painter, int x, int y, int size, int accent) {
        painter.rect(x, y, size, size, withAlpha(accent, 0x88));
        painter.border(x, y, size, size, accent, 1);
        int half = size / 2;
        for (int dy = -half + 4; dy <= half - 4; dy++) {
            int width = (half - 4) - Math.abs(dy);
            if (width > 0) {
                painter.rect(x + half - width, y + half + dy, width * 2 + 1, 1, 0xCCFFFFFF);
            }
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
