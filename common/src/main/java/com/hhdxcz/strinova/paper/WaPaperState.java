package com.hhdxcz.strinova.paper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaPaperState {

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
        }
    }

    public static void setWall(UUID playerId, boolean wall) {
        if (playerId == null) {
            return;
        }
        if (wall) {
            WALL_PLAYERS.add(playerId);
        } else {
            WALL_PLAYERS.remove(playerId);
            WALL_Y.remove(playerId);
            WALL_PLANE.remove(playerId);
            WALL_KEEP_LESS.remove(playerId);
            WALL_FRONT_TO_CAMERA.remove(playerId);
        }
    }

    public static void setWallAnchorY(UUID playerId, double y) {
        if (playerId == null) {
            return;
        }
        WALL_Y.put(playerId, y);
    }

    public static Double getWallAnchorY(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return WALL_Y.get(playerId);
    }

    public static void setWallPlane(UUID playerId, boolean axisX, double value) {
        if (playerId == null) {
            return;
        }
        WALL_PLANE.put(playerId, new WallPlane(axisX, value));
    }

    public static WallPlane getWallPlane(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return WALL_PLANE.get(playerId);
    }

    public static void setWallKeepLess(UUID playerId, boolean keepLess) {
        if (playerId == null) {
            return;
        }
        WALL_KEEP_LESS.put(playerId, keepLess);
    }

    public static Boolean getWallKeepLess(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return WALL_KEEP_LESS.get(playerId);
    }

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

    public static boolean isCtrlPaper(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return CTRL_PAPER_PLAYERS.contains(playerId);
    }

    public static boolean isPaper(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean ctrlPaper = isCtrlPaper(playerId)
                && !FLY_PLAYERS.contains(playerId);
        return ctrlPaper || WALL_PLAYERS.contains(playerId);
    }

    public static boolean isWall(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return WALL_PLAYERS.contains(playerId);
    }

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

    public static boolean isWallFrontToCamera(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return WALL_FRONT_TO_CAMERA.contains(playerId);
    }

    public static boolean isFly(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return FLY_PLAYERS.contains(playerId);
    }

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

    public static void clearAll() {
        CTRL_PAPER_PLAYERS.clear();
        FLY_PLAYERS.clear();
        WALL_PLAYERS.clear();
        WALL_Y.clear();
        WALL_PLANE.clear();
        WALL_KEEP_LESS.clear();
        WALL_FRONT_TO_CAMERA.clear();
    }

    public static final class WallPlane {
        public final boolean axisX;
        public final double value;

        public WallPlane(boolean axisX, double value) {
            this.axisX = axisX;
            this.value = value;
        }
    }
}
