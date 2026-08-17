package com.hhdxcz.strinova.client;

import java.util.UUID;

/**
 * 碰撞箱预览飞行模式上下文。
 * 使用 ThreadLocal 在渲染线程中传递当前预览玩家的 UUID 和飞行状态，
 * 用于在模型渲染时识别是否需要应用飞行姿态。
 */
public final class StrinovaCollisionPreviewFlyContext {
    private static final ThreadLocal<UUID> PREVIEW_FLY_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> PREVIEW_ACTIVE_PLAYER = new ThreadLocal<>();

    private StrinovaCollisionPreviewFlyContext() {
    }

    /** 设置指定玩家的飞行预览状态。 */
    public static void setPreviewFly(UUID playerId, boolean previewFly) {
        if (!previewFly) {
            PREVIEW_FLY_PLAYER.remove();
            return;
        }
        PREVIEW_FLY_PLAYER.set(playerId);
    }

    /** 判断指定玩家是否处于飞行预览状态。 */
    public static boolean isPreviewFly(UUID playerId) {
        UUID cur = PREVIEW_FLY_PLAYER.get();
        return cur != null && cur.equals(playerId);
    }

    /** 标记指定玩家为预览活跃状态。 */
    public static void setPreviewActive(UUID playerId) {
        PREVIEW_ACTIVE_PLAYER.set(playerId);
    }

    /** 判断指定玩家是否处于预览活跃状态。 */
    public static boolean isPreviewActive(UUID playerId) {
        UUID cur = PREVIEW_ACTIVE_PLAYER.get();
        return cur != null && cur.equals(playerId);
    }

    /** 判断当前是否有活跃的预览玩家。 */
    public static boolean isPreviewActive() {
        return PREVIEW_ACTIVE_PLAYER.get() != null;
    }

    /** 清除所有预览上下文。 */
    public static void clear() {
        PREVIEW_FLY_PLAYER.remove();
        PREVIEW_ACTIVE_PLAYER.remove();
    }
}