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

    // ==================== TAB 计分板（可视化编辑器可调） ====================
    /** 面板宽度占屏幕比例。 */
    public float scoreboardWidthRatio = 0.66F;
    /** 面板高度占屏幕比例。 */
    public float scoreboardHeightRatio = 0.515F;
    /** 面板相对屏幕中心的偏移（GUI 像素）。 */
    public float scoreboardOffsetX = 0.0F;
    public float scoreboardOffsetY = 0.0F;
    /** 计分板文字缩放倍率（1.0 = 默认字号）。 */
    public float scoreboardTextScale = 1.0F;
    /** 数据行高度（GUI 像素）。 */
    public int scoreboardRowHeight = 20;

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
                        + "scoreboardOffsetY=%.1f, scoreboardTextScale=%.2f, scoreboardRowHeight=%d",
                scoreboardWidthRatio, scoreboardHeightRatio, scoreboardOffsetX,
                scoreboardOffsetY, scoreboardTextScale, scoreboardRowHeight);
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
