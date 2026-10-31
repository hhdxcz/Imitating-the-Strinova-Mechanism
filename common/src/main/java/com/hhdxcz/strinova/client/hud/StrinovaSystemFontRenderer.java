package com.hhdxcz.strinova.client.hud;

import com.hhdxcz.strinova.StrinovaMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 运行时可选的「微软雅黑」字体渲染器：
 * 从系统字体目录（C:\Windows\Fonts\msyh.ttc / msyhbd.ttc）或 config/strinova-fonts/ 下的
 * .ttf/.ttc 字体文件加载，把字符按需绘制到一张 512×512 的动态字形图集，再用 GuiGraphics 绘制。
 *
 * <p>找不到字体 / 加载失败时自动回退原版字体（{@link #isReady()} 为 false）。</p>
 */
public final class StrinovaSystemFontRenderer {

    private static final int ATLAS_SIZE = 512;
    /** 字形单元格与 AWT 渲染字号：尽量接近实际显示像素，减少缩小比例。 */
    private static final int CELL = 20;
    private static final int COLS = ATLAS_SIZE / CELL;
    private static final int MAX_GLYPHS = COLS * COLS;
    private static final int FONT_PX = 14;
    private static final int TARGET_TEXT_PX = 9;

    private static boolean triedInit;
    private static boolean ready;
    private static Font awtFont;
    private static BufferedImage measureImage;
    private static FontMetrics measureMetrics;
    private static NativeImage atlasImage;
    private static DynamicTexture atlasTexture;
    private static ResourceLocation atlasId;
    private static int nextGlyph;
    private static boolean dirty;
    private static final Map<Integer, Integer> GLYPH_CELL = new HashMap<>();
    private static final Map<Integer, Integer> GLYPH_ADVANCE = new HashMap<>();

    private StrinovaSystemFontRenderer() {
    }

    public static synchronized boolean isReady() {
        ensureInit();
        return ready;
    }

    /** 文本宽度（与默认字体 9px 基准一致；调用方自行乘缩放）。 */
    public static synchronized int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        ensureInit();
        if (!ready) {
            return 0;
        }
        float sum = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            int ch = text.charAt(i);
            Integer advance = GLYPH_ADVANCE.get(ch);
            if (advance == null) {
                ensureGlyph(ch);
                advance = GLYPH_ADVANCE.get(ch);
            }
            if (advance == null) {
                advance = FONT_PX / 2;
            }
            sum += advance;
        }
        return Math.round(sum * ((float) TARGET_TEXT_PX / FONT_PX));
    }

    /**
     * 绘制文本；坐标/颜色语义与 {@link StrinovaHudPainter#textScaled} 一致。
     *
     * @return true 表示已用雅黑绘制；false 表示字体不可用，调用方应回退原版字体
     */
    public static synchronized boolean draw(GuiGraphics graphics, String text, float x, float y,
                                            float scale, int argb, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        ensureInit();
        if (!ready) {
            return false;
        }
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        float pxPerFontPx = (TARGET_TEXT_PX * scale) / FONT_PX;
        float drawX = x;
        try {
            if (shadow) {
                RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, alpha / 255.0F * 0.6F);
                drawGlyphs(graphics, text, drawX + 1.0F, y + 1.0F, pxPerFontPx);
            }
            RenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            drawGlyphs(graphics, text, drawX, y, pxPerFontPx);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return true;
    }

    private static void drawGlyphs(GuiGraphics graphics, String text, float x, float y, float pxPerFontPx) {
        float drawX = x;
        for (int i = 0; i < text.length(); i++) {
            int ch = text.charAt(i);
            if (ch == ' ') {
                drawX += (FONT_PX / 3.0F) * pxPerFontPx;
                continue;
            }
            int cell = ensureGlyph(ch);
            if (cell < 0) {
                continue;
            }
            int cellX = (cell % COLS) * CELL;
            int cellY = (cell / COLS) * CELL;
            int drawW = Math.max(1, Math.round(CELL * pxPerFontPx));
            int drawH = Math.max(1, Math.round(CELL * pxPerFontPx));
            flushAtlas();
            // 画到整数像素，避免半像素采样导致的“像素块”感
            graphics.blit(atlasId, Math.round(drawX), Math.round(y), drawW, drawH,
                    cellX, cellY, CELL, CELL, ATLAS_SIZE, ATLAS_SIZE);
            Integer advance = GLYPH_ADVANCE.get(ch);
            drawX += (advance == null ? FONT_PX / 2.0F : advance) * pxPerFontPx;
        }
    }

    private static int ensureGlyph(int ch) {
        Integer cached = GLYPH_CELL.get(ch);
        if (cached != null) {
            return cached;
        }
        if (nextGlyph >= MAX_GLYPHS) {
            return -1;
        }
        int cell = nextGlyph++;
        int cellX = (cell % COLS) * CELL;
        int cellY = (cell / COLS) * CELL;
        BufferedImage glyph = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = glyph.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(awtFont.deriveFont((float) FONT_PX));
            g2.setColor(Color.WHITE);
            g2.drawString(String.valueOf((char) ch), 1, CELL - 6);
            FontMetrics metrics = g2.getFontMetrics();
            int advance = metrics.charWidth((char) ch);
            if (advance <= 0) {
                advance = FONT_PX / 2;
            }
            GLYPH_ADVANCE.put(ch, advance);
        } finally {
            g2.dispose();
        }
        for (int py = 0; py < CELL; py++) {
            for (int px = 0; px < CELL; px++) {
                int argb = glyph.getRGB(px, py);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha > 0) {
                    atlasImage.setPixelRGBA(cellX + px, cellY + py, (alpha << 24) | 0xFFFFFF);
                }
            }
        }
        GLYPH_CELL.put(ch, cell);
        dirty = true;
        return cell;
    }

    private static void flushAtlas() {
        if (!dirty || atlasTexture == null) {
            return;
        }
        dirty = false;
        try {
            var textureManager = Minecraft.getInstance().getTextureManager();
            if (textureManager.getTexture(atlasId, null) == null) {
                atlasTexture = new DynamicTexture(atlasImage);
                atlasTexture.setFilter(true, false);
                textureManager.register(atlasId, atlasTexture);
            }
            atlasTexture.upload();
        } catch (Throwable throwable) {
            ready = false;
            StrinovaMod.LOGGER.warn("Strinova: 字形图集上传失败，回退原版字体: {}", throwable.toString());
        }
    }

    private static synchronized void ensureInit() {
        if (triedInit) {
            return;
        }
        triedInit = true;
        try {
            File fontFile = findFontFile();
            if (fontFile == null || !fontFile.isFile()) {
                StrinovaMod.LOGGER.info("Strinova: 未找到微软雅黑字体，使用原版字体");
                return;
            }
            Font[] fonts = Font.createFonts(fontFile);
            if (fonts == null || fonts.length == 0) {
                return;
            }
            awtFont = fonts[0];
            measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = measureImage.createGraphics();
            g2.setFont(awtFont.deriveFont((float) FONT_PX));
            measureMetrics = g2.getFontMetrics();
            g2.dispose();
            atlasImage = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
            atlasImage.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0);
            atlasTexture = new DynamicTexture(atlasImage);
            // 线性过滤：缩小时平滑（之前用最近邻，缩小后就是一块块像素）
            atlasTexture.setFilter(true, false);
            atlasId = new ResourceLocation(StrinovaMod.MOD_ID, "dynamic_scoreboard_font");
            Minecraft.getInstance().getTextureManager().register(atlasId, atlasTexture);
            ready = true;
            StrinovaMod.LOGGER.info("Strinova: 已加载系统字体 {}（计分板/HUD 使用微软雅黑）", fontFile.getName());
        } catch (Throwable throwable) {
            ready = false;
            StrinovaMod.LOGGER.warn("Strinova: 加载微软雅黑失败，回退原版字体: {}", throwable.toString());
        }
    }

    private static File findFontFile() {
        try {
            File configFonts = dev.architectury.platform.Platform.getConfigFolder()
                    .resolve("strinova-fonts").toFile();
            if (configFonts.isDirectory()) {
                File[] files = configFonts.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                        if (name.endsWith(".ttf") || name.endsWith(".ttc")) {
                            return file;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String windir = System.getenv("WINDIR");
        if (windir == null || windir.isEmpty()) {
            windir = "C:\\Windows";
        }
        File fontsDir = new File(windir, "Fonts");
        String[] candidates = {"msyh.ttc", "msyh.ttf", "msyhbd.ttc", "simhei.ttf"};
        for (String candidate : candidates) {
            File file = new File(fontsDir, candidate);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
