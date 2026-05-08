package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StrinovaCollisionPreviewState {
    public enum Mode {
        SYNC,
        FLY
    }

    private static final ConcurrentHashMap<UUID, Mode> MODE_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, StrinovaCollisionBoxTuning.Tuning> PREVIEW_TUNING_BY_PLAYER = new ConcurrentHashMap<>();

    private StrinovaCollisionPreviewState() {
    }

    public static Mode getMode(UUID playerId) {
        if (playerId == null) {
            return Mode.SYNC;
        }
        Mode mode = MODE_BY_PLAYER.get(playerId);
        return mode == null ? Mode.SYNC : mode;
    }

    public static void setMode(UUID playerId, Mode mode) {
        if (playerId == null || mode == null) {
            return;
        }
        MODE_BY_PLAYER.put(playerId, mode);
    }

    public static StrinovaCollisionBoxTuning.Tuning getTuning(UUID playerId) {
        if (playerId == null) {
            return StrinovaCollisionBoxTuning.getSync(null);
        }
        StrinovaCollisionBoxTuning.Tuning preview = PREVIEW_TUNING_BY_PLAYER.get(playerId);
        if (preview != null) {
            return preview;
        }
        return getMode(playerId) == Mode.FLY ? StrinovaCollisionBoxTuning.getFly(playerId) : StrinovaCollisionBoxTuning.getSync(playerId);
    }

    public static void setTuning(UUID playerId, StrinovaCollisionBoxTuning.Tuning tuning) {
        if (playerId == null || tuning == null) {
            return;
        }
        PREVIEW_TUNING_BY_PLAYER.put(playerId, tuning);
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        MODE_BY_PLAYER.remove(playerId);
        PREVIEW_TUNING_BY_PLAYER.remove(playerId);
    }
}
