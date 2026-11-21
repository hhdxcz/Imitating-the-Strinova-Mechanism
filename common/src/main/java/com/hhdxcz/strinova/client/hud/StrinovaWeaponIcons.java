package com.hhdxcz.strinova.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 武器/投掷物 HUD 贴图映射：按 wiki 武器名 / 投掷物名找 {@code textures/gui/weapons/*.png}。
 *
 * <p>贴图就是官方 {@code T_Dynamic_WeaponWhite_*} 剪影原图（270×90 / 72×72，自带留白），
 * 由 {@code tools/gen_weapon_icons.py} 原样复制，未经任何裁剪或缩放。</p>
 *
 * <p>渲染时通过 {@link #fit} 等比适配卡片：<b>永远不会把贴图放大到超过原始像素</b>
 * （按当前 GUI 缩放换算），这样图标既不会拉伸变形，也不会因放大而发糊。</p>
 */
public final class StrinovaWeaponIcons {

    /** 贴图读不到尺寸时的兜底比例（宽/高）。 */
    private static final float FALLBACK_ASPECT = 2.0F;
    private static final int[] FALLBACK_DIMS = {256, 128};

    private static final Map<String, String> FILES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, int[]> DIMS = new HashMap<>();

    static {
        // 主武器（只收录有旧版贴图的；其余武器渲染端会程序化仿制）⁠​‌​​​‍​‌​​‍​​​‍‍‌‍​​‌‍‌‌​‌‌‌‌‌​‍​‌‌‍​‍‍‍‍​​​‌‍‌‍​​‌‌‌​‌​​​‍‌​‍‍​‍​​​‌‌‌‌‌​​​‌‍​​​​​‍​‌‍​‌​​‍‍‌‍‌​​‌‌‌‌​‌​​​​​​‌​​‍​​‍​​‍​‍‍‌‍​​​‍​‌‌⁡
        String[] main = {"北极星", "彩绘", "潮音", "齿锋", "独舞", "枫鸣", "幻霜", "静风", "警探",
                "绝对执行", "空境", "鸣火", "逆焰", "破晓", "欺诈师", "审判官", "隼", "卫冕",
                "校准仪", "谢幕曲", "夜镰", "影袭", "雨鸥", "绽放", "自由意志"};
        // 没有旧版贴图的武器：不再用卡面抠图，直接不做贴图，渲染端画仿制剪影
        java.util.Set<String> noOldTexture = java.util.Set.of(
                "潮音", "枫鸣", "静风", "绝对执行", "校准仪", "夜镰", "雨鸥");
        for (int i = 0; i < main.length; i++) {
            if (!noOldTexture.contains(main[i])) {
                FILES.put(main[i], String.format("m%02d", i));
            }
        }
        // 副武器
        FILES.put("小蜜蜂", "s00");
        FILES.put("雪鸮", "s01");
        FILES.put("焚焰者", "s02");
        FILES.put("重焰", "s03");
        // 刀
        FILES.put("忍锋", "knife");
        FILES.put("刀", "knife");
        // 投掷物
        String[] throwables = {"手雷", "闪光弹", "烟雾弹", "风场雷", "减速雷", "治疗雷", "报警器", "拦截器", "雪球"};
        for (int i = 0; i < throwables.length; i++) {
            FILES.put(throwables[i], String.format("g%02d", i));
        }
    }

    private StrinovaWeaponIcons() {
    }

    /** 按武器/物品显示名找贴图；找不到返回 null（调用方画程序化仿制剪影）。 */
    public static ResourceLocation textureFor(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String file = FILES.get(name);
        if (file == null) {
            for (Map.Entry<String, String> entry : FILES.entrySet()) {
                if (name.contains(entry.getKey()) || entry.getKey().contains(name)) {
                    file = entry.getValue();
                    break;
                }
            }
        }
        return file == null ? null : new ResourceLocation("strinova", "textures/gui/weapons/" + file + ".png");
    }

    public static ResourceLocation selectedLine() {
        return new ResourceLocation("strinova", "textures/gui/weapons/selected_line.png");
    }

    /** 贴图原始像素尺寸 {宽, 高}；读取失败返回兜底尺寸。 */
    public static int[] dims(ResourceLocation texture) {
        if (texture == null) {
            return FALLBACK_DIMS;
        }
        int[] cached = DIMS.get(texture);
        if (cached != null) {
            return cached;
        }
        int[] dims = loadDims(texture);
        DIMS.put(texture, dims);
        return dims;
    }

    private static int[] loadDims(ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return FALLBACK_DIMS;
        }
        try (InputStream in = minecraft.getResourceManager().open(texture)) {
            NativeImage image = NativeImage.read(in);
            try {
                if (image.getWidth() > 0 && image.getHeight() > 0) {
                    return new int[]{image.getWidth(), image.getHeight()};
                }
            } finally {
                image.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // 贴图缺失或读取失败：用兜底尺寸，避免渲染崩溃
        }
        return FALLBACK_DIMS;
    }

    /** 贴图原始宽高比（宽 / 高）。 */
    public static float aspect(ResourceLocation texture) {
        int[] dims = dims(texture);
        return dims[1] > 0 ? dims[0] / (float) dims[1] : FALLBACK_ASPECT;
    }

    /** 当前 GUI 缩放（1 个 GUI 单位对应多少屏幕像素）。 */
    private static int guiScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null) {
            int scale = (int) Math.round(minecraft.getWindow().getGuiScale());
            if (scale > 0) {
                return scale;
            }
        }
        return 2;
    }

    /**
     * 在 (x,y,boxW,boxH) 的盒子内按贴图原始比例等比居中，返回 {drawX, drawY, drawW, drawH}。
     *
     * <p>同时限制最大尺寸：绘制尺寸换算成屏幕像素后不超过贴图原始像素，
     * 即只允许「缩小」不允许「放大」，避免图标被放大后发糊。</p>
     */
    public static int[] fit(ResourceLocation texture, int x, int y, int boxW, int boxH) {
        int[] dims = dims(texture);
        int scale = guiScale();
        float aspect = dims[1] > 0 ? dims[0] / (float) dims[1] : FALLBACK_ASPECT;
        int maxWidth = Math.max(1, dims[0] / Math.max(1, scale));
        int maxHeight = Math.max(1, dims[1] / Math.max(1, scale));

        int boxWidth = Math.max(1, boxW);
        int boxHeight = Math.max(1, boxH);

        int width = Math.min(boxWidth, maxWidth);
        int height = Math.max(1, Math.round(width / aspect));
        if (height > Math.min(boxHeight, maxHeight)) {
            height = Math.max(1, Math.min(boxHeight, maxHeight));
            width = Math.max(1, Math.round(height * aspect));
            if (width > maxWidth) {
                width = maxWidth;
                height = Math.max(1, Math.round(width / aspect));
            }
        }
        width = Math.max(1, width);
        height = Math.max(1, height);
        return new int[]{x + (boxWidth - width) / 2, y + (boxHeight - height) / 2, width, height};
    }

    /** 贴图固定为「整张贴图」绘制，UV 用 1x1 归一化即可，无需知道实际像素尺寸。 */
    public static final int UV_SIZE = 1;

    /**
     * 绘制武器剪影贴图（打开 Alpha 混合）。
     *
     * <p>关键：GUI 里直接 {@code graphics.blit} 时环境混合状态不一定开着，官方剪影边缘是半透明的，
     * 不开混合会把边缘直接截成硬锯齿（看起来又糊又毛）。HUD 的 painter 自己开了混合所以一直很清楚，
     * 这里让增幅网络/选角界面走同一套状态。</p>
     */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, UV_SIZE, UV_SIZE, UV_SIZE, UV_SIZE);
        if (!blendWasEnabled) {
            RenderSystem.disableBlend();
        }
    }

    /**
     * 没有官方贴图时的「仿制剪影」：用矩形拼一个白色武器形状。
     *
     * <p>缺旧版贴图的武器（潮音/枫鸣/静风/绝对执行/校准仪/夜镰/雨鸥）用这个兜底，
     * 按 2:1 的比例在盒子里居中，和官方剪影的观感保持一致。</p>
     */
    public static void drawProcedural(GuiGraphics graphics, int index, int x, int y,
                                      int boxW, int boxH, int color) {
        // 官方剪影在 270x90 画布里只占约 72% 宽，这里仿制时也乘同样系数，保证视觉大小一致
        int canvas = Math.max(10, Math.min(boxW, boxH * 2));
        int width = Math.max(8, Math.round(canvas * 0.72F));
        int height = Math.max(5, Math.round(width / 2.0F));
        if (height > boxH) {
            height = Math.max(5, boxH);
            width = height * 2;
        }
        if (width > boxW) {
            width = Math.max(8, boxW);
            height = Math.max(5, Math.round(width / 2.0F));
        }
        int left = x + (boxW - width) / 2;
        int top = y + (boxH - height) / 2;
        switch (index) {
            case 1 -> drawPistol(graphics, left, top, width, height, color);
            case 2 -> drawKnife(graphics, left, top, width, height, color);
            case 3, 4 -> drawGrenade(graphics, left, top, width, height, color);
            default -> drawRifle(graphics, left, top, width, height, color);
        }
    }

    /** 长枪：枪管 + 护木 + 机匣 + 枪托 + 弹匣 + 握把 + 瞄具。 */
    private static void drawRifle(GuiGraphics g, int x, int y, int w, int h, int color) {
        int px = Math.max(1, w / 100);
        rect(g, x + w * 2 / 100, y + h * 45 / 100, x + w * 6 / 100, y + h * 55 / 100, color);        // 枪口
        rect(g, x + w * 6 / 100, y + h * 47 / 100, x + w * 20 / 100, y + h * 53 / 100, color);       // 枪管
        rect(g, x + w * 20 / 100, y + h * 41 / 100, x + w * 40 / 100, y + h * 59 / 100, color);      // 护木
        rect(g, x + w * 38 / 100, y + h * 35 / 100, x + w * 64 / 100, y + h * 62 / 100, color);      // 机匣
        rect(g, x + w * 44 / 100, y + h * 24 / 100, x + w * 60 / 100, y + h * 36 / 100, color);      // 瞄具
        rect(g, x + w * 64 / 100, y + h * 33 / 100, x + w * 92 / 100, y + h * 58 / 100, color);      // 枪托
        rect(g, x + w * 92 / 100, y + h * 36 / 100, x + w * 99 / 100, y + h * 66 / 100, color);      // 托底
        rect(g, x + w * 47 / 100, y + h * 62 / 100, x + w * 53 / 100, y + h * 84 / 100, color);      // 握把
        rect(g, x + w * 56 / 100, y + h * 62 / 100, x + w * 63 / 100, y + h * 92 / 100, color);      // 弹匣
        rect(g, x + w * 40 / 100 + px, y + h * 60 / 100, x + w * 47 / 100, y + h * 66 / 100, color); // 扳机护圈
    }

    /** 手枪：套筒 + 枪管 + 握把 + 弹匣底。 */
    private static void drawPistol(GuiGraphics g, int x, int y, int w, int h, int color) {
        rect(g, x + w * 22 / 100, y + h * 28 / 100, x + w * 82 / 100, y + h * 48 / 100, color);      // 套筒
        rect(g, x + w * 82 / 100, y + h * 32 / 100, x + w * 96 / 100, y + h * 44 / 100, color);      // 枪管
        rect(g, x + w * 30 / 100, y + h * 48 / 100, x + w * 52 / 100, y + h * 56 / 100, color);      // 扳机护圈
        rect(g, x + w * 26 / 100, y + h * 48 / 100, x + w * 44 / 100, y + h * 88 / 100, color);      // 握把
        rect(g, x + w * 24 / 100, y + h * 88 / 100, x + w * 46 / 100, y + h * 95 / 100, color);      // 弹匣底
    }

    /** 近战：斜向长刀刃 + 护手 + 刀柄。 */
    private static void drawKnife(GuiGraphics g, int x, int y, int w, int h, int color) {
        int steps = Math.max(8, w * 8 / 10);
        int thickness = Math.max(2, h / 5);
        for (int i = 0; i < steps; i++) {
            int px = x + w * 8 / 100 + i;
            int py = y + h * 82 / 100 - (int) ((long) i * h * 55 / 100 / Math.max(1, steps));
            g.fill(px, py, px + 2, py + thickness, color);
        }
        rect(g, x + w * 4 / 100, y + h * 58 / 100, x + w * 14 / 100, y + h * 88 / 100, color);       // 刀柄
        rect(g, x + w * 12 / 100, y + h * 44 / 100, x + w * 18 / 100, y + h * 86 / 100, color);      // 护手
    }

    /** 战术道具：菱形手雷 + 拉环。 */
    private static void drawGrenade(GuiGraphics g, int x, int y, int w, int h, int color) {
        int cx = x + w / 2;
        int cy = y + h * 60 / 100;
        int radius = Math.max(3, h * 30 / 100);
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
        rect(g, cx - Math.max(1, radius / 6), y + h * 22 / 100,
                cx + Math.max(2, radius / 6), cy - radius, color);                                    // 保险杆
        rect(g, cx + radius / 2, y + h * 18 / 100, cx + radius, y + h * 28 / 100, color);            // 拉环
    }

    private static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (x2 > x1 && y2 > y1) {
            g.fill(x1, y1, x2, y2, color);
        }
    }
}
