package com.hhdxcz.wa.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhdxcz.wa.WaMod;
import com.hhdxcz.wa.collision.WaCollisionBoxTuning;
import com.hhdxcz.wa.gameplay.WaAirJumpSettings;
import com.hhdxcz.wa.paper.WaPaperDamageReduction;
import com.hhdxcz.wa.paper.WaWallBlacklist;
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

public final class WaCommonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve(WaMod.MOD_ID + ".json");
    private static ConfigData data = ConfigData.defaults();

    private WaCommonConfig() {
    }

    public static synchronized void init() {
        data = load();
        applyRuntime(data);
    }

    public static synchronized ConfigData snapshot() {
        return data.copy();
    }

    public static synchronized void update(ConfigData next) {
        ConfigData sanitized = sanitize(next);
        data = sanitized;
        applyRuntime(sanitized);
        save(sanitized);
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

    public static synchronized WaCollisionBoxTuning.Tuning getDefaultSyncTuning() {
        return new WaCollisionBoxTuning.Tuning(
                data.defaultSyncOffsetX, data.defaultSyncOffsetY, data.defaultSyncOffsetZ,
                data.defaultSyncSizeX, data.defaultSyncSizeY, data.defaultSyncSizeZ
        );
    }

    public static synchronized WaCollisionBoxTuning.Tuning getDefaultFlyTuning() {
        return new WaCollisionBoxTuning.Tuning(
                data.defaultFlyOffsetX, 0.0D, data.defaultFlyOffsetZ,
                data.defaultFlySizeX, 0.0D, data.defaultFlySizeZ
        );
    }

    public static synchronized List<ResourceLocation> getWallBlacklist() {
        return parseWallBlacklist(data.wallBlacklistCsv);
    }

    public static synchronized void setWallBlacklist(List<ResourceLocation> list) {
        ConfigData next = data.copy();
        next.wallBlacklistCsv = normalizeWallBlacklistCsv(list);
        update(next);
    }

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
            WaMod.LOGGER.error("Failed to read config, using defaults", e);
            return ConfigData.defaults();
        }
    }

    private static void save(ConfigData cfg) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(cfg, writer);
            }
        } catch (IOException e) {
            WaMod.LOGGER.error("Failed to save config", e);
        }
    }

    private static ConfigData sanitize(ConfigData raw) {
        ConfigData out = raw == null ? ConfigData.defaults() : raw.copy();
        if (out.defaultExtraJumps < 0) {
            out.defaultExtraJumps = 0;
        } else if (out.defaultExtraJumps > WaAirJumpSettings.MAX_EXTRA_JUMPS) {
            out.defaultExtraJumps = WaAirJumpSettings.MAX_EXTRA_JUMPS;
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

    private static void applyRuntime(ConfigData cfg) {
        WaPaperDamageReduction.set(cfg.paperDamageReduction);
        WaWallBlacklist.replaceServerList(parseWallBlacklist(cfg.wallBlacklistCsv));
    }

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

    public static final class ConfigData {
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

        public static ConfigData defaults() {
            return new ConfigData();
        }

        public ConfigData copy() {
            ConfigData out = new ConfigData();
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
