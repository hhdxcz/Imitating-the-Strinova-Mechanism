package com.hhdxcz.strinova.client.hud;

import java.util.List;

/**
 * 超弦体（角色）图鉴数据：定位、阵营、强调色。
 * 纯数据类，不依赖贴图；选角界面按此渲染卡片，后续战斗系统可用同一份数据绑定武器/技能。
 *
 * <p>定位与阵营数据来源：卡拉彼丘 wiki（超弦体定位页，6 个定位共 24 名角色）。</p>
 */
public final class StrinovaAgentRoster {

    /** 角色定位（6 类）。 */
    public enum Role {
        ASSAULT("hud.strinova.role.assault"),
        DUELIST("hud.strinova.role.duelist"),
        GUARDIAN("hud.strinova.role.guardian"),
        SUPPORT("hud.strinova.role.support"),
        VANGUARD("hud.strinova.role.vanguard"),
        CONTROLLER("hud.strinova.role.controller");

        private final String nameKey;

        Role(String nameKey) {
            this.nameKey = nameKey;
        }

        public String nameKey() {
            return nameKey;
        }

        public String descriptionKey() {
            return nameKey + ".desc";
        }

        /** 定位强调色（Strinova 风格）。 */
        public int accentColor() {
            return switch (this) {
                case ASSAULT -> 0xFFBC3B47;
                case DUELIST -> 0xFFDEB639;
                case GUARDIAN -> 0xFF4B77DC;
                case SUPPORT -> 0xFF35D6E8;
                case VANGUARD -> 0xFFFF4FA3;
                case CONTROLLER -> 0xFF9B6BFF;
            };
        }
    }

    /** 角色阵营。 */
    public enum Faction {
        OPAL("hud.strinova.faction.opal"),
        SCISSORS("hud.strinova.faction.scissors"),
        URBINO("hud.strinova.faction.urbino");

        private final String nameKey;

        Faction(String nameKey) {
            this.nameKey = nameKey;
        }

        public String nameKey() {
            return nameKey;
        }
    }

    /** 一名超弦体。 */
    public record Agent(String id, String name, Role role, Faction faction) {
        public int accentColor() {
            return role.accentColor();
        }
    }

    private static final List<Agent> AGENTS = List.of(
            // 欧泊
            new Agent("michele", "米雪儿·李", Role.GUARDIAN, Faction.OPAL),
            new Agent("xin", "信", Role.GUARDIAN, Faction.OPAL),
            new Agent("xinxia", "心夏", Role.SUPPORT, Faction.OPAL),
            new Agent("yvette", "伊薇特", Role.CONTROLLER, Faction.OPAL),
            new Agent("flavia", "芙拉薇娅", Role.ASSAULT, Faction.OPAL),
            new Agent("youwu", "忧雾", Role.CONTROLLER, Faction.OPAL),
            new Agent("leona", "蕾欧娜", Role.GUARDIAN, Faction.OPAL),
            new Agent("chiyo", "千代", Role.DUELIST, Faction.OPAL),
            // 剪刀手
            new Agent("ming", "明", Role.ASSAULT, Faction.SCISSORS),
            new Agent("lawi", "拉薇", Role.VANGUARD, Faction.SCISSORS),
            new Agent("meridith", "梅瑞狄斯", Role.CONTROLLER, Faction.SCISSORS),
            new Agent("ling", "令", Role.CONTROLLER, Faction.SCISSORS),
            new Agent("kanami", "香奈美", Role.VANGUARD, Faction.SCISSORS),
            new Agent("aika", "艾卡", Role.DUELIST, Faction.SCISSORS),
            new Agent("fagrans", "珐格兰丝", Role.SUPPORT, Faction.SCISSORS),
            new Agent("mala", "玛拉", Role.ASSAULT, Faction.SCISSORS),
            new Agent("nono", "诺诺", Role.VANGUARD, Faction.SCISSORS),
            // 乌尔比诺
            new Agent("audrey", "奥黛丽·格罗夫", Role.GUARDIAN, Faction.URBINO),
            new Agent("madeleine", "玛德蕾娜·利里", Role.CONTROLLER, Faction.URBINO),
            new Agent("freesia", "绯莎", Role.DUELIST, Faction.URBINO),
            new Agent("xinghui", "星绘", Role.SUPPORT, Faction.URBINO),
            new Agent("baimo", "白墨", Role.DUELIST, Faction.URBINO),
            new Agent("galatea", "加拉蒂亚·利里", Role.VANGUARD, Faction.URBINO),
            new Agent("xi", "汐", Role.ASSAULT, Faction.URBINO)
    );

    private StrinovaAgentRoster() {
    }

    public static List<Agent> agents() {
        return AGENTS;
    }

    public static Agent byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (Agent agent : AGENTS) {
            if (agent.id().equals(id)) {
                return agent;
            }
        }
        return null;
    }

    public static Agent defaultAgent() {
        return AGENTS.get(0);
    }

    public static String roleNameKey(Role role) {
        return role == null ? Role.GUARDIAN.nameKey() : role.nameKey();
    }

    public static String roleDescKey(Role role) {
        return role == null ? Role.GUARDIAN.descriptionKey() : role.descriptionKey();
    }

    public static String factionNameKey(Faction faction) {
        return faction == null ? Faction.OPAL.nameKey() : faction.nameKey();
    }
}
