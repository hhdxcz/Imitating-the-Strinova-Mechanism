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
    private static final int CENTER_PLATE_W = 150;
    private static final int CENTER_PLATE_H = 54;
    private static final int SCORE_PLATE_W = 70;
    private static final int SCORE_PLATE_H = 22;

    /** 表格指定贴图：左=sprite_235（蓝），右=sprite_377（红）。 */
    private static final ResourceLocation TEX_SCORE_BLUE =
            new ResourceLocation("strinova", "textures/gui/topbar/score_blue.png");
    private static final ResourceLocation TEX_SCORE_RED =
            new ResourceLocation("strinova", "textures/gui/topbar/score_red.png");

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
        painter.text(roundText, centerX - painter.textWidth(roundText) / 2, y + 4,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        // 中央倒计时：官方 DP_Num 七段数码数字
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        float digitHeight = 27.0F;
        float timeWidth = StrinovaHudDigits.textWidth(time, digitHeight);
        StrinovaHudDigits.draw(painter, time, Math.round(centerX - timeWidth / 2.0F), y + 16,
                digitHeight, 0xFFFFFFFF, 1.0F);

        // 左右比分板（斜切）
        int scoreY = y + 18;
        int leftScoreX = centerX - 75 - SCORE_PLATE_W;
        int rightScoreX = centerX + 75;
        // 严格按表格：左 sprite_235（蓝），右 sprite_377（红）
        drawScorePlate(painter, leftScoreX, scoreY, SCORE_PLATE_W, SCORE_PLATE_H, 10,
                COLOR_PLATE, COLOR_ALLY, twoDigits(alliesScore), 0xFFFFFFFF, true, TEX_SCORE_BLUE);
        drawScorePlate(painter, rightScoreX, scoreY, SCORE_PLATE_W, SCORE_PLATE_H, -10,
                COLOR_PLATE, COLOR_ENEMY, twoDigits(enemiesScore), 0xFFFFFFFF, false, TEX_SCORE_RED);
    }

    /** 比分板：严格按表格使用 sprite_235 / sprite_377。 */
    private static void drawScorePlate(StrinovaHudPainter painter, int x, int y, int width, int height,
                                       int skew, int bg, int edge, String score, int scoreColor,
                                       boolean allySide, ResourceLocation barTexture) {
        // 平行四边形比分板（sprite_377=红 / sprite_235=蓝），横向拉长贴合区域
        painter.textureRegion(barTexture, x, y, width, height,
                0.0F, 0.0F, 95, 42, 95, 42, 0xFFFFFFFF, 1.0F);
        int textY = y + (height - painter.textHeight()) / 2;
        if (allySide) {
            painter.textRightAligned(score, x + width - 8, textY, scoreColor, true);
        } else {
            painter.text(score, x + 8, textY, scoreColor, true);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** 顶部面板玩家头像：官方 RoleScoreboard 贴图；未选角/中途加入用 HalfEnterEmpty。 */
    private static void drawTeamPortrait(StrinovaHudPainter painter, int x, int y,
                                         StrinovaScoreboardState.Entry entry, boolean enemy) {
        ResourceLocation texture = entry == null ? null
                : StrinovaHudTextures.avatarByAgentName(entry.agentName());
        if (texture == null) {
            texture = StrinovaHudTextures.SCOREBOARD_EMPTY;
        }
        // 头像原图 56×32：底框、边框都贴合图片本身（不再包正方形大框）
        int fitHeight = Math.max(6, Math.round(PORTRAIT * 32.0F / 56.0F));
        int fitY = y + (PORTRAIT - fitHeight) / 2;
        painter.rect(x, fitY, PORTRAIT, fitHeight, 0xB3101820);
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
        painter.border(x, fitY, PORTRAIT, fitHeight, border, 1);
        // 底部细高光，贴近真实 UI 的玻璃质感
        painter.rect(x + 1, fitY + fitHeight - 2, PORTRAIT - 2, 1, 0x33FFFFFF);
        if (entry != null && entry.status() == StrinovaScoreboardState.Status.ALIVE) {
            painter.rect(x + PORTRAIT - 4, fitY + fitHeight - 4, 3, 3, 0xFF5FE08C);
        }
    }

    private static StrinovaScoreboardState.Entry entry(List<StrinovaScoreboardState.Entry> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }
}
