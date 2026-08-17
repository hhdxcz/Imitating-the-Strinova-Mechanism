package com.hhdxcz.strinova.gameplay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 空中段跳运行时计数器：追踪每个玩家当前已使用的空中跳跃次数
public final class StrinovaAirJumpRuntime {

    private static final Map<UUID, Integer> USED = new ConcurrentHashMap<>();

    private StrinovaAirJumpRuntime() {
    }

    // 获取玩家已使用的空中跳跃次数
    public static int getUsed(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Integer v = USED.get(playerId);
        if (v == null) {
            return 0;
        }
        return Math.max(0, v.intValue());
    }

    // 递增玩家的空中跳跃使用计数
    public static int incrementUsed(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        int jumps = USED.getOrDefault(playerId, 0);
        if (jumps < Integer.MAX_VALUE) {
            USED.put(playerId, jumps + 1);
        }
        return USED.getOrDefault(playerId, 0);
    }

    // 重置玩家的空中跳跃计数（落地后恢复）
    public static void reset(UUID playerId) {
        if (playerId == null) {
            return;
        }
        USED.remove(playerId);
    }

    // 清除指定玩家的计数
    public static void clearPlayer(UUID playerId) {
        USED.remove(playerId);
    }

    // 清除所有玩家的计数
    public static void clearAll() {
        USED.clear();
    }
}