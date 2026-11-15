package com.hhdxcz.strinova.client.hud;

import java.util.List;

/**
 * 增幅网络右侧武器栏的可选武器清单（与官方「武器配置」界面一致的分类与候选）。
 *
 * <p>每个槽位对应一个分类：1 主武器 / 2 副武器 / 3 近战 / 4、5 战术道具。
 * 点击武器卡打开对应分类的选择面板，选中后写入 {@link StrinovaHudConfig#weaponLoadout}。</p>
 */
public final class StrinovaWeaponCatalog {

    /** 槽位分类。 */
    public enum Category {
        PRIMARY, SECONDARY, MELEE, GADGET
    }

    /**
     * 一件可选武器。
     *
     * @param name 显示名（写进配置、显示在卡片/面板上）
     * @param icon 贴图名（{@link StrinovaWeaponIcons} 的 key；与显示名不同时用于找剪影）
     */
    public record Entry(String name, String icon) {
    }

    private static Entry e(String name) {
        return new Entry(name, name);
    }

    private static Entry e(String name, String icon) {
        return new Entry(name, icon);
    }

    /** 主武器（与 HUD 武器栏一致，共 25 把）。 */
    private static final List<Entry> PRIMARY = List.of(
            e("北极星"), e("彩绘"), e("潮音"), e("齿锋"), e("独舞"),
            e("枫鸣"), e("幻霜"), e("静风"), e("警探"), e("绝对执行"),
            e("空境"), e("鸣火"), e("逆焰"), e("破晓"), e("欺诈师"),
            e("审判官"), e("隼"), e("卫冕"), e("校准仪"), e("谢幕曲"),
            e("夜镰"), e("影袭"), e("雨鸥"), e("绽放"), e("自由意志"));

    /** 副武器。 */
    private static final List<Entry> SECONDARY = List.of(
            e("小蜜蜂"), e("雪鸮"), e("焚焰者"), e("重焰"));

    /** 近战。 */
    private static final List<Entry> MELEE = List.of(
            e("忍锋"));

    /** 战术道具（显示名用官方叫法，贴图沿用已有剪影）。 */
    private static final List<Entry> GADGET = List.of(
            e("破片手雷", "手雷"),
            e("治疗雷"),
            e("烟雾弹"),
            e("闪光弹"),
            e("拦截者", "拦截器"),
            e("减速雷"),
            e("报警器"),
            e("风场雷"),
            e("雪球"),
            e("防弹屏障"));

    private StrinovaWeaponCatalog() {
    }

    /** 槽位（0~4）对应的分类。 */
    public static Category categoryForSlot(int slot) {
        return switch (slot) {
            case 0 -> Category.PRIMARY;
            case 1 -> Category.SECONDARY;
            case 2 -> Category.MELEE;
            default -> Category.GADGET;
        };
    }

    /** 槽位（0~4）可选的武器列表。 */
    public static List<Entry> entriesForSlot(int slot) {
        return switch (categoryForSlot(slot)) {
            case PRIMARY -> PRIMARY;
            case SECONDARY -> SECONDARY;
            case MELEE -> MELEE;
            case GADGET -> GADGET;
        };
    }

    /** 槽位默认装备（主武器跟随角色，返回空串表示不设默认）。 */
    public static String defaultNameForSlot(int slot) {
        return switch (slot) {
            case 1 -> "雪鸮";
            case 2 -> "忍锋";
            case 3 -> "破片手雷";
            case 4 -> "治疗雷";
            default -> "";
        };
    }

    /** 按显示名找到该槽位分类里的条目（找不到返回 null）。 */
    public static Entry find(int slot, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Entry entry : entriesForSlot(slot)) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    /** 显示名 → 贴图名（找不到时原样返回，交给后续模糊匹配/程序化兜底）。 */
    public static String iconName(int slot, String name) {
        Entry entry = find(slot, name);
        return entry == null ? name : entry.icon();
    }
}
