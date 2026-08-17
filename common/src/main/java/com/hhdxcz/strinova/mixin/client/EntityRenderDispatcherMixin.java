package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import com.hhdxcz.strinova.client.StrinovaCollisionPreviewScreen;
import com.hhdxcz.strinova.client.StrinovaRenderPoseLeakGuard;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.render.StrinovaDebugRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

// 实体渲染调度器 Mixin，负责纸片化渲染、贴墙变形、分段碰撞箱渲染、轮廓线强制渲染等核心渲染逻辑
@Mixin(value = EntityRenderDispatcher.class, priority = 2000)
public abstract class EntityRenderDispatcherMixin {

    // 轮廓线队伍名前缀
    private static final String STRINOVA_OUTLINE_TEAM_PREFIX = "strinova_outline_";
    // 贴墙渲染时的微调偏移量，防止 Z-fighting
    private static final float STRINOVA_WALL_RENDER_NUDGE = -0.002F;
    // 肩视角 API 可用性缓存
    private static Boolean STRINOVA_SHOULDER_API_AVAILABLE;

    // 判断实体是否为轮廓线玩家（通过队伍名前缀识别）
    private static boolean strinova$isOutlinedPlayer(Entity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        Team team = player.getTeam();
        if (team == null) {
            return false;
        }
        String name = team.getName();
        return name != null && name.startsWith(STRINOVA_OUTLINE_TEAM_PREFIX);
    }

    // 贴墙模式下对墙角附近的玩家施加弯曲变形，使贴墙过渡更平滑
    private static void strinova$applyCornerDeformation(AbstractClientPlayer player, PoseStack poseStack, WaPaperState.WallPlane plane, float tickDelta) {
        double limit = 0.25D;
        double maxAngle = 90.0D;

        double x = net.minecraft.util.Mth.lerp(tickDelta, player.xo, player.getX());
        double z = net.minecraft.util.Mth.lerp(tickDelta, player.zo, player.getZ());

        BlockPos pos = BlockPos.containing(x, player.getY(), z);
        float angle = 0.0F;
        double pivotX = 0.0D;
        double pivotZ = 0.0D;
        boolean hasDeformation = false;

        if (plane.axisX) {
            double frac = z - Math.floor(z);
            double wallX = plane.value;
            boolean wallIsEast = x < wallX;
            Direction wallFace = wallIsEast ? Direction.EAST : Direction.WEST;

            pivotX = wallX - x;

            if (frac < limit) {
                Direction moveDir = Direction.NORTH;
                BlockPos neighbor = pos.relative(moveDir);
                BlockPos wallBlockPos = neighbor.relative(wallFace);

                boolean inner = player.level().getBlockState(neighbor).isFaceSturdy(player.level(), neighbor, Direction.SOUTH);

                if (inner) {
                    double rawRatio = (limit - frac) / limit;
                    double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                    float deg = (float) (ratio * maxAngle);
                    angle = wallIsEast ? deg : -deg;
                    pivotZ = Math.floor(z) - z;
                    hasDeformation = true;
                } else {
                    boolean continuous = !player.level().getBlockState(wallBlockPos).isAir()
                            && player.level().getBlockState(wallBlockPos).isFaceSturdy(player.level(), wallBlockPos, wallFace.getOpposite());
                    if (!continuous) {
                        double rawRatio = (limit - frac) / limit;
                        double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                        float deg = (float) (ratio * maxAngle);
                        angle = wallIsEast ? -deg : deg;
                        pivotZ = Math.floor(z) - z;
                        hasDeformation = true;
                    }
                }
            } else if (frac > (1.0 - limit)) {
                Direction moveDir = Direction.SOUTH;
                BlockPos neighbor = pos.relative(moveDir);
                BlockPos wallBlockPos = neighbor.relative(wallFace);

                boolean inner = player.level().getBlockState(neighbor).isFaceSturdy(player.level(), neighbor, Direction.NORTH);
                if (inner) {
                    double rawRatio = (frac - (1.0 - limit)) / limit;
                    double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                    float deg = (float) (ratio * maxAngle);
                    angle = wallIsEast ? deg : -deg;
                    pivotZ = Math.ceil(z) - z;
                    hasDeformation = true;
                } else {
                    boolean continuous = !player.level().getBlockState(wallBlockPos).isAir()
                            && player.level().getBlockState(wallBlockPos).isFaceSturdy(player.level(), wallBlockPos, wallFace.getOpposite());
                    if (!continuous) {
                        double rawRatio = (frac - (1.0 - limit)) / limit;
                        double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                        float deg = (float) (ratio * maxAngle);
                        angle = wallIsEast ? -deg : deg;
                        pivotZ = Math.ceil(z) - z;
                        hasDeformation = true;
                    }
                }
            }
        } else {
            double frac = x - Math.floor(x);
            double wallZ = plane.value;
            boolean wallIsSouth = z < wallZ;
            Direction wallFace = wallIsSouth ? Direction.SOUTH : Direction.NORTH;

            pivotZ = wallZ - z;

            if (frac < limit) {
                Direction moveDir = Direction.WEST;
                BlockPos neighbor = pos.relative(moveDir);
                BlockPos wallBlockPos = neighbor.relative(wallFace);

                boolean inner = player.level().getBlockState(neighbor).isFaceSturdy(player.level(), neighbor, Direction.EAST);
                if (inner) {
                    double rawRatio = (limit - frac) / limit;
                    double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                    float deg = (float) (ratio * maxAngle);
                    angle = wallIsSouth ? deg : -deg;
                    pivotX = Math.floor(x) - x;
                    hasDeformation = true;
                } else {
                    boolean continuous = !player.level().getBlockState(wallBlockPos).isAir()
                            && player.level().getBlockState(wallBlockPos).isFaceSturdy(player.level(), wallBlockPos, wallFace.getOpposite());
                    if (!continuous) {
                        double rawRatio = (limit - frac) / limit;
                        double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                        float deg = (float) (ratio * maxAngle);
                        angle = wallIsSouth ? -deg : deg;
                        pivotX = Math.floor(x) - x;
                        hasDeformation = true;
                    }
                }
            } else if (frac > (1.0 - limit)) {
                Direction moveDir = Direction.EAST;
                BlockPos neighbor = pos.relative(moveDir);
                BlockPos wallBlockPos = neighbor.relative(wallFace);

                boolean inner = player.level().getBlockState(neighbor).isFaceSturdy(player.level(), neighbor, Direction.WEST);
                if (inner) {
                    double rawRatio = (frac - (1.0 - limit)) / limit;
                    double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                    float deg = (float) (ratio * maxAngle);
                    angle = wallIsSouth ? deg : -deg;
                    pivotX = Math.ceil(x) - x;
                    hasDeformation = true;
                } else {
                    boolean continuous = !player.level().getBlockState(wallBlockPos).isAir()
                            && player.level().getBlockState(wallBlockPos).isFaceSturdy(player.level(), wallBlockPos, wallFace.getOpposite());
                    if (!continuous) {
                        double rawRatio = (frac - (1.0 - limit)) / limit;
                        double ratio = rawRatio < 0.5 ? 2 * rawRatio * rawRatio : 1 - Math.pow(-2 * rawRatio + 2, 2) / 2;

                        float deg = (float) (ratio * maxAngle);
                        angle = wallIsSouth ? -deg : deg;
                        pivotX = Math.ceil(x) - x;
                        hasDeformation = true;
                    }
                }
            }
        }

        if (hasDeformation && Math.abs(angle) > 0.01F) {
            poseStack.translate(pivotX, 0.0D, pivotZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            float angleRad = (float) Math.toRadians(angle);
            float widthScale = 1.0F - (float) Math.abs(Math.sin(2 * angleRad)) * 0.3F;
            poseStack.scale(widthScale, 1.0F, 1.0F);
            poseStack.translate(-pivotX, 0.0D, -pivotZ);
        }
    }

    // 进入碰撞箱调试渲染上下文，对合成碰撞玩家使用分段碰撞箱渲染
    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void strinova$enterHitboxDebugRender(PoseStack poseStack, VertexConsumer consumer, Entity entity, float tickDelta, CallbackInfo ci) {
        StrinovaDebugRenderContext.enterHitbox();

        if (Minecraft.getInstance().screen instanceof StrinovaCollisionPreviewScreen) {
            ci.cancel();
            return;
        }
        if (!(entity instanceof Player player)) return;
        UUID playerId = player.getUUID();

        if (WaPaperState.isPaper(playerId) || WaPaperState.isFly(playerId)) return;

        if (StrinovaCompoundCollision.getCollisionType(playerId) == StrinovaCompoundCollision.CollisionType.GENERIC) return;

        List<StrinovaCompoundCollision.Part> parts = StrinovaCompoundCollision.getSyncParts(playerId);
        parts = StrinovaCompoundCollision.applyCrouchScale(parts, player);
        if (parts == null || parts.isEmpty()) return;

        ci.cancel();
        strinova$renderSegmentedParts(poseStack, consumer, player, tickDelta, parts);
    }

    // 为分段碰撞箱的每个部位应用对应的动画旋转变换
    private static void strinova$applyPartAnimation(PoseStack ps, String partName,
                                                    float limbSwing, float limbSwingAmount,
                                                    float headYawRad, float headPitchRad,
                                                    float bodyYawRad,
                                                    float halfSizeY) {
        switch (partName) {
            case "head":
                ps.mulPose(Axis.YP.rotation(headYawRad));
                ps.translate(0.0D, -halfSizeY, 0.0D);
                ps.mulPose(Axis.XP.rotation(headPitchRad));
                ps.translate(0.0D, halfSizeY, 0.0D);
                break;
            case "body":
                ps.mulPose(Axis.YP.rotation(-bodyYawRad));
                break;
            case "left_arm":
                ps.mulPose(Axis.YP.rotation(-bodyYawRad));
                ps.translate(0.0D, halfSizeY, 0.0D);
                ps.mulPose(Axis.XP.rotation(
                        Mth.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F));
                ps.translate(0.0D, -halfSizeY, 0.0D);
                break;
            case "right_arm":
                ps.mulPose(Axis.YP.rotation(-bodyYawRad));
                ps.translate(0.0D, halfSizeY, 0.0D);
                ps.mulPose(Axis.XP.rotation(
                        Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 2.0F * limbSwingAmount * 0.5F));
                ps.translate(0.0D, -halfSizeY, 0.0D);
                break;
            case "left_leg":
                ps.mulPose(Axis.YP.rotation(-bodyYawRad));
                ps.translate(0.0D, halfSizeY, 0.0D);
                ps.mulPose(Axis.XP.rotation(
                        Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount));
                ps.translate(0.0D, -halfSizeY, 0.0D);
                break;
            case "right_leg":
                ps.mulPose(Axis.YP.rotation(-bodyYawRad));
                ps.translate(0.0D, halfSizeY, 0.0D);
                ps.mulPose(Axis.XP.rotation(
                        Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount));
                ps.translate(0.0D, -halfSizeY, 0.0D);
                break;
            default:
                break;
        }
    }

    // 退出碰撞箱调试渲染上下文
    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void strinova$exitHitboxDebugRender(PoseStack poseStack, VertexConsumer consumer, Entity entity, float tickDelta, CallbackInfo ci) {
        StrinovaDebugRenderContext.exitHitbox();
    }

    // 渲染合成碰撞体的分段碰撞箱，每个部位独立旋转并绘制线框
    private static void strinova$renderSegmentedParts(PoseStack poseStack, VertexConsumer consumer, Player player, float tickDelta,
                                                      List<StrinovaCompoundCollision.Part> parts) {
        if (parts == null || parts.isEmpty()) return;

        try {
            float limbSwing = player.walkAnimation.position();
            float limbSwingAmount = player.walkAnimation.speed(tickDelta);
            float bodyYawRad = (float) Math.toRadians(Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot));
            double headYawDeg = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
            float headYawRad = (float) Math.toRadians(-headYawDeg);
            float headPitchRad = (float) Math.toRadians(player.getXRot());

            float cosY = Mth.cos(bodyYawRad);
            float sinY = Mth.sin(bodyYawRad);

            for (StrinovaCompoundCollision.Part part : parts) {
                double rx = -part.offsetX() * cosY - part.offsetZ() * sinY;
                double rz = -part.offsetX() * sinY + part.offsetZ() * cosY;

                poseStack.pushPose();
                poseStack.translate(rx, part.offsetY(), rz);
                strinova$applyPartAnimation(poseStack, part.name(),
                        limbSwing, limbSwingAmount,
                        headYawRad, headPitchRad, bodyYawRad,
                        (float) (part.sizeY() * 0.5D));

                double hx = part.sizeX() * 0.5D;
                double hy = part.sizeY() * 0.5D;
                double hz = part.sizeZ() * 0.5D;
                LevelRenderer.renderLineBox(poseStack, consumer,
                        new AABB(-hx, -hy, -hz, hx, hy, hz), 1.0F, 1.0F, 1.0F, 1.0F);

                poseStack.popPose();
            }
        } catch (Exception ignored) {

        }
    }

    // 在实体渲染前应用纸片化/贴墙变换，包括压扁缩放、贴墙偏移、墙角弯曲等
    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void strinova$applyPaperTransformBeforeEntityRender(Entity entity, double x, double y, double z, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean paper = WaPaperState.isPaper(playerId);
        boolean fly = WaPaperState.isFly(playerId);
        boolean previewActive = StrinovaCollisionPreviewFlyContext.isPreviewActive(playerId);
        boolean previewFly = StrinovaCollisionPreviewFlyContext.isPreviewFly(playerId);

        if (previewActive) {
            paper = false;
            fly = previewFly;
        } else if (previewFly) {
            fly = true;
        }

        if (!paper && !fly) {
            return;
        }
        if (fly && !paper && strinova$isShoulderSurfingActive()) {
            return;
        }
        if (StrinovaRenderPoseLeakGuard.enterPaperTransform() > 0) {
            return;
        }

        // 飘飞渲染时设置标记，强制 getViewXRot 返回 90（面朝地面）
        if (fly && !paper) {
            StrinovaRenderPoseLeakGuard.setFlyRenderPlayer(playerId);
        }

        poseStack.pushPose();
        StrinovaRenderPoseLeakGuard.afterPush(poseStack);

        float thickness = 0.06F;
        if (paper) {
            boolean wall = WaPaperState.isWall(playerId);
            if (wall) {
                boolean frontToCamera = WaPaperState.isWallFrontToCamera(playerId);
                if (!frontToCamera) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                }

                WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
                if (plane != null) {
                    if (plane.axisX) {
                        double sign = plane.value > player.getX() ? 1.0D : -1.0D;
                        poseStack.translate(STRINOVA_WALL_RENDER_NUDGE * sign, 0.0D, 0.0D);
                    } else {
                        double sign = plane.value > player.getZ() ? 1.0D : -1.0D;
                        poseStack.translate(0.0D, 0.0D, STRINOVA_WALL_RENDER_NUDGE * sign);
                    }
                    strinova$applyCornerDeformation(player, poseStack, plane, partialTick);
                }
                if (plane != null && plane.axisX) {
                    poseStack.scale(thickness, 1.0F, 1.0F);
                } else {
                    poseStack.scale(1.0F, 1.0F, thickness);
                }
            } else {
                float vanillaYaw = 180.0F - entityYaw;
                poseStack.mulPose(Axis.YP.rotationDegrees(vanillaYaw));
                poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
                poseStack.scale(1.0F, 1.0F, thickness);
                poseStack.mulPose(Axis.YP.rotationDegrees(-45.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(-vanillaYaw));
            }
        } else {
            // 用视角方向（entityYaw）作为朝向：转动视角立即转向，响应快；
            // 俯仰已由 getXRot()=-90 锁定为与地面平行，因此不会随视角上下俯仰
            float flyYaw = entityYaw;
            float vanillaYaw = 180.0F - flyYaw;
            float yawRad = flyYaw * Mth.DEG_TO_RAD;
            float backX = -Mth.sin(yawRad) * -1.0F;
            float backZ = Mth.cos(yawRad) * -1.0F;
            poseStack.translate(backX, 0.0D, backZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(vanillaYaw));
            net.minecraft.world.phys.AABB box = player.getBoundingBox();
            double lift = box.minY - player.getY();
            double pivot = player.getEyeY() - (player.getY() + lift);
            poseStack.translate(0.0D, lift, 0.0D);
            poseStack.translate(0.0D, pivot, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.translate(0.0D, -pivot, 0.0D);
            poseStack.scale(1.0F, 1.0F, thickness);
            poseStack.mulPose(Axis.YP.rotationDegrees(-vanillaYaw));
        }
    }

    // 在实体渲染后恢复 PoseStack，弹出纸片化变换
    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void strinova$applyPaperTransformAfterEntityRender(Entity entity, double x, double y, double z, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean paper = WaPaperState.isPaper(playerId);
        boolean fly = WaPaperState.isFly(playerId);

        if (StrinovaCollisionPreviewFlyContext.isPreviewFly(playerId)) {
            fly = true;
        }

        if (!paper && !fly) {
            return;
        }
        if (fly && !paper && strinova$isShoulderSurfingActive()) {
            return;
        }
        if (StrinovaRenderPoseLeakGuard.exitPaperTransform() != 0) {
            return;
        }
        StrinovaRenderPoseLeakGuard.removeFlyRenderPlayer();
        poseStack.popPose();
        StrinovaRenderPoseLeakGuard.afterPop(poseStack);
    }

    // 检查实体是否在视锥体内
    private static boolean strinova$isInView(Entity entity, Frustum frustum) {
        if (frustum == null) {
            return true;
        }
        return frustum.isVisible(entity.getBoundingBox());
    }

    // 检查实体是否在合理渲染距离内
    private static boolean strinova$isWithinReasonableRange(Entity entity, double camX, double camY, double camZ) {
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double max = 512.0D;
        return dx * dx + dy * dy + dz * dz <= max * max;
    }

    // 通过反射检测 ShoulderSurfing 模组是否处于越肩视角
    private static boolean strinova$isShoulderSurfingActive() {
        Boolean available = STRINOVA_SHOULDER_API_AVAILABLE;
        if (available != null && !available.booleanValue()) {
            return false;
        }
        try {
            Class<?> shoulderClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
            Object api = shoulderClass.getMethod("getInstance").invoke(null);
            Object value = api.getClass().getMethod("isShoulderSurfing").invoke(api);
            STRINOVA_SHOULDER_API_AVAILABLE = Boolean.TRUE;
            return value instanceof Boolean b && b.booleanValue();
        } catch (Throwable ignored) {
            STRINOVA_SHOULDER_API_AVAILABLE = Boolean.FALSE;
            return false;
        }
    }

    // 在 shouldRender HEAD 阶段，强制对轮廓线玩家返回 true，确保其总是被渲染
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void strinova$forceRenderOutlinedPlayersHead(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!strinova$isOutlinedPlayer(entity)) {
            return;
        }
        if (!strinova$isInView(entity, frustum)) {
            return;
        }
        if (!strinova$isWithinReasonableRange(entity, camX, camY, camZ)) {
            return;
        }
        cir.setReturnValue(true);
        cir.cancel();
    }

    // 在 shouldRender RETURN 阶段，对轮廓线玩家兜底设为可见
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void strinova$forceRenderOutlinedPlayers(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        if (!strinova$isOutlinedPlayer(entity)) {
            return;
        }
        if (!strinova$isInView(entity, frustum)) {
            return;
        }
        if (!strinova$isWithinReasonableRange(entity, camX, camY, camZ)) {
            return;
        }

        cir.setReturnValue(true);
    }
}