package com.hhdxcz.strinova.render;

// 调试渲染上下文：通过 ThreadLocal 追踪命中盒渲染的嵌套深度
public final class StrinovaDebugRenderContext {
    private static final ThreadLocal<Integer> HITBOX_DEPTH = ThreadLocal.withInitial(() -> 0);

    private StrinovaDebugRenderContext() {
    }

    // 进入命中盒渲染，返回进入前的深度
    public static int enterHitbox() {
        int depth = HITBOX_DEPTH.get();
        HITBOX_DEPTH.set(depth + 1);
        return depth;
    }

    // 退出命中盒渲染，返回退出后的深度
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

    // 判断当前是否在命中盒渲染中
    public static boolean isHitbox() {
        return HITBOX_DEPTH.get() > 0;
    }

    // 重置命中盒深度
    public static void resetHitbox() {
        HITBOX_DEPTH.set(0);
    }
}