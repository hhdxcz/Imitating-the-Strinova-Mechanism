package com.hhdxcz.wa.mixin.client;

import com.hhdxcz.wa.mixin.EntityCollisionAccessor;
import com.hhdxcz.wa.net.WaNetwork;
import com.hhdxcz.wa.paper.WaPaperState;
import com.hhdxcz.wa.config.WaCommonConfig;
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

import java.util.Locale;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Unique
    private static Boolean WA_TACZ_LOADED;

    @Unique
    private static Boolean WA_SHOULDER_SURFING_LOADED;

    @Unique
    private static Boolean WA_TPS_ZERO_LOADED;

    @Unique
    private boolean wa$taczForcedFirstPerson;

    @Unique
    private CameraType wa$taczPrevCameraType;

    @Unique
    private int wa$taczRestoreTicks;

    @Unique
    private boolean wa$taczPrevShoulderSurfing;

    @Unique
    private boolean wa$lastTaczGunMainhand;

    @Unique
    private boolean wa$lastTaczAction;
    
    @Unique
    private Direction wa$lastMoveDir;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void wa$glideStep(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (WaPaperState.isWall(self.getUUID())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.keyJump.isDown()) {
                WaPaperState.setWall(self.getUUID(), false);
                WaNetwork.sendWallOff();
                if (WaPaperState.isCtrlPaper(self.getUUID())) {
                    WaPaperState.setPaper(self.getUUID(), false);
                    WaNetwork.sendPaper(false);
                }
                return;
            }
            WaPaperState.WallPlane plane = WaPaperState.getWallPlane(self.getUUID());
            if (plane == null) {
                WaPaperState.setWall(self.getUUID(), false);
                WaNetwork.sendWallOff();
                if (WaPaperState.isCtrlPaper(self.getUUID())) {
                    WaPaperState.setPaper(self.getUUID(), false);
                    WaNetwork.sendPaper(false);
                }
                return;
            }

            // 1. 预先计算移动意图 (mx, mz)
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

            if (plane.axisX) {
                // Wall Axis X (Normal is Z or -Z). We want Z motion.
                double rawMz = strafe * Math.sin(yawRad);
                mz = Math.abs(rawMz) > 0.01 ? Math.copySign(strafe, rawMz) : 0.0D;
                
                // Fallback for parallel viewing angle: Use last known direction if input persists
                if (Math.abs(mz) < 1.0E-4D && hasInput && wa$lastMoveDir != null && (wa$lastMoveDir == Direction.SOUTH || wa$lastMoveDir == Direction.NORTH)) {
                    mz = (wa$lastMoveDir == Direction.SOUTH ? 1.0D : -1.0D) * climbSpeed;
                }
            } else {
                // Wall Axis Z (Normal is X or -X). We want X motion.
                double rawMx = strafe * Math.cos(yawRad);
                mx = Math.abs(rawMx) > 0.01 ? Math.copySign(strafe, rawMx) : 0.0D;
                
                // Fallback for parallel viewing angle
                if (Math.abs(mx) < 1.0E-4D && hasInput && wa$lastMoveDir != null && (wa$lastMoveDir == Direction.EAST || wa$lastMoveDir == Direction.WEST)) {
                    mx = (wa$lastMoveDir == Direction.EAST ? 1.0D : -1.0D) * climbSpeed;
                }
            }

            // 2. 检测是否有墙
            boolean hasWall = false;
            boolean handled = false;
            BlockPos pos = self.blockPosition();
            
            // 缓存当前的移动方向，用于后续判定
            Direction primaryMoveDir = null;
            if (plane.axisX) {
                 if (Math.abs(mz) > 1.0E-4D) primaryMoveDir = mz > 0 ? Direction.SOUTH : Direction.NORTH;
            } else {
                 if (Math.abs(mx) > 1.0E-4D) primaryMoveDir = mx > 0 ? Direction.EAST : Direction.WEST;
            }
            
            if (primaryMoveDir != null) {
                wa$lastMoveDir = primaryMoveDir;
            }

            if (plane.axisX) {
                double wallX = plane.value;
                boolean wallIsEast = self.getX() < wallX;
                Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;
                BlockPos wallBlockPos = pos.relative(wallDir);
                
                if (!mc.level.getBlockState(wallBlockPos).isAir()) {
                    hasWall = true;
                } else if (primaryMoveDir != null) {
                    // 容错处理：如果我们刚刚走进空气块（外角），检查上一个位置是否有墙
                    BlockPos prevPos = pos.relative(primaryMoveDir.getOpposite());
                    BlockPos prevWallBlock = prevPos.relative(wallDir);
                    if (!mc.level.getBlockState(prevWallBlock).isAir() && 
                        !mc.level.getBlockState(prevWallBlock).getCollisionShape(mc.level, prevWallBlock).isEmpty()) {
                        // 确认是外角，允许切换
                         hasWall = false; // 确实没墙了，需要切换
                         
                         // 触发外角切换逻辑
                         double newVal = primaryMoveDir == Direction.SOUTH ? pos.getZ() : pos.getZ() + 1.0D;
                         WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                         WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                         WaNetwork.sendWall(true, false, newVal, self.getY());
                         handled = true;
                    }
                }
                
                // 正常的预判逻辑（还在墙上时预判前方）
                double zSpeed = mz;
                if (!handled && hasWall && Math.abs(zSpeed) > 1.0E-4D) {
                    Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                    BlockPos nextPos = pos.relative(moveDir);
                    BlockPos nextWallBlock = nextPos.relative(wallDir);
                    
                    if (mc.level.getBlockState(nextWallBlock).isAir() || 
                        mc.level.getBlockState(nextWallBlock).getCollisionShape(mc.level, nextWallBlock).isEmpty()) {
                        
                        double edgeZ = moveDir == Direction.SOUTH ? pos.getZ() + 1.0D : pos.getZ();
                        double distToEdge = Math.abs(self.getZ() - edgeZ);
                        
                        // 稍微放宽判定距离，防止掉落
                        if (distToEdge < 0.2D) {
                            hasWall = false;
                            double newVal = moveDir == Direction.SOUTH ? pos.getZ() + 1.0D : pos.getZ();
                            WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                            WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                            WaNetwork.sendWall(true, false, newVal, self.getY());
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
                    // 容错处理：外角回退检测
                    BlockPos prevPos = pos.relative(primaryMoveDir.getOpposite());
                    BlockPos prevWallBlock = prevPos.relative(wallDir);
                    if (!mc.level.getBlockState(prevWallBlock).isAir() && 
                        !mc.level.getBlockState(prevWallBlock).getCollisionShape(mc.level, prevWallBlock).isEmpty()) {
                         hasWall = false;
                         double newVal = primaryMoveDir == Direction.EAST ? pos.getX() : pos.getX() + 1.0D;
                         WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                         WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                         WaNetwork.sendWall(true, true, newVal, self.getY());
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
                            WaNetwork.sendWall(true, true, newVal, self.getY());
                            handled = true;
                        }
                    }
                }
            }

            // boolean handled = false; // Moved to top
            // 重新计算 pos，因为上面的逻辑可能跨过了边界，或者我们需要基于浮点位置更精确的判定
            // 但这里保持一致性即可。
            
            if (!hasWall) {
                // 3a. 检查外角（Outer Corner）
                // 注意：如果 hasWall 是因为上面的距离检测被强制设为 false 的，
                // 那么我们已经确认了前方有外角。
                // 此时我们需要再次确认 cornerPos，并执行吸附。
                
                // 但为了防止 pos 变化导致检测失败（比如玩家刚跨过边界，pos 变了，导致 relative 计算错误），
                // 我们应该使用更稳健的方式寻找 cornerPos。
                
                // 重新获取 pos，确保它是“离墙消失点最近的那个格子”
                // 如果 distToEdge 很小，玩家可能在 pos，也可能在 nextPos。
                // 我们需要找到那个“有墙”的 pos。
                
                // 简单的做法：检查 pos 的墙是否存在。如果不存在，检查 pos 后方（反移动方向）的墙。
                
                BlockPos checkPos = pos;
                if (plane.axisX) {
                     double wallX = plane.value;
                     boolean wallIsEast = self.getX() < wallX;
                     Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;
                     
                     if (mc.level.getBlockState(checkPos.relative(wallDir)).isAir()) {
                         // 当前格子的墙没了，说明可能跨过边界了。回退一格试试。
                         Direction moveDir = mz > 0 ? Direction.SOUTH : Direction.NORTH; // 依然假设沿意图移动
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
                
                // 使用修正后的 checkPos 进行外角检测
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
                            // Inverse L / Chimney Corner
                            double newVal = moveDir == Direction.SOUTH ? cornerPos.getZ() : cornerPos.getZ() + 1.0D;
                            WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                            WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                            WaNetwork.sendWall(true, false, newVal, self.getY());
                            handled = true;
                        } else {
                            // Open Corner (L-shape). 
                            // Check if the wall block ITSELF is solid.
                            // cornerPos is air. wallBlock is pos.relative(wallDir).
                            // If wallBlock is solid, we can wrap around it.
                            BlockPos wallBlock = pos.relative(wallDir);
                            if (!mc.level.getBlockState(wallBlock).isAir() && 
                                !mc.level.getBlockState(wallBlock).getCollisionShape(mc.level, wallBlock).isEmpty()) {
                                
                                double newVal = moveDir == Direction.SOUTH ? wallBlock.getZ() + 1.0D : wallBlock.getZ();
                                WaPaperState.setWallPlane(self.getUUID(), false, newVal);
                                WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                                WaNetwork.sendWall(true, false, newVal, self.getY());
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
                            WaNetwork.sendWall(true, true, newVal, self.getY());
                            handled = true;
                        } else {
                            BlockPos wallBlock = pos.relative(wallDir);
                            if (!mc.level.getBlockState(wallBlock).isAir() && 
                                !mc.level.getBlockState(wallBlock).getCollisionShape(mc.level, wallBlock).isEmpty()) {
                                
                                double newVal = moveDir == Direction.EAST ? wallBlock.getX() + 1.0D : wallBlock.getX();
                                WaPaperState.setWallPlane(self.getUUID(), true, newVal);
                                WaPaperState.setWallAnchorY(self.getUUID(), self.getY());
                                WaNetwork.sendWall(true, true, newVal, self.getY());
                                handled = true;
                            }
                        }
                    }
                }

                if (!handled) {
                    WaPaperState.setWall(self.getUUID(), false);
                    WaNetwork.sendWallOff();
                    if (WaPaperState.isCtrlPaper(self.getUUID())) {
                        WaPaperState.setPaper(self.getUUID(), false);
                        WaNetwork.sendPaper(false);
                    }
                    return;
                }
            } else {
                // 3b. 检查内角（Inner Corner）
                double threshold = self.getBbWidth() * 0.5D + 0.05D; // 半个身位 + 小余量
                
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
                                WaNetwork.sendWall(true, false, newVal, self.getY());
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
                                WaNetwork.sendWall(true, true, newVal, self.getY());
                            }
                        }
                    }
                }
            }
            
            // 3c. 墙面切换检测与速度继承（解决转弯卡顿问题）
            // 检查 plane 是否发生了变化（无论是因为外角还是内角）
            WaPaperState.WallPlane currentPlane = WaPaperState.getWallPlane(self.getUUID());
            if (currentPlane != null && (currentPlane.axisX != plane.axisX || Math.abs(currentPlane.value - plane.value) > 1.0E-4D)) {
                boolean wasAxisX = plane.axisX;
                double oldVal = plane.value;
                plane = currentPlane; // 更新局部变量，确保后续逻辑使用新墙面

                // 转换速度方向，保持动量
                if (wasAxisX) {
                    // 从 X 轴墙面（Z 向移动）转出
                    // 如果旧墙在东 (X+)，我们需要顺时针/逆时针转弯
                    // 简单逻辑：根据旧墙方位，将 Z 速度映射到 X 速度
                    boolean oldWallIsEast = self.getX() < oldVal;
                    mx = mz * (oldWallIsEast ? 1.0D : -1.0D);
                    mz = 0.0D;
                } else {
                    // 从 Z 轴墙面（X 向移动）转出
                    boolean oldWallIsSouth = self.getZ() < oldVal;
                    mz = mx * (oldWallIsSouth ? 1.0D : -1.0D);
                    mx = 0.0D;
                }
            }

            // 4. 应用移动
            double dy = 0.0D;
            if (mc.options.keyUp.isDown()) {
                dy = climbSpeed;
            }
            if (mc.options.keyDown.isDown()) {
                dy = -climbSpeed;
            }
            
            // 贴附逻辑
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

        var playerId = self.getUUID();
        if (WaPaperState.isFly(playerId) && self instanceof EntityCollisionAccessor accessor) {
            boolean hitWall = accessor.klbq$isHorizontalCollision() || accessor.klbq$isMinorHorizontalCollision();
            if (hitWall) {
                WaPaperState.setFly(playerId, false);
                WaNetwork.sendFly(false);
                Vec3 motion = self.getDeltaMovement();
                self.setDeltaMovement(0.0D, motion.y, 0.0D);
            }
        }

        wa$taczTickPerspective(self);
    }

    @Unique
    private void wa$taczTickPerspective(LocalPlayer self) {
        if (!wa$isTaczLoaded()) {
            wa$resetTaczPerspectiveState();
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
        boolean gunMainhand = wa$isTaczGunMainhand(self);
        boolean isAction = gunMainhand
                && (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown());
        boolean allowTpsExitFlyOnAction = WaCommonConfig.isTpsExitFlyOnAction();
        boolean thirdPersonCompatActive = WaCommonConfig.isThirdPersonCompatBypassEnabled() && wa$isThirdPersonShootCompatLoaded();
        if (thirdPersonCompatActive) {
            var playerId = self.getUUID();
            if (allowTpsExitFlyOnAction && isAction && !wa$lastTaczAction && WaPaperState.isFly(playerId)) {
                WaPaperState.setFly(playerId, false);
                WaNetwork.sendFly(false);
            }
        }
        if (wa$taczForcedFirstPerson && current != CameraType.FIRST_PERSON) {
            wa$resetTaczPerspectiveState();
            return;
        }

        wa$lastTaczGunMainhand = gunMainhand;
        wa$lastTaczAction = isAction;
        boolean taczExitStatesOnAction = WaCommonConfig.isTaczExitStatesOnAction();
        boolean taczForceFirstPerson = WaCommonConfig.isTaczForceFirstPerson();

        if (isAction && taczExitStatesOnAction) {
            var playerId = self.getUUID();
            if (WaPaperState.isWall(playerId)) {
                WaPaperState.setWall(playerId, false);
                WaNetwork.sendWallOff();
            }
            if (WaPaperState.isCtrlPaper(playerId) || WaPaperState.isPaper(playerId)) {
                WaPaperState.setPaper(playerId, false);
                WaNetwork.sendPaper(false);
            }
            if (WaPaperState.isFly(playerId)) {
                WaPaperState.setFly(playerId, false);
                WaNetwork.sendFly(false);
            }
        }
        if (!taczForceFirstPerson) {
            if (wa$taczForcedFirstPerson) {
                wa$restoreTaczPerspectiveIfNeeded(mc);
            }
            return;
        }

        if (!wa$taczForcedFirstPerson) {
            if (current != CameraType.FIRST_PERSON && isAction) {
                wa$taczPrevCameraType = current;
                wa$taczPrevShoulderSurfing = wa$isShoulderSurfingActive();
                wa$taczForcedFirstPerson = true;
                wa$taczRestoreTicks = 4;
                if (wa$taczPrevShoulderSurfing) {
                    wa$changeShoulderPerspective("FIRST_PERSON");
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            return;
        }

        if (isAction) {
            wa$taczRestoreTicks = 4;
            return;
        }
        if (wa$taczRestoreTicks > 0) {
            wa$taczRestoreTicks--;
            return;
        }
        wa$restoreTaczPerspectiveIfNeeded(mc);
    }

    @Unique
    private void wa$restoreTaczPerspectiveIfNeeded(Minecraft mc) {
        boolean restoredShoulder = false;
        if (wa$taczPrevShoulderSurfing) {
            restoredShoulder = wa$changeShoulderPerspective("SHOULDER_SURFING");
        }
        if (mc != null
                && mc.options != null
                && !restoredShoulder
                && mc.options.getCameraType() == CameraType.FIRST_PERSON
                && wa$taczPrevCameraType != null
                && wa$taczPrevCameraType != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(wa$taczPrevCameraType);
        }
        wa$resetTaczPerspectiveState();
    }

    @Unique
    private void wa$resetTaczPerspectiveState() {
        wa$taczForcedFirstPerson = false;
        wa$taczPrevCameraType = null;
        wa$taczRestoreTicks = 0;
        wa$taczPrevShoulderSurfing = false;
        wa$lastTaczGunMainhand = false;
        wa$lastTaczAction = false;
    }

    @Unique
    private static boolean wa$isTaczLoaded() {
        Boolean loaded = WA_TACZ_LOADED;
        if (loaded != null) {
            return loaded.booleanValue();
        }
        boolean v;
        try {
            v = Platform.isModLoaded("tacz");
        } catch (Throwable t) {
            v = false;
        }
        WA_TACZ_LOADED = v;
        return v;
    }

    @Unique
    private static boolean wa$isThirdPersonShootCompatLoaded() {
        Boolean shoulderLoaded = WA_SHOULDER_SURFING_LOADED;
        if (shoulderLoaded == null) {
            shoulderLoaded = wa$isAnyModLoaded("shouldersurfing", "shoulder_surfing");
            WA_SHOULDER_SURFING_LOADED = shoulderLoaded;
        }
        if (shoulderLoaded.booleanValue()) {
            return true;
        }
        Boolean tpsLoaded = WA_TPS_ZERO_LOADED;
        if (tpsLoaded == null) {
            tpsLoaded = wa$isAnyModLoaded("tp_shooting", "third_person_shooting", "third_person_shooting_zero");
            WA_TPS_ZERO_LOADED = tpsLoaded;
        }
        return tpsLoaded.booleanValue();
    }

    @Unique
    private static boolean wa$isAnyModLoaded(String... modIds) {
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

    @Unique
    private static boolean wa$isShoulderSurfingActive() {
        if (!wa$isAnyModLoaded("shouldersurfing", "shoulder_surfing")) {
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

    @Unique
    private static boolean wa$changeShoulderPerspective(String perspectiveName) {
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

    @Unique
    private static boolean wa$isTaczGunMainhand(LocalPlayer player) {
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
