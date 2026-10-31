package com.hhdxcz.strinova.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 程序化 HUD 绘制实现：不依赖任何贴图资源，先用几何图形 + 系统字体把 HUD 跑起来。
 * 后续换贴图版时新增另一个 {@link StrinovaHudPainter} 实现即可。
 */
public final class StrinovaProgrammaticPainter implements StrinovaHudPainter {

    public static final int COLOR_PANEL = 0xB30A111C;
    public static final int COLOR_PANEL_BORDER = 0x55335F73;
    public static final int COLOR_TEXT = 0xFFEAF6FF;
    public static final int COLOR_TEXT_DIM = 0xFF9FC6D8;
    public static final int COLOR_ACCENT_CYAN = 0xFF35D6E8;
    public static final int COLOR_ACCENT_MAGENTA = 0xFFFF4FA3;
    public static final int COLOR_HP = 0xFF5FE08C;
    public static final int COLOR_HP_LOW = 0xFFFF4757;
    public static final int COLOR_ARMOR = 0xFF5FD8FF;
    public static final int COLOR_WARN = 0xFFFFD166;

    private final GuiGraphics graphics;
    private final Font font;

    public StrinovaProgrammaticPainter(GuiGraphics graphics) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
    }

    @Override
    public int textWidth(String text) {
        if (text == null) {
            return 0;
        }
        if (StrinovaSystemFontRenderer.isReady()) {
            int width = StrinovaSystemFontRenderer.width(text);
            if (width > 0 || text.isEmpty()) {
                return width;
            }
        }
        return font.width(text);
    }

    @Override
    public int textHeight() {
        return font.lineHeight;
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        if (graphics == null || text == null || text.isEmpty()) {
            return;
        }
        if (StrinovaSystemFontRenderer.isReady()
                && StrinovaSystemFontRenderer.draw(graphics, text, x, y, 1.0F, argb, shadow)) {
            return;
        }
        graphics.drawString(font, text, x, y, argb, shadow);
    }

    @Override
    public void textScaled(String text, float x, float y, float scale, int argb, boolean shadow) {
        if (graphics == null || text == null || text.isEmpty()) {
            return;
        }
        if (StrinovaSystemFontRenderer.isReady()
                && StrinovaSystemFontRenderer.draw(graphics, text, x, y, scale, argb, shadow)) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, argb, shadow);
        pose.popPose();
    }

    @Override
    public void textRightAligned(String text, int rightX, int y, int argb, boolean shadow) {
        text(text, rightX - textWidth(text), y, argb, shadow);
    }

    @Override
    public void rect(int x, int y, int width, int height, int argb) {
        if (graphics == null || width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, argb);
    }

    @Override
    public void panel(int x, int y, int width, int height, int accentArgb) {
        if (graphics == null || width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        graphics.fill(x, y, x + 2, y + height, accentArgb);
        graphics.fill(x, y, x + width, y + 1, COLOR_PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_PANEL_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_PANEL_BORDER);
    }

    @Override
    public void border(int x, int y, int width, int height, int argb, int thickness) {
        if (graphics == null || width <= 0 || height <= 0) {
            return;
        }
        int t = Math.max(1, thickness);
        graphics.fill(x, y, x + width, y + t, argb);
        graphics.fill(x, y + height - t, x + width, y + height, argb);
        graphics.fill(x, y, x + t, y + height, argb);
        graphics.fill(x + width - t, y, x + width, y + height, argb);
    }

    @Override
    public void bar(int x, int y, int width, int height, float ratio, int fillArgb, int backgroundArgb) {
        if (graphics == null || width <= 0 || height <= 0) {
            return;
        }
        float clamped = Math.min(1.0F, Math.max(0.0F, ratio));
        graphics.fill(x, y, x + width, y + height, backgroundArgb);
        int filled = Math.round(width * clamped);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, fillArgb);
        }
        graphics.fill(x, y, x + width, y + 1, 0x33FFFFFF);
    }

    @Override
    public void icon(String iconId, int x, int y, int size, int argb) {
        if (graphics == null || size <= 0) {
            return;
        }
        graphics.fill(x, y, x + size, y + size, 0x44101820);
        border(x, y, size, size, 0x66FFFFFF, 1);
        // 贴图版会替换为图集图标；程序化版本先用首字母占位
        String label = iconId == null || iconId.isEmpty() ? "?" : iconId.substring(0, 1).toUpperCase();
        text(label, x + (size - textWidth(label)) / 2, y + (size - textHeight()) / 2, argb, false);
    }

    @Override
    public void line(int x1, int y1, int x2, int y2, int argb) {
        if (graphics == null) {
            return;
        }
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1;
        int y = y1;
        int guard = dx + dy + 1;
        while (guard-- > 0) {
            graphics.fill(x, y, x + 1, y + 1, argb);
            if (x == x2 && y == y2) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
