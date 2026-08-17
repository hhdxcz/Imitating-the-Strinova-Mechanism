package com.hhdxcz.strinova.gameplay;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 飘飞运行时状态追踪：记录玩家是否已使用过飘飞，防止重复触发
public final class StrinovaFlyRuntime {
    private static final Set<UUID> USED_IN_AIR = ConcurrentHashMap.newKeySet();

    private StrinovaFlyRuntime() {
    }

    // 查询玩家是否已使用过飘飞
    public static boolean hasUsed(UUID playerId) {
        return playerId != null && USED_IN_AIR.contains(playerId);
    }

    // 标记玩家已使用飘飞
    public static void markUsed(UUID playerId) {
        if (playerId == null) {
            return;
        }
        USED_IN_AIR.add(playerId);
    }

    // 重置玩家的飘飞状态（落地后恢复）
    public static void reset(UUID playerId) {
        if (playerId == null) {
            return;
        }
        USED_IN_AIR.remove(playerId);
    }

    // 清除指定玩家的飘飞追踪
    public static void clearPlayer(UUID playerId) {
        USED_IN_AIR.remove(playerId);
    }

    // 清除所有玩家的飘飞追踪
    public static void clearAll() {
        USED_IN_AIR.clear();
    }
}