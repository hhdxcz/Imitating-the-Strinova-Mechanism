package com.hhdxcz.strinova.client.hud;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「弦能增幅网络」（成长/加点）数据契约。
 *
 * <p>数据来自 {@link StrinovaWikiData}：每名角色 8 个分支（4 武器网络 + 主动技能 / 被动技能 / 护甲 / 弦化），
 * 每个分支 1~2 个升级项（含成本与「属性变化」详细文案），以及 3 个觉醒（名称 / 描述 / 三色需求）。
 * wiki 数据不可用时 {@link #hasData()} 为 false，界面显示占位提示。</p>
 *
 * <p>三色需求语义与游戏一致：蓝=武器网络已购买项数、黄=主动/被动技能网络已购买项数、绿=护甲/弦化已购买项数。</p>
 */
public final class StrinovaGrowthState {

    /** 一个升级项。{@code change} 为悬停显示的属性变化文案，可能为空。 */
    public record UpgradeItem(String text, int cost, String change, boolean purchased) {
    }

    /** 一个成长分支：slot 1~8，kind 为 weapon / active / passive / armor / string。 */
    public record Category(int slot, String kind, String label, List<UpgradeItem> items) {
    }

    /** 一个觉醒；{@code active} 由已购买的三色项数是否满足需求决定。 */
    public record Awakening(String name, String desc,
                            int needWeapon, int needSkill, int needDefense, boolean active) {
    }

    private static final int START_ENERGY = 330;

    private static volatile String agentId = "";
    private static volatile int energyPoints = START_ENERGY;
    private static volatile List<Category> categories = List.of();
    private static volatile List<Awakening> awakenings = List.of();
    private static volatile int purchasedWeapon;
    private static volatile int purchasedSkill;
    private static volatile int purchasedDefense;
    private static final Set<String> purchased = new LinkedHashSet<>();
    private static List<StrinovaWikiData.NetworkGroup> groups = List.of();
    private static StrinovaWikiData.Agent wikiAgent;

    private StrinovaGrowthState() {
    }

    /** 当前数据所属角色 id。 */
    public static String agentId() {
        return agentId;
    }

    /** 数据是否可用（wiki 数据存在且该角色有增幅网络）。 */
    public static boolean hasData() {
        return !categories.isEmpty();
    }

    /**
     * 切换当前角色：id 变化时重置能量点与购买状态，并读取该角色的网络数据。
     * 界面 init 时调用（由客户端配置 selectedAgentId 决定）。
     */
    public static void selectAgent(String id) {
        String next = id == null ? "" : id;
        if (next.equals(agentId) && wikiAgent != null) {
            return;
        }
        agentId = next;
        wikiAgent = StrinovaWikiData.byId(next);
        groups = wikiAgent == null ? List.of() : wikiAgent.network();
        reset();
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

    /** 已购买的武器网络项数（觉醒蓝点）。 */
    public static int purchasedWeapon() {
        return purchasedWeapon;
    }

    /** 已购买的主动/被动技能网络项数（觉醒黄点）。 */
    public static int purchasedSkill() {
        return purchasedSkill;
    }

    /** 已购买的护甲/弦化项数（觉醒绿点）。 */
    public static int purchasedDefense() {
        return purchasedDefense;
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
        purchased.add(key(category.slot(), itemIndex));
        energyPoints -= item.cost();
        rebuild();
        return true;
    }

    /** 重置当前角色的能量点与购买状态。 */
    public static void reset() {
        energyPoints = START_ENERGY;
        purchased.clear();
        rebuild();
    }

    private static String key(int slot, int itemIndex) {
        return slot + ":" + itemIndex;
    }

    /** 依据 wiki 数据 + 已购买集合重建类别 / 觉醒 / 三色计数。 */
    private static void rebuild() {
        List<Category> nextCategories = new ArrayList<>();
        int weapon = 0;
        int skill = 0;
        int defense = 0;
        for (StrinovaWikiData.NetworkGroup group : groups) {
            List<UpgradeItem> items = new ArrayList<>();
            for (int i = 0; i < group.items().size(); i++) {
                StrinovaWikiData.NetworkItem item = group.items().get(i);
                boolean owned = purchased.contains(key(group.slot(), i));
                if (owned) {
                    switch (group.kind()) {
                        case "weapon" -> weapon++;
                        case "active", "passive" -> skill++;
                        default -> defense++;
                    }
                }
                items.add(new UpgradeItem(item.text(), item.cost(), item.change(), owned));
            }
            nextCategories.add(new Category(group.slot(), group.kind(), group.label(), List.copyOf(items)));
        }
        purchasedWeapon = weapon;
        purchasedSkill = skill;
        purchasedDefense = defense;
        categories = List.copyOf(nextCategories);
        awakenings = buildAwakenings(wikiAgent, weapon, skill, defense);
    }

    private static List<Awakening> buildAwakenings(StrinovaWikiData.Agent agent,
                                                   int weapon, int skill, int defense) {
        if (agent == null || agent.awakenings().isEmpty()) {
            return List.of();
        }
        List<Awakening> result = new ArrayList<>();
        for (StrinovaWikiData.Awakening source : agent.awakenings()) {
            boolean active = weapon >= source.needWeapon()
                    && skill >= source.needSkill()
                    && defense >= source.needDefense();
            result.add(new Awakening(source.name(), source.desc(),
                    source.needWeapon(), source.needSkill(), source.needDefense(), active));
        }
        return List.copyOf(result);
    }
}
