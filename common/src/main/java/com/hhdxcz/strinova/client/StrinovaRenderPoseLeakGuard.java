package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.mixin.client.PoseStackAccessor;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Deque;

public final class StrinovaRenderPoseLeakGuard {
    private static final ThreadLocal<PoseStack> LAST_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Integer> LEAK_PUSH_COUNT = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> PAPER_TRANSFORM_DEPTH = ThreadLocal.withInitial(() -> 0);

    private StrinovaRenderPoseLeakGuard() {
    }

    public static void afterPush(PoseStack stack) {
        if (stack == null) {
            return;
        }
        if (LAST_STACK.get() != stack) {
            LAST_STACK.set(stack);
            LEAK_PUSH_COUNT.set(1);
        } else {
            LEAK_PUSH_COUNT.set(LEAK_PUSH_COUNT.get() + 1);
        }
    }

    public static void afterPop(PoseStack stack) {
        if (stack == null || LAST_STACK.get() != stack) {
            return;
        }
        int v = LEAK_PUSH_COUNT.get();
        if (v <= 0) {
            LEAK_PUSH_COUNT.set(0);
            LAST_STACK.set(null);
            return;
        }
        LEAK_PUSH_COUNT.set(v - 1);
        if (v - 1 == 0) {
            LAST_STACK.set(null);
        }
    }

    public static int enterPaperTransform() {
        int depth = PAPER_TRANSFORM_DEPTH.get();
        PAPER_TRANSFORM_DEPTH.set(depth + 1);
        return depth;
    }

    public static int exitPaperTransform() {
        int depth = PAPER_TRANSFORM_DEPTH.get();
        if (depth <= 0) {
            PAPER_TRANSFORM_DEPTH.set(0);
            return -1;
        }
        int next = depth - 1;
        PAPER_TRANSFORM_DEPTH.set(next);
        return next;
    }

    public static void resetPaperTransform() {
        PAPER_TRANSFORM_DEPTH.set(0);
    }

    public static void drain(PoseStack poseStack) {
        if (poseStack == null || LAST_STACK.get() != poseStack) {
            LEAK_PUSH_COUNT.set(0);
            LAST_STACK.set(null);
            return;
        }
        int leaks = LEAK_PUSH_COUNT.get();
        if (leaks <= 0) {
            LAST_STACK.set(null);
            return;
        }

        // 保护性回收：防止其他模组在 HEAD 取消渲染导致我们 pushPose 后没机会 popPose，从而触发 Pose stack not empty 崩溃
        try {
            int maxPops = leaks;
            if (poseStack instanceof PoseStackAccessor accessor) {
                Deque<PoseStack.Pose> stack = accessor.klbq$getPoseStack();
                if (stack != null) {
                    maxPops = Math.min(maxPops, Math.max(0, stack.size() - 1));
                }
            }
            for (int i = 0; i < maxPops; i++) {
                poseStack.popPose();
            }
        } catch (Exception e) {
            StrinovaMod.LOGGER.error("Failed to drain leaked PoseStack pushes", e);
        } finally {
            LEAK_PUSH_COUNT.set(0);
            LAST_STACK.set(null);
        }
    }
}
