package com.hhdxcz.strinova.client.hud;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 武器/投掷物 HUD 贴图映射：按 wiki 武器名 / 投掷物名找 {@code textures/gui/weapons/*.png}。
 *
 * <p>主武器图标来自官方武器卡面抠图（旧版图片可直接用），副武器/刀/投掷物来自
 * {@code T_Dynamic_WeaponWhite_*} 剪影，统一归一化到 256×128 透明画布。</p>
 */
public final class StrinovaWeaponIcons {

    public static final int TEX_WIDTH = 256;
    public static final int TEX_HEIGHT = 128;

    private static final Map<String, String> FILES = new LinkedHashMap<>();

    static {
        // 主武器（卡面抠图）
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
}
