package com.hhdxcz.strinova.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhdxcz.strinova.StrinovaMod;
import dev.architectury.platform.Platform;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端战斗 HUD 配置。
 * 独立于服务端 strinova.json：多人服务器不会覆盖玩家本地 HUD 偏好。
 * 配置文件位置：config/strinova-client.json
 */
public final class StrinovaHudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Platform.getConfigFolder().resolve("strinova-client.json");

    private static StrinovaHudConfig instance = new StrinovaHudConfig();

    public boolean hudEnabled = true;
    public boolean crosshairEnabled = true;
    public int crosshairColor = 0xFFEAF6FF;
    public int crosshairGap = 4;
    public int crosshairLength = 7;
    public int crosshairThickness = 2;
    public boolean crosshairDynamic = true;
    public boolean crosshairDot = true;
    public boolean damageNumbersEnabled = true;
    public boolean killFeedEnabled = true;
    public boolean vitalsEnabled = true;
    public boolean ammoEnabled = true;
    public boolean skillsEnabled = true;
    public boolean stateEnabled = true;
    public boolean matchHeaderEnabled = true;
    public boolean scoreboardEnabled = true;
    public String selectedAgentId = "";
    public StrinovaHudLayout layout = new StrinovaHudLayout();

    // ==================== TAB 计分板（可视化编辑器可调） ====================
    /** 面板宽度占屏幕比例。 */
    public float scoreboardWidthRatio = 0.66F;
    /** 面板高度占屏幕比例。 */
    public float scoreboardHeightRatio = 0.515F;
    /** 面板相对屏幕中心的偏移（GUI 像素）。 */
    public float scoreboardOffsetX = -0.5F;
    public float scoreboardOffsetY = -0.5F;
    /** 计分板文字缩放倍率（1.0 = 默认字号）。 */
    public float scoreboardTextScale = 1.0F;
    /** 数据行高度（GUI 像素）。 */
    public int scoreboardRowHeight = 18;

    /** 中间回合记录带「我方/敌方」单行高度（0 = 自动跟随数据行高）。 */
    public int scoreboardRoundRowHeight = 24;

    /** 头像左侧留白（拖动「头像前」的列边界调整，0~24 像素）。 */
    public int scoreboardNameLeftPadding = 0;

    /** 左侧竖栏宽度占面板宽度比例（拖动「头像前」边界可左右调整，0.05~0.25）。 */
    public float scoreboardSidebarWidthRatio = 0.0903F;

    /** 计分板整体不透明度（0.20~1.00，越小越透明）。 */
    public float scoreboardOpacity = 0.95F;

    /** TAB 时是否把游戏画面整体压暗（默认关闭 → 大背景透明）。 */
    public boolean scoreboardDimBackground = false;

    /** 是否显示计分板最外层的大边框（默认关闭）。 */
    public boolean scoreboardPanelBorder = false;

    /** 每个部件独立样式（可见性/字号/文字色/背景色）。 */
    public java.util.LinkedHashMap<String, ScoreboardComponentStyle> scoreboardComponents;

    /**
     * 各列宽度权重（类似 Excel 列宽）：name / kda / ultimate / growth / weapon / damage / skill / assist / ping。
     * 初值 null，由 sanitize()/scoreboardColumnWeight() 惰性补齐（避免静态初始化顺序 NPE）。
     */
    public java.util.LinkedHashMap<String, Float> scoreboardColumnWeights;

    /** 列 key（与权重 map 一一对应；编辑器拖列边界时按相邻两列调整）。 */
    public static final String[] SCOREBOARD_COLUMN_KEYS = {
            "name", "kda", "ultimate", "growth", "weapon", "damage", "skill", "assist", "ping"
    };

    /** 列的中文名（编辑器显示用）。 */
    public static String scoreboardColumnLabel(String key) {
        return switch (key) {
            case "name" -> "头像+名字";
            case "kda" -> "击败/阵亡/助攻";
            case "ultimate" -> "绝招";
            case "growth" -> "成长等级";
            case "weapon" -> "主武器";
            case "damage" -> "输出分";
            case "skill" -> "技能分";
            case "assist" -> "协作分";
            case "ping" -> "PING";
            default -> key;
        };
    }

    private static java.util.LinkedHashMap<String, Float> defaultScoreboardColumnWeights() {
        java.util.LinkedHashMap<String, Float> map = new java.util.LinkedHashMap<>();
        // 默认列宽来自用户导出的调好配置（config/strinova-scoreboard.json）
        float[] defaults = {
                0.17487265F, 0.12056741F, 0.084535345F, 0.1932312F, 0.07712453F,
                0.111460865F, 0.07112326F, 0.11013797F, 0.056946784F
        };
        for (int i = 0; i < SCOREBOARD_COLUMN_KEYS.length; i++) {
            map.put(SCOREBOARD_COLUMN_KEYS[i], defaults[i]);
        }
        return map;
    }

    /** 取某列宽度权重（0.02～0.60），不存在时补默认。 */
    public float scoreboardColumnWeight(String key) {
        if (scoreboardColumnWeights == null) {
            scoreboardColumnWeights = defaultScoreboardColumnWeights();
        }
        return scoreboardColumnWeights.computeIfAbsent(key, k -> 0.08F);
    }

    public void setScoreboardColumnWeight(String key, float value) {
        if (scoreboardColumnWeights == null) {
            scoreboardColumnWeights = defaultScoreboardColumnWeights();
        }
        scoreboardColumnWeights.put(key, Math.max(0.02F, Math.min(0.60F, value)));
    }

    /** 单个计分板部件的独立样式；颜色为 0 表示用默认色。 */
    public static final class ScoreboardComponentStyle {
        public boolean visible = true;
        public float fontScale = 1.0F;
        public int textColor = 0;
        public int backgroundColor = 0;
        /** 文字水平居中（垂直方向默认就在单元格内居中）。 */
        public boolean centerH = false;
    }

    /** 计分板部件 key 列表（编辑器与覆盖层共用）。 */
    public static final String[] SCOREBOARD_COMPONENT_KEYS = {
            "panel", "sidebar", "header", "name", "kda", "ultimate", "growth", "weapon",
            "damage", "skill", "assist", "ping", "rows", "rounds", "roundAlly", "roundEnemy"
    };

    /** 部件的默认中文名（编辑器显示用）。 */
    public static String scoreboardComponentLabel(String key) {
        return switch (key) {
            case "panel" -> "面板";
            case "sidebar" -> "左侧竖栏";
            case "header" -> "列头行";
            case "name" -> "头像+名字";
            case "kda" -> "击败/阵亡/助攻";
            case "ultimate" -> "绝招";
            case "growth" -> "成长等级";
            case "weapon" -> "主武器";
            case "damage" -> "输出分";
            case "skill" -> "技能分";
            case "assist" -> "协作分";
            case "ping" -> "PING";
            case "rows" -> "数据行";
            case "rounds" -> "回合记录带";
            case "roundAlly" -> "我方回合";
            case "roundEnemy" -> "敌方回合";
            default -> key;
        };
    }

    private static java.util.LinkedHashMap<String, ScoreboardComponentStyle> defaultScoreboardComponents() {
        java.util.LinkedHashMap<String, ScoreboardComponentStyle> map = new java.util.LinkedHashMap<>();
        for (String key : SCOREBOARD_COMPONENT_KEYS) {
            map.put(key, new ScoreboardComponentStyle());
        }
        // 默认样式来自用户导出的调好配置（config/strinova-scoreboard.json）
        ScoreboardComponentStyle header = map.get("header");
        header.fontScale = 1.05F;
        header.centerH = true;
        for (String key : new String[]{"kda", "ultimate", "weapon", "damage", "skill", "assist", "ping"}) {
            map.get(key).centerH = true;
        }
        map.get("rows").fontScale = 0.85F;
        ScoreboardComponentStyle rounds = map.get("rounds");
        rounds.fontScale = 1.25F;
        rounds.centerH = true;
        return map;
    }

    /** 取部件样式；不存在时补默认。 */
    public ScoreboardComponentStyle scoreboardComponent(String key) {
        if (scoreboardComponents == null) {
            scoreboardComponents = defaultScoreboardComponents();
        }
        return scoreboardComponents.computeIfAbsent(key, k -> new ScoreboardComponentStyle());
    }

    /** 把某个部件恢复为内置默认样式。 */
    public void resetScoreboardComponent(String key) {
        if (scoreboardComponents == null) {
            scoreboardComponents = defaultScoreboardComponents();
        }
        ScoreboardComponentStyle defaults = defaultScoreboardComponents().get(key);
        scoreboardComponents.put(key, defaults != null ? defaults : new ScoreboardComponentStyle());
    }

    /** 把所有部件恢复为内置默认样式。 */
    public void resetScoreboardComponentDefaults() {
        scoreboardComponents = defaultScoreboardComponents();
    }

    /** 把所有列宽恢复为内置默认权重。 */
    public void resetScoreboardColumnDefaults() {
        scoreboardColumnWeights = defaultScoreboardColumnWeights();
    }

    /** 某列的内置默认权重。 */
    public float defaultScoreboardColumnWeight(String key) {
        java.util.LinkedHashMap<String, Float> defaults = defaultScoreboardColumnWeights();
        return defaults.getOrDefault(key, 0.08F);
    }

    private StrinovaHudConfig() {
    }

    public static StrinovaHudConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                StrinovaHudConfig loaded = GSON.fromJson(reader, StrinovaHudConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                StrinovaMod.LOGGER.error("Failed to read client HUD config, using defaults", e);
                instance = new StrinovaHudConfig();
            }
        }
        instance.sanitize();
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            StrinovaMod.LOGGER.error("Failed to save client HUD config", e);
        }
    }

    private void sanitize() {
        if (layout == null) {
            layout = new StrinovaHudLayout();
        }
        layout.ensureDefaults();
        crosshairGap = clamp(crosshairGap, 0, 12);
        crosshairLength = clamp(crosshairLength, 4, 16);
        crosshairThickness = clamp(crosshairThickness, 1, 4);
        scoreboardWidthRatio = clampf(scoreboardWidthRatio, 0.30F, 1.00F);
        scoreboardHeightRatio = clampf(scoreboardHeightRatio, 0.25F, 0.95F);
        scoreboardOffsetX = clampf(scoreboardOffsetX, -600.0F, 600.0F);
        scoreboardOffsetY = clampf(scoreboardOffsetY, -400.0F, 400.0F);
        scoreboardTextScale = clampf(scoreboardTextScale, 0.6F, 1.8F);
        scoreboardRowHeight = clamp(scoreboardRowHeight, 14, 30);
        scoreboardRoundRowHeight = clamp(scoreboardRoundRowHeight, 0, 24);
        scoreboardNameLeftPadding = clamp(scoreboardNameLeftPadding, 0, 24);
        scoreboardSidebarWidthRatio = clampf(scoreboardSidebarWidthRatio, 0.05F, 0.25F);
        scoreboardOpacity = clampf(scoreboardOpacity, 0.20F, 1.0F);
        if (scoreboardComponents == null) {
            scoreboardComponents = defaultScoreboardComponents();
        }
        for (String key : SCOREBOARD_COMPONENT_KEYS) {
            ScoreboardComponentStyle style = scoreboardComponent(key);
            style.fontScale = clampf(style.fontScale, 0.5F, 2.0F);
        }        if (scoreboardColumnWeights == null) {
            scoreboardColumnWeights = defaultScoreboardColumnWeights();
        }
        for (String key : SCOREBOARD_COLUMN_KEYS) {
            float value = scoreboardColumnWeight(key);
            scoreboardColumnWeights.put(key, Math.max(0.02F, Math.min(0.60F, value)));
        }
    }

    private static float clampf(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public StrinovaHudLayout layout() {
        if (layout == null) {
            layout = new StrinovaHudLayout();
        }
        return layout;
    }

    /** 当前计分板调整值（一行文本，便于发反馈）。 */
    public String scoreboardValuesSummary() {
        return String.format(java.util.Locale.ROOT,
                "scoreboardWidthRatio=%.3f, scoreboardHeightRatio=%.3f, scoreboardOffsetX=%.1f, "
                        + "scoreboardOffsetY=%.1f, scoreboardTextScale=%.2f, scoreboardRowHeight=%d, "
                        + "scoreboardRoundRowHeight=%d, nameLeftPadding=%d, sidebarWidthRatio=%.3f, columns=[%s]",
                scoreboardWidthRatio, scoreboardHeightRatio, scoreboardOffsetX,
                scoreboardOffsetY, scoreboardTextScale, scoreboardRowHeight,
                scoreboardRoundRowHeight, scoreboardNameLeftPadding, scoreboardSidebarWidthRatio,
                columnWeightsText());
    }

    /** 导出计分板全部自定义配置为 JSON（尺寸/位置/字号/行高/列宽/逐部件样式）。 */
    public String scoreboardExportJson() {
        sanitize();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_type", "strinova_scoreboard");
        map.put("version", 1);
        map.put("widthRatio", scoreboardWidthRatio);
        map.put("heightRatio", scoreboardHeightRatio);
        map.put("offsetX", scoreboardOffsetX);
        map.put("offsetY", scoreboardOffsetY);
        map.put("textScale", scoreboardTextScale);
        map.put("rowHeight", scoreboardRowHeight);
        map.put("roundRowHeight", scoreboardRoundRowHeight);
        map.put("nameLeftPadding", scoreboardNameLeftPadding);
        map.put("sidebarWidthRatio", scoreboardSidebarWidthRatio);
        map.put("opacity", scoreboardOpacity);
        map.put("dimBackground", scoreboardDimBackground);
        map.put("panelBorder", scoreboardPanelBorder);
        Map<String, Float> weights = new LinkedHashMap<>();
        for (String key : SCOREBOARD_COLUMN_KEYS) {
            weights.put(key, scoreboardColumnWeight(key));
        }
        map.put("columns", weights);
        map.put("components", scoreboardComponents);
        return GSON.toJson(map);
    }

    /** 从导出的 JSON 导入计分板配置；成功返回 true（不自动保存，由调用方决定）。 */
    public boolean scoreboardImportJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonElement root = JsonParser.parseString(json.trim());
            if (!root.isJsonObject()) {
                return false;
            }
            JsonObject object = root.getAsJsonObject();
            if (object.has("widthRatio")) {
                scoreboardWidthRatio = object.get("widthRatio").getAsFloat();
            }
            if (object.has("heightRatio")) {
                scoreboardHeightRatio = object.get("heightRatio").getAsFloat();
            }
            if (object.has("offsetX")) {
                scoreboardOffsetX = object.get("offsetX").getAsFloat();
            }
            if (object.has("offsetY")) {
                scoreboardOffsetY = object.get("offsetY").getAsFloat();
            }
            if (object.has("textScale")) {
                scoreboardTextScale = object.get("textScale").getAsFloat();
            }
            if (object.has("rowHeight")) {
                scoreboardRowHeight = object.get("rowHeight").getAsInt();
            }
            if (object.has("roundRowHeight")) {
                scoreboardRoundRowHeight = object.get("roundRowHeight").getAsInt();
            }
            if (object.has("nameLeftPadding")) {
                scoreboardNameLeftPadding = object.get("nameLeftPadding").getAsInt();
            }
            if (object.has("sidebarWidthRatio")) {
                scoreboardSidebarWidthRatio = object.get("sidebarWidthRatio").getAsFloat();
            }
            if (object.has("opacity")) {
                scoreboardOpacity = object.get("opacity").getAsFloat();
            }
            if (object.has("dimBackground")) {
                scoreboardDimBackground = object.get("dimBackground").getAsBoolean();
            }
            if (object.has("panelBorder")) {
                scoreboardPanelBorder = object.get("panelBorder").getAsBoolean();
            }
            if (object.has("columns") && object.get("columns").isJsonObject()) {
                JsonObject columns = object.getAsJsonObject("columns");
                for (String key : SCOREBOARD_COLUMN_KEYS) {
                    if (columns.has(key)) {
                        setScoreboardColumnWeight(key, columns.get(key).getAsFloat());
                    }
                }
            }
            if (object.has("components") && object.get("components").isJsonObject()) {
                JsonObject components = object.getAsJsonObject("components");
                for (String key : SCOREBOARD_COMPONENT_KEYS) {
                    if (!components.has(key) || !components.get(key).isJsonObject()) {
                        continue;
                    }
                    JsonObject comp = components.getAsJsonObject(key);
                    ScoreboardComponentStyle style = scoreboardComponent(key);
                    if (comp.has("visible")) {
                        style.visible = comp.get("visible").getAsBoolean();
                    }
                    if (comp.has("fontScale")) {
                        style.fontScale = comp.get("fontScale").getAsFloat();
                    }
                    if (comp.has("textColor")) {
                        style.textColor = comp.get("textColor").getAsInt();
                    }
                    if (comp.has("backgroundColor")) {
                        style.backgroundColor = comp.get("backgroundColor").getAsInt();
                    }
                    if (comp.has("centerH")) {
                        style.centerH = comp.get("centerH").getAsBoolean();
                    }
                }
            }
            sanitize();
            return true;
        } catch (Exception e) {
            StrinovaMod.LOGGER.warn("导入计分板配置失败: {}", e.toString());
            return false;
        }
    }

    /** 计分板导出文件的默认路径：config/strinova-scoreboard.json。 */
    public static Path scoreboardExportPath() {
        return Platform.getConfigFolder().resolve("strinova-scoreboard.json");
    }

    /** 列宽权重文本（如 name=0.20,kda=0.12,...）。 */
    private String columnWeightsText() {
        StringBuilder sb = new StringBuilder();
        for (String key : SCOREBOARD_COLUMN_KEYS) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%s=%.2f", key, scoreboardColumnWeight(key)));
        }
        return sb.toString();
    }

    /** 当前计分板调整值（JSON 片段，可直接贴回配置）。 */
    public String scoreboardValuesJson() {
        return String.format(java.util.Locale.ROOT,
                "\"scoreboardWidthRatio\": %.3f, \"scoreboardHeightRatio\": %.3f, "
                        + "\"scoreboardOffsetX\": %.1f, \"scoreboardOffsetY\": %.1f, "
                        + "\"scoreboardTextScale\": %.2f, \"scoreboardRowHeight\": %d",
                scoreboardWidthRatio, scoreboardHeightRatio, scoreboardOffsetX,
                scoreboardOffsetY, scoreboardTextScale, scoreboardRowHeight);
    }

    public boolean isElementEnabled(StrinovaHudLayout.Element element) {
        return switch (element) {
            case HEALTH_ARMOR -> vitalsEnabled;
            case AMMO -> ammoEnabled;
            case SKILLS -> skillsEnabled;
            case STATE -> stateEnabled;
            case CROSSHAIR -> crosshairEnabled;
            case KILL_FEED -> killFeedEnabled;
            case DAMAGE_NUMBERS -> damageNumbersEnabled;
            case MATCH_HEADER -> matchHeaderEnabled;
        };
    }

    public void setElementEnabled(StrinovaHudLayout.Element element, boolean enabled) {
        switch (element) {
            case HEALTH_ARMOR -> vitalsEnabled = enabled;
            case AMMO -> ammoEnabled = enabled;
            case SKILLS -> skillsEnabled = enabled;
            case STATE -> stateEnabled = enabled;
            case CROSSHAIR -> crosshairEnabled = enabled;
            case KILL_FEED -> killFeedEnabled = enabled;
            case DAMAGE_NUMBERS -> damageNumbersEnabled = enabled;
            case MATCH_HEADER -> matchHeaderEnabled = enabled;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
