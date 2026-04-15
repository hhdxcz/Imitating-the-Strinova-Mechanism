package com.hhdxcz.wa.collision;

import com.hhdxcz.wa.config.WaCommonConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaCollisionBoxTuning {

    public record Tuning(double offsetX, double offsetY, double offsetZ, double sizeX, double sizeY, double sizeZ) {
    }

    private static final double LIMIT_MIN = -8.0D;
    private static final double LIMIT_MAX = 8.0D;
    private static final double FLY_BASE_MIN_Y_OFFSET = 0.0D;
    private static final double FLY_WORLD_Y_OFFSET = 1.55D;

    private static final ConcurrentHashMap<UUID, Tuning> SYNC = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Tuning> FLY = new ConcurrentHashMap<>();

    private WaCollisionBoxTuning() {
    }

    public static Tuning getSync(UUID playerId) {
        if (playerId == null) {
            return WaCommonConfig.getDefaultSyncTuning();
        }
        Tuning t = SYNC.get(playerId);
        return t == null ? WaCommonConfig.getDefaultSyncTuning() : t;
    }

    public static Tuning getCustomSync(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return SYNC.get(playerId);
    }

    public static Tuning getFly(UUID playerId) {
        if (playerId == null) {
            return WaCommonConfig.getDefaultFlyTuning();
        }
        Tuning t = FLY.get(playerId);
        return t == null ? WaCommonConfig.getDefaultFlyTuning() : t;
    }

    public static double getFlyBaseMinYOffset() {
        return FLY_BASE_MIN_Y_OFFSET;
    }

    public static double getFlyWorldYOffset() {
        return FLY_WORLD_Y_OFFSET;
    }

    public static Tuning getCustomFly(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return FLY.get(playerId);
    }

    public static boolean setSyncOffset(UUID playerId, double x, double y, double z) {
        return updateOffset(SYNC, playerId, x, y, z);
    }

    public static boolean setSyncSize(UUID playerId, double x, double y, double z) {
        return updateSize(SYNC, playerId, x, y, z);
    }

    public static boolean setFlyOffset(UUID playerId, double x, double y, double z) {
        return updateOffset(FLY, playerId, x, y, z);
    }

    public static boolean setFlySize(UUID playerId, double x, double y, double z) {
        return updateSize(FLY, playerId, x, y, z);
    }

    public static double normalizeForUi(double v) {
        return normalize(v);
    }

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
        Tuning base = map == SYNC ? WaCommonConfig.getDefaultSyncTuning() : WaCommonConfig.getDefaultFlyTuning();
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

    private static boolean updateSize(ConcurrentHashMap<UUID, Tuning> map, UUID playerId, double x, double y, double z) {
        if (playerId == null) {
            return false;
        }
        x = normalize(x);
        y = normalize(y);
        z = normalize(z);
        Tuning prev = map.get(playerId);
        Tuning base = map == SYNC ? WaCommonConfig.getDefaultSyncTuning() : WaCommonConfig.getDefaultFlyTuning();
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

    private static boolean equals(Tuning a, Tuning b) {
        return a.offsetX() == b.offsetX()
                && a.offsetY() == b.offsetY()
                && a.offsetZ() == b.offsetZ()
                && a.sizeX() == b.sizeX()
                && a.sizeY() == b.sizeY()
                && a.sizeZ() == b.sizeZ();
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SYNC.remove(playerId);
        FLY.remove(playerId);
    }

    public static void clearAll() {
        SYNC.clear();
        FLY.clear();
    }
}
