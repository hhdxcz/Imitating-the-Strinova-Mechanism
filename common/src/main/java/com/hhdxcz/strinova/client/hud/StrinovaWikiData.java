package com.hhdxcz.strinova.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhdxcz.strinova.StrinovaMod;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 卡拉彼丘 wiki 数据（中英双语）运行时读取层。
 *
 * <p>数据文件（随 jar 打包）：</p>
 * <ul>
 *     <li>{@code assets/strinova/data/wiki_zh.json}：主数据（biligame wiki，中文官方文本 + 数值）；</li>
 *     <li>{@code assets/strinova/data/wiki_en.json}：英文文本覆盖层（strinova.org wiki）。</li>
 * </ul>
 *
 * <p>合并规则：结构与数值（成本 / 觉醒三色需求 / 定位 / 阵营）始终取中文主数据；当游戏语言为英文时，
 * 用英文文件按 {@code id} 覆盖名称 / 简介 / 技能 / 觉醒 / 增幅网络文案，英文缺失的字段回退中文。
 * 数据缺失或解析失败时 {@link #available()} 为 false，界面回退到原有占位文案，不影响游戏启动。</p>
 *
 * <p>数据契约见 {@code docs/wiki-research/wiki-data-schema.md}。</p>
 */
public final class StrinovaWikiData {

    private static final String ZH_PATH = "assets/strinova/data/wiki_zh.json";
    private static final String EN_PATH = "assets/strinova/data/wiki_en.json";

    /** 一个技能：名称 + 一句话描述。 */
    public record Skill(String name, String desc) {
        public boolean isEmpty() {
            return isBlank(name) && isBlank(desc);
        }
    }

    /** 增幅网络里的一个升级项。{@code change} 是「属性变化」详细文案（可能为空）。 */
    public record NetworkItem(String text, int cost, String change) {
    }

    /**
     * 增幅网络的一个分支。
     *
     * @param slot  1~8（1~4 武器网络，5 主动技能，6 被动技能，7 护甲，8 弦化）
     * @param kind  weapon / active / passive / armor / string
     * @param label 分支标题（武器类别名 / 技能名 / 护甲 / 弦化）
     */
    public record NetworkGroup(int slot, String kind, String label, List<NetworkItem> items) {
    }

    /** 一个觉醒（名称 + 描述 + 三色需求点数）。 */
    public record Awakening(String name, String desc, int needWeapon, int needSkill, int needDefense) {
    }

    /** 主武器：名称 / 类型 / 介绍。 */
    public record Weapon(String name, String type, String desc) {
    }

    /** 角色基础数值（生命 / 护甲 / 护甲回复 / 移速 / 弦化移速 / 弦化减伤%）。 */
    public record Stats(int hp, int armor, int armorRegen, int moveSpeed, int stringMove, int stringDr) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0);
    }

    /** 一名超弦体的完整 wiki 数据。 */
    public record Agent(String id,
                        String name,
                        String enName,
                        String role,
                        String faction,
                        String identity,
                        String intro,
                        String quote,
                        List<String> traits,
                        Weapon weapon,
                        Stats stats,
                        Skill active,
                        Skill passive,
                        Skill ultimate,
                        Skill tactical,
                        List<Awakening> awakenings,
                        List<NetworkGroup> network) {

        public Skill skill(String key) {
            if (key == null) {
                return new Skill("", "");
            }
            return switch (key) {
                case "active" -> this.active;
                case "passive" -> this.passive;
                case "ultimate" -> this.ultimate;
                case "tactical" -> this.tactical;
                default -> new Skill("", "");
            };
        }
    }

    private static volatile List<Agent> agents;
    private static volatile Map<String, Agent> byId;
    private static volatile String loadedLanguage = "";

    private StrinovaWikiData() {
    }

    /** 全部角色（按 wiki 文件顺序，与 {@link StrinovaAgentRoster} 一致）；数据不可用时为空列表。 */
    public static List<Agent> agents() {
        ensureLoaded();
        List<Agent> current = agents;
        return current == null ? List.of() : current;
    }

    /** 数据是否可用（文件存在且解析出至少一名角色）。 */
    public static boolean available() {
        return !agents().isEmpty();
    }

    /** 按 id 查询；未命中返回 null。 */
    public static Agent byId(String id) {
        if (isBlank(id)) {
            return null;
        }
        ensureLoaded();
        Map<String, Agent> current = byId;
        return current == null ? null : current.get(id);
    }

    /** 清空缓存（切换语言或重载资源后调用）。 */
    public static void invalidate() {
        agents = null;
        byId = null;
        loadedLanguage = "";
    }

    // ------------------------------------------------------------------
    // 加载 / 合并
    // ------------------------------------------------------------------

    private static void ensureLoaded() {
        String language = currentLanguage();
        if (agents != null && language.equals(loadedLanguage)) {
            return;
        }
        synchronized (StrinovaWikiData.class) {
            if (agents != null && language.equals(loadedLanguage)) {
                return;
            }
            boolean english = language.startsWith("en");
            List<Agent> loaded = new ArrayList<>();
            try {
                JsonObject zh = readJson(ZH_PATH);
                JsonObject en = english ? readJson(EN_PATH) : null;
                loaded = merge(zh, en);
            } catch (Exception exception) {
                StrinovaMod.LOGGER.warn("Strinova: 读取 wiki 数据失败，界面将回退占位文本", exception);
            }
            Map<String, Agent> map = new LinkedHashMap<>();
            for (Agent agent : loaded) {
                map.put(agent.id(), agent);
            }
            byId = Collections.unmodifiableMap(map);
            agents = Collections.unmodifiableList(loaded);
            loadedLanguage = language;
        }
    }

    /** 当前游戏语言代码（如 zh_cn / en_us）；取不到时按英文处理并回退中文数据。 */
    private static String currentLanguage() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getLanguageManager() != null) {
                String selected = minecraft.getLanguageManager().getSelected();
                if (!isBlank(selected)) {
                    return selected.toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
            // 非客户端环境或语言尚未初始化：使用默认
        }
        return "zh_cn";
    }

    /** 读取 json 资源；文件不存在时返回 null。 */
    private static JsonObject readJson(String path) {
        try (InputStream stream = StrinovaWikiData.class.getResourceAsStream("/" + path)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            }
        } catch (Exception exception) {
            StrinovaMod.LOGGER.warn("Strinova: 解析 wiki 数据失败：" + path, exception);
            return null;
        }
    }

    private static List<Agent> merge(JsonObject zh, JsonObject en) {
        Map<String, JsonObject> enAgents = indexAgents(en);
        List<Agent> result = new ArrayList<>();
        JsonArray array = zh == null ? null : array(zh, "agents");
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject source = element.getAsJsonObject();
            String id = str(source, "id", "");
            if (isBlank(id)) {
                continue;
            }
            JsonObject overlay = enAgents.get(id);
            result.add(parseAgent(source, overlay));
        }
        return result;
    }

    private static Map<String, JsonObject> indexAgents(JsonObject en) {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        JsonArray array = en == null ? null : array(en, "agents");
        if (array == null) {
            return map;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject agent = element.getAsJsonObject();
            String id = str(agent, "id", "");
            if (!isBlank(id)) {
                map.put(id, agent);
            }
        }
        return map;
    }

    private static Agent parseAgent(JsonObject source, JsonObject overlay) {
        String id = str(source, "id", "");
        JsonObject weapon = obj(source, "weapon");
        JsonObject stats = obj(source, "stats");
        JsonObject skills = obj(source, "skills");
        JsonObject enWeapon = obj(overlay, "weapon");
        JsonObject enSkills = obj(overlay, "skills");

        return new Agent(
                id,
                pick(str(overlay, "name", ""), str(source, "name", "")),
                pick(str(source, "enName", ""), str(overlay, "enName", "")),
                str(source, "role", ""),
                str(source, "faction", ""),
                pick(str(overlay, "identity", ""), str(source, "identity", "")),
                pick(str(overlay, "intro", ""), str(source, "intro", "")),
                pick(str(overlay, "quote", ""), str(source, "quote", "")),
                traits(source),
                new Weapon(
                        pick(str(enWeapon, "name", ""), str(weapon, "name", "")),
                        pick(str(enWeapon, "type", ""), str(weapon, "type", "")),
                        pick(str(enWeapon, "desc", ""), str(weapon, "desc", ""))),
                new Stats(
                        integer(stats, "hp", 0),
                        integer(stats, "armor", 0),
                        integer(stats, "armorRegen", 0),
                        integer(stats, "moveSpeed", 0),
                        integer(stats, "stringMove", 0),
                        integer(stats, "stringDr", 0)),
                skill(skills, enSkills, "active"),
                skill(skills, enSkills, "passive"),
                skill(skills, enSkills, "ultimate"),
                skill(skills, enSkills, "tactical"),
                awakenings(source, overlay),
                network(source, overlay));
    }

    private static List<String> traits(JsonObject source) {
        List<String> traits = new ArrayList<>();
        JsonArray array = array(source, "traits");
        if (array != null) {
            for (JsonElement element : array) {
                String value = element.isJsonPrimitive() ? element.getAsString() : "";
                if (!isBlank(value)) {
                    traits.add(value);
                }
            }
        }
        return traits;
    }

    private static Skill skill(JsonObject base, JsonObject overlay, String key) {
        JsonObject source = obj(base, key);
        JsonObject target = obj(overlay, key);
        return new Skill(
                pick(str(target, "name", ""), str(source, "name", "")),
                pick(str(target, "desc", ""), str(source, "desc", "")));
    }

    private static List<Awakening> awakenings(JsonObject source, JsonObject overlay) {
        List<Awakening> result = new ArrayList<>();
        JsonArray base = array(source, "awakenings");
        JsonArray en = array(overlay, "awakenings");
        if (base == null) {
            return result;
        }
        for (int i = 0; i < base.size(); i++) {
            JsonElement element = base.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject awakening = element.getAsJsonObject();
            JsonObject target = en != null && i < en.size() && en.get(i).isJsonObject()
                    ? en.get(i).getAsJsonObject() : null;
            result.add(new Awakening(
                    pick(str(target, "name", ""), str(awakening, "name", "")),
                    pick(str(target, "desc", ""), str(awakening, "desc", "")),
                    integer(awakening, "needWeapon", 0),
                    integer(awakening, "needSkill", 0),
                    integer(awakening, "needDefense", 0)));
        }
        return result;
    }

    private static List<NetworkGroup> network(JsonObject source, JsonObject overlay) {
        List<NetworkGroup> result = new ArrayList<>();
        JsonArray base = array(source, "network");
        JsonArray en = array(overlay, "network");
        if (base == null) {
            return result;
        }
        for (int i = 0; i < base.size(); i++) {
            JsonElement element = base.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject group = element.getAsJsonObject();
            JsonObject target = en != null && i < en.size() && en.get(i).isJsonObject()
                    ? en.get(i).getAsJsonObject() : null;
            int slot = integer(group, "slot", i + 1);
            List<NetworkItem> items = networkItems(group, target, slot);
            if (items.isEmpty()) {
                continue;
            }
            result.add(new NetworkGroup(
                    slot,
                    str(group, "kind", ""),
                    pick(str(target, "label", ""), str(group, "label", "")),
                    items));
        }
        return result;
    }

    private static List<NetworkItem> networkItems(JsonObject group, JsonObject overlay, int slot) {
        List<NetworkItem> result = new ArrayList<>();
        JsonArray base = array(group, "items");
        JsonArray en = array(overlay, "items");
        if (base == null) {
            return result;
        }
        for (int i = 0; i < base.size(); i++) {
            JsonElement element = base.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            JsonObject target = en != null && i < en.size() && en.get(i).isJsonObject()
                    ? en.get(i).getAsJsonObject() : null;
            String text = pick(str(target, "text", ""), str(item, "text", ""));
            if (isBlank(text)) {
                continue;
            }
            result.add(new NetworkItem(
                    text,
                    integer(item, "cost", 0),
                    str(item, "change", "")));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // JSON 小工具（容忍字段缺失）
    // ------------------------------------------------------------------

    private static String pick(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static JsonObject obj(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static String str(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}
