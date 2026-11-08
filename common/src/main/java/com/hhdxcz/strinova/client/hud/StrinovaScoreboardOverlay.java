package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * TAB 计分板（对齐卡拉彼丘布局、支持可视化编辑器逐部件自定义）。
 *
 * <p>全局尺寸/位置/字号来自 {@link StrinovaHudConfig} 的 scoreboard* 字段；
 * 每个部件（面板、竖栏、列头、名字、各数据列、数据行、回合记录带、我方/敌方回合）
 * 还可以用 {@link StrinovaHudConfig.ScoreboardComponentStyle} 单独设置：
 * 可见性、字号倍率、文字色、背景色（/strinova_client scoreboard style 打开样式面板）。</p>
 */
public final class StrinovaScoreboardOverlay {

    // ==================== 默认颜色（部件样式为 0 时使用） ====================
    private static final ResourceLocation GROWTH_SQUARE =
            new ResourceLocation("strinova", "textures/gui/hud/growth_square.png");
    private static final ResourceLocation GROWTH_CIRCLE =
            new ResourceLocation("strinova", "textures/gui/hud/growth_circle.png");
    private static final ResourceLocation GROWTH_TRIANGLE =
            new ResourceLocation("strinova", "textures/gui/hud/growth_triangle.png");

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

    // ==================== 基准尺寸 ====================
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

    // ==================== 部件样式解析 ====================

    private static StrinovaHudConfig.ScoreboardComponentStyle style(String key) {
        return StrinovaHudConfig.get().scoreboardComponent(key);
    }

    private static boolean visible(String key) {
        return style(key).visible;
    }

    private static float fontScale(String key) {
        return style(key).fontScale;
    }

    private static int textColor(String key, int fallback) {
        int color = style(key).textColor;
        return color == 0 ? fallback : color;
    }

    private static int backgroundColor(String key, int fallback) {
        int color = style(key).backgroundColor;
        return color == 0 ? fallback : color;
    }

    /** 应用计分板整体不透明度（只影响背景填充，文字保持清晰）。 */
    private static int opacity(int argb) {
        float value = Math.max(0.20F, Math.min(1.0F, StrinovaHudConfig.get().scoreboardOpacity));
        int alpha = Math.round(((argb >>> 24) & 0xFF) * value);
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
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
        // 计分板独立于 HUD 总开关：即使关闭了血甲/技能等 HUD，按 TAB 仍应显示计分板
        if (!config.scoreboardEnabled) {
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

    /** 游戏内渲染（默认不压暗背景，可在配置开启）。 */
    public static void render(GuiGraphics graphics, StrinovaHudPainter painter) {
        if (StrinovaHudConfig.get().scoreboardDimBackground) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), DIM);
        }
        renderPanel(graphics, painter);
    }

    /** 编辑器预览渲染（不压暗，便于在编辑界面里看清面板）。 */
    public static void renderPreview(GuiGraphics graphics, StrinovaHudPainter painter) {
        renderPanel(graphics, painter);
    }

    private static void renderPanel(GuiGraphics graphics, StrinovaHudPainter painter) {
        Layout layout = computeLayout(graphics.guiWidth(), graphics.guiHeight());

        painter.rect(layout.left(), layout.top(), layout.width(), layout.height(),
                opacity(backgroundColor("panel", PANEL_BG)));
        if (StrinovaHudConfig.get().scoreboardPanelBorder) {
            painter.border(layout.left(), layout.top(), layout.width(), layout.height(),
                    textColor("panel", PANEL_BORDER), 1);
        }

        renderTeamContent(painter, layout, true);
        renderTeamContent(painter, layout, false);
        renderSidebarBlock(painter, layout, true);
        renderSidebarBlock(painter, layout, false);
        renderMiddleBand(painter, layout);
    }

    private static Layout computeLayout(int screenWidth, int screenHeight) {
        return buildLayout(screenWidth, screenHeight);
    }

    /** 当前「我方/敌方」单行有效高度（0=自动时按数据行高推算）。 */
    public static int effectiveRoundRowHeight() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int rowHeight = Math.max(14, Math.min(30, config.scoreboardRowHeight));
        return config.scoreboardRoundRowHeight > 0
                ? Math.max(6, Math.min(24, config.scoreboardRoundRowHeight))
                : Math.max(8, (rowHeight + 5) / 2);
    }

    /** 供编辑器使用：计算完整面板布局。 */
    public static Layout computeLayoutPublic(int screenWidth, int screenHeight) {
        return buildLayout(screenWidth, screenHeight);
    }

    private static Layout buildLayout(int screenWidth, int screenHeight) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int rowHeight = Math.max(14, Math.min(30, config.scoreboardRowHeight));
        int headerHeight = Math.max(12, rowHeight - 3);
        int roundRowHeight = config.scoreboardRoundRowHeight > 0
                ? Math.max(6, Math.min(24, config.scoreboardRoundRowHeight))
                : Math.max(8, (rowHeight + 5) / 2);
        int middleHeight = Math.max(14, roundRowHeight * 2 + 2);
        int blockHeight = headerHeight + ROW_COUNT * rowHeight + BOTTOM_PAD;
        int contentHeight = blockHeight * 2 + middleHeight;

        int targetWidth = Math.round(screenWidth * config.scoreboardWidthRatio);
        int targetHeight = Math.round(screenHeight * config.scoreboardHeightRatio);
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, targetWidth));
        int panelHeight = Math.max(contentHeight, Math.min(targetHeight, screenHeight - 8));
        int left = (screenWidth - panelWidth) / 2 + Math.round(config.scoreboardOffsetX);
        int top = Math.max(2, (screenHeight - panelHeight) / 2 + Math.round(config.scoreboardOffsetY));
        int sidebarWidth = Math.max(40, Math.round(panelWidth * config.scoreboardSidebarWidthRatio));
        int blockTop = top + Math.max(0, (panelHeight - contentHeight) / 2);
        int middleTop = blockTop + blockHeight;
        int enemyTop = middleTop + middleHeight;
        float textScale = Math.max(0.5F, Math.min(1.9F, config.scoreboardTextScale));
        return new Layout(left, top, panelWidth, panelHeight, sidebarWidth, rowHeight, headerHeight,
                middleHeight, blockHeight, blockTop, middleTop, enemyTop, textScale);
    }

    // ==================== 中间回合记录带 ====================

    private static void renderMiddleBand(StrinovaHudPainter painter, Layout layout) {
        if (!visible("rounds")) {
            return;
        }
        int left = layout.left();
        int top = layout.middleTop();
        int contentX = left + layout.sidebarWidth();
        int contentW = layout.width() - layout.sidebarWidth() - 1;
        int height = layout.middleHeight();
        float textScale = layout.textScale() * fontScale("rounds");

        painter.rect(left + 1, top, layout.width() - 2, height - 1, opacity(backgroundColor("rounds", MIDDLE_BG)));
        painter.rect(left + 1, top, layout.width() - 2, 1, withAlpha(0xFFFFFF, 0x1E));
        painter.rect(left + 1, top + height - 1, layout.width() - 2, 1, withAlpha(0xFFFFFF, 0x1E));

        int rowHeight = Math.max(6, (height - 2) / 2);
        float labelScale = 0.7F * textScale;
        int labelH = Math.round(9 * labelScale);
        String allyLabel = Component.translatable("hud.strinova.match.allies").getString();
        String enemyLabel = Component.translatable("hud.strinova.match.enemies").getString();
        int allyLabelX = left + 3;
        int enemyLabelX = left + 3;
        if (style("roundAlly").centerH) {
            int width = Math.round(painter.textWidth(allyLabel) * labelScale);
            allyLabelX = left + Math.max(1, (layout.sidebarWidth() - width) / 2);
        }
        if (style("roundEnemy").centerH) {
            int width = Math.round(painter.textWidth(enemyLabel) * labelScale);
            enemyLabelX = left + Math.max(1, (layout.sidebarWidth() - width) / 2);
        }
        painter.textScaled(allyLabel, allyLabelX, top + 1 + Math.max(0, (rowHeight - labelH) / 2),
                labelScale, textColor("roundAlly", ALLY_WIN), false);
        painter.textScaled(enemyLabel, enemyLabelX,
                top + 1 + rowHeight + Math.max(0, (rowHeight - labelH) / 2),
                labelScale, textColor("roundEnemy", ENEMY_WIN), false);

        List<StrinovaMatchState.RoundResult> results = StrinovaMatchState.roundResults();
        // 回合格随中间带行高自适应放大（图标要看得清）
        // 对齐游戏内 TAB：两排等距菱形圆点 + 中间竖向分隔线
        int roundCell = Math.max(8, Math.min(14, rowHeight - 4));
        int minStep = Math.max(14, roundCell + 8);
        int slotCount = Math.max(1, Math.min(20, (contentW - 8) / minStep));
        int slotStep = Math.max(1, (contentW - 8) / slotCount);
        int start = Math.max(0, results.size() - slotCount);
        int allyCenterY = top + 1 + rowHeight / 2;
        int enemyCenterY = top + 1 + rowHeight + rowHeight / 2;
        int dotRadius = Math.max(1, Math.min(3, rowHeight / 7));
        for (int slot = 0; slot < slotCount; slot++) {
            int centerX = contentX + 4 + slotStep * slot + slotStep / 2;
            int roundIndex = start + slot;
            if (roundIndex < results.size()) {
                StrinovaMatchState.RoundResult result = results.get(roundIndex);
                int centerY = result.allyWon() ? allyCenterY : enemyCenterY;
                int otherY = result.allyWon() ? enemyCenterY : allyCenterY;
                ResourceLocation icon = StrinovaScoreboardIcons.roundIcon(result.reasonKey(), result.allyWon());
                if (icon != null) {
                    int iconSize = roundCell;
                    painter.texture(icon, centerX - iconSize / 2, centerY - iconSize / 2,
                            iconSize, iconSize, 0xFFFFFFFF, 1.0F);
                } else {
                    painter.textScaled(reasonGlyph(result.reasonKey()), centerX - 2, centerY - 4,
                            0.5F * textScale, TEXT, false);
                }
                drawDiamondDot(painter, centerX, otherY, dotRadius, withAlpha(0xFFFFFF, 0x3A));
            } else {
                drawDiamondDot(painter, centerX, allyCenterY, dotRadius, withAlpha(0xFFFFFF, 0x55));
                drawDiamondDot(painter, centerX, enemyCenterY, dotRadius, withAlpha(0xFFFFFF, 0x55));
            }
        }
        int dividerX = contentX + 4 + (slotStep * slotCount) / 2;
        painter.line(dividerX, top + 4, dividerX, top + height - 5, withAlpha(0xFFFFFF, 0x28));
    }

    /** 菱形小点（回合记录占位）。 */
    private static void drawDiamondDot(StrinovaHudPainter painter, int centerX, int centerY,
                                       int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            painter.rect(centerX - half, centerY + dy, half * 2 + 1, 1, color);
        }
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
        if (!visible("sidebar")) {
            return;
        }
        int left = layout.left();
        int top = ally ? layout.blockTop() : layout.enemyTop();
        int sidebarWidth = layout.sidebarWidth();
        int headerHeight = layout.headerHeight();
        int blockHeight = layout.blockHeight();
        // 左侧竖栏字号整体放大 15%
        float textScale = layout.textScale() * fontScale("sidebar") * 1.15F;
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        String teamKey = ally ? StrinovaMatchState.getAlliesTeamKey() : StrinovaMatchState.getEnemiesTeamKey();
        if (teamKey == null || teamKey.isEmpty()) {
            // 大厅/测试条目没有 match 队伍键时，用「剪刀手/欧泊」默认阵营名
            teamKey = ally
                    ? com.hhdxcz.strinova.gameplay.StrinovaMatchManager.alliesTeamKey()
                    : com.hhdxcz.strinova.gameplay.StrinovaMatchManager.enemiesTeamKey();
        }
        String teamName = teamKey == null || teamKey.isEmpty()
                ? Component.translatable(ally ? "hud.strinova.match.allies" : "hud.strinova.match.enemies").getString()
                : Component.translatable(teamKey).getString();
        int score = ally ? StrinovaMatchState.getAlliesScore() : StrinovaMatchState.getEnemiesScore();

        painter.rect(left + 1, top, sidebarWidth - 1, blockHeight - 1, opacity(backgroundColor("sidebar", SIDEBAR_BG)));
        // 阵营名斜切横幅（01 原图是白色矩形，用队伍色染色）
        painter.textureRegion(StrinovaScoreboardIcons.FACTION_BANNER, left + 1, top,
                sidebarWidth - 1, headerHeight, 0.0F, 0.0F, 144, 31, 144, 31, accent, 0.95F);
        float teamScale = 0.8F * textScale;
        int teamX = left + 3;
        if (style("sidebar").centerH) {
            int teamWidth = Math.round(painter.textWidth(teamName) * teamScale);
            teamX = left + Math.max(1, (sidebarWidth - teamWidth) / 2);
        }
        painter.textScaled(teamName, teamX, top + Math.round((headerHeight - 9 * teamScale) / 2),
                teamScale, textColor("sidebar", TEXT), true);

        int areaTop = top + headerHeight;
        int areaHeight = blockHeight - headerHeight - 1;
        // 阵营徽记：剪刀手=粉色翅膀，欧泊=蓝色 P.U.S 盾；比分叠在徽记中间
        boolean scissors = teamName.contains("剪刀")
                || (teamKey != null && teamKey.toLowerCase(java.util.Locale.ROOT).contains("scissor"));
        boolean opal = teamName.contains("欧泊")
                || (teamKey != null && teamKey.toLowerCase(java.util.Locale.ROOT).contains("opal"));
        ResourceLocation emblem = scissors ? StrinovaScoreboardIcons.SCISSORS_EMBLEM
                : opal ? StrinovaScoreboardIcons.OPAL_EMBLEM
                : (ally ? StrinovaScoreboardIcons.SCISSORS_EMBLEM : StrinovaScoreboardIcons.OPAL_EMBLEM);
        int emblemW = Math.max(18, Math.min(sidebarWidth - 8,
                Math.round(Math.max(20, areaHeight - 10) * 108.0F / 132.0F)));
        int emblemH = Math.round(emblemW * 132.0F / 108.0F);
        int emblemX = left + (sidebarWidth - emblemW) / 2;
        // 徽记上下居中；比分用大字号叠在徽记中央
        int emblemY = areaTop + Math.max(2, (areaHeight - emblemH - 8) / 2);
        painter.texture(emblem, emblemX, emblemY, emblemW, emblemH, 0xFFFFFFFF, 0.92F);
        String scoreText = String.valueOf(score);
        float baseWidth = Math.max(1.0F, painter.textWidth(scoreText));
        float scoreScale = Math.max(1.2F, Math.min(
                (emblemH * 0.36F) / 9.0F,
                (emblemW * 0.78F) / baseWidth));
        float scoreWidth = baseWidth * scoreScale;
        // 自定义字体按墨迹盒绘制：补偿字形墨迹相对 9px 行框的偏移，数字才真正居中
        float scoreX = emblemX + (emblemW - scoreWidth) / 2.0F
                - Math.round(scoreScale * 2.5F);
        int scoreY = emblemY + Math.max(0, (emblemH - Math.round(9 * scoreScale)) / 2);
        painter.textScaled(scoreText, scoreX, scoreY,
                scoreScale, textColor("sidebar", TEXT), true);
    }

    // ==================== 右侧内容区 ====================

    private static void renderTeamContent(StrinovaHudPainter painter, Layout layout, boolean ally) {
        // 没有进行中对局时，用当前在线玩家生成一份列表，保证一进游戏按 TAB 就有内容
        List<StrinovaScoreboardState.Entry> entries = StrinovaMatchState.isActive()
                ? (ally ? StrinovaScoreboardState.allies() : StrinovaScoreboardState.enemies())
                : fallbackEntries(ally);
        int top = ally ? layout.blockTop() : layout.enemyTop();
        int contentX = layout.left() + layout.sidebarWidth();
        int contentW = layout.width() - layout.sidebarWidth() - 1;
        int headerHeight = layout.headerHeight();
        int rowHeight = layout.rowHeight();
        float baseScale = layout.textScale();
        // 「数据行」部件的字号倍率作用于两个阵营的所有数据行
        float rowScale = baseScale * fontScale("rows");
        int accent = ally ? TEAM_ALLY : TEAM_ENEMY;
        int[] starts = columnStarts(contentW);
        boolean headerCenter = style("header").centerH;

        // 列头行
        if (visible("header")) {
            float headerScale = BASE_HEADER_TEXT_SCALE * baseScale * fontScale("header");
            painter.rect(contentX, top, contentW, headerHeight, opacity(backgroundColor("header", HEADER_ROW)));
            int headerY = top + Math.round((headerHeight - 8.1F * headerScale) / 2);
            int headerColor = textColor("header", TEXT_DIM);
            drawColumnText(painter, kdaHeader(), contentX, starts, contentW, 1, headerY, headerScale,
                    headerColor, headerCenter, false);
            drawColumnText(painter, Component.translatable("hud.strinova.scoreboard.ultimate").getString(),
                    contentX, starts, contentW, 2, headerY, headerScale, headerColor, headerCenter, false);
            drawColumnText(painter, Component.translatable("hud.strinova.scoreboard.growth").getString(),
                    contentX, starts, contentW, 3, headerY, headerScale, headerColor, headerCenter, false);
            drawColumnText(painter, Component.translatable("hud.strinova.scoreboard.weapon").getString(),
                    contentX, starts, contentW, 4, headerY, headerScale, headerColor, headerCenter, false);
            drawColumnText(painter, Component.translatable("hud.strinova.scoreboard.damage_score").getString(),
                    contentX, starts, contentW, 5, headerY, headerScale, headerColor, headerCenter, false);
            drawColumnText(painter, Component.translatable("hud.strinova.scoreboard.skill_score").getString(),
                    contentX, starts, contentW, 6, headerY, headerScale, headerColor, headerCenter, false);
            drawColumnText(painter, Component.translatable("hud.strinova.scoreboard.assist_score").getString(),
                    contentX, starts, contentW, 7, headerY, headerScale, headerColor, headerCenter, false);
            drawColumnText(painter, "PING", contentX, starts, contentW, 8, headerY, headerScale,
                    headerColor, headerCenter, true);
            painter.rect(contentX, top + headerHeight - 1, contentW, 1, withAlpha(accent, 0xB0));
        }

        // 玩家行
        int rowsTop = top + headerHeight;
        for (int i = 0; i < ROW_COUNT; i++) {
            int rowY = rowsTop + i * rowHeight;
            boolean has = i < entries.size();
            StrinovaScoreboardState.Entry entry = has ? entries.get(i) : null;
            boolean dead = has && entry.status() == StrinovaScoreboardState.Status.DEAD;
            // 服务端同步的 self 标记可能缺失：用本地玩家名字/UUID 再兜底一次
            boolean self = has && (entry.self() || isLocalPlayer(entry.name()));

            int defaultRowBg = self ? ROW_SELF : dead ? ROW_DEAD : ((i % 2 == 0) ? ROW_BG : ROW_ALT);
            painter.rect(contentX, rowY, contentW, rowHeight, opacity(backgroundColor("rows", defaultRowBg)));
            if (self) {
                painter.rect(contentX, rowY, 2, rowHeight, GOLD);
            }
            painter.rect(contentX, rowY + rowHeight - 1, contentW, 1, GRID_LINE);

            if (!has) {
                continue;
            }

            // 头像尽量填满行高（对齐原版卡拉彼丘 TAB：头像≈行高，名字在右侧）
            int avatarSize = Math.max(10, Math.min(28, rowHeight - 2));
            int avatarY = rowY + (rowHeight - avatarSize) / 2;
            int avatarX = contentX + starts[0];
            int textY = rowY
                    + Math.round((rowHeight - 9.0F * BASE_VALUE_TEXT_SCALE * rowScale) / 2);

            if (visible("name")) {
                float nameScale = BASE_NAME_TEXT_SCALE * rowScale * fontScale("name");
                int accentColor = agentAccent(entry.agentName(), accent);
                painter.rect(avatarX, avatarY, avatarSize, avatarSize, 0xFF0D1218);
                ResourceLocation mobileAvatar = StrinovaHudTextures.mobileAvatarByAgentName(entry.agentName());
                ResourceLocation avatarTexture = mobileAvatar != null ? mobileAvatar
                        : StrinovaHudTextures.avatarByAgentName(entry.agentName());
                boolean noAgent = entry.agentName() == null || entry.agentName().isEmpty();
                if (avatarTexture == null && noAgent) {
                    avatarTexture = StrinovaHudTextures.SCOREBOARD_EMPTY;
                }
                if (avatarTexture != null) {
                    if (mobileAvatar != null) {
                        // 移动端头像 100×100：等比缩放到方框（铺满）
                        painter.textureRegion(mobileAvatar, avatarX, avatarY, avatarSize, avatarSize,
                                0.0F, 0.0F, 100, 100, 100, 100, 0xFFFFFFFF, 1.0F);
                    } else {
                        // RoleScoreboard 56×32：按原比例缩到方框内（宽度占满、上下留暗底）
                        int fitHeight = Math.max(4, Math.round(avatarSize * 32.0F / 56.0F));
                        int fitY = avatarY + (avatarSize - fitHeight) / 2;
                        painter.textureRegion(avatarTexture, avatarX, fitY, avatarSize, fitHeight,
                                0.0F, 0.0F, 56, 32, 56, 32, 0xFFFFFFFF, 1.0F);
                    }
                } else {
                    String initial = entry.agentName() == null || entry.agentName().isEmpty()
                            ? "?" : entry.agentName().substring(0, 1);
                    painter.textScaled(initial, avatarX + Math.round((avatarSize - 6.75F * rowScale) / 2),
                            avatarY + Math.round((avatarSize - 6.75F * rowScale) / 2),
                            0.75F * rowScale, TEXT, false);
                }
                painter.border(avatarX, avatarY, avatarSize, avatarSize, 0x66FFFFFF, 1);
                if (entry.status() == StrinovaScoreboardState.Status.ALIVE) {
                    painter.rect(avatarX + avatarSize - 4, avatarY + avatarSize - 3, 3, 3, GREEN);
                }
                String nameText = entry.name() == null ? "" : entry.name();
                int nameX = avatarX + avatarSize + 5;
                if (style("name").centerH) {
                    int areaStart = nameX;
                    int areaEnd = contentX + starts[1];
                    int nameWidth = Math.round(painter.textWidth(nameText) * nameScale);
                    nameX = areaStart + Math.max(0, (areaEnd - areaStart - nameWidth) / 2);
                }
                painter.textScaled(nameText, nameX, textY, nameScale,
                        textColor("name", self ? GOLD : dead ? TEXT_DIM : TEXT), false);
            }

            drawColumnValue(painter, "kda", entry.kills() + " / " + entry.deaths() + " / " + entry.assists(),
                    contentX, starts, contentW, 1, textY, rowScale, TEXT);
            drawColumnValue(painter, "ultimate", entry.ultimatePoints() + "/" + entry.ultimateMax(),
                    contentX, starts, contentW, 2, textY, rowScale, 0xFF8FD8FF);

            if (visible("growth")) {
                float growthScale = BASE_VALUE_TEXT_SCALE * rowScale * fontScale("growth");
                int colStart = contentX + starts[3];
                int colEnd = contentX + columnEnd(starts, contentW, 3);
                int available = Math.max(24, colEnd - colStart - 3);
                // 蓝方块=武器网络点数 / 黄圆=主动+被动技能 / 绿三角=护甲+弦化
                // 全队玩家的分色点数由 C2S_PROFILE -> S2C_MATCH_STATE 同步；默认 0，形状内显示 0。
                int[] counts = {entry.weaponPoints(), entry.skillPoints(), entry.defensePoints()};
                // 其他玩家的觉醒通过服务端同步的 mask 显示；自己的行再用本地状态兜底
                int[] active = StrinovaGrowthState.indicesFromMask(entry.awakeningMask());
                if (active.length == 0 && self && StrinovaGrowthState.hasData()) {
                    active = StrinovaGrowthState.activeAwakeningIndices();
                }
                int slots = 3 + (active.length > 0 ? 1 + active.length : 0);
                int slot = Math.max(7, Math.min(16, available / slots));
                int shape = Math.max(6, slot - 2);
                int shapeY = rowY + (rowHeight - shape) / 2;
                int arrowH = Math.max(5, Math.min(12, rowHeight - 6));
                int arrowWidth = Math.max(3, Math.round(arrowH * 11.0F / 17.0F));
                int cx = colStart;
                if (style("growth").centerH) {
                    int total = shape * 3 + 6
                            + (active.length > 0 ? arrowWidth + active.length * (slot + 2) : 0);
                    cx = colStart + Math.max(0, (colEnd - colStart - total) / 2);
                }
                drawGrowthSquare(painter, cx, shapeY, shape, 0xFF4FA3FF, counts[0], growthScale);
                cx += shape + 2;
                drawGrowthCircle(painter, cx, shapeY, shape, 0xFFE8B54D, counts[1], growthScale);
                cx += shape + 2;
                drawGrowthTriangle(painter, cx, shapeY, shape, 0xFF57C785, counts[2], growthScale);
                cx += shape + 3;
                if (active.length > 0) {
                    painter.textureRegion(StrinovaScoreboardIcons.AWAKEN_ARROW, cx,
                            rowY + (rowHeight - arrowH) / 2, arrowWidth, arrowH,
                            0.0F, 0.0F, 11, 17, 11, 17, 0xFFFFFFFF, 1.0F);
                    cx += arrowWidth + 2;
                }
                for (int index : active) {
                    int starY = rowY + (rowHeight - slot) / 2;
                    painter.textureRegion(StrinovaScoreboardIcons.AWAKEN_STAR, cx,
                            starY, slot, slot,
                            0.0F, 0.0F, 26, 26, 26, 26, 0xFFFFFFFF, 1.0F);
                    // 星芒贴图本身没有数字，这里叠上觉醒序号（1~3）
                    String label = String.valueOf(index);
                    float labelScale = Math.max(0.4F, slot * 0.52F / 9.0F);
                    int labelW = Math.round(painter.textWidth(label) * labelScale);
                    int labelH = Math.round(9.0F * labelScale);
                    painter.textScaled(label,
                            cx + (slot - labelW) / 2.0F - Math.round(labelScale * 2.0F),
                            starY + (slot - labelH) / 2.0F,
                            labelScale, 0xFFFFFFFF, true);
                    cx += slot + 2;
                }
            }

            drawColumnValue(painter, "weapon", entry.weaponName() == null ? "" : entry.weaponName(),
                    contentX, starts, contentW, 4, textY, rowScale, TEXT_DIM);
            drawColumnValue(painter, "damage", String.valueOf(entry.damageScore()),
                    contentX, starts, contentW, 5, textY, rowScale, TEXT);
            drawColumnValue(painter, "skill", String.valueOf(entry.skillScore()),
                    contentX, starts, contentW, 6, textY, rowScale, TEXT);
            drawColumnValue(painter, "assist", String.valueOf(entry.assistScore()),
                    contentX, starts, contentW, 7, textY, rowScale, TEXT);

            // 输出/技能/协作里唯一最高的一项，在数值右边显示奖章图标
            int[] scores = {entry.damageScore(), entry.skillScore(), entry.assistScore()};
            int bestColumn = -1;
            int bestValue = 0;
            boolean tied = false;
            for (int scoreIndex = 0; scoreIndex < scores.length; scoreIndex++) {
                if (scores[scoreIndex] > bestValue) {
                    bestValue = scores[scoreIndex];
                    bestColumn = scoreIndex;
                    tied = false;
                } else if (scores[scoreIndex] == bestValue && bestValue > 0) {
                    tied = true;
                }
            }
            if (!tied && bestValue > 0 && bestColumn >= 0) {
                int index = 5 + bestColumn;
                String key = StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS[index];
                String valueText = String.valueOf(bestValue);
                float valueScale = BASE_VALUE_TEXT_SCALE * rowScale * fontScale(key);
                int colStart = contentX + starts[index];
                int colEnd = contentX + columnEnd(starts, contentW, index);
                int valueWidth = Math.round(painter.textWidth(valueText) * valueScale);
                int valueX = style(key).centerH
                        ? colStart + Math.max(0, (colEnd - colStart - valueWidth) / 2)
                        : colStart;
                int iconH = Math.max(6, Math.round(9.0F * rowScale));
                int iconW = Math.max(5, Math.round(iconH * 15.0F / 19.0F));
                int iconX = valueX + valueWidth + 2;
                if (iconX + iconW > colEnd - 1) {
                    iconX = Math.max(colStart, colEnd - 1 - iconW);
                }
                int iconY = textY + Math.max(0, Math.round((9.0F * valueScale - iconH) / 2.0F));
                painter.textureRegion(StrinovaScoreboardIcons.MEDAL, iconX, iconY, iconW, iconH,
                        0.0F, 0.0F, 15, 19, 15, 19, 0xFFFFFFFF, 1.0F);
            }

            if (visible("ping")) {
                if (entry.ping() <= 0) {
                    // 玩家断链：PING 列显示断线图标
                    int iconH = Math.max(6, Math.round(8.0F * rowScale));
                    int iconW = Math.max(8, Math.round(iconH * 33.0F / 21.0F));
                    int colStart = contentX + starts[8];
                    int colEnd = contentX + columnEnd(starts, contentW, 8);
                    int iconX = style("ping").centerH
                            ? colStart + Math.max(0, (colEnd - colStart - iconW) / 2)
                            : Math.max(colStart, colEnd - 4 - iconW);
                    int iconY = rowY + Math.max(0, (rowHeight - iconH) / 2);
                    painter.textureRegion(StrinovaScoreboardIcons.DISCONNECT, iconX, iconY,
                            iconW, iconH, 0.0F, 0.0F, 33, 21, 33, 21, 0xFFFFFFFF, 1.0F);
                } else {
                    float pingScale = BASE_VALUE_TEXT_SCALE * rowScale * fontScale("ping");
                    String pingText = String.valueOf(entry.ping());
                    int pingColor = textColor("ping", entry.ping() > 100 ? GOLD : TEXT_DIM);
                    if (style("ping").centerH) {
                        drawColumnText(painter, pingText, contentX, starts, contentW, 8, textY, pingScale,
                                pingColor, true, false);
                    } else {
                        int pingWidth = Math.round(painter.textWidth(pingText) * pingScale);
                        painter.textScaled(pingText, contentX + contentW - 4 - pingWidth, textY, pingScale,
                                pingColor, false);
                    }
                }
            }
        }
    }

    /** 列起点数组（9 列：name/kda/ultimate/growth/weapon/damage/skill/assist/ping）。 */
    public static int[] columnStarts(int contentW) {
        int[] anchors = columnAnchors(contentW);
        int[] starts = new int[9];
        for (int i = 0; i < anchors.length; i++) {
            starts[i] = anchors[i];
        }
        // 头像左侧留白（可拖「头像前」边界调整，0~24px）
        int padding = Math.max(0, Math.min(24, StrinovaHudConfig.get().scoreboardNameLeftPadding));
        starts[0] = Math.min(starts[1] - 8, anchors[0] + padding);
        starts[0] = Math.max(1, starts[0]);
        // PING 起点按「协作分宽度权重」累计，这样拖动「协作分|PING」边界时边界会跟着动
        StrinovaHudConfig config = StrinovaHudConfig.get();
        String[] keys = StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS;
        float total = 0.0F;
        for (String key : keys) {
            total += config.scoreboardColumnWeight(key);
        }
        float assistFraction = total <= 0.0F ? 0.10F
                : Math.max(0.02F, config.scoreboardColumnWeight("assist") / total);
        int pingStart = anchors[anchors.length - 1] + Math.round(contentW * assistFraction);
        starts[8] = Math.max(starts[7] + 8, Math.min(contentW - 8, pingStart));
        return starts;
    }

    /** 成长等级：蓝色方块（武器网络点数）——官方 HUD 贴图。 */
    private static void drawGrowthSquare(StrinovaHudPainter painter, int x, int y, int size,
                                         int color, int count, float scale) {
        painter.texture(GROWTH_SQUARE, x, y, size, size, 0xFFFFFFFF, 1.0F);
        drawShapeCount(painter, count, x, y, size, scale);
    }

    /** 成长等级：黄色圆（主动/被动技能点数）——官方 HUD 贴图。 */
    private static void drawGrowthCircle(StrinovaHudPainter painter, int x, int y, int size,
                                         int color, int count, float scale) {
        painter.texture(GROWTH_CIRCLE, x, y, size, size, 0xFFFFFFFF, 1.0F);
        drawShapeCount(painter, count, x, y, size, scale);
    }

    /** 成长等级：绿色三角（护甲/弦化点数）——官方 HUD 贴图。 */
    private static void drawGrowthTriangle(StrinovaHudPainter painter, int x, int y, int size,
                                           int color, int count, float scale) {
        painter.texture(GROWTH_TRIANGLE, x, y, size, size, 0xFFFFFFFF, 1.0F);
        drawShapeCount(painter, count, x, y, size, scale);
    }

    /** 在形状内部居中绘制数字。 */
    private static void drawShapeCount(StrinovaHudPainter painter, int count, int x, int y,
                                       int size, float scale) {
        String text = String.valueOf(Math.max(0, count));
        float numberScale = Math.min(0.8F * scale, size * 0.85F / 9.0F);
        int width = Math.round(painter.textWidth(text) * numberScale);
        // 自定义字体按墨迹盒绘制：字形墨迹顶部/左侧相对 9px 行框有偏移，
        // 这里补偿一下，数字才真正落在形状中心（否则会偏右下）。
        int inkOffsetX = Math.round(numberScale * 2.0F);
        painter.textScaled(text,
                x + Math.max(1, (size - width) / 2) - inkOffsetX,
                y + Math.max(0, (size - 9.0F * numberScale) / 2),
                numberScale, 0xFFFFFFFF, true);
    }

    /** 八芒星（已激活觉醒）：橙金色，星内数字为觉醒序号（1~3）。 */
    private static void drawEightPointStar(StrinovaHudPainter painter, int x, int y, int size,
                                           int color, int index, float scale) {
        int radius = Math.max(3, size / 2);
        int centerX = x + radius;
        int centerY = y + radius;
        // 8 根芒尖（每 45°，两像素宽）
        for (int k = 0; k < 8; k++) {
            double angle = Math.PI * 2.0 * k / 8.0;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dy = (int) Math.round(Math.sin(angle) * radius);
            painter.line(centerX, centerY, centerX + dx, centerY + dy, color);
            painter.line(centerX + (dx > 0 ? 1 : dx < 0 ? -1 : 0), centerY,
                    centerX + dx + (dx > 0 ? 1 : dx < 0 ? -1 : 0),
                    centerY + dy, color);
        }
        // 中心小菱形 + 序号
        for (int dy = -radius / 2; dy <= radius / 2; dy++) {
            int half = radius / 2 - Math.abs(dy);
            painter.rect(centerX - half, centerY + dy, half * 2 + 1, 1, color);
        }
        String text = String.valueOf(index);
        float numberScale = Math.min(0.75F * scale, size * 0.8F / 9.0F);
        int width = Math.round(painter.textWidth(text) * numberScale);
        painter.textScaled(text, centerX - width / 2,
                centerY - Math.round(4.5F * numberScale), numberScale, 0xFFFFFFFF, false);
    }

    private static int columnEnd(int[] starts, int contentW, int index) {
        return index + 1 < starts.length ? starts[index + 1] : contentW;
    }

    /** 按列绘制数据文本：支持左对齐 / 居中 / 右对齐（PING）。 */
    private static void drawColumnValue(StrinovaHudPainter painter, String key, String text,
                                        int contentX, int[] starts, int contentW, int index,
                                        int y, float textScale, int fallbackColor) {
        if (!visible(key)) {
            return;
        }
        float scale = BASE_VALUE_TEXT_SCALE * textScale * fontScale(key);
        drawColumnText(painter, text, contentX, starts, contentW, index, y, scale,
                textColor(key, fallbackColor), style(key).centerH, false);
    }

    private static void drawColumnText(StrinovaHudPainter painter, String text, int contentX,
                                       int[] starts, int contentW, int index, int y, float scale,
                                       int color, boolean center, boolean right) {
        int start = starts[index];
        int end = columnEnd(starts, contentW, index);
        int width = Math.round(painter.textWidth(text) * scale);
        int x = contentX + start;
        if (center) {
            x = contentX + start + Math.max(0, (end - start - width) / 2);
        } else if (right) {
            x = contentX + end - 4 - width;
        }
        painter.textScaled(text, x, y, scale, color, false);
    }

    // ==================== 列位置 ====================

    /** 各列起点（内容区坐标，含头像+名字列）；编辑器拖列边界时用同一套计算。 */
    public static int[] columnAnchors(int contentW) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        String[] keys = StrinovaHudConfig.SCOREBOARD_COLUMN_KEYS;
        float total = 0.0F;
        for (String key : keys) {
            total += config.scoreboardColumnWeight(key);
        }
        if (total <= 0.0F) {
            total = 1.0F;
        }
        int[] anchors = new int[8];
        anchors[0] = 4;
        float pos = config.scoreboardColumnWeight(keys[0]) / total;
        for (int i = 1; i < anchors.length; i++) {
            anchors[i] = Math.round(4 + contentW * pos);
            pos += config.scoreboardColumnWeight(keys[i]) / total;
        }
        return anchors;
    }

    private static String kdaHeader() {
        return Component.translatable("hud.strinova.scoreboard.kills").getString() + "/"
                + Component.translatable("hud.strinova.scoreboard.deaths").getString() + "/"
                + Component.translatable("hud.strinova.scoreboard.assists").getString();
    }

    /** 队伍徽章（程序化几何占位：菱形）。 */
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

    /** 无进行中对局时的 TAB 列表：用当前在线玩家（前一半我方、后一半敌方）。 */
    private static List<StrinovaScoreboardState.Entry> fallbackEntries(boolean ally) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return List.of();
        }
        List<PlayerInfo> infos = new ArrayList<>(minecraft.getConnection().getOnlinePlayers());
        infos.sort(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        int allyCount = Math.min(ROW_COUNT, (infos.size() + 1) / 2);
        List<StrinovaScoreboardState.Entry> result = new ArrayList<>();
        for (int i = 0; i < infos.size(); i++) {
            boolean isAlly = i < allyCount;
            if (isAlly != ally) {
                continue;
            }
            if (result.size() >= ROW_COUNT) {
                break;
            }
            PlayerInfo info = infos.get(i);
            UUID id = info.getProfile().getId();
            boolean self = minecraft.player != null && minecraft.player.getUUID().equals(id);
            int weaponPoints = 0;
            int skillPoints = 0;
            int defensePoints = 0;
            if (self && StrinovaGrowthState.hasData()) {
                weaponPoints = StrinovaGrowthState.purchasedWeapon();
                skillPoints = StrinovaGrowthState.purchasedSkill();
                defensePoints = StrinovaGrowthState.purchasedDefense();
            }
            result.add(new StrinovaScoreboardState.Entry(
                    info.getProfile().getName(), "", isAlly, self,
                    0, 0, 0, 150.0F, 80.0F, StrinovaScoreboardState.Status.ALIVE,
                    0, 7, 1, "", 0, 0, 0, info.getLatency(),
                    weaponPoints, skillPoints, defensePoints,
                    self && StrinovaGrowthState.hasData()
                            ? StrinovaGrowthState.activeAwakeningMask() : 0));
        }
        return result;
    }

    private static boolean isLocalPlayer(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && name != null
                && name.equals(minecraft.player.getName().getString());
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
