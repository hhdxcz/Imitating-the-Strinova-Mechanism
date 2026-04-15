package com.hhdxcz.wa.gameplay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaAirJumpRuntime {

    private static final Map<UUID, Integer> USED = new ConcurrentHashMap<>();

    private WaAirJumpRuntime() {
    }

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

    public static int incrementUsed(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return USED.merge(playerId, 1, (a, b) -> {
            int next = a.intValue() + b.intValue();
            if (next < 0) {
                return 0;
            }
            return next;
        });
    }

    public static void reset(UUID playerId) {
        if (playerId == null) {
            return;
        }
        USED.remove(playerId);
    }
}
