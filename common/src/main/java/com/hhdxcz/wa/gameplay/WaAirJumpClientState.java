package com.hhdxcz.wa.gameplay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaAirJumpClientState {

    private static final Map<UUID, Integer> EXTRA_JUMPS = new ConcurrentHashMap<>();

    private WaAirJumpClientState() {
    }

    public static int getExtraJumps(UUID playerId) {
        if (playerId == null) {
            return WaAirJumpSettings.DEFAULT_EXTRA_JUMPS;
        }
        Integer v = EXTRA_JUMPS.get(playerId);
        if (v == null) {
            return WaAirJumpSettings.DEFAULT_EXTRA_JUMPS;
        }
        int raw = v.intValue();
        if (raw == WaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
            return WaAirJumpSettings.INFINITE_EXTRA_JUMPS;
        }
        return Math.max(0, raw);
    }

    public static void setExtraJumps(UUID playerId, int extraJumps) {
        if (playerId == null) {
            return;
        }
        int v;
        if (extraJumps == WaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
            v = WaAirJumpSettings.INFINITE_EXTRA_JUMPS;
        } else {
            v = Math.max(0, Math.min(WaAirJumpSettings.MAX_EXTRA_JUMPS, extraJumps));
        }
        EXTRA_JUMPS.put(playerId, v);
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        EXTRA_JUMPS.remove(playerId);
    }

    public static void clearAll() {
        EXTRA_JUMPS.clear();
    }
}
