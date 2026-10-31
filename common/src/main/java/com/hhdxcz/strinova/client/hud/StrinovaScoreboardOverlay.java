package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * TAB 计分板（对齐卡拉彼丘真实屏占比与布局）：
 *
 * <p>真实游戏 TAB 计分板约占屏幕 <b>59% 宽、50% 高</b>（居中），本实现按同样比例自适应：
 * 面板宽度 = 屏幕宽 × 0.59、面板高度 = 屏幕高 × 0.50（内容放不下时以内容为准），
 * 内部字号/行高按比例缩小，保证不同 GUI 缩放下屏占比一致，不会像之前那样铺满整屏。</p>
 *
 * <pre>
 * 左竖栏            右内容区
 * ┌──────┬──────────────────────────────────────────────────┐
 * │ 剪刀手│ 头像+名字 | 击败/阵亡/助攻 | 绝招 | 成长等级 | 主武器 | 输出分 | 技能分 | 协作分 | PING
 * │ logo ├──────────────────────────────────────────────────┤
 * │ 比分 │ 玩家数据行 ×5
 * ├──────┼──────────────────────────────────────────────────┤
 * │ 我方 │ ●爆   ○    ●灭   ○   ...  ← 我方赢下的回合（格子里是赢法）
 * │ 敌方 │ ○    ●拆   ○    ●时  ...  ← 敌方赢下的回合
 * ├──────┼──────────────────────────────────────────────────┤
 * │ 欧泊 │ 列头 + 玩家数据行 ×5
 * │ logo │
 * └──────┴──────────────────────────────────────────────────┘
 * </pre>
 */
public final class StrinovaScoreboardOverlay {

    // ==================== 颜色 ====================
    private static final int DIM = 0xB3000000;
    private static final int PANEL_BG = 0xF20C1016;
    private static final int PANEL_BORDER = 0x55FFFFFF;
    private static final int SIDEBAR_BG = 0xFF0A0E13;
    private static final int MIDDLE_BG = 0xFF0A0E13;
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
    private static final int GRID_LINE = 0x18FFFFFF;
    private static final int ALLY_WIN = 0xFFFF9AA6;
    private static final int ENEMY_WIN = 0xFF9BB6FF;

    // ==================== 尺寸（对齐真实屏占比） ====================
    /** 真实计分板占屏宽度比例。 */
    private static final float SCREEN_WIDTH_RATIO = 0.59F;
    /** 真实计分板占屏高度比例。 */
    private static final float SCREEN_HEIGHT_RATIO = 0.50F;
    private static final int MAX_PANEL_WIDTH = 1020;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int HEADER_HEIGHT = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_COUNT = 5;
    private static final int BOTTOM_PAD = 3;
    private static final int MIDDLE_HEIGHT = 26;
    /** 回合记录格子。 */
    private static final int ROUND_CELL = 9;
    private static final int ROUND_STEP = 11;
    /** 内容文字缩放：真实计分板文字相对面板更小，按 0.7~0.85 缩放。 */
    private static final float HEADER_TEXT_SCALE = 0.72F;
    private static final float VALUE_TEXT_SCALE = 0.80F;
    private static final float NAME_TEXT_SCALE = 0.85F;

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
        graphics.fill(0, 0, screenWidth, screenHeight, DIM);

        int targetWidth = Math.round(screenWidth * SCREEN_WIDTH_RATIO);
        int targetHeight = Math.round(screenHeight * SCREEN_HEIGHT_RATIO);
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, targetWidth));
        int sidebarWidth = Math.max(52, Math.round(panelWidth * 0.11F));
        int blockHeight = HEADER_HEIGHT + ROW_COUNT * ROW_HEIGHT + BOTTOM_PAD;
        int contentHeight = blockHeight * 2 + MIDDLE_HEIGHT;
        int panelHeight = Math.max(contentHeight, Math.min(targetHeight, screenHeight - 8));
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = Math.max(4, (screenHeight - panelHeight) / 2);
        // 内容垂直居中：目标高度大于内容高度时，上下留白平分
        int blocksTop = panelTop + Math.max(0, (panelHeight - contentHeight) / 2);
        int middleTop = blocksTop + blockHeight;
        int enemyTop = middleTop + MIDDLE_HEIGHT;

        painter.rect(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BG);
        painter.border(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BORDER, 1);

        renderTeamContent(painter, panelLeft, blocksTop, panelWidth, sidebarWidth, true);
        renderTeamContent(painter, panelLeft, enemyTop, panelWidth, sidebarWidth, false);
        renderSidebarBlock(painter, panelLeft, blocksTop, blockHeight, sidebarWidth, true);
        renderSidebarBlock(painter, panelLeft, enemyTop, blockHeight, sidebarWidth, false);
        renderMiddleBand(painter, panelLeft, middleTop, panelWidth, sidebarWidth);
    }

    // ==================== 中间回合记录带 ====================

    private static void renderMiddleBand(StrinovaHudPainter painter, int left, int top,
                                         int panelWidth, int sidebarWidth) {
        int contentX = left + sidebarWidth;
        int contentW = panelWidth - sidebarWidth - 1;
        int height = MIDDLE_HEIGHT;

        painter.rect(left + 1, top, panelWidth - 2, height - 1, MIDDLE_BG);
        painter.rect(left + 1, top, panelWidth - 2, 1, withAlpha(0xFFFFFF, 0x1E));
        painter.rect(left + 1, top + height - 1, panelWidth - 2, 1, withAlpha(0xFFFFFF, 0x1E));

        int rowHeight = height / 2;
        painter.textScaled(Component.translatable("hud.strinova.match.allies").getString(),
                left + 4, top + 1, 0.7F, ALLY_WIN, false);
        painter.textScaled(Component.translatable("hud.strinova.match.enemies").getString(),
                left + 4, top + 1 + rowHeight, 0.7F, ENEMY_WIN, false);

        List<StrinovaMatchState.RoundResult> results = StrinovaMatchState.roundResults();
        int maxCells = Math.max(1, (contentW - 6) / ROUND_STEP);
        int start = Math.max(0, results.size() - maxCells);
        int cellX = contentX + 3;
        int drawn = 0;
        for (int i = start; i < results.size() && drawn < maxCells; i++) {
            StrinovaMatchState.RoundResult result = results.get(i);
            int allyY = top + 1;
            int enemyY = top + 1 + rowHeight;
            drawEmptyDot(painter, cellX + 3, allyY + 3);
            drawEmptyDot(painter, cellX + 3, enemyY + 3);
            if (result.allyWon()) {
                drawRoundCell(painter, cellX, allyY, result.reasonKey(), ALLY_WIN);
            } else {
                drawRoundCell(painter, cellX, enemyY, result.reasonKey(), ENEMY_WIN);
            }
            cellX += ROUND_STEP;
            drawn++;
        }
        int placeholders = Math.min(maxCells - drawn, 8);
        for (int i = 0; i < placeholders; i++) {
            drawEmptyDot(painter, cellX + 3, top + 4);
            drawEmptyDot(painter, cellX + 3, top + 4 + rowHeight);
            cellX += ROUND_STEP;
        }
    }

    private static void drawEmptyDot(StrinovaHudPainter painter, int x, int y) {
        painter.rect(x, y, 2, 2, withAlpha(0xFFFFFF, 0x55));
    }

    private static void drawRoundCell(StrinovaHudPainter painter, int x, int y, String reasonKey, int color) {
        painter.rect(x, y, ROUND_CELL, ROUND_CELL, withAlpha(color, 0x55));
        painter.border(x, y, ROUND_CELL, ROUND_CELL, color, 1);
        painter.textScaled(reasonGlyph(reasonKey), x + 1, y + 1, 0.5F, TEXT, false);
    }

    private static String reasonGlyph(String reasonKey) {
        if (reasonKey == null) {
            return "?";
        }
        return switch (reasonKey) {
            case "hud.strinova.match.reason.explode" -> "爆";
            case "hud.strinova.match.reason.defuse" -> "拆";
            case "hud.strinova.match.reason.elimination" -> "灭";
            case "hud.strinova.match.reason.timeout" -> "时";
            default -> "?";
        };
    }

    // ==================== 左侧竖栏（按阵营区块） ====================

    private static void renderSidebarBlock(StrinovaHudPainter painter, int left, int top,
                                           int blockHeight, int sidebarWidth, boolean ally) {
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        String teamKey = ally ? StrinovaMatchState.getAlliesTeamKey() : StrinovaMatchState.getEnemiesTeamKey();
        String teamName = teamKey == null || teamKey.isEmpty()
                ? Component.translatable(ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString()
                : Component.translatable(teamKey).getString();
        String sideLabel = Component.translatable(
                ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString();
        int score = ally ? StrinovaMatchState.getAlliesScore() : StrinovaMatchState.getEnemiesScore();

        painter.rect(left + 1, top, sidebarWidth - 1, blockHeight - 1, SIDEBAR_BG);
        // 表头：队名 + 我方/敌方
        painter.rect(left + 1, top, sidebarWidth - 1, HEADER_HEIGHT, withAlpha(accent, 0xE6));
        painter.textScaled(teamName, left + 4, top + 4, 0.8F, TEXT, true);
        painter.textScaled(sideLabel, left + 4 + Math.round(painter.textWidth(teamName) * 0.8F) + 4,
                top + 5, 0.62F, 0xE6FFFFFF, false);

        // logo + 大比分（在表头下方居中）
        int areaTop = top + HEADER_HEIGHT;
        int areaHeight = blockHeight - HEADER_HEIGHT - 1;
        int crestSize = 20;
        int blockHeightPx = crestSize + 16;
        int crestTop = areaTop + Math.max(0, (areaHeight - blockHeightPx) / 2);
        drawCrest(painter, left + (sidebarWidth - crestSize) / 2, crestTop, crestSize, accent);
        String scoreText = String.valueOf(score);
        float scoreScale = 1.1F;
        float scoreWidth = painter.textWidth(scoreText) * scoreScale;
        painter.textScaled(scoreText, left + (sidebarWidth - scoreWidth) / 2,
                crestTop + crestSize + 3, scoreScale, TEXT, true);
    }

    // ==================== 右侧内容区 ====================

    private static void renderTeamContent(StrinovaHudPainter painter, int left, int top,
                                          int panelWidth, int sidebarWidth, boolean ally) {
        List<StrinovaScoreboardState.Entry> entries =
                ally ? StrinovaScoreboardState.allies() : StrinovaScoreboardState.enemies();
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        int contentX = left + sidebarWidth;
        int contentW = panelWidth - sidebarWidth - 1;
        int[] anchors = columnAnchors(contentW);

        // 列头行（第一列头像+名字不写列头）
        painter.rect(contentX, top, contentW, HEADER_HEIGHT, HEADER_ROW);
        drawHeader(painter, kdaHeader(), contentX + anchors[1], top);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.ultimate").getString(), contentX + anchors[2], top);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.growth").getString(), contentX + anchors[3], top);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.weapon").getString(), contentX + anchors[4], top);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.damage_score").getString(), contentX + anchors[5], top);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.skill_score").getString(), contentX + anchors[6], top);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.assist_score").getString(), contentX + anchors[7], top);
        drawHeaderRight(painter, "PING", contentX + contentW - 4, top);
        painter.rect(contentX, top + HEADER_HEIGHT - 1, contentW, 1, withAlpha(accent, 0xB0));

        // 玩家行
        int rowsTop = top + HEADER_HEIGHT;
        for (int i = 0; i < ROW_COUNT; i++) {
            int rowY = rowsTop + i * ROW_HEIGHT;
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
            painter.rect(contentX, rowY, contentW, ROW_HEIGHT, rowBg);
            if (self) {
                painter.rect(contentX, rowY, 2, ROW_HEIGHT, GOLD);
            }
            painter.rect(contentX, rowY + ROW_HEIGHT - 1, contentW, 1, GRID_LINE);

            if (!has) {
                painter.textScaled("—", contentX + anchors[0], rowY + 5, 0.7F, withAlpha(TEXT_DIM, 0x66), false);
                continue;
            }

            int avatarX = contentX + anchors[0];
            int accentColor = agentAccent(entry.agentName(), accent);
            painter.rect(avatarX, rowY + 2, 14, 14, 0xFF0D1218);
            painter.rect(avatarX, rowY + 2, 2, 14, accentColor);
            painter.border(avatarX, rowY + 2, 14, 14, 0x66FFFFFF, 1);
            String initial = entry.agentName() == null || entry.agentName().isEmpty()
                    ? "?" : entry.agentName().substring(0, 1);
            painter.textScaled(initial, avatarX + (14 - painter.textWidth(initial) * 0.75F) / 2, rowY + 4,
                    0.75F, TEXT, false);
            if (entry.status() == StrinovaScoreboardState.Status.ALIVE) {
                painter.rect(avatarX + 10, rowY + 13, 3, 3, GREEN);
            }
            int nameX = avatarX + 19;
            String name = entry.name() == null ? "" : entry.name();
            painter.textScaled(name, nameX, rowY + 4, NAME_TEXT_SCALE,
                    self ? GOLD : dead ? TEXT_DIM : TEXT, false);

            int valueY = rowY + 5;
            painter.textScaled(entry.kills() + " / " + entry.deaths() + " / " + entry.assists(),
                    contentX + anchors[1], valueY, VALUE_TEXT_SCALE, TEXT, false);
            painter.textScaled(entry.ultimatePoints() + "/" + entry.ultimateMax(),
                    contentX + anchors[2], valueY, VALUE_TEXT_SCALE, 0xFF8FD8FF, false);

            int growthColor = entry.growthLevel() >= 2 ? 0xFFDEB639 : 0xFF4B77DC;
            painter.rect(contentX + anchors[3], rowY + 4, 10, 10, growthColor);
            painter.textScaled(String.valueOf(entry.growthLevel()), contentX + anchors[3] + 3, rowY + 5,
                    0.6F, 0xFF0A111C, false);

            painter.textScaled(entry.weaponName() == null ? "" : entry.weaponName(),
                    contentX + anchors[4], valueY, VALUE_TEXT_SCALE, TEXT_DIM, false);
            painter.textScaled(String.valueOf(entry.damageScore()), contentX + anchors[5], valueY,
                    VALUE_TEXT_SCALE, TEXT, false);
            painter.textScaled(String.valueOf(entry.skillScore()), contentX + anchors[6], valueY,
                    VALUE_TEXT_SCALE, TEXT, false);
            painter.textScaled(String.valueOf(entry.assistScore()), contentX + anchors[7], valueY,
                    VALUE_TEXT_SCALE, TEXT, false);
            String pingText = String.valueOf(entry.ping());
            int pingWidth = Math.round(painter.textWidth(pingText) * VALUE_TEXT_SCALE);
            painter.textScaled(pingText, contentX + contentW - 4 - pingWidth, valueY, VALUE_TEXT_SCALE,
                    entry.ping() > 100 ? GOLD : TEXT_DIM, false);
        }
    }

    private static void drawHeader(StrinovaHudPainter painter, String text, int x, int top) {
        painter.textScaled(text, x, top + 4, HEADER_TEXT_SCALE, TEXT_DIM, false);
    }

    private static void drawHeaderRight(StrinovaHudPainter painter, String text, int rightX, int top) {
        int width = Math.round(painter.textWidth(text) * HEADER_TEXT_SCALE);
        painter.textScaled(text, rightX - width, top + 4, HEADER_TEXT_SCALE, TEXT_DIM, false);
    }

    // ==================== 列位置 ====================

    private static int[] columnAnchors(int contentW) {
        // 头像+名字列贴左，其后按真实比例均分：击败/阵亡/助攻、绝招、成长等级、主武器、输出分、技能分、协作分、PING
        return new int[]{
                4,
                Math.round(contentW * 0.13F),
                Math.round(contentW * 0.24F),
                Math.round(contentW * 0.35F),
                Math.round(contentW * 0.46F),
                Math.round(contentW * 0.57F),
                Math.round(contentW * 0.66F),
                Math.round(contentW * 0.74F)
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
        for (int dy = -half + 3; dy <= half - 3; dy++) {
            int width = (half - 3) - Math.abs(dy);
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
