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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * HUD/计分板字体渲染器：内置「Noto Sans SC（中文）+ Saira（数字/英文）」双字体。
 *
 * <p>中文字符走 {@code NotoSansSC-Regular.ttf}（OFL），拉丁字母/数字/符号走
 * {@code Saira-Medium.ttf}（OFL），与卡拉彼丘官网 UI 字体栈一致；两者按字符自动选择，
 * 字形按需光栅化到 1024×1024 动态图集（每格 28px、四周留 4px 透明边，缩放采样不串字）。</p>
 *
 * <p>找不到内置字体时回退到 {@code config/strinova-fonts/} 或系统字体（雅黑/文泉驿）；
 * {@link StrinovaHudConfig#useYaheiFont} 关闭时整体回退原版字体。</p>
 */
public final class StrinovaSystemFontRenderer {

    private static final int ATLAS_SIZE = 1024;
    /** 每格 28px，字符四周留 4px 透明边，缩放采样时不会吃到相邻字。 */
    private static final int CELL = 28;
    private static final int CELL_PADDING = 4;
    private static final int COLS = ATLAS_SIZE / CELL;
    private static final int MAX_GLYPHS = COLS * COLS;
    /** 以 18px 光栅化、再缩到 9px 显示，缩小采样比 12→9 更平滑。 */
    private static final int FONT_PX = 18;
    private static final int TARGET_TEXT_PX = 9;

    private static final int FONT_CJK = 0;
    private static final int FONT_LATIN = 1;
    private static final int FONT_FALLBACK = 2;

    /** 每个字形的图集位置 + 墨迹包围盒 + 字宽。 */
    private record GlyphData(int cell, int inkX, int inkY, int inkW, int inkH, int advance) {
    }

    private static boolean triedInit;
    private static boolean ready;
    private static Font fontCjk;
    private static Font fontLatin;
    private static Font fontFallback;
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
     * @return true 表示已用自定义字体绘制；false 表示字体不可用，调用方应回退原版字体
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
                // 阴影偏移随缩放走，避免小字号时 1px 固定偏移把字“糊成两层”
                float shadowOffset = Math.max(1.0F, Math.round(ratio * 2.0F));
                RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, alpha / 255.0F * 0.5F);
                drawGlyphs(graphics, text, x + shadowOffset, y + shadowOffset, ratio);
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
                drawX += spaceAdvance() * ratio;
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
            return spaceAdvance();
        }
        GlyphData glyph = glyph(ch);
        return glyph == null ? FONT_PX / 2 : glyph.advance();
    }

    private static int spaceAdvance() {
        return Math.max(4, Math.round(FONT_PX * 0.28F));
    }

    private static GlyphData glyph(int ch) {
        int fontId = fontIdFor((char) ch);
        if (fontId < 0) {
            return null;
        }
        int key = (fontId << 16) | ch;
        GlyphData cached = GLYPHS.get(key);
        if (cached != null) {
            return cached;
        }
        if (nextGlyph >= MAX_GLYPHS) {
            return null;
        }
        Font font = fontById(fontId);
        if (font == null) {
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
            g2.setFont(font.deriveFont((float) FONT_PX));
            g2.setColor(Color.WHITE);
            g2.drawString(String.valueOf((char) ch), CELL_PADDING, CELL - 5);
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
        GLYPHS.put(key, data);
        dirty = true;
        return data;
    }

    private static int fontIdFor(char ch) {
        if (ch >= 0x20 && ch <= 0x7E) {
            if (fontLatin != null && fontLatin.canDisplay(ch)) {
                return FONT_LATIN;
            }
        }
        if (fontCjk != null && fontCjk.canDisplay(ch)) {
            return FONT_CJK;
        }
        if (fontLatin != null && fontLatin.canDisplay(ch)) {
            return FONT_LATIN;
        }
        if (fontFallback != null && fontFallback.canDisplay(ch)) {
            return FONT_FALLBACK;
        }
        return -1;
    }

    private static Font fontById(int fontId) {
        return switch (fontId) {
            case FONT_LATIN -> fontLatin;
            case FONT_FALLBACK -> fontFallback;
            default -> fontCjk;
        };
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
            // 1) 内置字体：Noto Sans SC（中文）+ Saira（数字/拉丁），OFL 许可，可随模组分发
            fontCjk = loadResourceFont("NotoSansSC-Regular.ttf");
            fontLatin = loadResourceFont("Saira-Medium.ttf");

            // 2) 回退字体：config/strinova-fonts 或系统雅黑/文泉驿
            Font fallback = loadFallbackFont();
            fontFallback = fallback;
            if (fontCjk == null) {
                fontCjk = fallback;
            }
            if (fontLatin == null) {
                fontLatin = fallback;
            }
            if (fontCjk == null && fontLatin == null) {
                StrinovaMod.LOGGER.info("Strinova: 未找到可用字体，使用原版字体");
                return;
            }

            atlasImage = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
            atlasImage.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0);
            atlasTexture = new DynamicTexture(atlasImage);
            atlasTexture.setFilter(true, false);
            atlasId = new ResourceLocation(StrinovaMod.MOD_ID, "dynamic_scoreboard_font");
            Minecraft.getInstance().getTextureManager().register(atlasId, atlasTexture);
            ready = true;
            StrinovaMod.LOGGER.info("Strinova: 已加载 HUD 字体（中文 Noto Sans SC Regular，数字/英文 Saira Medium）");
        } catch (Throwable throwable) {
            ready = false;
            StrinovaMod.LOGGER.warn("Strinova: 加载 HUD 字体失败，回退原版字体: {}", throwable.toString());
        }
    }

    private static Font loadResourceFont(String fileName) {
        try (InputStream in = StrinovaSystemFontRenderer.class.getResourceAsStream(
                "/assets/strinova/fonts/" + fileName)) {
            if (in == null) {
                return null;
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Font loadFallbackFont() {
        try {
            File fontFile = findFontFile();
            if (fontFile == null || !fontFile.isFile()) {
                return null;
            }
            Font[] fonts = Font.createFonts(fontFile);
            return fonts == null || fonts.length == 0 ? null : fonts[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File findFontFile() {
        // 1) config/strinova-fonts：优先 font.ttf/msyh，其次文泉驿/其它 ttf/ttc
        try {
            File configFonts = dev.architectury.platform.Platform.getConfigFolder()
                    .resolve("strinova-fonts").toFile();
            if (configFonts.isDirectory()) {
                File[] files = configFonts.listFiles();
                if (files != null) {
                    File fallback = null;
                    for (File file : files) {
                        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                        if (!name.endsWith(".ttf") && !name.endsWith(".ttc")) {
                            continue;
                        }
                        if (name.equals("font.ttf") || name.startsWith("msyh")) {
                            return file;
                        }
                        if (name.contains("wqy") || name.contains("microhei") || name.contains("wenquanyi")) {
                            return file;
                        }
                        if (fallback == null) {
                            fallback = file;
                        }
                    }
                    if (fallback != null) {
                        return fallback;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 2) 系统字体：文泉驿微米黑优先，找不到再回退微软雅黑/黑体
        String windir = System.getenv("WINDIR");
        if (windir == null || windir.isEmpty()) {
            windir = "C:\\Windows";
        }
        File fontsDir = new File(windir, "Fonts");
        String[] candidates = {
                "wqy-microhei.ttc", "wqy-microhei.ttf", "wqy-zenhei.ttc",
                "msyh.ttc", "msyh.ttf", "simhei.ttf"
        };
        for (String candidate : candidates) {
            File file = new File(fontsDir, candidate);
            if (file.isFile()) {
                return file;
            }
        }
        // 3) Linux 常见路径（在 Linux 客户端上直接可用）
        String[] linuxPaths = {
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
        };
        for (String path : linuxPaths) {
            File file = new File(path);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
