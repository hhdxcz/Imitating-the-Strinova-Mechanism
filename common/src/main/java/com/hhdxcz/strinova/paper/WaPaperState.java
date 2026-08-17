package com.hhdxcz.strinova.paper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理玩家"纸片化"状态，包括控纸、贴墙、飞行三种模式的状态存储与查询。
 * 使用线程安全的并发集合，支持服务端和客户端共用。
 */
public final class WaPaperState {

    /** 贴墙时玩家与墙面的偏移量，用于避免渲染穿插。 */
    public static final double WALL_GAP = -0.002D;

    private static final Set<UUID> CTRL_PAPER_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> WALL_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FLY_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Double> WALL_Y = new ConcurrentHashMap<>();
    private static final Map<UUID, WallPlane> WALL_PLANE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WALL_KEEP_LESS = new ConcurrentHashMap<>();
    private static final Set<UUID> WALL_FRONT_TO_CAMERA = ConcurrentHashMap.newKeySet();

    private WaPaperState() {
    }

    /**
     * 设置玩家的控纸状态。
     */
    public static void setPaper(UUID playerId, boolean paper) {
        if (playerId == null) {
            return;
        }
        if (paper) {
            CTRL_PAPER_PLAYERS.add(playerId);
        } else {
            CTRL_PAPER_PLAYERS.remove(playerId);
        }
    }

    /**
     * 切换玩家的贴墙状态，开启时清除旧的面数据。
     */
    public static void toggleWall(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (WALL_PLAYERS.contains(playerId)) {
            WALL_PLAYERS.remove(playerId);
            WALL_Y.remove(playerId);
            WALL_PLANE.remove(playerId);
            WALL_KEEP_LESS.remove(playerId);
            WALL_FRONT_TO_CAMERA.remove(playerId);
        } else {
            WALL_PLAYERS.add(playerId);
            WALL_Y.remove(playerId);
            WALL_PLANE.remove(playerId);
            WALL_KEEP_LESS.remove(playerId);
            WALL_FRONT_TO_CAMERA.remove(playerId);
        }
    }

    /**
     * 设置玩家的贴墙状态，开启时清除旧的面数据。
     */
    public static void setWall(UUID playerId, boolean wall) {
        if (playerId == null) {
            return;
        }
        if (wall) {
            WALL_PLAYERS.add(playerId);
            WALL_Y.remove(playerId);
            WALL_PLANE.remove(playerId);
            WALL_KEEP_LESS.remove(playerId);
            WALL_FRONT_TO_CAMERA.remove(playerId);
        } else {
            WALL_PLAYERS.remove(playerId);
            WALL_Y.remove(playerId);
            WALL_PLANE.remove(playerId);
            WALL_KEEP_LESS.remove(playerId);
            WALL_FRONT_TO_CAMERA.remove(playerId);
        }
    }

    /**
     * 记录玩家贴墙时的锚点 Y 坐标。
     */
    public static void setWallAnchorY(UUID playerId, double y) {
        if (playerId == null) {
            return;
        }
        WALL_Y.put(playerId, y);
    }

    /**
     * 获取玩家贴墙时的锚点 Y 坐标。
     */
    public static Double getWallAnchorY(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return WALL_Y.get(playerId);
    }

    /**
     * 记录玩家贴墙的面信息（轴向和坐标值）。
     */
    public static void setWallPlane(UUID playerId, boolean axisX, double value) {
        if (playerId == null) {
            return;
        }
        WALL_PLANE.put(playerId, new WallPlane(axisX, value));
    }

    /**
     * 获取玩家贴墙的面信息。
     */
    public static WallPlane getWallPlane(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return WALL_PLANE.get(playerId);
    }

    /**
     * 设置贴墙时玩家的朝向偏好（保留较小坐标侧）。
     */
    public static void setWallKeepLess(UUID playerId, boolean keepLess) {
        if (playerId == null) {
            return;
        }
        WALL_KEEP_LESS.put(playerId, keepLess);
    }

    /**
     * 获取贴墙时玩家的朝向偏好。
     */
    public static Boolean getWallKeepLess(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return WALL_KEEP_LESS.get(playerId);
    }

    /**
     * 设置玩家的飞行状态。
     */
    public static void setFly(UUID playerId, boolean fly) {
        if (playerId == null) {
            return;
        }
        if (fly) {
            FLY_PLAYERS.add(playerId);
        } else {
            FLY_PLAYERS.remove(playerId);
        }
    }

    /**
     * 查询玩家是否处于控纸状态。
     */
    public static boolean isCtrlPaper(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return CTRL_PAPER_PLAYERS.contains(playerId);
    }

    /**
     * 查询玩家是否处于纸片化状态（控纸且未飞行，或正在贴墙）。
     */
    public static boolean isPaper(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean ctrlPaper = isCtrlPaper(playerId)
                && !FLY_PLAYERS.contains(playerId);
        return ctrlPaper || WALL_PLAYERS.contains(playerId);
    }

    /**
     * 查询玩家是否处于贴墙状态。
     */
    public static boolean isWall(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return WALL_PLAYERS.contains(playerId);
    }

    /**
     * 设置贴墙时玩家是否面向摄像机方向。
     */
    public static void setWallFrontToCamera(UUID playerId, boolean frontToCamera) {
        if (playerId == null) {
            return;
        }
        if (frontToCamera) {
            WALL_FRONT_TO_CAMERA.add(playerId);
        } else {
            WALL_FRONT_TO_CAMERA.remove(playerId);
        }
    }

    /**
     * 查询贴墙时玩家是否面向摄像机方向。
     */
    public static boolean isWallFrontToCamera(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return WALL_FRONT_TO_CAMERA.contains(playerId);
    }

    /**
     * 查询玩家是否处于飞行状态。
     */
    public static boolean isFly(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return FLY_PLAYERS.contains(playerId);
    }

    /**
     * 清除指定玩家的所有状态数据。
     */
    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        CTRL_PAPER_PLAYERS.remove(playerId);
        FLY_PLAYERS.remove(playerId);
        WALL_PLAYERS.remove(playerId);
        WALL_Y.remove(playerId);
        WALL_PLANE.remove(playerId);
        WALL_KEEP_LESS.remove(playerId);
        WALL_FRONT_TO_CAMERA.remove(playerId);
    }

    /**
     * 清除所有玩家的全部状态数据。
     */
    public static void clearAll() {
        CTRL_PAPER_PLAYERS.clear();
        FLY_PLAYERS.clear();
        WALL_PLAYERS.clear();
        WALL_Y.clear();
        WALL_PLANE.clear();
        WALL_KEEP_LESS.clear();
        WALL_FRONT_TO_CAMERA.clear();
    }

    /**
     * 描述贴墙面的信息：轴向（X 轴或 Z 轴）以及墙面坐标值。
     */
    public static final class WallPlane {
        public final boolean axisX;
        public final double value;

        public WallPlane(boolean axisX, double value) {
            this.axisX = axisX;
            this.value = value;
        }
    }
}