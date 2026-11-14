package com.hhdxcz.strinova.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 武器/投掷物 HUD 贴图映射：按 wiki 武器名 / 投掷物名找 {@code textures/gui/weapons/*.png}。
 *
 * <p>贴图直接是官方 {@code T_Dynamic_WeaponWhite_*} 剪影的原始像素（按 alpha 外接框裁剪，零重采样），
 * 因此每个文件的宽高比都不一样。渲染时必须按贴图原始比例放缩，否则会被拉伸、显得发糊——
 * 所以这里通过 {@link #fit} 统一计算等比适配后的绘制矩形。</p>
 */
public final class StrinovaWeaponIcons {

    /** 贴图读不到尺寸时的兜底比例（宽/高）。 */
    private static final float FALLBACK_ASPECT = 2.0F;

    private static final Map<String, String> FILES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Float> ASPECTS = new HashMap<>();

    static {
        // 主武器
        String[] main = {"北极星", "彩绘", "潮音", "齿锋", "独舞", "枫鸣", "幻霜", "静风", "警探",
                "绝对执行", "空境", "鸣火", "逆焰", "破晓", "欺诈师", "审判官", "隼", "卫冕",
                "校准仪", "谢幕曲", "夜镰", "影袭", "雨鸥", "绽放", "自由意志"};
        for (int i = 0; i < main.length; i++) {
            FILES.put(main[i], String.format("m%02d", i));
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

    /** 按武器/物品显示名找贴图；找不到返回 null（调用方回退几何剪影）。 */
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

    /** 贴图原始宽高比（宽 / 高）。读取 PNG 尺寸并缓存，失败返回兜底比例。 */
    public static float aspect(ResourceLocation texture) {
        if (texture == null) {
            return FALLBACK_ASPECT;
        }
        Float cached = ASPECTS.get(texture);
        if (cached != null) {
            return cached;
        }
        float aspect = loadAspect(texture);
        ASPECTS.put(texture, aspect);
        return aspect;
    }

    private static float loadAspect(ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return FALLBACK_ASPECT;
        }
        try (InputStream in = minecraft.getResourceManager().open(texture)) {
            NativeImage image = NativeImage.read(in);
            try {
                if (image.getWidth() > 0 && image.getHeight() > 0) {
                    return image.getWidth() / (float) image.getHeight();
                }
            } finally {
                image.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // 贴图缺失或读取失败：用兜底比例，避免渲染崩溃
        }
        return FALLBACK_ASPECT;
    }

    /** 在 (x,y,boxW,boxH) 的盒子内按贴图原始比例等比居中，返回 {drawX, drawY, drawW, drawH}。 */
    public static int[] fit(ResourceLocation texture, int x, int y, int boxW, int boxH) {
        int boxWidth = Math.max(1, boxW);
        int boxHeight = Math.max(1, boxH);
        float aspect = aspect(texture);
        int width = boxWidth;
        int height = Math.max(1, Math.round(width / aspect));
        if (height > boxHeight) {
            height = boxHeight;
            width = Math.max(1, Math.round(height * aspect));
        }
        return new int[]{x + (boxWidth - width) / 2, y + (boxHeight - height) / 2, width, height};
    }

    /** 贴图固定为「整张贴图」绘制，UV 用 1x1 归一化即可，无需知道实际像素尺寸。 */
    public static final int UV_SIZE = 1;
}
