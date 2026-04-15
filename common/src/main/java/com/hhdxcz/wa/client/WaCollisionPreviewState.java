package com.hhdxcz.wa.client;

import com.hhdxcz.wa.collision.WaCollisionBoxTuning;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaCollisionPreviewState {
    public enum Mode {
        SYNC,
        FLY
    }

    private static final ConcurrentHashMap<UUID, Mode> MODE_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, WaCollisionBoxTuning.Tuning> PREVIEW_TUNING_BY_PLAYER = new ConcurrentHashMap<>();

    private WaCollisionPreviewState() {
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

    public static WaCollisionBoxTuning.Tuning getTuning(UUID playerId) {
        if (playerId == null) {
            return WaCollisionBoxTuning.getSync(null);
        }
        WaCollisionBoxTuning.Tuning preview = PREVIEW_TUNING_BY_PLAYER.get(playerId);
        if (preview != null) {
            return preview;
        }
        return getMode(playerId) == Mode.FLY ? WaCollisionBoxTuning.getFly(playerId) : WaCollisionBoxTuning.getSync(playerId);
    }

    public static void setTuning(UUID playerId, WaCollisionBoxTuning.Tuning tuning) {
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
