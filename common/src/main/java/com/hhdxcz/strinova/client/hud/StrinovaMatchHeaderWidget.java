package com.hhdxcz.strinova.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 对局顶部面板（图形编辑器版）：
 * 左右头像行、左右比分条（sprite_235/377）、中间目标/计时都由独立图层绘制，
 * 每个图层可在 {@link com.hhdxcz.strinova.client.StrinovaMatchHeaderEditorScreen} 中单独移动/缩放。
 */
public final class StrinovaMatchHeaderWidget implements StrinovaHudWidget {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 60;
    private static final int PORTRAIT = 24;
    private static final int PORTRAIT_STEP = 24;

    private static final ResourceLocation TEX_SCORE_BLUE =
            new ResourceLocation("strinova", "textures/gui/topbar/score_blue.png");
    private static final ResourceLocation TEX_SCORE_RED =
            new ResourceLocation("strinova", "textures/gui/topbar/score_red.png");
    /** 最上层两张长条：sprite_666（蓝）/ sprite_777（红）。 */
    private static final ResourceLocation TEX_TOP_BLUE =
            new ResourceLocation("strinova", "textures/gui/topbar/top_blue.png");
    private static final ResourceLocation TEX_TOP_RED =
            new ResourceLocation("strinova", "textures/gui/topbar/top_red.png");
    /** 额外底板：sprite_206（灰色梯形）。 */
    private static final ResourceLocation TEX_EXTRA_206 =
            new ResourceLocation("strinova", "textures/gui/topbar/extra_206.png");
    private static final int COLOR_TEXT_DIM = 0xFF9FC6D8;

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

    /** 一个可编辑图层：key 用于配置，x/y/w/h 为当前（已应用偏移缩放）矩形。 */
    public record Layer(String key, String type, int x, int y, int w, int h,
                        boolean visible, int baseX, int baseY, int baseW, int baseH,
                        float rotation, float opacity, boolean mirrorX, boolean mirrorY, int order) {
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        boolean active = StrinovaMatchState.isActive();
        int alliesScore = active ? StrinovaMatchState.getAlliesScore() : 3;
        int enemiesScore = active ? StrinovaMatchState.getEnemiesScore() : 2;
        String scoreAlly = twoDigits(alliesScore);
        String scoreEnemy = twoDigits(enemiesScore);
        List<StrinovaScoreboardState.Entry> allies = StrinovaScoreboardState.allies();
        List<StrinovaScoreboardState.Entry> enemies = StrinovaScoreboardState.enemies();

        List<Layer> sorted = new ArrayList<>(layers(painter, x, y));
        sorted.sort(Comparator.comparingInt(Layer::order));
        for (Layer layer : sorted) {
            if (!layer.visible()) {
                continue;
            }
            boolean transformed = layer.rotation() != 0.0F || layer.mirrorX() || layer.mirrorY();
            if (transformed) {
                painter.pushTransform(layer.x() + layer.w() / 2.0F, layer.y() + layer.h() / 2.0F,
                        layer.rotation(), layer.mirrorX(), layer.mirrorY());
            }
            StrinovaHudPainter draw = layer.opacity() < 0.999F
                    ? new StrinovaAlphaPainter(painter, layer.opacity()) : painter;
            switch (layer.key()) {
                case "leftTop" -> draw.textureRegion(TEX_TOP_BLUE, layer.x(), layer.y(),
                        layer.w(), layer.h(), 0, 0, 587, 42, 587, 42, 0xFFFFFFFF, 1.0F);
                case "rightTop" -> draw.textureRegion(TEX_TOP_RED, layer.x(), layer.y(),
                        layer.w(), layer.h(), 0, 0, 588, 42, 588, 42, 0xFFFFFFFF, 1.0F);
                case "leftBottom" -> draw.textureRegion(TEX_SCORE_BLUE, layer.x(), layer.y(),
                        layer.w(), layer.h(), 0, 0, 95, 42, 95, 42, 0xFFFFFFFF, 1.0F);
                case "rightBottom" -> draw.textureRegion(TEX_SCORE_RED, layer.x(), layer.y(),
                        layer.w(), layer.h(), 0, 0, 95, 42, 95, 42, 0xFFFFFFFF, 1.0F);
                case "extra206" -> draw.textureRegion(TEX_EXTRA_206, layer.x(), layer.y(),
                        layer.w(), layer.h(), 0, 0, 256, 77, 256, 77, 0xFFFFFFFF, 1.0F);
                case "leftScore" -> drawScoreText(draw, layer, scoreAlly);
                case "rightScore" -> drawScoreText(draw, layer, scoreEnemy);
                case "portraitsLeft" -> drawPortraitRow(draw, layer, allies);
                case "portraitsRight" -> drawPortraitRow(draw, layer, enemies);
                case "target" -> drawTargetText(draw, layer, active);
                case "separator" -> drawSeparator(draw, layer);
                case "timer" -> drawTimer(draw, layer, active);
                default -> {
                }
            }
            if (transformed) {
                painter.popTransform();
            }
        }
    }

    /** 所有图层（含隐藏图层），供渲染和编辑器共用。 */
    public static List<Layer> layers(StrinovaHudPainter painter, int x, int y) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        boolean active = StrinovaMatchState.isActive();
        int centerX = x + WIDTH / 2;
        int alliesScore = active ? StrinovaMatchState.getAlliesScore() : 3;
        int enemiesScore = active ? StrinovaMatchState.getEnemiesScore() : 2;
        int round = active ? StrinovaMatchState.getRound() : 9;
        int seconds = active ? StrinovaMatchState.getRoundSecondsLeft() : 18;
        if (seconds < 0) {
            seconds = 0;
        }
        String roundText = Component.translatable("hud.strinova.match.target_round", round).getString();
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);

        int portraitY = y + (HEIGHT - PORTRAIT) / 2;
        int leftPortraitX = x + 4;
        int rightPortraitX = x + WIDTH - 4 - PORTRAIT * 5;

        int leftTopW = 96;
        int leftTopH = 12;
        int leftTopX = centerX - 105 - leftTopW / 2;
        int leftTopY = y + 3;

        int bottomH = 22;
        int bottomW = Math.round(bottomH * 95.0F / 42.0F);
        int bottomY = y + 18;
        int leftBottomX = centerX - 105 - bottomW / 2;
        int rightBottomX = centerX + 105 - bottomW / 2;

        int targetW = Math.max(1, painter.textWidth(roundText));
        int timerW = Math.max(1, Math.round(StrinovaHudDigits.textWidth(time, 24.0F)));
        int sepW = 42;

        List<Layer> out = new ArrayList<>();
        out.add(layer(config, "extra206", "image206",
                centerX - 110, y - 3, 220, 66));
        out.add(layer(config, "portraitsLeft", "portraits",
                leftPortraitX, portraitY, PORTRAIT * 5, PORTRAIT));
        out.add(layer(config, "portraitsRight", "portraits",
                rightPortraitX, portraitY, PORTRAIT * 5, PORTRAIT));
        out.add(layer(config, "target", "target",
                centerX - targetW / 2, y + 3, targetW, 9));
        out.add(layer(config, "separator", "separator",
                centerX - sepW / 2, y + 15, sepW, 3));
        out.add(layer(config, "timer", "timer",
                centerX - timerW / 2, y + 19, timerW, 24));
        out.add(layer(config, "leftTop", "image",
                leftTopX, leftTopY, leftTopW, leftTopH));
        out.add(layer(config, "rightTop", "image",
                centerX + 105 - leftTopW / 2, leftTopY, leftTopW, leftTopH));
        out.add(layer(config, "leftBottom", "image",
                leftBottomX, bottomY, bottomW, bottomH));
        out.add(layer(config, "rightBottom", "image",
                rightBottomX, bottomY, bottomW, bottomH));
        int scoreTextW = Math.max(1, painter.textWidth("00"));
        int scoreTextH = 9;
        int scoreTextY = bottomY + (bottomH - scoreTextH) / 2;
        out.add(layer(config, "leftScore", "score",
                centerX - 105 - scoreTextW / 2, scoreTextY, scoreTextW, scoreTextH));
        out.add(layer(config, "rightScore", "score",
                centerX + 105 - scoreTextW / 2, scoreTextY, scoreTextW, scoreTextH));
        return out;
    }

    private static Layer layer(StrinovaHudConfig config, String key, String type,
                               int baseX, int baseY, int baseW, int baseH) {
        StrinovaHudConfig.HeaderLayerStyle style = config.matchHeaderLayer(key);
        int w = Math.max(2, Math.round(baseW * style.scaleX));
        int h = Math.max(2, Math.round(baseH * style.scaleY));
        int x = baseX + style.offsetX - (w - baseW) / 2;
        int y = baseY + style.offsetY - (h - baseH) / 2;
        return new Layer(key, type, x, y, w, h, style.visible, baseX, baseY, baseW, baseH,
                style.rotation, style.opacity, style.mirrorX, style.mirrorY, style.order);
    }

    private static void drawScoreText(StrinovaHudPainter painter, Layer layer, String score) {
        float scale = Math.max(0.5F, Math.min(3.0F, layer.h() / 9.0F));
        float width = painter.textWidth(score) * scale;
        float x = layer.x() + (layer.w() - width) / 2.0F;
        float y = layer.y() + (layer.h() - 9.0F * scale) / 2.0F;
        painter.textScaled(score, x, y, scale, 0xFFFFFFFF, true);
    }

    private static void drawPortraitRow(StrinovaHudPainter painter, Layer layer,
                                        List<StrinovaScoreboardState.Entry> entries) {
        float scale = Math.max(0.3F, layer.h() / (float) PORTRAIT);
        int size = Math.max(6, Math.round(PORTRAIT * scale));
        int step = Math.max(6, Math.round(layer.w() / 5.0F));
        for (int i = 0; i < 5; i++) {
            drawTeamPortrait(painter, layer.x() + i * step, layer.y(), size, entry(entries, i));
        }
    }

    private static void drawTargetText(StrinovaHudPainter painter, Layer layer, boolean active) {
        int round = active ? StrinovaMatchState.getRound() : 9;
        String text = Component.translatable("hud.strinova.match.target_round", round).getString();
        float scale = Math.max(0.4F, layer.h() / 9.0F);
        float width = painter.textWidth(text) * scale;
        painter.textScaled(text, layer.x() + (layer.w() - width) / 2.0F, layer.y(),
                scale, COLOR_TEXT_DIM, true);
    }

    private static void drawSeparator(StrinovaHudPainter painter, Layer layer) {
        int thickness = Math.max(1, layer.h() / 3);
        int third = Math.max(2, layer.w() / 5);
        int y = layer.y() + layer.h() / 2 - thickness / 2;
        painter.rect(layer.x(), y, third, thickness, 0xB3FFFFFF);
        painter.rect(layer.x() + layer.w() / 2 - 2, y - 1, 4, thickness + 2, 0xE6FFFFFF);
        painter.rect(layer.x() + layer.w() - third, y, third, thickness, 0xB3FFFFFF);
    }

    private static void drawTimer(StrinovaHudPainter painter, Layer layer, boolean active) {
        int seconds = active ? StrinovaMatchState.getRoundSecondsLeft() : 18;
        if (seconds < 0) {
            seconds = 0;
        }
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        float height = Math.max(8.0F, layer.h());
        float width = StrinovaHudDigits.textWidth(time, height);
        int x = Math.round(layer.x() + (layer.w() - width) / 2.0F);
        // 高分辨率 Saira Medium 数字图集：和 TAB 数值同款字形，无阴影、不发糊
        StrinovaHudDigits.draw(painter, time, x, layer.y(), height, 0xFFFFFFFF, 1.0F);
    }

    /** 顶部面板玩家头像：官方 RoleScoreboard 贴图。 */
    private static void drawTeamPortrait(StrinovaHudPainter painter, int x, int y, int size,
                                         StrinovaScoreboardState.Entry entry) {
        ResourceLocation texture = entry == null ? null
                : StrinovaHudTextures.avatarByAgentName(entry.agentName());
        if (texture == null) {
            texture = StrinovaHudTextures.SCOREBOARD_EMPTY;
        }
        int fitHeight = Math.max(6, Math.round(size * 32.0F / 56.0F));
        int fitY = y + (size - fitHeight) / 2;
        painter.rect(x, fitY, size, fitHeight, 0xB3101820);
        painter.textureRegion(texture, x, fitY, size, fitHeight,
                0.0F, 0.0F, 56, 32, 56, 32, 0xFFFFFFFF, 1.0F);
        int border;
        if (entry != null && entry.self()) {
            border = 0xFFFFD166;
        } else if (entry != null && entry.status() == StrinovaScoreboardState.Status.ALIVE) {
            border = 0xCCFFFFFF;
        } else {
            border = 0x55FFFFFF;
        }
        painter.border(x, fitY, size, fitHeight, border, 1);
    }

    private static StrinovaScoreboardState.Entry entry(List<StrinovaScoreboardState.Entry> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", Math.max(0, value));
    }
}
