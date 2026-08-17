package com.hhdxcz.strinova.collision;

import com.hhdxcz.strinova.config.StrinovaCommonConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 碰撞箱微调管理器：允许按玩家自定义同步/飘飞碰撞箱的偏移和尺寸
public final class StrinovaCollisionBoxTuning {

    // 碰撞箱调参记录
    public record Tuning(double offsetX, double offsetY, double offsetZ, double sizeX, double sizeY, double sizeZ) {
    }

    private static final double LIMIT_MIN = -8.0D;
    private static final double LIMIT_MAX = 8.0D;
    private static final double FLY_BASE_MIN_Y_OFFSET = 0.0D;
    private static final double FLY_WORLD_Y_OFFSET = 1.55D;

    private static final ConcurrentHashMap<UUID, Tuning> SYNC = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Tuning> FLY = new ConcurrentHashMap<>();

    private StrinovaCollisionBoxTuning() {
    }

    // 获取玩家的同步碰撞箱调参（含默认值）
    public static Tuning getSync(UUID playerId) {
        if (playerId == null) {
            return StrinovaCommonConfig.getDefaultSyncTuning();
        }
        Tuning t = SYNC.get(playerId);
        return t == null ? StrinovaCommonConfig.getDefaultSyncTuning() : t;
    }

    // 获取玩家的自定义同步调参（无自定义则返回 null）
    public static Tuning getCustomSync(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return SYNC.get(playerId);
    }

    // 获取玩家的飘飞碰撞箱调参（含默认值）
    public static Tuning getFly(UUID playerId) {
        if (playerId == null) {
            return StrinovaCommonConfig.getDefaultFlyTuning();
        }
        Tuning t = FLY.get(playerId);
        return t == null ? StrinovaCommonConfig.getDefaultFlyTuning() : t;
    }

    // 飘飞碰撞箱基础 Y 轴偏移
    public static double getFlyBaseMinYOffset() {
        return FLY_BASE_MIN_Y_OFFSET;
    }

    // 飘飞状态下的世界 Y 轴偏移量
    public static double getFlyWorldYOffset() {
        return FLY_WORLD_Y_OFFSET;
    }

    // 获取玩家的自定义飘飞调参（无自定义则返回 null）
    public static Tuning getCustomFly(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return FLY.get(playerId);
    }

    // 设置同步碰撞箱位置偏移
    public static boolean setSyncOffset(UUID playerId, double x, double y, double z) {
        return updateOffset(SYNC, playerId, x, y, z);
    }

    // 设置同步碰撞箱尺寸增量
    public static boolean setSyncSize(UUID playerId, double x, double y, double z) {
        return updateSize(SYNC, playerId, x, y, z);
    }

    // 设置飘飞碰撞箱位置偏移
    public static boolean setFlyOffset(UUID playerId, double x, double y, double z) {
        return updateOffset(FLY, playerId, x, y, z);
    }

    // 设置飘飞碰撞箱尺寸增量
    public static boolean setFlySize(UUID playerId, double x, double y, double z) {
        return updateSize(FLY, playerId, x, y, z);
    }

    // 规范化值用于 UI 显示
    public static double normalizeForUi(double v) {
        return normalize(v);
    }

    // 更新偏移量（同步模式下 Y 轴偏移限制为 ≤0）
    private static boolean updateOffset(ConcurrentHashMap<UUID, Tuning> map, UUID playerId, double x, double y, double z) {
        if (playerId == null) {
            return false;
        }
        x = normalize(x);
        y = normalize(y);
        z = normalize(z);
        if (map == SYNC) {
            y = Math.min(0.0D, y);
        }
        Tuning prev = map.get(playerId);
        Tuning base = map == SYNC ? StrinovaCommonConfig.getDefaultSyncTuning() : StrinovaCommonConfig.getDefaultFlyTuning();
        double sx = prev == null ? base.sizeX() : prev.sizeX();
        double sy = prev == null ? base.sizeY() : prev.sizeY();
        double sz = prev == null ? base.sizeZ() : prev.sizeZ();
        Tuning next = new Tuning(x, y, z, sx, sy, sz);
        if (prev != null && equals(prev, next)) {
            return false;
        }
        map.put(playerId, next);
        if (equals(next, base)) {
            map.remove(playerId, next);
        }
        return true;
    }

    // 更新尺寸增量
    private static boolean updateSize(ConcurrentHashMap<UUID, Tuning> map, UUID playerId, double x, double y, double z) {
        if (playerId == null) {
            return false;
        }
        x = normalize(x);
        y = normalize(y);
        z = normalize(z);
        Tuning prev = map.get(playerId);
        Tuning base = map == SYNC ? StrinovaCommonConfig.getDefaultSyncTuning() : StrinovaCommonConfig.getDefaultFlyTuning();
        double ox = prev == null ? base.offsetX() : prev.offsetX();
        double oy = prev == null ? base.offsetY() : prev.offsetY();
        double oz = prev == null ? base.offsetZ() : prev.offsetZ();
        Tuning next = new Tuning(ox, oy, oz, x, y, z);
        if (prev != null && equals(prev, next)) {
            return false;
        }
        map.put(playerId, next);
        if (equals(next, base)) {
            map.remove(playerId, next);
        }
        return true;
    }

    // 规范化数值：限制范围、保留两位小数、去除 -0.0
    private static double normalize(double v) {
        if (!Double.isFinite(v)) {
            return 0.0D;
        }
        if (v < LIMIT_MIN) {
            v = LIMIT_MIN;
        } else if (v > LIMIT_MAX) {
            v = LIMIT_MAX;
        }
        v = Math.round(v * 100.0D) / 100.0D;
        if (v < LIMIT_MIN) {
            v = LIMIT_MIN;
        } else if (v > LIMIT_MAX) {
            v = LIMIT_MAX;
        }
        return v == -0.0D ? 0.0D : v;
    }

    // 比较两个调参记录是否相等
    private static boolean equals(Tuning a, Tuning b) {
        return a.offsetX() == b.offsetX()
                && a.offsetY() == b.offsetY()
                && a.offsetZ() == b.offsetZ()
                && a.sizeX() == b.sizeX()
                && a.sizeY() == b.sizeY()
                && a.sizeZ() == b.sizeZ();
    }

    // 清除指定玩家的调参
    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SYNC.remove(playerId);
        FLY.remove(playerId);
    }

    // 清除所有调参
    public static void clearAll() {
        SYNC.clear();
        FLY.clear();
    }
}