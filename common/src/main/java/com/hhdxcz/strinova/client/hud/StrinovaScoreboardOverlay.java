package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * TAB 计分板（对齐卡拉彼丘布局、支持可视化编辑器调整）。
 *
 * <p>所有尺寸/位置/字号都来自客户端配置 {@link StrinovaHudConfig}：
 * scoreboardWidthRatio / scoreboardHeightRatio / scoreboardOffsetX / scoreboardOffsetY /
 * scoreboardTextScale / scoreboardRowHeight，因此可以通过计分板编辑器（/strinova_client scoreboard）
 * 拖动和按钮实时调整。</p>
 *
 * <pre>
 * 左竖栏            右内容区
 * ┌──────┬────────────────────────────────────────────────────┐
 * │ 剪刀手│ 头像+名字 | 击败/阵亡/助攻 | 绝招 | 成长等级 | 主武器 | 输出分 | 技能分 | 协作分 | PING
 * │ logo ├────────────────────────────────────────────────────┤
 * │ 比分 │ 玩家数据行 ×5
 * ├──────┼────────────────────────────────────────────────────┤
 * │ 我方 │ ●爆   ○    ●灭   ○   ...  ← 我方赢下的回合（格子里是赢法）
 * │ 敌方 │ ○    ●拆   ○    ●时  ...  ← 敌方赢下的回合
 * ├──────┼────────────────────────────────────────────────────┤
 * │ 欧泊 │ 列头 + 玩家数据行 ×5
 * │ logo │
 * └──────┴────────────────────────────────────────────────────┘
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

    // ==================== 基准尺寸（可被配置缩放） ====================
    private static final int MAX_PANEL_WIDTH = 1020;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int ROW_COUNT = 5;
    private static final int BOTTOM_PAD = 3;
    private static final int ROUND_CELL = 9;
    private static final int ROUND_STEP = 11;
    private static final float BASE_HEADER_TEXT_SCALE = 0.90F;
    private static final float BASE_VALUE_TEXT_SCALE = 1.00F;
    private static final float BASE_NAME_TEXT_SCALE = 1.05F;

    /** 计算后面板布局（含编辑器所需的边界）。 */
    public record Layout(int left, int top, int width, int height, int sidebarWidth,
                         int rowHeight, int headerHeight, int middleHeight, int blockHeight,
                         int blockTop, int middleTop, int enemyTop, float textScale) {
    }

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

    /** 供编辑器使用：面板在屏幕上的边界 [left, top, width, height]。 */
    public static int[] panelBounds(int screenWidth, int screenHeight) {
        Layout layout = computeLayout(screenWidth, screenHeight);
        return new int[]{layout.left(), layout.top(), layout.width(), layout.height()};
    }

    /** 游戏内渲染（带背景压暗）。 */
    public static void render(GuiGraphics graphics, StrinovaHudPainter painter) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), DIM);
        renderPanel(graphics, painter);
    }

    /** 编辑器预览渲染（不压暗，便于在编辑界面里看清面板）。 */
    public static void renderPreview(GuiGraphics graphics, StrinovaHudPainter painter) {
        renderPanel(graphics, painter);
    }

    private static void renderPanel(GuiGraphics graphics, StrinovaHudPainter painter) {
        Layout layout = computeLayout(graphics.guiWidth(), graphics.guiHeight());

        painter.rect(layout.left(), layout.top(), layout.width(), layout.height(), PANEL_BG);
        painter.border(layout.left(), layout.top(), layout.width(), layout.height(), PANEL_BORDER, 1);

        renderTeamContent(painter, layout, true);
        renderTeamContent(painter, layout, false);
        renderSidebarBlock(painter, layout, true);
        renderSidebarBlock(painter, layout, false);
        renderMiddleBand(painter, layout);
    }

    private static Layout computeLayout(int screenWidth, int screenHeight) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int rowHeight = Math.max(14, Math.min(30, config.scoreboardRowHeight));
        int headerHeight = Math.max(12, rowHeight - 3);
        int middleHeight = Math.max(16, rowHeight + 5);
        int blockHeight = headerHeight + ROW_COUNT * rowHeight + BOTTOM_PAD;
        int contentHeight = blockHeight * 2 + middleHeight;

        int targetWidth = Math.round(screenWidth * config.scoreboardWidthRatio);
        int targetHeight = Math.round(screenHeight * config.scoreboardHeightRatio);
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, targetWidth));
        int panelHeight = Math.max(contentHeight, Math.min(targetHeight, screenHeight - 8));
        int left = (screenWidth - panelWidth) / 2 + Math.round(config.scoreboardOffsetX);
        int top = Math.max(2, (screenHeight - panelHeight) / 2 + Math.round(config.scoreboardOffsetY));
        int sidebarWidth = Math.max(48, Math.round(panelWidth * 0.11F));
        int blockTop = top + Math.max(0, (panelHeight - contentHeight) / 2);
        int middleTop = blockTop + blockHeight;
        int enemyTop = middleTop + middleHeight;
        float textScale = Math.max(0.5F, Math.min(1.9F, config.scoreboardTextScale));
        return new Layout(left, top, panelWidth, panelHeight, sidebarWidth, rowHeight, headerHeight,
                middleHeight, blockHeight, blockTop, middleTop, enemyTop, textScale);
    }

    // ==================== 中间回合记录带 ====================

    private static void renderMiddleBand(StrinovaHudPainter painter, Layout layout) {
        int left = layout.left();
        int top = layout.middleTop();
        int contentX = left + layout.sidebarWidth();
        int contentW = layout.width() - layout.sidebarWidth() - 1;
        int height = layout.middleHeight();
        float textScale = layout.textScale();

        painter.rect(left + 1, top, layout.width() - 2, height - 1, MIDDLE_BG);
        painter.rect(left + 1, top, layout.width() - 2, 1, withAlpha(0xFFFFFF, 0x1E));
        painter.rect(left + 1, top + height - 1, layout.width() - 2, 1, withAlpha(0xFFFFFF, 0x1E));

        int rowHeight = Math.max(8, height / 2);
        painter.textScaled(Component.translatable("hud.strinova.match.allies").getString(),
                left + 3, top + 1, 0.7F * textScale, ALLY_WIN, false);
        painter.textScaled(Component.translatable("hud.strinova.match.enemies").getString(),
                left + 3, top + 1 + rowHeight, 0.7F * textScale, ENEMY_WIN, false);

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
                drawRoundCell(painter, cellX, allyY, result.reasonKey(), ALLY_WIN, textScale);
            } else {
                drawRoundCell(painter, cellX, enemyY, result.reasonKey(), ENEMY_WIN, textScale);
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

    private static void drawRoundCell(StrinovaHudPainter painter, int x, int y, String reasonKey,
                                      int color, float textScale) {
        painter.rect(x, y, ROUND_CELL, ROUND_CELL, withAlpha(color, 0x55));
        painter.border(x, y, ROUND_CELL, ROUND_CELL, color, 1);
        painter.textScaled(reasonGlyph(reasonKey), x + 1, y + 1, 0.5F * textScale, TEXT, false);
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

    // ==================== 左侧竖栏 ====================

    private static void renderSidebarBlock(StrinovaHudPainter painter, Layout layout, boolean ally) {
        int left = layout.left();
        int top = ally ? layout.blockTop() : layout.enemyTop();
        int sidebarWidth = layout.sidebarWidth();
        int headerHeight = layout.headerHeight();
        int blockHeight = layout.blockHeight();
        float textScale = layout.textScale();
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        String teamKey = ally ? StrinovaMatchState.getAlliesTeamKey() : StrinovaMatchState.getEnemiesTeamKey();
        String teamName = teamKey == null || teamKey.isEmpty()
                ? Component.translatable(ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString()
                : Component.translatable(teamKey).getString();
        int score = ally ? StrinovaMatchState.getAlliesScore() : StrinovaMatchState.getEnemiesScore();

        painter.rect(left + 1, top, sidebarWidth - 1, blockHeight - 1, SIDEBAR_BG);
        painter.rect(left + 1, top, sidebarWidth - 1, headerHeight, withAlpha(accent, 0xE6));
        painter.textScaled(teamName, left + 3, top + (headerHeight - 9 * 0.8F * textScale) / 2,
                0.8F * textScale, TEXT, true);

        int areaTop = top + headerHeight;
        int areaHeight = blockHeight - headerHeight - 1;
        int crestSize = Math.max(12, Math.min(24, Math.round(sidebarWidth * 0.3F)));
        int crestTop = areaTop + Math.max(0, (areaHeight - crestSize - 14) / 2);
        drawCrest(painter, left + (sidebarWidth - crestSize) / 2, crestTop, crestSize, accent);
        String scoreText = String.valueOf(score);
        float scoreScale = 1.1F * textScale;
        float scoreWidth = painter.textWidth(scoreText) * scoreScale;
        painter.textScaled(scoreText, left + (sidebarWidth - scoreWidth) / 2,
                crestTop + crestSize + 2, scoreScale, TEXT, true);
    }

    // ==================== 右侧内容区 ====================

    private static void renderTeamContent(StrinovaHudPainter painter, Layout layout, boolean ally) {
        List<StrinovaScoreboardState.Entry> entries =
                ally ? StrinovaScoreboardState.allies() : StrinovaScoreboardState.enemies();
        int top = ally ? layout.blockTop() : layout.enemyTop();
        int contentX = layout.left() + layout.sidebarWidth();
        int contentW = layout.width() - layout.sidebarWidth() - 1;
        int headerHeight = layout.headerHeight();
        int rowHeight = layout.rowHeight();
        float textScale = layout.textScale();
        float headerScale = BASE_HEADER_TEXT_SCALE * textScale;
        float valueScale = BASE_VALUE_TEXT_SCALE * textScale;
        float nameScale = BASE_NAME_TEXT_SCALE * textScale;
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        int[] anchors = columnAnchors(contentW);

        // 列头行（第一列头像+名字不写列头）
        painter.rect(contentX, top, contentW, headerHeight, HEADER_ROW);
        int headerY = top + Math.round((headerHeight - 8.1F * textScale) / 2);
        drawHeader(painter, kdaHeader(), contentX + anchors[1], headerY, headerScale);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.ultimate").getString(),
                contentX + anchors[2], headerY, headerScale);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.growth").getString(),
                contentX + anchors[3], headerY, headerScale);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.weapon").getString(),
                contentX + anchors[4], headerY, headerScale);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.damage_score").getString(),
                contentX + anchors[5], headerY, headerScale);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.skill_score").getString(),
                contentX + anchors[6], headerY, headerScale);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.assist_score").getString(),
                contentX + anchors[7], headerY, headerScale);
        drawHeaderRight(painter, "PING", contentX + contentW - 4, headerY, headerScale);
        painter.rect(contentX, top + headerHeight - 1, contentW, 1, withAlpha(accent, 0xB0));

        // 玩家行
        int rowsTop = top + headerHeight;
        for (int i = 0; i < ROW_COUNT; i++) {
            int rowY = rowsTop + i * rowHeight;
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
            painter.rect(contentX, rowY, contentW, rowHeight, rowBg);
            if (self) {
                painter.rect(contentX, rowY, 2, rowHeight, GOLD);
            }
            painter.rect(contentX, rowY + rowHeight - 1, contentW, 1, GRID_LINE);

            if (!has) {
                continue;
            }

            int avatarSize = Math.max(10, Math.min(16, rowHeight - 6));
            int avatarY = rowY + (rowHeight - avatarSize) / 2;
            int avatarX = contentX + anchors[0];
            int accentColor = agentAccent(entry.agentName(), accent);
            painter.rect(avatarX, avatarY, avatarSize, avatarSize, 0xFF0D1218);
            painter.rect(avatarX, avatarY, 2, avatarSize, accentColor);
            painter.border(avatarX, avatarY, avatarSize, avatarSize, 0x66FFFFFF, 1);
            String initial = entry.agentName() == null || entry.agentName().isEmpty()
                    ? "?" : entry.agentName().substring(0, 1);
            painter.textScaled(initial, avatarX + Math.round((avatarSize - 6.75F * textScale) / 2),
                    avatarY + Math.round((avatarSize - 6.75F * textScale) / 2), 0.75F * textScale, TEXT, false);
            if (entry.status() == StrinovaScoreboardState.Status.ALIVE) {
                painter.rect(avatarX + avatarSize - 4, avatarY + avatarSize - 3, 3, 3, GREEN);
            }

            int textY = rowY + Math.round((rowHeight - 9.0F * textScale) / 2);
            int nameX = avatarX + avatarSize + 5;
            String name = entry.name() == null ? "" : entry.name();
            painter.textScaled(name, nameX, textY, nameScale, self ? GOLD : dead ? TEXT_DIM : TEXT, false);

            painter.textScaled(entry.kills() + " / " + entry.deaths() + " / " + entry.assists(),
                    contentX + anchors[1], textY, valueScale, TEXT, false);
            painter.textScaled(entry.ultimatePoints() + "/" + entry.ultimateMax(),
                    contentX + anchors[2], textY, valueScale, 0xFF8FD8FF, false);

            int growthColor = entry.growthLevel() >= 2 ? 0xFFDEB639 : 0xFF4B77DC;
            int growthY = rowY + (rowHeight - 10) / 2;
            painter.rect(contentX + anchors[3], growthY, 10, 10, growthColor);
            painter.textScaled(String.valueOf(entry.growthLevel()), contentX + anchors[3] + 3,
                    growthY + 1, 0.6F * textScale, 0xFF0A111C, false);

            painter.textScaled(entry.weaponName() == null ? "" : entry.weaponName(),
                    contentX + anchors[4], textY, valueScale, TEXT_DIM, false);
            painter.textScaled(String.valueOf(entry.damageScore()), contentX + anchors[5], textY,
                    valueScale, TEXT, false);
            painter.textScaled(String.valueOf(entry.skillScore()), contentX + anchors[6], textY,
                    valueScale, TEXT, false);
            painter.textScaled(String.valueOf(entry.assistScore()), contentX + anchors[7], textY,
                    valueScale, TEXT, false);
            String pingText = String.valueOf(entry.ping());
            int pingWidth = Math.round(painter.textWidth(pingText) * valueScale);
            painter.textScaled(pingText, contentX + contentW - 4 - pingWidth, textY, valueScale,
                    entry.ping() > 100 ? GOLD : TEXT_DIM, false);
        }
    }

    private static void drawHeader(StrinovaHudPainter painter, String text, int x, int y, float scale) {
        painter.textScaled(text, x, y, scale, TEXT_DIM, false);
    }

    private static void drawHeaderRight(StrinovaHudPainter painter, String text, int rightX, int y, float scale) {
        int width = Math.round(painter.textWidth(text) * scale);
        painter.textScaled(text, rightX - width, y, scale, TEXT_DIM, false);
    }

    // ==================== 列位置 ====================

    private static int[] columnAnchors(int contentW) {
        // 按卡拉彼丘实测比例：头像+名字列更长，其后依次 击败/阵亡/助攻、绝招、成长等级、主武器、输出分、技能分、协作分、PING
        return new int[]{
                4,
                Math.round(contentW * 0.23F),
                Math.round(contentW * 0.35F),
                Math.round(contentW * 0.46F),
                Math.round(contentW * 0.59F),
                Math.round(contentW * 0.67F),
                Math.round(contentW * 0.76F),
                Math.round(contentW * 0.83F)
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
