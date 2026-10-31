package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * TAB 计分板（按目标草图布局）：
 *
 * <pre>
 * ┌────────┬──────────────┬────────────┬────────┬──────────┬────────┬───────┬───────┬───────┬──────┐
 * │ 剪刀手 │ 击败/阵亡/助攻 │   绝招     │ 成长等级 │ 主武器   │ 输出分 │ 技能分 │ 协作分 │ PING │
 * │ (我方) ├──────────────┼────────────┼────────┼──────────┼────────┼───────┼───────┼───────┼──────┤
 * │ logo   │ 头像+名字     │  K/D/A     │  0/7   │  主武器  │  ...  │  ...  │  ...  │ ...  │ ...  │
 * │        │ ...          │            │        │          │       │       │       │      │      │
 * └────────┴──────────────┴────────────┴────────┴──────────┴────────┴───────┴───────┴───────┴──────┘
 * </pre>
 *
 * 上下两个队伍区块；区块左侧竖栏放队伍名/我方敌方/徽章，右侧为列头与 5 行玩家数据。
 * 面板为不透明填色风格：队伍色块、深色行、金色本人行、灰色阵亡行、细网格分隔线。
 */
public final class StrinovaScoreboardOverlay {

    // ==================== 颜色 ====================
    private static final int DIM = 0xB3000000;
    private static final int PANEL_BG = 0xF20C1016;
    private static final int PANEL_BORDER = 0x55FFFFFF;
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

    // ==================== 尺寸 ====================
    private static final int MAX_PANEL_WIDTH = 1020;
    private static final int SIDEBAR_WIDTH = 88;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_COUNT = 5;
    private static final int BOTTOM_PAD = 4;
    private static final int BLOCK_GAP = 6;

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

        int panelWidth = Math.max(420, Math.min(MAX_PANEL_WIDTH, screenWidth - 16));
        int blockHeight = HEADER_HEIGHT + ROW_COUNT * ROW_HEIGHT + BOTTOM_PAD;
        int panelHeight = blockHeight * 2 + BLOCK_GAP;
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = Math.max(6, (screenHeight - panelHeight) / 2);

        // 面板底色（不透明）+ 边框
        painter.rect(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BG);
        painter.border(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BORDER, 1);

        renderTeam(painter, panelLeft, panelTop, panelWidth, blockHeight, true);
        renderTeam(painter, panelLeft, panelTop + blockHeight + BLOCK_GAP, panelWidth, blockHeight, false);
    }

    // ==================== 单个队伍区块 ====================

    private static void renderTeam(StrinovaHudPainter painter, int left, int top,
                                   int panelWidth, int blockHeight, boolean ally) {
        List<StrinovaScoreboardState.Entry> entries =
                ally ? StrinovaScoreboardState.allies() : StrinovaScoreboardState.enemies();
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        String teamKey = ally ? StrinovaMatchState.getAlliesTeamKey() : StrinovaMatchState.getEnemiesTeamKey();
        String teamName = teamKey == null || teamKey.isEmpty()
                ? Component.translatable(ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString()
                : Component.translatable(teamKey).getString();
        String sideLabel = Component.translatable(
                ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString();

        int sidebarW = SIDEBAR_WIDTH;
        int contentX = left + sidebarW;
        int contentW = panelWidth - sidebarW - 1;
        int[] anchors = columnAnchors(contentW);
        boolean compact = contentW < 560;

        // ---- 区块头：左侧队伍名 + 我方/敌方，右侧列头 ----
        painter.rect(left + 1, top, sidebarW - 1, HEADER_HEIGHT, withAlpha(accent, 0xE6));
        painter.text(teamName, left + 6, top + 6, TEXT, true);
        painter.textRightAligned(sideLabel, left + sidebarW - 6, top + 6, 0xCCFFFFFF, false);

        painter.rect(contentX, top, contentW, HEADER_HEIGHT, HEADER_ROW);
        // 第一列（头像+名字）不写列头，与草图一致
        drawHeader(painter, kdaHeader(), contentX + anchors[1], top, compact);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.ultimate").getString(),
                contentX + anchors[2], top, compact);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.growth").getString(),
                contentX + anchors[3], top, compact);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.weapon").getString(),
                contentX + anchors[4], top, compact);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.damage_score").getString(),
                contentX + anchors[5], top, compact);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.skill_score").getString(),
                contentX + anchors[6], top, compact);
        drawHeader(painter, Component.translatable("hud.strinova.scoreboard.assist_score").getString(),
                contentX + anchors[7], top, compact);
        drawHeaderRight(painter, "PING", contentX + contentW - 6, top, compact);
        // 列头底部队伍色分隔线
        painter.rect(contentX, top + HEADER_HEIGHT - 2, contentW, 2, withAlpha(accent, 0xB0));

        // ---- 左侧竖栏：logo 徽章（占满数据区高度） ----
        int rowsTop = top + HEADER_HEIGHT;
        int rowsHeight = blockHeight - HEADER_HEIGHT - 1;
        painter.rect(left + 1, rowsTop, sidebarW - 1, rowsHeight, withAlpha(accent, 0x2E));
        painter.rect(left + 1, rowsTop, 3, rowsHeight, accent);
        int crestSize = 34;
        drawCrest(painter, left + (sidebarW - crestSize) / 2, rowsTop + (rowsHeight - crestSize) / 2 - 6,
                crestSize, accent);
        String score = String.valueOf(ally ? StrinovaMatchState.getAlliesScore() : StrinovaMatchState.getEnemiesScore());
        float scoreScale = 1.4F;
        float scoreWidth = painter.textWidth(score) * scoreScale;
        painter.textScaled(score, left + (sidebarW - scoreWidth) / 2,
                rowsTop + (rowsHeight + crestSize) / 2 - 2, scoreScale, TEXT, true);

        // ---- 玩家行（固定 5 行，空格也填色） ----
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
            // 行间细网格线
            painter.rect(contentX, rowY + ROW_HEIGHT - 1, contentW, 1, GRID_LINE);

            if (!has) {
                painter.text("—", contentX + anchors[0], rowY + 8, withAlpha(TEXT_DIM, 0x66), false);
                continue;
            }

            // 头像 + 名字
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
            painter.text(name, nameX, rowY + 4, self ? GOLD : dead ? TEXT_DIM : TEXT, false);
            String agent = entry.agentName() == null || entry.agentName().isEmpty() ? "未选角色" : entry.agentName();
            painter.textScaled(agent, nameX, rowY + 13, 0.8F, withAlpha(TEXT_DIM, dead ? 0x88 : 0xCC), false);

            // 数值列
            int valueY = rowY + 8;
            painter.text(entry.kills() + " / " + entry.deaths() + " / " + entry.assists(),
                    contentX + anchors[1], valueY, TEXT, false);
            painter.text(entry.ultimatePoints() + "/" + entry.ultimateMax(),
                    contentX + anchors[2], valueY, 0xFF8FD8FF, false);

            int growthColor = entry.growthLevel() >= 2 ? 0xFFDEB639 : 0xFF4B77DC;
            painter.rect(contentX + anchors[3], rowY + 7, 13, 11, growthColor);
            painter.text(String.valueOf(entry.growthLevel()), contentX + anchors[3] + 4, rowY + 8,
                    0xFF0A111C, false);

            painter.text(entry.weaponName() == null ? "" : entry.weaponName(),
                    contentX + anchors[4], valueY, TEXT_DIM, false);
            painter.text(String.valueOf(entry.damageScore()), contentX + anchors[5], valueY, TEXT, false);
            painter.text(String.valueOf(entry.skillScore()), contentX + anchors[6], valueY, TEXT, false);
            painter.text(String.valueOf(entry.assistScore()), contentX + anchors[7], valueY, TEXT, false);
            painter.textRightAligned(String.valueOf(entry.ping()), contentX + contentW - 6, valueY,
                    entry.ping() > 100 ? GOLD : TEXT_DIM, false);
        }
    }

    private static void drawHeader(StrinovaHudPainter painter, String text, int x, int top, boolean compact) {
        if (compact) {
            painter.textScaled(text, x, top + 5, 0.8F, TEXT_DIM, false);
        } else {
            painter.text(text, x, top + 6, TEXT_DIM, false);
        }
    }

    private static void drawHeaderRight(StrinovaHudPainter painter, String text, int rightX, int top, boolean compact) {
        if (compact) {
            int width = Math.round(painter.textWidth(text) * 0.8F);
            painter.textScaled(text, rightX - width, top + 5, 0.8F, TEXT_DIM, false);
        } else {
            painter.textRightAligned(text, rightX, top + 6, TEXT_DIM, false);
        }
    }

    // ==================== 列位置（按内容宽度比例均分） ====================

    private static int[] columnAnchors(int contentW) {
        // 比例对齐草图：头像+名字列紧贴左侧竖栏，其后依次 击败/阵亡/助攻、绝招、成长等级、主武器、输出分、技能分、协作分、PING
        return new int[]{
                6,
                Math.round(contentW * 0.13F),
                Math.round(contentW * 0.30F),
                Math.round(contentW * 0.42F),
                Math.round(contentW * 0.55F),
                Math.round(contentW * 0.64F),
                Math.round(contentW * 0.72F),
                Math.round(contentW * 0.80F)
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
