package com.hhdxcz.strinova.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * 「弦能增幅网络」（成长/加点）数据契约。
 * 当前为客户端模拟数据（能量点 330、7 类成长、3 个觉醒），后续由服务端/配装系统同步。
 */
public final class StrinovaGrowthState {

    public record UpgradeItem(String nameKey, int cost, boolean purchased) {
    }

    public record Category(String nameKey, List<UpgradeItem> items) {
    }

    public record Awakening(String nameKey, String descKey, boolean active) {
    }

    private static volatile int energyPoints = 330;
    private static volatile List<Category> categories = defaultCategories();
    private static volatile List<Awakening> awakenings = refreshAwakenings(categories, 330);

    private StrinovaGrowthState() {
    }

    public static int energyPoints() {
        return energyPoints;
    }

    public static List<Category> categories() {
        return categories;
    }

    public static List<Awakening> awakenings() {
        return awakenings;
    }

    /** 购买某个成长项：能量足够且未购买时扣能量并置为已购买。 */
    public static boolean purchase(int categoryIndex, int itemIndex) {
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            return false;
        }
        Category category = categories.get(categoryIndex);
        if (itemIndex < 0 || itemIndex >= category.items().size()) {
            return false;
        }
        UpgradeItem item = category.items().get(itemIndex);
        if (item.purchased() || energyPoints < item.cost()) {
            return false;
        }
        List<UpgradeItem> items = new ArrayList<>(category.items());
        items.set(itemIndex, new UpgradeItem(item.nameKey(), item.cost(), true));
        List<Category> next = new ArrayList<>(categories);
        next.set(categoryIndex, new Category(category.nameKey(), items));
        categories = next;
        energyPoints -= item.cost();
        awakenings = refreshAwakenings(next, energyPoints);
        return true;
    }

    public static void reset() {
        energyPoints = 330;
        categories = defaultCategories();
        awakenings = refreshAwakenings(categories, energyPoints);
    }

    private static List<Category> defaultCategories() {
        return List.of(
                category("config.strinova.growth.cat.output", item("damage", 150), item("burst", 150)),
                category("config.strinova.growth.cat.crit", item("headshot", 150), item("string_damage", 150)),
                category("config.strinova.growth.cat.sustain", item("magazine", 150), item("reload", 150)),
                category("config.strinova.growth.cat.accuracy", item("spread", 150), item("recoil", 150)),
                category("config.strinova.growth.cat.arcane", item("cooldown", 250), item("skill_damage", 250)),
                category("config.strinova.growth.cat.armor", item("light_armor", 250), item("heavy_armor", 250)),
                category("config.strinova.growth.cat.string", item("string_speed", 250), item("string_reduction", 250))
        );
    }

    private static UpgradeItem item(String id, int cost) {
        return new UpgradeItem("config.strinova.growth.item." + id, cost, false);
    }

    private static Category category(String nameKey, UpgradeItem... items) {
        return new Category(nameKey, List.of(items));
    }

    private static List<Awakening> refreshAwakenings(List<Category> categories, int points) {
        int purchased = 0;
        for (Category category : categories) {
            for (UpgradeItem item : category.items()) {
                if (item.purchased()) {
                    purchased++;
                }
            }
        }
        boolean a1 = purchased >= 1;
        boolean a2 = purchased >= 4;
        boolean a3 = purchased >= 8;
        return List.of(
                new Awakening("config.strinova.growth.awaken.1", "config.strinova.growth.awaken.1.desc", a1),
                new Awakening("config.strinova.growth.awaken.2", "config.strinova.growth.awaken.2.desc", a2),
                new Awakening("config.strinova.growth.awaken.3", "config.strinova.growth.awaken.3.desc", a3)
        );
    }
}
