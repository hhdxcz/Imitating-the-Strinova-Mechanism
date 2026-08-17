package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.mixin.client.PoseStackAccessor;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Deque;
import java.util.UUID;

/**
 * PoseStack 泄漏防护工具。
 * 追踪 pushPose/popPose 的调用配对，在渲染结束时排空未配对的 push，
 * 防止渲染状态泄漏导致后续渲染异常。
 */
public final class StrinovaRenderPoseLeakGuard {
    private static final ThreadLocal<PoseStack> LAST_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Integer> LEAK_PUSH_COUNT = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> PAPER_TRANSFORM_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<UUID> FLY_RENDER_PLAYER = new ThreadLocal<>();

    private StrinovaRenderPoseLeakGuard() {
    }

    /** 查询当前渲染的飘飞玩家是否匹配指定 UUID。 */
    public static boolean isFlyRenderActive(UUID playerId) {
        UUID active = FLY_RENDER_PLAYER.get();
        return active != null && active.equals(playerId);
    }

    /** 设置当前渲染的飘飞玩家 UUID。 */
    public static void setFlyRenderPlayer(UUID playerId) {
        FLY_RENDER_PLAYER.set(playerId);
    }

    /** 清除当前渲染的飘飞玩家标记。 */
    public static void removeFlyRenderPlayer() {
        FLY_RENDER_PLAYER.remove();
    }

    /** 在 pushPose 后调用，记录推送计数。 */
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

    /** 在 popPose 后调用，减少推送计数。 */
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

    /** 进入纸化变换，返回当前深度。 */
    public static int enterPaperTransform() {
        int depth = PAPER_TRANSFORM_DEPTH.get();
        PAPER_TRANSFORM_DEPTH.set(depth + 1);
        return depth;
    }

    /** 退出纸化变换，返回新的深度。 */
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

    /** 重置纸化变换深度。 */
    public static void resetPaperTransform() {
        PAPER_TRANSFORM_DEPTH.set(0);
    }

    /** 排空所有未配对的 pushPose，防止状态泄漏。 */
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

        try {
            int maxPops = leaks;
            if (poseStack instanceof PoseStackAccessor accessor) {
                Deque<PoseStack.Pose> stack = accessor.strinova$getPoseStack();
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