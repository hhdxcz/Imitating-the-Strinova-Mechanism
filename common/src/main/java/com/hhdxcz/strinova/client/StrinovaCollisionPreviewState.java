package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 碰撞箱预览状态管理。
 * 以玩家 UUID 为键，存储各玩家在预览界面中的模式、调参、分区数据等临时状态。
 */
public final class StrinovaCollisionPreviewState {
    /** 预览模式枚举：SYNC（同步）和 FLY（飞行）。 */
    public enum Mode {
        SYNC,
        FLY
    }

    private static final ConcurrentHashMap<UUID, Mode> MODE_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, StrinovaCollisionBoxTuning.Tuning> PREVIEW_TUNING_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<StrinovaCompoundCollision.Part>> PREVIEW_PARTS_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> PREVIEW_SELECTED_PART_INDEX = new ConcurrentHashMap<>();

    private StrinovaCollisionPreviewState() {
    }

    /** 获取指定玩家的预览模式，默认为 SYNC。 */
    public static Mode getMode(UUID playerId) {
        if (playerId == null) {
            return Mode.SYNC;
        }
        Mode mode = MODE_BY_PLAYER.get(playerId);
        return mode == null ? Mode.SYNC : mode;
    }

    /** 设置指定玩家的预览模式。 */
    public static void setMode(UUID playerId, Mode mode) {
        if (playerId == null || mode == null) {
            return;
        }
        MODE_BY_PLAYER.put(playerId, mode);
    }

    /** 获取指定玩家的碰撞箱调参，优先返回预览数据，否则从持久化存储获取。 */
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

    /** 设置指定玩家的预览碰撞箱调参。 */
    public static void setTuning(UUID playerId, StrinovaCollisionBoxTuning.Tuning tuning) {
        if (playerId == null || tuning == null) {
            return;
        }
        PREVIEW_TUNING_BY_PLAYER.put(playerId, tuning);
    }

    /** 获取指定玩家的复合碰撞箱分区列表。 */
    public static List<StrinovaCompoundCollision.Part> getParts(UUID playerId) {
        if (playerId == null) {
            return StrinovaCompoundCollision.defaultSyncParts();
        }
        List<StrinovaCompoundCollision.Part> preview = PREVIEW_PARTS_BY_PLAYER.get(playerId);
        if (preview != null) {
            return preview;
        }
        return getMode(playerId) == Mode.FLY
                ? StrinovaCompoundCollision.getFlyParts(playerId)
                : StrinovaCompoundCollision.getSyncParts(playerId);
    }

    /** 设置指定玩家的预览分区列表（自动归一化）。 */
    public static void setParts(UUID playerId, List<StrinovaCompoundCollision.Part> parts) {
        if (playerId == null || parts == null) {
            return;
        }
        PREVIEW_PARTS_BY_PLAYER.put(playerId, StrinovaCompoundCollision.normalize(parts));
    }

    /** 获取指定玩家当前选中的分区索引。 */
    public static int getSelectedPartIndex(UUID playerId) {
        if (playerId == null) return 0;
        return PREVIEW_SELECTED_PART_INDEX.getOrDefault(playerId, 0);
    }

    /** 设置指定玩家当前选中的分区索引。 */
    public static void setSelectedPartIndex(UUID playerId, int index) {
        if (playerId == null || index < 0) return;
        PREVIEW_SELECTED_PART_INDEX.put(playerId, index);
    }

    /** 清除指定玩家的所有预览状态。 */
    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        MODE_BY_PLAYER.remove(playerId);
        PREVIEW_TUNING_BY_PLAYER.remove(playerId);
        PREVIEW_PARTS_BY_PLAYER.remove(playerId);
        PREVIEW_SELECTED_PART_INDEX.remove(playerId);
    }

    /** 获取指定玩家的碰撞类型。 */
    public static StrinovaCompoundCollision.CollisionType getCollisionType(UUID playerId) {
        return StrinovaCompoundCollision.getCollisionType(playerId);
    }

    /** 设置指定玩家的碰撞类型。 */
    public static void setCollisionType(UUID playerId, StrinovaCompoundCollision.CollisionType type) {
        StrinovaCompoundCollision.setCollisionType(playerId, type);
    }
}