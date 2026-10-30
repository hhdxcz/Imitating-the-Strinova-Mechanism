package com.hhdxcz.strinova.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhdxcz.strinova.StrinovaMod;
import dev.architectury.platform.Platform;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

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
    }

    public StrinovaHudLayout layout() {
        if (layout == null) {
            layout = new StrinovaHudLayout();
        }
        return layout;
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
