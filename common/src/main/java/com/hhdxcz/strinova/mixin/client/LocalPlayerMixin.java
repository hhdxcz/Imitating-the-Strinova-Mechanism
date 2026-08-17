package com.hhdxcz.strinova.mixin.client;

import com.hhdxcz.strinova.mixin.EntityCollisionAccessor;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import dev.architectury.platform.Platform;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 本地玩家 Mixin，实现贴墙攀爬逻辑、TACZ 视角兼容、纸片化状态管理等核心客户端功能
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    // TACZ 模组加载状态缓存
    @Unique
    private static Boolean STRINOVA_TACZ_LOADED;

    // 肩视角模组加载状态缓存
    @Unique
    private static Boolean STRINOVA_SHOULDER_SURFING_LOADED;

    // 第三人称射击兼容模组加载状态缓存
    @Unique
    private static Boolean STRINOVA_TPS_ZERO_LOADED;

    // 是否因 TACZ 强制切换了第一人称
    @Unique
    private boolean strinova$taczForcedFirstPerson;

    // TACZ 强制第一人称前的视角类型
    @Unique
    private CameraType strinova$taczPrevCameraType;

    // TACZ 视角恢复延迟 tick
    @Unique
    private int strinova$taczRestoreTicks;

    // TACZ 强制第一人称前的肩视角状态
    @Unique
    private boolean strinova$taczPrevShoulderSurfing;

    // 上一 tick 的主手是否为 TACZ 枪械
    @Unique
    private boolean strinova$lastTaczGunMainhand;

    // 上一 tick 是否在执行 TACZ 动作（攻击或使用）
    @Unique
    private boolean strinova$lastTaczAction;

    // 贴墙移动时的上一移动方向，用于处理方向切换时的初始移动
    @Unique
    private Direction strinova$lastMoveDir;

    // 上一 tick 是否处于贴墙状态，用于检测贴墙状态变化
    @Unique
    private boolean strinova$prevWallState;

    // 是否因贴墙强制切换到了第三人称视角
    @Unique
    private boolean strinova$wallForcedThirdPerson;

    // 贴墙强制切换前的视角类型
    @Unique
    private CameraType strinova$wallPrevCameraType;

    // 每 tick 执行的核心逻辑：处理贴墙攀爬移动、飞行碰撞检测、TACZ 视角兼容
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void strinova$glideStep(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (WaPaperState.isWall(self.getUUID())) {
            Minecraft mc = Minecraft.getInstance();
            // 跳跃键退出贴墙
            if (mc.options.keyJump.isDown()) {
                WaPaperState.setWall(self.getUUID(), false);
                StrinovaNetwork.sendWallOff();
                if (WaPaperState.isCtrlPaper(self.getUUID())) {
                    WaPaperState.setPaper(self.getUUID(), false);
                    StrinovaNetwork.sendPaper(false);
                }
                return;
            }
            WaPaperState.WallPlane plane = WaPaperState.getWallPlane(self.getUUID());
            if (plane == null) {
                WaPaperState.setWall(self.getUUID(), false);
                StrinovaNetwork.sendWallOff();
                if (WaPaperState.isCtrlPaper(self.getUUID())) {
                    WaPaperState.setPaper(self.getUUID(), false);
                    StrinovaNetwork.sendPaper(false);
                }
                return;
            }

            double climbSpeed = 0.3D;
            double strafe = 0.0D;
            boolean hasInput = mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

            if (mc.options.keyLeft.isDown()) {
                strafe += climbSpeed;
            }
            if (mc.options.keyRight.isDown()) {
                strafe -= climbSpeed;
            }

            float yaw = self.getYRot();
            double yawRad = Math.toRadians(yaw);
            double mx = 0.0D;
            double mz = 0.0D;

            // 根据贴墙平面轴向计算移动向量
            if (plane.axisX) {

                double rawMz = strafe * Math.sin(yawRad);
                mz = Math.abs(rawMz) > 0.01 ? Math.copySign(strafe, rawMz) : 0.0D;

                if (Math.abs(mz) < 1.0E-4D && hasInput && strinova$lastMoveDir != null && (strinova$lastMoveDir == Direction.SOUTH || strinova$lastMoveDir == Direction.NORTH)) {
                    mz = (strinova$lastMoveDir == Direction.SOUTH ? 1.0D : -1.0D) * climbSpeed;
                }
            } else {

                double rawMx = strafe * Math.cos(yawRad);
                mx = Math.abs(rawMx) > 0.01 ? Math.copySign(strafe, rawMx) : 0.0D;

                if (Math.abs(mx) < 1.0E-4D && hasInput && strinova$lastMoveDir != null && (strinova$lastMoveDir == Direction.EAST || strinova$lastMoveDir == Direction.WEST)) {
                    mx = (strinova$lastMoveDir == Direction.EAST ? 1.0D : -1.0D) * climbSpeed;
                }
            }

            boolean hasWall = false;
            boolean handled = false;
            BlockPos pos = self.blockPosition();

            Direction primaryMoveDir = null;
            if (plane.axisX) {
                 if (Math.abs(mz) > 1.0E-4D) primaryMoveDir = mz > 0 ? Direction.SOUTH : Direction.NORTH;
            } else {
                 if (Math.abs(mx) > 1.0E-4D) primaryMoveDir = mx > 0 ? Direction.EAST : Direction.WEST;
            }

            if (primaryMoveDir != null) {
                strinova$lastMoveDir = primaryMoveDir;
            }

            // 检测墙壁连续性，处理跨方块转角
            if (plane.axisX) {
                double wallX = plane.value;
                boolean wallIsEast = self.getX() < wallX;
                Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;
                BlockPos wallBlockPos = pos.relative(wallDir);

                if (!mc.level.getBlockState(wallBlockPos).isAir()) {
                    hasWall = true;
                } else if (primaryMoveDir != null) {

                    BlockPos prevPos = pos.relative(primaryMoveDir.getOpposite());
                    BlockPos prevWallBlock = prevPos.relative(wallDir);
                    if (!mc.level.getBlockState(prevWallBlock).isAir() &&
                        !mc.level.getBlockState(prevWallBlock).getCollisionShape(mc.level, prevWallBlock).isEmpty()) {

                         hasWall = false;

                         double newVal = primaryMoveDir == Direction.SOUTH ? pos.getZ() : pos.getZ() + 1.0D;
                         WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                         WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                         StrinovaNetwork.sendWall(true, false, newVal, self.getY());
                         handled = true;
                    }
                }

                double zSpeed = mz;
                if (!handled && hasWall && Math.abs(zSpeed) > 1.0E-4D) {
                    Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                    BlockPos nextPos = pos.relative(moveDir);
                    BlockPos nextWallBlock = nextPos.relative(wallDir);

                    if (mc.level.getBlockState(nextWallBlock).isAir() ||
                        mc.level.getBlockState(nextWallBlock).getCollisionShape(mc.level, nextWallBlock).isEmpty()) {

                        double edgeZ = moveDir == Direction.SOUTH ? pos.getZ() + 1.0D : pos.getZ();
                        double distToEdge = Math.abs(self.getZ() - edgeZ);

                        if (distToEdge < 0.2D) {
                            hasWall = false;
                            double newVal = moveDir == Direction.SOUTH ? pos.getZ() + 1.0D : pos.getZ();
                            WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                            WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                            StrinovaNetwork.sendWall(true, false, newVal, self.getY());
                            handled = true;
                        }
                    }
                }
            } else {
                double wallZ = plane.value;
                boolean wallIsSouth = self.getZ() < wallZ;
                Direction wallDir = wallIsSouth ? Direction.SOUTH : Direction.NORTH;
                BlockPos wallBlockPos = pos.relative(wallDir);

                if (!mc.level.getBlockState(wallBlockPos).isAir()) {
                    hasWall = true;
                } else if (primaryMoveDir != null) {

                    BlockPos prevPos = pos.relative(primaryMoveDir.getOpposite());
                    BlockPos prevWallBlock = prevPos.relative(wallDir);
                    if (!mc.level.getBlockState(prevWallBlock).isAir() &&
                        !mc.level.getBlockState(prevWallBlock).getCollisionShape(mc.level, prevWallBlock).isEmpty()) {
                         hasWall = false;
                         double newVal = primaryMoveDir == Direction.EAST ? pos.getX() : pos.getX() + 1.0D;
                         WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                         WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                         StrinovaNetwork.sendWall(true, true, newVal, self.getY());
                         handled = true;
                    }
                }

                double xSpeed = mx;
                if (!handled && hasWall && Math.abs(xSpeed) > 1.0E-4D) {
                    Direction moveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
                    BlockPos nextPos = pos.relative(moveDir);
                    BlockPos nextWallBlock = nextPos.relative(wallDir);

                    if (mc.level.getBlockState(nextWallBlock).isAir() ||
                        mc.level.getBlockState(nextWallBlock).getCollisionShape(mc.level, nextWallBlock).isEmpty()) {

                        double edgeX = moveDir == Direction.EAST ? pos.getX() + 1.0D : pos.getX();
                        double distToEdge = Math.abs(self.getX() - edgeX);

                        if (distToEdge < 0.2D) {
                            hasWall = false;
                            double newVal = moveDir == Direction.EAST ? pos.getX() + 1.0D : pos.getX();
                            WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                            WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                            StrinovaNetwork.sendWall(true, true, newVal, self.getY());
                            handled = true;
                        }
                    }
                }
            }

            // 无墙壁时处理转角重新贴墙逻辑
            if (!hasWall) {

                BlockPos checkPos = pos;
                if (plane.axisX) {
                     double wallX = plane.value;
                     boolean wallIsEast = self.getX() < wallX;
                     Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;

                     if (mc.level.getBlockState(checkPos.relative(wallDir)).isAir()) {

                         Direction moveDir = mz > 0 ? Direction.SOUTH : Direction.NORTH;
                         checkPos = checkPos.relative(moveDir.getOpposite());
                     }
                } else {
                     double wallZ = plane.value;
                     boolean wallIsSouth = self.getZ() < wallZ;
                     Direction wallDir = wallIsSouth ? Direction.SOUTH : Direction.NORTH;

                     if (mc.level.getBlockState(checkPos.relative(wallDir)).isAir()) {
                         Direction moveDir = mx > 0 ? Direction.EAST : Direction.WEST;
                         checkPos = checkPos.relative(moveDir.getOpposite());
                     }
                }

                pos = checkPos;

                if (plane.axisX) {
                    double zSpeed = mz;
                    if (Math.abs(zSpeed) > 1.0E-4D) {
                        Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                        BlockPos nextPos = pos.relative(moveDir);

                        double wallX = plane.value;
                        boolean wallIsEast = self.getX() < wallX;
                        Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;
                        BlockPos cornerPos = nextPos.relative(wallDir);

                        boolean cornerIsSolid = !mc.level.getBlockState(cornerPos).isAir() &&
                            !mc.level.getBlockState(cornerPos).getCollisionShape(mc.level, cornerPos).isEmpty();

                        if (cornerIsSolid) {

                            double newVal = moveDir == Direction.SOUTH ? cornerPos.getZ() : cornerPos.getZ() + 1.0D;
                            WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                            WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                            StrinovaNetwork.sendWall(true, false, newVal, self.getY());
                            handled = true;
                        } else {

                            BlockPos wallBlock = pos.relative(wallDir);
                            if (!mc.level.getBlockState(wallBlock).isAir() &&
                                !mc.level.getBlockState(wallBlock).getCollisionShape(mc.level, wallBlock).isEmpty()) {

                                double newVal = moveDir == Direction.SOUTH ? wallBlock.getZ() + 1.0D : wallBlock.getZ();
                                WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                                WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                                StrinovaNetwork.sendWall(true, false, newVal, self.getY());
                                handled = true;
                            }
                        }
                    }
                } else {
                    double xSpeed = mx;
                    if (Math.abs(xSpeed) > 1.0E-4D) {
                        Direction moveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
                        BlockPos nextPos = pos.relative(moveDir);

                        double wallZ = plane.value;
                        boolean wallIsSouth = self.getZ() < wallZ;
                        Direction wallDir = wallIsSouth ? Direction.SOUTH : Direction.NORTH;
                        BlockPos cornerPos = nextPos.relative(wallDir);

                        boolean cornerIsSolid = !mc.level.getBlockState(cornerPos).isAir() &&
                            !mc.level.getBlockState(cornerPos).getCollisionShape(mc.level, cornerPos).isEmpty();

                        if (cornerIsSolid) {
                            double newVal = moveDir == Direction.EAST ? cornerPos.getX() : cornerPos.getX() + 1.0D;
                            WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                            WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                            StrinovaNetwork.sendWall(true, true, newVal, self.getY());
                            handled = true;
                        } else {
                            BlockPos wallBlock = pos.relative(wallDir);
                            if (!mc.level.getBlockState(wallBlock).isAir() &&
                                !mc.level.getBlockState(wallBlock).getCollisionShape(mc.level, wallBlock).isEmpty()) {

                                double newVal = moveDir == Direction.EAST ? wallBlock.getX() + 1.0D : wallBlock.getX();
                                WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                                WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                                StrinovaNetwork.sendWall(true, true, newVal, self.getY());
                                handled = true;
                            }
                        }
                    }
                }

                if (!handled) {
                    WaPaperState.setWall(self.getUUID(), false);
                    StrinovaNetwork.sendWallOff();
                    if (WaPaperState.isCtrlPaper(self.getUUID())) {
                        WaPaperState.setPaper(self.getUUID(), false);
                        StrinovaNetwork.sendPaper(false);
                    }
                    return;
                }
            } else {

                // 有墙壁时检测前方碰撞，必要时更新贴墙平面
                double threshold = self.getBbWidth() * 0.5D + 0.05D;

                if (plane.axisX) {
                    double zSpeed = mz;
                    if (Math.abs(zSpeed) > 1.0E-4D) {
                        Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                        BlockPos nextPos = pos.relative(moveDir);
                        if (!mc.level.getBlockState(nextPos).isAir() &&
                            !mc.level.getBlockState(nextPos).getCollisionShape(mc.level, nextPos).isEmpty()) {

                            double newVal = moveDir == Direction.SOUTH ? nextPos.getZ() : nextPos.getZ() + 1.0D;
                            double dist = Math.abs(self.getZ() - newVal);

                            if (dist < threshold) {
                                WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                                WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                                StrinovaNetwork.sendWall(true, false, newVal, self.getY());
                            }
                        }
                    }
                } else {
                    double xSpeed = mx;
                    if (Math.abs(xSpeed) > 1.0E-4D) {
                        Direction moveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
                        BlockPos nextPos = pos.relative(moveDir);
                        if (!mc.level.getBlockState(nextPos).isAir() &&
                            !mc.level.getBlockState(nextPos).getCollisionShape(mc.level, nextPos).isEmpty()) {

                            double newVal = moveDir == Direction.EAST ? nextPos.getX() : nextPos.getX() + 1.0D;
                            double dist = Math.abs(self.getX() - newVal);

                            if (dist < threshold) {
                                WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                                WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                                StrinovaNetwork.sendWall(true, true, newVal, self.getY());
                            }
                        }
                    }
                }
            }

            // 贴墙平面切换时重新计算移动向量
            WaPaperState.WallPlane currentPlane = WaPaperState.getWallPlane(self.getUUID());
            if (currentPlane != null && (currentPlane.axisX != plane.axisX || Math.abs(currentPlane.value - plane.value) > 1.0E-4D)) {
                boolean wasAxisX = plane.axisX;
                double oldVal = plane.value;
                plane = currentPlane;

                if (wasAxisX) {

                    boolean oldWallIsEast = self.getX() < oldVal;
                    mx = mz * (oldWallIsEast ? 1.0D : -1.0D);
                    mz = 0.0D;
                } else {

                    boolean oldWallIsSouth = self.getZ() < oldVal;
                    mz = mx * (oldWallIsSouth ? 1.0D : -1.0D);
                    mx = 0.0D;
                }
            }

            // 上下方向移动
            double dy = 0.0D;
            if (mc.options.keyUp.isDown()) {
                dy = climbSpeed;
            }
            if (mc.options.keyDown.isDown()) {
                dy = -climbSpeed;
            }

            // 锁定贴墙位置并应用移动速度
            if (plane.axisX) {
                double half = self.getBoundingBox().getXsize() * 0.5D;
                double sign = plane.value > self.getX() ? -1.0D : 1.0D;
                double targetX = plane.value + sign * half;
                if (Math.abs(self.getX() - targetX) > 0.02D) {
                    self.setPos(targetX, self.getY(), self.getZ());
                }
                self.setDeltaMovement(0.0D, dy, mz);
            } else {
                double half = self.getBoundingBox().getZsize() * 0.5D;
                double sign = plane.value > self.getZ() ? -1.0D : 1.0D;
                double targetZ = plane.value + sign * half;
                if (Math.abs(self.getZ() - targetZ) > 0.02D) {
                    self.setPos(self.getX(), self.getY(), targetZ);
                }
                self.setDeltaMovement(mx, dy, 0.0D);
            }
            self.setOnGround(false);
        }

        // 飞行碰撞检测：撞墙时退出飘飞
        var playerId = self.getUUID();
        if (WaPaperState.isFly(playerId) && self instanceof EntityCollisionAccessor accessor) {
            boolean hitWall = accessor.strinova$isHorizontalCollision() || accessor.strinova$isMinorHorizontalCollision();
            if (hitWall) {
                WaPaperState.setFly(playerId, false);
                StrinovaNetwork.sendFly(false);
                Vec3 motion = self.getDeltaMovement();
                self.setDeltaMovement(0.0D, motion.y, 0.0D);
            }
        }

        strinova$taczTickPerspective(self);
    }

    // 独立的 aiStep TAIL 注入：无论贴墙逻辑内部是否提前 return，都能检测贴墙状态变化
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void strinova$wallPerspectiveStep(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        strinova$tickWallPerspective(self);
    }

    // 贴墙视角适配：进入贴墙时若处于第一人称，强制切到第三人称背面视角，避免相机嵌墙导致黑屏；离开贴墙时恢复原视角
    @Unique
    private void strinova$tickWallPerspective(LocalPlayer self) {
        boolean wall = WaPaperState.isWall(self.getUUID());
        boolean prevWall = strinova$prevWallState;
        if (wall == prevWall) {
            return;
        }
        strinova$prevWallState = wall;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        if (wall) {
            // 进入贴墙：仅当当前为第一人称且尚未被本逻辑切换过时，记录并切换
            if (!strinova$wallForcedThirdPerson && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                strinova$wallPrevCameraType = CameraType.FIRST_PERSON;
                strinova$wallForcedThirdPerson = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else {
            // 离开贴墙：恢复贴墙前的视角；若玩家贴墙期间手动改过视角则不强制覆盖
            if (strinova$wallForcedThirdPerson) {
                CameraType prev = strinova$wallPrevCameraType;
                strinova$wallForcedThirdPerson = false;
                strinova$wallPrevCameraType = null;
                if (prev != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                    mc.options.setCameraType(prev);
                }
            }
        }
    }

    // TACZ 视角兼容逻辑：枪械使用时强制第一人称，使用结束后恢复
    @Unique
    private void strinova$taczTickPerspective(LocalPlayer self) {
        if (!strinova$isTaczLoaded()) {
            strinova$resetTaczPerspectiveState();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        CameraType current = mc.options.getCameraType();
        if (current == null) {
            return;
        }
        boolean gunMainhand = strinova$isTaczGunMainhand(self);
        boolean isAttack = gunMainhand && mc.options.keyAttack.isDown();
        boolean isUse = gunMainhand && mc.options.keyUse.isDown();
        boolean isAction = isAttack || isUse;
        boolean allowTpsExitFlyOnAction = StrinovaCommonConfig.isTpsExitFlyOnAction();
        boolean thirdPersonCompatActive = StrinovaCommonConfig.isThirdPersonCompatBypassEnabled() && strinova$isThirdPersonShootCompatLoaded();
        if (thirdPersonCompatActive) {
            var playerId = self.getUUID();
            if (allowTpsExitFlyOnAction && isAction && !strinova$lastTaczAction && WaPaperState.isFly(playerId)) {
                WaPaperState.setFly(playerId, false);
                StrinovaNetwork.sendFly(false);
            }
        }
        if (strinova$taczForcedFirstPerson && current != CameraType.FIRST_PERSON) {
            strinova$resetTaczPerspectiveState();
            return;
        }

        strinova$lastTaczGunMainhand = gunMainhand;
        strinova$lastTaczAction = isAction;
        boolean taczExitStatesOnAction = StrinovaCommonConfig.isTaczExitStatesOnAction();
        boolean taczForceFirstPerson = StrinovaCommonConfig.isTaczForceFirstPerson();

        // 枪械动作时退出所有纸片化状态
        if (isAction && taczExitStatesOnAction) {
            var playerId = self.getUUID();
            if (WaPaperState.isWall(playerId)) {
                WaPaperState.setWall(playerId, false);
                StrinovaNetwork.sendWallOff();
            }
            if (WaPaperState.isCtrlPaper(playerId) || WaPaperState.isPaper(playerId)) {
                WaPaperState.setPaper(playerId, false);
                StrinovaNetwork.sendPaper(false);
            }
            if (WaPaperState.isFly(playerId)) {
                WaPaperState.setFly(playerId, false);
                StrinovaNetwork.sendFly(false);
            }
        }
        if (!taczForceFirstPerson) {
            if (strinova$taczForcedFirstPerson) {
                strinova$restoreTaczPerspectiveIfNeeded(mc);
            }
            return;
        }

        // 枪械使用时强制切换到第一人称
        if (!strinova$taczForcedFirstPerson) {
            if (current != CameraType.FIRST_PERSON && isUse) {
                strinova$taczPrevCameraType = current;
                strinova$taczPrevShoulderSurfing = strinova$isShoulderSurfingActive();
                strinova$taczForcedFirstPerson = true;
                strinova$taczRestoreTicks = 4;
                if (strinova$taczPrevShoulderSurfing) {
                    strinova$changeShoulderPerspective("FIRST_PERSON");
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            return;
        }

        if (isUse) {
            strinova$taczRestoreTicks = 4;
            return;
        }
        if (strinova$taczRestoreTicks > 0) {
            strinova$taczRestoreTicks--;
            return;
        }
        strinova$restoreTaczPerspectiveIfNeeded(mc);
    }

    // 恢复 TACZ 强制切换前的视角设置
    @Unique
    private void strinova$restoreTaczPerspectiveIfNeeded(Minecraft mc) {
        boolean restoredShoulder = false;
        if (strinova$taczPrevShoulderSurfing) {
            restoredShoulder = strinova$changeShoulderPerspective("SHOULDER_SURFING");
        }
        if (mc != null
                && mc.options != null
                && !restoredShoulder
                && mc.options.getCameraType() == CameraType.FIRST_PERSON
                && strinova$taczPrevCameraType != null
                && strinova$taczPrevCameraType != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(strinova$taczPrevCameraType);
        }
        strinova$resetTaczPerspectiveState();
    }

    // 重置所有 TACZ 视角状态字段
    @Unique
    private void strinova$resetTaczPerspectiveState() {
        strinova$taczForcedFirstPerson = false;
        strinova$taczPrevCameraType = null;
        strinova$taczRestoreTicks = 0;
        strinova$taczPrevShoulderSurfing = false;
        strinova$lastTaczGunMainhand = false;
        strinova$lastTaczAction = false;
    }

    // 检测 TACZ 模组是否已加载
    @Unique
    private static boolean strinova$isTaczLoaded() {
        Boolean loaded = STRINOVA_TACZ_LOADED;
        if (loaded != null) {
            return loaded.booleanValue();
        }
        boolean v;
        try {
            v = Platform.isModLoaded("tacz");
        } catch (Throwable t) {
            v = false;
        }
        STRINOVA_TACZ_LOADED = v;
        return v;
    }

    // 检测是否有第三人称射击兼容模组（肩视角或第三人称射击）已加载
    @Unique
    private static boolean strinova$isThirdPersonShootCompatLoaded() {
        Boolean shoulderLoaded = STRINOVA_SHOULDER_SURFING_LOADED;
        if (shoulderLoaded == null) {
            shoulderLoaded = strinova$isAnyModLoaded("shouldersurfing", "shoulder_surfing");
            STRINOVA_SHOULDER_SURFING_LOADED = shoulderLoaded;
        }
        if (shoulderLoaded.booleanValue()) {
            return true;
        }
        Boolean tpsLoaded = STRINOVA_TPS_ZERO_LOADED;
        if (tpsLoaded == null) {
            tpsLoaded = strinova$isAnyModLoaded("tp_shooting", "third_person_shooting", "third_person_shooting_zero");
            STRINOVA_TPS_ZERO_LOADED = tpsLoaded;
        }
        return tpsLoaded.booleanValue();
    }

    // 检查任意模组 ID 是否已加载
    @Unique
    private static boolean strinova$isAnyModLoaded(String... modIds) {
        if (modIds == null || modIds.length == 0) {
            return false;
        }
        for (String modId : modIds) {
            if (modId == null || modId.isEmpty()) {
                continue;
            }
            try {
                if (Platform.isModLoaded(modId)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // 通过反射检测 ShoulderSurfing 模组当前是否处于越肩视角
    @Unique
    private static boolean strinova$isShoulderSurfingActive() {
        if (!strinova$isAnyModLoaded("shouldersurfing", "shoulder_surfing")) {
            return false;
        }
        try {
            Class<?> shoulderClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
            Object api = shoulderClass.getMethod("getInstance").invoke(null);
            Object value = api.getClass().getMethod("isShoulderSurfing").invoke(api);
            if (value instanceof Boolean b) {
                return b.booleanValue();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // 通过反射切换 ShoulderSurfing 模组的视角模式
    @Unique
    private static boolean strinova$changeShoulderPerspective(String perspectiveName) {
        if (perspectiveName == null || perspectiveName.isEmpty()) {
            return false;
        }
        try {
            Class<?> shoulderClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
            Class<?> perspectiveClass = Class.forName("com.github.exopandora.shouldersurfing.api.model.Perspective");
            Object api = shoulderClass.getMethod("getInstance").invoke(null);
            Object perspective = perspectiveClass.getField(perspectiveName).get(null);
            api.getClass().getMethod("changePerspective", perspectiveClass).invoke(api, perspective);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    // 判断玩家主手是否持有 TACZ 模组的枪械物品
    @Unique
    private static boolean strinova$isTaczGunMainhand(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        var stack = player.getMainHandItem();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !"tacz".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path != null && path.contains("gun");
    }
}