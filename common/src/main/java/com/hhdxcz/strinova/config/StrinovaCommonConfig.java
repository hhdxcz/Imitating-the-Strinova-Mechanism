package com.hhdxcz.strinova.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.paper.StrinovaPaperDamageReduction;
import com.hhdxcz.strinova.paper.StrinovaWallBlacklist;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 通用配置文件管理器：负责配置的加载、保存、校验和运行时应用
public final class StrinovaCommonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve(StrinovaMod.MOD_ID + ".json");
    private static ConfigData data = ConfigData.defaults();

    private StrinovaCommonConfig() {
    }

    // 初始化配置：从文件加载并应用运行时设置
    public static synchronized void init() {
        data = load();
        applyRuntime(data);
    }

    // 获取当前配置的只读副本
    public static synchronized ConfigData snapshot() {
        return data.copy();
    }

    // 更新配置：校验、保存并应用
    public static synchronized void update(ConfigData next) {
        ConfigData sanitized = sanitize(next);
        data = sanitized;
        applyRuntime(sanitized);
        save(sanitized);
    }

    public static synchronized boolean isModEnabled() {
        return data.modEnabled;
    }

    public static synchronized void setModEnabled(boolean value) {
        ConfigData next = data.copy();
        next.modEnabled = value;
        update(next);
    }

    public static synchronized int getDefaultExtraJumps() {
        return data.defaultExtraJumps;
    }

    public static synchronized boolean isTaczForceFirstPerson() {
        return data.taczForceFirstPerson;
    }

    public static synchronized boolean isTaczExitStatesOnAction() {
        return data.taczExitStatesOnAction;
    }

    public static synchronized boolean isThirdPersonCompatBypassEnabled() {
        return data.thirdPersonCompatBypass;
    }

    public static synchronized boolean isTpsExitFlyOnAction() {
        return data.tpsExitFlyOnAction;
    }

    // 获取默认的同步碰撞箱偏移/尺寸参数
    public static synchronized StrinovaCollisionBoxTuning.Tuning getDefaultSyncTuning() {
        return new StrinovaCollisionBoxTuning.Tuning(
                data.defaultSyncOffsetX, data.defaultSyncOffsetY, data.defaultSyncOffsetZ,
                data.defaultSyncSizeX, data.defaultSyncSizeY, data.defaultSyncSizeZ
        );
    }

    // 获取默认的飘飞碰撞箱偏移/尺寸参数（Y 轴固定为 0）
    public static synchronized StrinovaCollisionBoxTuning.Tuning getDefaultFlyTuning() {
        return new StrinovaCollisionBoxTuning.Tuning(
                data.defaultFlyOffsetX, 0.0D, data.defaultFlyOffsetZ,
                data.defaultFlySizeX, 0.0D, data.defaultFlySizeZ
        );
    }

    // 解析禁止贴墙方块名单
    public static synchronized List<ResourceLocation> getWallBlacklist() {
        return parseWallBlacklist(data.wallBlacklistCsv);
    }

    // 设置禁止贴墙方块名单
    public static synchronized void setWallBlacklist(List<ResourceLocation> list) {
        ConfigData next = data.copy();
        next.wallBlacklistCsv = normalizeWallBlacklistCsv(list);
        update(next);
    }

    // 从 JSON 文件加载配置，若不存在则创建默认配置
    private static ConfigData load() {
        if (!Files.exists(CONFIG_PATH)) {
            ConfigData defaults = ConfigData.defaults();
            save(defaults);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            return sanitize(loaded);
        } catch (Exception e) {
            StrinovaMod.LOGGER.error("Failed to read config, using defaults", e);
            ConfigData defaults = ConfigData.defaults();
            save(defaults);
            return defaults;
        }
    }

    // 将配置写入 JSON 文件
    private static void save(ConfigData cfg) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(cfg, writer);
            }
        } catch (IOException e) {
            StrinovaMod.LOGGER.error("Failed to save config", e);
        }
    }

    // 校验并修正配置值，确保所有字段在合法范围内
    private static ConfigData sanitize(ConfigData raw) {
        ConfigData out = raw == null ? ConfigData.defaults() : raw.copy();
        if (out.defaultExtraJumps < 0) {
            out.defaultExtraJumps = 0;
        } else if (out.defaultExtraJumps > StrinovaAirJumpSettings.MAX_EXTRA_JUMPS) {
            out.defaultExtraJumps = StrinovaAirJumpSettings.MAX_EXTRA_JUMPS;
        }
        if (out.paperDamageReduction < 0.0D) {
            out.paperDamageReduction = 0.0D;
        } else if (out.paperDamageReduction > 1.0D) {
            out.paperDamageReduction = 1.0D;
        }
        out.defaultSyncOffsetX = clampBox(out.defaultSyncOffsetX);
        out.defaultSyncOffsetY = clampBox(out.defaultSyncOffsetY);
        out.defaultSyncOffsetZ = clampBox(out.defaultSyncOffsetZ);
        out.defaultSyncSizeX = clampBox(out.defaultSyncSizeX);
        out.defaultSyncSizeY = clampBox(out.defaultSyncSizeY);
        out.defaultSyncSizeZ = clampBox(out.defaultSyncSizeZ);
        out.defaultFlyOffsetX = clampBox(out.defaultFlyOffsetX);
        out.defaultFlyOffsetZ = clampBox(out.defaultFlyOffsetZ);
        out.defaultFlySizeX = clampBox(out.defaultFlySizeX);
        out.defaultFlySizeZ = clampBox(out.defaultFlySizeZ);
        out.wallBlacklistCsv = normalizeWallBlacklistCsv(parseWallBlacklist(out.wallBlacklistCsv));
        return out;
    }

    // 将配置值应用到运行时系统
    private static void applyRuntime(ConfigData cfg) {
        StrinovaPaperDamageReduction.set(cfg.paperDamageReduction);
        StrinovaWallBlacklist.replaceServerList(parseWallBlacklist(cfg.wallBlacklistCsv));
    }

    // 将碰撞箱参数限制在 [-8, 8] 范围内，并四舍五入到两位小数
    private static double clampBox(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        if (value < -8.0D) {
            value = -8.0D;
        } else if (value > 8.0D) {
            value = 8.0D;
        }
        double rounded = Math.round(value * 100.0D) / 100.0D;
        return rounded == -0.0D ? 0.0D : rounded;
    }

    // 从 CSV 字符串解析方块 ID 列表
    private static List<ResourceLocation> parseWallBlacklist(String csv) {
        List<ResourceLocation> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return list;
        }
        Set<String> dedup = new LinkedHashSet<>();
        String[] tokens = csv.split(",");
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String s = token.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) {
                dedup.add(id.toString());
            }
        }
        for (String s : dedup) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) {
                list.add(id);
            }
        }
        return list;
    }

    // 将方块 ID 列表序列化为 CSV 字符串
    private static String normalizeWallBlacklistCsv(List<ResourceLocation> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (ResourceLocation id : list) {
            if (id != null) {
                dedup.add(id.toString());
            }
        }
        return String.join(",", dedup);
    }

    // 配置数据对象：包含所有可配置的字段
    public static final class ConfigData {
        public boolean modEnabled = false;
        public int defaultExtraJumps = 1;
        public double paperDamageReduction = 0.4D;
        public boolean taczForceFirstPerson = true;
        public boolean taczExitStatesOnAction = true;
        public boolean thirdPersonCompatBypass = true;
        public boolean tpsExitFlyOnAction = true;
        public String wallBlacklistCsv = "";
        public double defaultSyncOffsetX = 0.0D;
        public double defaultSyncOffsetY = 0.0D;
        public double defaultSyncOffsetZ = 0.0D;
        public double defaultSyncSizeX = 0.0D;
        public double defaultSyncSizeY = 0.0D;
        public double defaultSyncSizeZ = 0.0D;
        public double defaultFlyOffsetX = 0.0D;
        public double defaultFlyOffsetZ = 0.0D;
        public double defaultFlySizeX = 0.0D;
        public double defaultFlySizeZ = 0.0D;

        // 创建默认配置实例
        public static ConfigData defaults() {
            return new ConfigData();
        }

        // 深拷贝当前配置
        public ConfigData copy() {
            ConfigData out = new ConfigData();
            out.modEnabled = this.modEnabled;
            out.defaultExtraJumps = this.defaultExtraJumps;
            out.paperDamageReduction = this.paperDamageReduction;
            out.taczForceFirstPerson = this.taczForceFirstPerson;
            out.taczExitStatesOnAction = this.taczExitStatesOnAction;
            out.thirdPersonCompatBypass = this.thirdPersonCompatBypass;
            out.tpsExitFlyOnAction = this.tpsExitFlyOnAction;
            out.wallBlacklistCsv = this.wallBlacklistCsv;
            out.defaultSyncOffsetX = this.defaultSyncOffsetX;
            out.defaultSyncOffsetY = this.defaultSyncOffsetY;
            out.defaultSyncOffsetZ = this.defaultSyncOffsetZ;
            out.defaultSyncSizeX = this.defaultSyncSizeX;
            out.defaultSyncSizeY = this.defaultSyncSizeY;
            out.defaultSyncSizeZ = this.defaultSyncSizeZ;
            out.defaultFlyOffsetX = this.defaultFlyOffsetX;
            out.defaultFlyOffsetZ = this.defaultFlyOffsetZ;
            out.defaultFlySizeX = this.defaultFlySizeX;
            out.defaultFlySizeZ = this.defaultFlySizeZ;
            return out;
        }
    }
}