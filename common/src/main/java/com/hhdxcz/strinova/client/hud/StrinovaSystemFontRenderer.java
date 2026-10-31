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
 * .ttf/.ttc 字体文件加载，把字符按需绘制到动态字形图集。
 *
 * <p>绘制时只按每个字的**实际墨迹包围盒**缩放输出（而不是整个方格），
 * 字间距按 AWT 字宽推进，避免相邻字互相覆盖导致的“糊在一起”。</p>
 */
public final class StrinovaSystemFontRenderer {

    private static final int ATLAS_SIZE = 512;
    private static final int CELL = 16;
    private static final int COLS = ATLAS_SIZE / CELL;
    private static final int MAX_GLYPHS = COLS * COLS;
    private static final int FONT_PX = 12;
    private static final int TARGET_TEXT_PX = 9;

    /** 每个字形的图集位置 + 墨迹包围盒 + 字宽。 */
    private record GlyphData(int cell, int inkX, int inkY, int inkW, int inkH, int advance) {
    }

    private static boolean triedInit;
    private static boolean ready;
    private static Font awtFont;
    private static NativeImage atlasImage;
    private static DynamicTexture atlasTexture;
    private static ResourceLocation atlasId;
    private static int nextGlyph;
    private static boolean dirty;
    private static final Map<Integer, GlyphData> GLYPHS = new HashMap<>();

    private StrinovaSystemFontRenderer() {
    }

    public static synchronized boolean isReady() {
        if (!StrinovaHudConfig.get().useYaheiFont) {
            return false;
        }
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
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += advanceOf(text.charAt(i));
        }
        return Math.round(total * ((float) TARGET_TEXT_PX / FONT_PX));
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
        float ratio = (TARGET_TEXT_PX * scale) / FONT_PX;
        try {
            if (shadow) {
                RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, alpha / 255.0F * 0.6F);
                drawGlyphs(graphics, text, x + 1.0F, y + 1.0F, ratio);
            }
            RenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            drawGlyphs(graphics, text, x, y, ratio);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return true;
    }

    private static void drawGlyphs(GuiGraphics graphics, String text, float startX, float startY, float ratio) {
        float drawX = startX;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                drawX += (FONT_PX / 3.0F) * ratio;
                continue;
            }
            GlyphData glyph = glyph(ch);
            if (glyph == null) {
                continue;
            }
            flushAtlas();
            int cellX = (glyph.cell() % COLS) * CELL;
            int cellY = (glyph.cell() / COLS) * CELL;
            int drawXpx = Math.round(drawX + glyph.inkX() * ratio);
            int drawYpx = Math.round(startY + glyph.inkY() * ratio);
            int drawW = Math.max(1, Math.round(glyph.inkW() * ratio));
            int drawH = Math.max(1, Math.round(glyph.inkH() * ratio));
            graphics.blit(atlasId, drawXpx, drawYpx, drawW, drawH,
                    cellX + glyph.inkX(), cellY + glyph.inkY(), glyph.inkW(), glyph.inkH(),
                    ATLAS_SIZE, ATLAS_SIZE);
            drawX += glyph.advance() * ratio;
        }
    }

    private static int advanceOf(int ch) {
        if (ch == ' ') {
            return FONT_PX / 3;
        }
        GlyphData glyph = glyph(ch);
        return glyph == null ? FONT_PX / 2 : glyph.advance();
    }

    private static GlyphData glyph(int ch) {
        GlyphData cached = GLYPHS.get(ch);
        if (cached != null) {
            return cached;
        }
        if (nextGlyph >= MAX_GLYPHS) {
            return null;
        }
        int cell = nextGlyph++;
        int cellX = (cell % COLS) * CELL;
        int cellY = (cell / COLS) * CELL;
        BufferedImage glyphImage = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        int advance;
        Graphics2D g2 = glyphImage.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(awtFont.deriveFont((float) FONT_PX));
            g2.setColor(Color.WHITE);
            g2.drawString(String.valueOf((char) ch), 1, CELL - 3);
            FontMetrics metrics = g2.getFontMetrics();
            advance = metrics.charWidth((char) ch);
            if (advance <= 0) {
                advance = FONT_PX / 2;
            }
        } finally {
            g2.dispose();
        }
        // 计算墨迹包围盒（去掉透明边，避免相邻字互相覆盖）
        int minX = CELL;
        int minY = CELL;
        int maxX = -1;
        int maxY = -1;
        for (int py = 0; py < CELL; py++) {
            for (int px = 0; px < CELL; px++) {
                int alpha = (glyphImage.getRGB(px, py) >>> 24) & 0xFF;
                if (alpha > 0) {
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, px);
                    maxY = Math.max(maxY, py);
                    atlasImage.setPixelRGBA(cellX + px, cellY + py, (alpha << 24) | 0xFFFFFF);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            minX = 0;
            minY = 0;
            maxX = 0;
            maxY = 0;
        }
        GlyphData data = new GlyphData(cell, minX, minY, maxX - minX + 1, maxY - minY + 1, advance);
        GLYPHS.put(ch, data);
        dirty = true;
        return data;
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
            atlasImage = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
            atlasImage.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0);
            atlasTexture = new DynamicTexture(atlasImage);
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
