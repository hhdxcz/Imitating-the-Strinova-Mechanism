package com.hhdxcz.strinova.gameplay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 客户端空中段跳状态：缓存服务端同步的玩家额外跳跃次数
public final class StrinovaAirJumpClientState {

    private static final Map<UUID, Integer> EXTRA_JUMPS = new ConcurrentHashMap<>();

    private StrinovaAirJumpClientState() {
    }

    // 获取玩家的额外跳跃次数，未同步则返回默认值
    public static int getExtraJumps(UUID playerId) {
        if (playerId == null) {
            return StrinovaAirJumpSettings.DEFAULT_EXTRA_JUMPS;
        }
        Integer v = EXTRA_JUMPS.get(playerId);
        if (v == null) {
            return StrinovaAirJumpSettings.DEFAULT_EXTRA_JUMPS;
        }
        int raw = v.intValue();
        if (raw == StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
            return StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS;
        }
        return Math.max(0, raw);
    }

    // 设置玩家的额外跳跃次数（由服务端网络包同步）
    public static void setExtraJumps(UUID playerId, int extraJumps) {
        if (playerId == null) {
            return;
        }
        int v;
        if (extraJumps == StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
            v = StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS;
        } else {
            v = Math.max(0, Math.min(StrinovaAirJumpSettings.MAX_EXTRA_JUMPS, extraJumps));
        }
        EXTRA_JUMPS.put(playerId, v);
    }

    // 清除指定玩家的状态
    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        EXTRA_JUMPS.remove(playerId);
    }

    // 清除所有玩家的状态
    public static void clearAll() {
        EXTRA_JUMPS.clear();
    }
}