package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import com.hhdxcz.strinova.client.StrinovaRenderPoseLeakGuard;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.render.StrinovaDebugRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = EntityRenderDispatcher.class, priority = 2000)
public abstract class EntityRenderDispatcherMixin {

    private static final String WA_OUTLINE_TEAM_PREFIX = "wa_outline_";
    private static final float WA_WALL_RENDER_NUDGE = -0.002F;
    private static Boolean WA_SHOULDER_API_AVAILABLE;

    private static boolean wa$isOutlinedPlayer(Entity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        Team team = player.getTeam();
        if (team == null) {
            return false;
        }
        String name = team.getName();
        return name != null && name.startsWith(WA_OUTLINE_TEAM_PREFIX);
    }

    private static void wa$applyCornerDeformation(AbstractClientPlayer player, PoseStack poseStack, WaPaperState.WallPlane plane, float tickDelta) {
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

    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("HEAD"),
            require = 0
    )
    private static void wa$enterHitboxDebugRender(PoseStack poseStack, VertexConsumer consumer, Entity entity, float tickDelta, CallbackInfo ci) {
        StrinovaDebugRenderContext.enterHitbox();
    }

    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void wa$exitHitboxDebugRender(PoseStack poseStack, VertexConsumer consumer, Entity entity, float tickDelta, CallbackInfo ci) {
        StrinovaDebugRenderContext.exitHitbox();
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void wa$applyPaperTransformBeforeEntityRender(Entity entity, double x, double y, double z, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean paper = WaPaperState.isPaper(playerId);
        boolean fly = WaPaperState.isFly(playerId);
        boolean previewFly = StrinovaCollisionPreviewFlyContext.isPreviewFly(playerId);

        // 真实 paper/fly 为基础：预览 fly 只补充飞行态所需的判断。
        if (previewFly) {
            fly = true;
        }

        if (!paper && !fly) {
            return;
        }
        if (fly && !paper && wa$isShoulderSurfingActive()) {
            return;
        }
        if (StrinovaRenderPoseLeakGuard.enterPaperTransform() > 0) {
            return;
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
                        poseStack.translate(WA_WALL_RENDER_NUDGE * sign, 0.0D, 0.0D);
                    } else {
                        double sign = plane.value > player.getZ() ? 1.0D : -1.0D;
                        poseStack.translate(0.0D, 0.0D, WA_WALL_RENDER_NUDGE * sign);
                    }
                    wa$applyCornerDeformation(player, poseStack, plane, partialTick);
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
            float vanillaYaw = 180.0F - entityYaw;
            poseStack.mulPose(Axis.YP.rotationDegrees(vanillaYaw));
            net.minecraft.world.phys.AABB box = player.getBoundingBox();
            double lift = box.minY - player.getY();
            double pivot = player.getEyeY() - (player.getY() + lift);
            poseStack.translate(0.0D, lift, 0.0D);
            poseStack.translate(0.0D, pivot, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.translate(0.0D, -pivot, 0.0D);
            poseStack.scale(1.0F, 1.0F, thickness);
            poseStack.mulPose(Axis.YP.rotationDegrees(-vanillaYaw));
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void wa$applyPaperTransformAfterEntityRender(Entity entity, double x, double y, double z, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
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
        if (fly && !paper && wa$isShoulderSurfingActive()) {
            return;
        }
        if (StrinovaRenderPoseLeakGuard.exitPaperTransform() != 0) {
            return;
        }
        poseStack.popPose();
        StrinovaRenderPoseLeakGuard.afterPop(poseStack);
    }

    private static boolean wa$isInView(Entity entity, Frustum frustum) {
        if (frustum == null) {
            return true;
        }
        return frustum.isVisible(entity.getBoundingBox());
    }

    private static boolean wa$isWithinReasonableRange(Entity entity, double camX, double camY, double camZ) {
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double max = 512.0D;
        return dx * dx + dy * dy + dz * dz <= max * max;
    }

    private static boolean wa$isShoulderSurfingActive() {
        Boolean available = WA_SHOULDER_API_AVAILABLE;
        if (available != null && !available.booleanValue()) {
            return false;
        }
        try {
            Class<?> shoulderClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
            Object api = shoulderClass.getMethod("getInstance").invoke(null);
            Object value = api.getClass().getMethod("isShoulderSurfing").invoke(api);
            WA_SHOULDER_API_AVAILABLE = Boolean.TRUE;
            return value instanceof Boolean b && b.booleanValue();
        } catch (Throwable ignored) {
            WA_SHOULDER_API_AVAILABLE = Boolean.FALSE;
            return false;
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void wa$forceRenderOutlinedPlayersHead(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!wa$isOutlinedPlayer(entity)) {
            return;
        }
        if (!wa$isInView(entity, frustum)) {
            return;
        }
        if (!wa$isWithinReasonableRange(entity, camX, camY, camZ)) {
            return;
        }
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void wa$forceRenderOutlinedPlayers(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        if (!wa$isOutlinedPlayer(entity)) {
            return;
        }
        if (!wa$isInView(entity, frustum)) {
            return;
        }
        if (!wa$isWithinReasonableRange(entity, camX, camY, camZ)) {
            return;
        }

        cir.setReturnValue(true);
    }
}
