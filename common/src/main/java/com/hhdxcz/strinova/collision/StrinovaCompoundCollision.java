package com.hhdxcz.strinova.collision;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 复合碰撞系统：支持多部件组合的碰撞箱，用于同步/飘飞状态的自定义碰撞检测
public final class StrinovaCompoundCollision {

    // 碰撞类型：SEGMENTED（分段碰撞）或 GENERIC（通用碰撞）
    public enum CollisionType {
        SEGMENTED,
        GENERIC
    }

    private static final ConcurrentHashMap<UUID, CollisionType> COLLISION_TYPES = new ConcurrentHashMap<>();

    // 获取玩家的碰撞类型
    public static CollisionType getCollisionType(UUID playerId) {
        if (playerId == null) return CollisionType.SEGMENTED;
        return COLLISION_TYPES.getOrDefault(playerId, CollisionType.SEGMENTED);
    }

    // 设置玩家的碰撞类型（SEGMENTED 时移除记录以节省内存）
    public static void setCollisionType(UUID playerId, CollisionType type) {
        if (playerId == null || type == null) return;
        if (type == CollisionType.SEGMENTED) {
            COLLISION_TYPES.remove(playerId);
        } else {
            COLLISION_TYPES.put(playerId, type);
        }
    }

    // 清除玩家的碰撞类型
    public static void clearCollisionType(UUID playerId) {
        if (playerId != null) COLLISION_TYPES.remove(playerId);
    }

    // 碰撞部件：定义单个碰撞子区域的位置、尺寸和关联的 YSM 骨骼名
    public record Part(
            String name,
            double offsetX, double offsetY, double offsetZ,
            double sizeX, double sizeY, double sizeZ,
             String ysmBoneName
    ) {
        // 将部件转换为世界坐标下的 AABB
        public AABB toAABB(double entityX, double entityY, double entityZ) {
            double hx = Math.max(0.01D, Math.abs(sizeX) * 0.5D);
            double hy = Math.max(0.01D, Math.abs(sizeY) * 0.5D);
            double hz = Math.max(0.01D, Math.abs(sizeZ) * 0.5D);
            double cx = entityX + offsetX;
            double cy = entityY + offsetY;
            double cz = entityZ + offsetZ;
            return new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
        }

        // 创建偏移量修改后的副本
        public Part withOffset(double ox, double oy, double oz) {
            return new Part(name, ox, oy, oz, sizeX, sizeY, sizeZ, ysmBoneName);
        }

        // 创建尺寸修改后的副本
        public Part withSize(double sx, double sy, double sz) {
            return new Part(name, offsetX, offsetY, offsetZ, Math.max(0.01D, sx), Math.max(0.01D, sy), Math.max(0.01D, sz), ysmBoneName);
        }
    }

    private static final ConcurrentHashMap<UUID, List<Part>> SYNC_PARTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<Part>> FLY_PARTS = new ConcurrentHashMap<>();

    private StrinovaCompoundCollision() {}

    // 默认同步状态碰撞部件（头、身、四肢）
    public static List<Part> defaultSyncParts() {
        return Collections.unmodifiableList(Arrays.asList(
                new Part("head",     0.0, 1.62, 0.0, 0.50, 0.50, 0.50, "Head"),
                new Part("body",     0.0, 1.00, 0.0, 0.45, 0.72, 0.25, "Body"),
                new Part("left_arm", -0.32, 1.05, 0.0, 0.17, 0.71, 0.22, "LeftArm"),
                new Part("right_arm", 0.32, 1.05, 0.0, 0.20, 0.71, 0.27, "RightArm"),
                new Part("left_leg", -0.10, 0.35, 0.0, 0.22, 0.70, 0.22, "LeftLeg"),
                new Part("right_leg", 0.10, 0.35, 0.0, 0.22, 0.70, 0.22, "RightLeg")
        ));
    }

    // 默认飘飞状态碰撞部件（扁平长条）
    public static List<Part> defaultFlyParts() {
        return Collections.unmodifiableList(Arrays.asList(
                new Part("fly_body", 0.0, 0.65, 0.0, 0.90, 0.12, 1.80, null)
        ));
    }

    // 下蹲时按比例下沉分体碰撞箱部件（纯姿态调整，不依赖 YSM 骨骼）
    public static List<Part> applyCrouchScale(List<Part> parts, Player player) {
        if (parts == null || parts.isEmpty() || player == null) return parts;
        if (!player.isCrouching()) return parts;
        double standing = player.getDimensions(Pose.STANDING).height;
        double crouching = player.getDimensions(Pose.CROUCHING).height;
        if (standing <= 0.0D) return parts;
        double scale = crouching / standing;
        List<Part> out = new ArrayList<>(parts.size());
        for (Part p : parts) {
            out.add(p.withOffset(p.offsetX(), p.offsetY() * scale, p.offsetZ())
                    .withSize(p.sizeX(), p.sizeY() * scale, p.sizeZ()));
        }
        return out;
    }

    // 获取玩家的同步碰撞部件
    public static List<Part> getSyncParts(UUID playerId) {
        if (playerId == null) return defaultSyncParts();

        if (getCollisionType(playerId) == CollisionType.GENERIC) return null;
        List<Part> p = SYNC_PARTS.get(playerId);
        return p == null || p.isEmpty() ? new ArrayList<>(defaultSyncParts()) : new ArrayList<>(p);
    }

    // 获取玩家的飘飞碰撞部件
    public static List<Part> getFlyParts(UUID playerId) {
        if (playerId == null) return defaultFlyParts();
        List<Part> p = FLY_PARTS.get(playerId);
        return p == null || p.isEmpty() ? new ArrayList<>(defaultFlyParts()) : new ArrayList<>(p);
    }

    // 检查玩家是否有自定义同步碰撞
    public static boolean hasCustomSync(UUID playerId) {
        return playerId != null && SYNC_PARTS.containsKey(playerId);
    }

    // 检查玩家是否有自定义飘飞碰撞
    public static boolean hasCustomFly(UUID playerId) {
        return playerId != null && FLY_PARTS.containsKey(playerId);
    }

    // 设置玩家的同步碰撞部件
    public static void setSyncParts(UUID playerId, List<Part> parts) {
        if (playerId == null) return;
        if (parts == null || parts.isEmpty()) { SYNC_PARTS.remove(playerId); return; }
        SYNC_PARTS.put(playerId, normalize(parts));
    }

    // 设置玩家的飘飞碰撞部件
    public static void setFlyParts(UUID playerId, List<Part> parts) {
        if (playerId == null) return;
        if (parts == null || parts.isEmpty()) { FLY_PARTS.remove(playerId); return; }
        FLY_PARTS.put(playerId, normalize(parts));
    }

    // 清除指定玩家的碰撞部件
    public static void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        SYNC_PARTS.remove(playerId);
        FLY_PARTS.remove(playerId);
    }

    // 清除所有玩家的碰撞部件
    public static void clearAll() {
        SYNC_PARTS.clear();
        FLY_PARTS.clear();
    }

    // 计算多个部件的包围盒（并集）
    public static AABB unionAABB(List<Part> parts, double x, double y, double z) {
        if (parts == null || parts.isEmpty()) return new AABB(x, y, z, x, y, z);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Part p : parts) {
            AABB b = p.toAABB(x, y, z);
            minX = Math.min(minX, b.minX);
            minY = Math.min(minY, b.minY);
            minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX);
            maxY = Math.max(maxY, b.maxY);
            maxZ = Math.max(maxZ, b.maxZ);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // 计算飘飞状态下部件的包围盒（考虑偏航角旋转）
    public static AABB unionAABBFly(List<Part> parts, double x, double y, double z, float yawRad) {
        if (parts == null || parts.isEmpty()) return new AABB(x, y, z, x, y, z);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double absCos = Math.abs(cos);
        double absSin = Math.abs(sin);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Part p : parts) {
            double hx = Math.max(0.01D, Math.abs(p.sizeX()) * 0.5D);
            double hy = Math.max(0.01D, Math.abs(p.sizeY()) * 0.5D);
            double hz = Math.max(0.01D, Math.abs(p.sizeZ()) * 0.5D);

            double rx = p.offsetX() * cos - p.offsetZ() * sin;
            double rz = p.offsetX() * sin + p.offsetZ() * cos;
            double cx = x + rx;
            double cy = y + p.offsetY();
            double cz = z + rz;

            double whx = hx * absCos + hz * absSin;
            double whz = hx * absSin + hz * absCos;
            minX = Math.min(minX, cx - whx); maxX = Math.max(maxX, cx + whx);
            minY = Math.min(minY, cy - hy);   maxY = Math.max(maxY, cy + hy);
            minZ = Math.min(minZ, cz - whz); maxZ = Math.max(maxZ, cz + whz);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // 计算部件绕偏航角旋转后的包围盒（与渲染线框一致的旋转方向）
    public static AABB unionAABBYaw(List<Part> parts, double x, double y, double z, float yawRad) {
        if (parts == null || parts.isEmpty()) return new AABB(x, y, z, x, y, z);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double absCos = Math.abs(cos);
        double absSin = Math.abs(sin);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Part p : parts) {
            double hx = Math.max(0.01D, Math.abs(p.sizeX()) * 0.5D);
            double hy = Math.max(0.01D, Math.abs(p.sizeY()) * 0.5D);
            double hz = Math.max(0.01D, Math.abs(p.sizeZ()) * 0.5D);

            double rx = -p.offsetX() * cos - p.offsetZ() * sin;
            double rz = -p.offsetX() * sin + p.offsetZ() * cos;
            double cx = x + rx;
            double cy = y + p.offsetY();
            double cz = z + rz;

            double whx = hx * absCos + hz * absSin;
            double whz = hx * absSin + hz * absCos;
            minX = Math.min(minX, cx - whx); maxX = Math.max(maxX, cx + whx);
            minY = Math.min(minY, cy - hy);   maxY = Math.max(maxY, cy + hy);
            minZ = Math.min(minZ, cz - whz); maxZ = Math.max(maxZ, cz + whz);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // 检测部件集合是否与目标 AABB 相交
    public static boolean intersectsAny(List<Part> parts, AABB target, double x, double y, double z) {
        if (parts == null || parts.isEmpty()) return false;
        for (Part p : parts) {
            if (p.toAABB(x, y, z).intersects(target)) return true;
        }
        return false;
    }

    // 规范化部件参数（限制范围、四舍五入）
    public static List<Part> normalize(List<Part> parts) {
        List<Part> out = new ArrayList<>(parts.size());
        for (Part p : parts) {
            out.add(new Part(p.name,
                    clamp(p.offsetX), clamp(p.offsetY), clamp(p.offsetZ),
                    clampSize(p.sizeX), clampSize(p.sizeY), clampSize(p.sizeZ),
                    p.ysmBoneName));
        }
        return out;
    }

    // 将偏移值限制在 [-8, 8] 并保留两位小数
    private static double clamp(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(-8.0, Math.min(8.0, Math.round(v * 100.0) / 100.0));
    }

    // 将尺寸值限制在 [0.01, 8.0]
    private static double clampSize(double v) {
        return Math.max(0.01, Math.min(8.0, clamp(v)));
    }
}