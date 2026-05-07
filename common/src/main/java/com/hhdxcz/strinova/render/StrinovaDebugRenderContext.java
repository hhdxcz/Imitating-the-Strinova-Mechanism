package com.hhdxcz.strinova.render;

public final class StrinovaDebugRenderContext {
    private static final ThreadLocal<Integer> HITBOX_DEPTH = ThreadLocal.withInitial(() -> 0);

    private StrinovaDebugRenderContext() {
    }

    public static int enterHitbox() {
        int depth = HITBOX_DEPTH.get();
        HITBOX_DEPTH.set(depth + 1);
        return depth;
    }

    public static int exitHitbox() {
        int depth = HITBOX_DEPTH.get();
        if (depth <= 0) {
            HITBOX_DEPTH.set(0);
            return 0;
        }
        int next = depth - 1;
        HITBOX_DEPTH.set(next);
        return next;
    }

    public static boolean isHitbox() {
        return HITBOX_DEPTH.get() > 0;
    }

    public static void resetHitbox() {
        HITBOX_DEPTH.set(0);
    }
}
