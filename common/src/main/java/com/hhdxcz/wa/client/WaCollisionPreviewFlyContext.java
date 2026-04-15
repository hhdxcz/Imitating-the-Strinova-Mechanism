package com.hhdxcz.wa.client;

import java.util.UUID;

/**
 * 客户端预览渲染上下文：让预览“像飘飞一样”触发渲染/碰撞盒变换。
 * <p>
 * 仅在预览渲染期间生效，不写 WaPaperState，避免触发 WaNetwork 回写。
 */
public final class WaCollisionPreviewFlyContext {
    private static final ThreadLocal<UUID> PREVIEW_FLY_PLAYER = new ThreadLocal<>();

    private WaCollisionPreviewFlyContext() {
    }

    public static void setPreviewFly(UUID playerId, boolean previewFly) {
        if (!previewFly) {
            clear();
            return;
        }
        PREVIEW_FLY_PLAYER.set(playerId);
    }

    public static boolean isPreviewFly(UUID playerId) {
        UUID cur = PREVIEW_FLY_PLAYER.get();
        return cur != null && cur.equals(playerId);
    }

    public static void clear() {
        PREVIEW_FLY_PLAYER.remove();
    }
}
