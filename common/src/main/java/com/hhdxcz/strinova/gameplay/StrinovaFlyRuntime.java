package com.hhdxcz.strinova.gameplay;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StrinovaFlyRuntime {
    private static final Set<UUID> USED_IN_AIR = ConcurrentHashMap.newKeySet();

    private StrinovaFlyRuntime() {
    }

    public static boolean hasUsed(UUID playerId) {
        return playerId != null && USED_IN_AIR.contains(playerId);
    }

    public static void markUsed(UUID playerId) {
        if (playerId == null) {
            return;
        }
        USED_IN_AIR.add(playerId);
    }

    public static void reset(UUID playerId) {
        if (playerId == null) {
            return;
        }
        USED_IN_AIR.remove(playerId);
    }
}
