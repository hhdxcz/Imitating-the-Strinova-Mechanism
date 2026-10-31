package com.hhdxcz.strinova.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 战斗 HUD 数据契约（客户端侧）。
 *
 * <p>战斗系统完成后，这些字段由服务端 S2C 战斗同步包填充（见 docs/combat-ui-data-contract.md）；
 * 在战斗系统落地之前，生命值临时取玩家真实血量映射到 0~100，弹药/技能/击杀播报由
 * {@link StrinovaHudMockData} 提供模拟数据，方便先把 UI 调好。</p>
 */
public final class StrinovaHudState {

    public enum FireMode {
        AUTO("hud.strinova.fire.auto"),
        SEMI("hud.strinova.fire.semi"),
        BURST("hud.strinova.fire.burst"),
        PUMP("hud.strinova.fire.pump"),
        BOLT("hud.strinova.fire.bolt"),
        MELEE("hud.strinova.fire.melee");

        private final String translationKey;

        FireMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record WeaponSlot(int index, String name, boolean selected) {
    }

    public record KillFeedEntry(String killer, String weapon, String victim, boolean headshot, long timeMs) {
    }

    public record DamageNumber(float amount, boolean headshot, boolean mitigated, long timeMs) {
    }

    public record DamageIndicator(float yawDegrees, long timeMs) {
    }

    /** 增益/状态提示（如护甲恢复、静心凝势），显示在血量面板下方。 */
    public record BuffEntry(String name, int color) {
    }

    public static final long KILL_FEED_LIFETIME_MS = 8000L;
    public static final long DAMAGE_NUMBER_LIFETIME_MS = 1100L;
    public static final long DAMAGE_INDICATOR_LIFETIME_MS = 900L;
    public static final long HIT_MARKER_LIFETIME_MS = 220L;
    private static final int MAX_KILL_FEED = 6;
    private static final int MAX_DAMAGE_NUMBERS = 24;
    private static final int MAX_DAMAGE_INDICATORS = 8;

    private static volatile float health = 130.0F;
    private static volatile float maxHealth = 150.0F;
    private static volatile float armor = 0.0F;
    private static volatile float maxArmor = 80.0F;
    private static volatile List<BuffEntry> buffs = List.of();
    /** 服务端对局是否激活（真实数据源）；激活时 HUD 不再用 MC 血量/模拟数据覆盖。 */
    private static volatile boolean serverMatchActive;
    private static volatile long lastServerSyncMs;

    private static volatile String weaponName = "";
    private static volatile FireMode fireMode = FireMode.AUTO;
    private static volatile int magazine = 0;
    private static volatile int reserve = 0;
    private static volatile long reloadStartMs = 0L;
    private static volatile long reloadDurationMs = 0L;
    private static volatile int selectedSlot = 0;
    private static volatile List<WeaponSlot> weaponSlots = List.of();

    private static volatile float[] skillCooldown = {0.0F, 0.0F, 0.0F};
    private static volatile boolean[] skillReady = {true, true, true};
    private static volatile int ultimatePoints = 0;
    private static volatile int ultimateMax = 7;

    private static volatile float crosshairSpread = 0.0F;

    private static final List<KillFeedEntry> KILL_FEED = new CopyOnWriteArrayList<>();
    private static final List<DamageNumber> DAMAGE_NUMBERS = new CopyOnWriteArrayList<>();
    private static final List<DamageIndicator> DAMAGE_INDICATORS = new CopyOnWriteArrayList<>();

    private static volatile long hitMarkerMs = 0L;
    private static volatile boolean hitMarkerKill = false;

    private StrinovaHudState() {
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    // ==================== 生命 / 护甲 ====================

    public static float getHealth() {
        return health;
    }

    public static void setHealth(float value, float max) {
        maxHealth = Math.max(1.0F, max);
        health = Math.max(0.0F, Math.min(maxHealth, value));
    }

    public static float getMaxHealth() {
        return maxHealth;
    }

    public static float getArmor() {
        return armor;
    }

    public static void setArmor(float value, float max) {
        maxArmor = Math.max(0.0F, max);
        armor = Math.max(0.0F, Math.min(maxArmor, value));
    }

    public static float getMaxArmor() {
        return maxArmor;
    }

    // ==================== 武器 / 弹药 ====================

    public static String getWeaponName() {
        return weaponName;
    }

    public static void setWeapon(String name, FireMode mode, int mag, int reserveAmmo) {
        weaponName = name == null ? "" : name;
        fireMode = mode == null ? FireMode.AUTO : mode;
        magazine = Math.max(0, mag);
        reserve = Math.max(0, reserveAmmo);
    }

    public static FireMode getFireMode() {
        return fireMode;
    }

    public static String getFireModeTranslationKey() {
        return fireMode.translationKey();
    }

    public static int getMagazine() {
        return magazine;
    }

    public static int getReserve() {
        return reserve;
    }

    public static int getSelectedSlot() {
        return selectedSlot;
    }

    public static void setSelectedSlot(int slot) {
        selectedSlot = Math.max(0, slot);
    }

    public static List<WeaponSlot> getWeaponSlots() {
        return weaponSlots;
    }

    public static void setWeaponSlots(List<WeaponSlot> slots) {
        weaponSlots = slots == null ? List.of() : List.copyOf(slots);
    }

    public static void beginReload(long durationMs) {
        reloadDurationMs = Math.max(1L, durationMs);
        reloadStartMs = now();
    }

    public static void finishReload() {
        reloadStartMs = 0L;
        reloadDurationMs = 0L;
    }

    /** 换弹进度 0~1；0 表示未在换弹。 */
    public static float reloadProgress() {
        if (reloadStartMs <= 0L || reloadDurationMs <= 0L) {
            return 0.0F;
        }
        long elapsed = now() - reloadStartMs;
        if (elapsed >= reloadDurationMs) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, elapsed / (float) reloadDurationMs));
    }

    // ==================== 技能 / 终极 ====================

    public static float getSkillCooldown(int index) {
        if (index < 0 || index >= skillCooldown.length) {
            return 0.0F;
        }
        return skillCooldown[index];
    }

    public static boolean isSkillReady(int index) {
        return index >= 0 && index < skillReady.length && skillReady[index];
    }

    public static void setSkills(float[] cooldowns, boolean[] ready) {
        if (cooldowns != null) {
            skillCooldown = new float[]{cooldowns[0], cooldowns[1], cooldowns[2]};
        }
        if (ready != null) {
            skillReady = new boolean[]{ready[0], ready[1], ready[2]};
        }
    }

    public static int getUltimatePoints() {
        return ultimatePoints;
    }

    public static int getUltimateMax() {
        return Math.max(1, ultimateMax);
    }

    public static void setUltimate(int points, int max) {
        ultimateMax = Math.max(1, max);
        ultimatePoints = Math.max(0, Math.min(ultimateMax, points));
    }

    // ==================== 准星扩散 ====================

    public static float getCrosshairSpread() {
        return crosshairSpread;
    }

    public static void setCrosshairSpread(float value) {
        crosshairSpread = Math.max(0.0F, Math.min(1.0F, value));
    }

    // ==================== 增益提示 ====================

    public static List<BuffEntry> getBuffs() {
        return buffs;
    }

    public static void setBuffs(List<BuffEntry> entries) {
        buffs = entries == null ? List.of() : List.copyOf(entries);
    }

    // ==================== 服务端对局同步状态 ====================

    public static boolean isServerMatchActive() {
        return serverMatchActive;
    }

    public static void setServerMatchActive(boolean value) {
        serverMatchActive = value;
        if (value) {
            lastServerSyncMs = now();
        }
    }

    public static long lastServerSyncMs() {
        return lastServerSyncMs;
    }

    /** 服务端同步包到达时刷新时间戳（HUD 用来判断是否还该显示服务端数据）。 */
    public static void touchServerSync() {
        lastServerSyncMs = now();
    }

    /** 对局结束/断线时清空服务端同步状态。 */
    public static void clearServerSync() {
        serverMatchActive = false;
        lastServerSyncMs = 0L;
    }

    // ==================== 击杀播报 / 伤害反馈 ====================

    public static void pushKillFeed(String killer, String weapon, String victim, boolean headshot) {
        KILL_FEED.add(0, new KillFeedEntry(killer, weapon, victim, headshot, now()));
        while (KILL_FEED.size() > MAX_KILL_FEED) {
            KILL_FEED.remove(KILL_FEED.size() - 1);
        }
    }

    public static List<KillFeedEntry> killFeed() {
        long now = now();
        KILL_FEED.removeIf(entry -> now - entry.timeMs() > KILL_FEED_LIFETIME_MS);
        return new ArrayList<>(KILL_FEED);
    }

    public static void pushDamageNumber(float amount, boolean headshot, boolean mitigated) {
        DAMAGE_NUMBERS.add(new DamageNumber(amount, headshot, mitigated, now()));
        while (DAMAGE_NUMBERS.size() > MAX_DAMAGE_NUMBERS) {
            DAMAGE_NUMBERS.remove(0);
        }
    }

    public static List<DamageNumber> damageNumbers() {
        long now = now();
        DAMAGE_NUMBERS.removeIf(number -> now - number.timeMs() > DAMAGE_NUMBER_LIFETIME_MS);
        return new ArrayList<>(DAMAGE_NUMBERS);
    }

    public static void pushDamageIndicator(float yawDegrees) {
        DAMAGE_INDICATORS.add(new DamageIndicator(yawDegrees, now()));
        while (DAMAGE_INDICATORS.size() > MAX_DAMAGE_INDICATORS) {
            DAMAGE_INDICATORS.remove(0);
        }
    }

    public static List<DamageIndicator> damageIndicators() {
        long now = now();
        DAMAGE_INDICATORS.removeIf(indicator -> now - indicator.timeMs() > DAMAGE_INDICATOR_LIFETIME_MS);
        return new ArrayList<>(DAMAGE_INDICATORS);
    }

    /** 命中标记（hitmarker）：返回 0~1 的生命周期进度，< 0 表示已消失。 */
    public static float hitMarkerProgress() {
        if (hitMarkerMs <= 0L) {
            return -1.0F;
        }
        long elapsed = now() - hitMarkerMs;
        if (elapsed < 0L || elapsed > HIT_MARKER_LIFETIME_MS) {
            return -1.0F;
        }
        return elapsed / (float) HIT_MARKER_LIFETIME_MS;
    }

    public static boolean isHitMarkerKill() {
        return hitMarkerKill;
    }

    public static void markHit(boolean kill) {
        hitMarkerMs = now();
        hitMarkerKill = kill;
    }

    // ==================== 生命周期 ====================

    /** 断开连接/切换世界时清理战斗数据。 */
    public static void clearCombatData() {
        health = 100.0F;
        maxHealth = 100.0F;
        armor = 0.0F;
        maxArmor = 0.0F;
        weaponName = "";
        fireMode = FireMode.AUTO;
        magazine = 0;
        reserve = 0;
        reloadStartMs = 0L;
        reloadDurationMs = 0L;
        selectedSlot = 0;
        weaponSlots = List.of();
        skillCooldown = new float[]{0.0F, 0.0F, 0.0F};
        skillReady = new boolean[]{true, true, true};
        ultimatePoints = 0;
        ultimateMax = 7;
        crosshairSpread = 0.0F;
        KILL_FEED.clear();
        DAMAGE_NUMBERS.clear();
        DAMAGE_INDICATORS.clear();
        hitMarkerMs = 0L;
        hitMarkerKill = false;
        buffs = List.of();
        clearServerSync();
    }
}
