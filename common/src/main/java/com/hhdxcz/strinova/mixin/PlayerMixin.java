package com.hhdxcz.strinova.mixin;

import com.hhdxcz.strinova.client.StrinovaCollisionPreviewFlyContext;
import com.hhdxcz.strinova.gameplay.StrinovaDoubleJump;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.paper.StrinovaPaperDamageReduction;
import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(LivingEntity.class)
public abstract class PlayerMixin {

    @Unique
    private boolean wa$lastPaper;

    @Unique
    private boolean wa$lastFly;

    @ModifyVariable(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private float wa$reduceDamageWhenPaper(float amount, DamageSource source) {
        if (amount <= 0.0F) {
            return amount;
        }
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return amount;
        }
        if (self.level().isClientSide) {
            return amount;
        }
        if (!WaPaperState.isPaper(self.getUUID())) {
            return amount;
        }
        return StrinovaPaperDamageReduction.apply(amount);
    }

    @Inject(method = "isFallFlying()Z", at = @At("RETURN"), cancellable = true)
    private void wa$enableGlide(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        // 真实飞行：来自服务器同步的 WaPaperState
        if (WaPaperState.isFly(self.getUUID())) {
            cir.setReturnValue(true);
            return;
        }
        // 客户端预览飞行：仅用于 GUI/预览渲染姿态，不写回 WaPaperState，避免网络矫正
        if (StrinovaCollisionPreviewFlyContext.isPreviewFly(self.getUUID())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void wa$disableInWallDamageGlobally(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object selfObj = this;
        if (!(selfObj instanceof LivingEntity self)) {
            return;
        }
        if (self.level().isClientSide) {
            return;
        }
        if (source != null && source.is(DamageTypes.IN_WALL)) {
            // 关闭全局窒息伤害（IN_WALL）
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void wa$updatePaperDimensions(CallbackInfo ci) {
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        if (self.level().isClientSide) {
            return;
        }
        boolean now = WaPaperState.isPaper(self.getUUID());
        boolean fly = WaPaperState.isFly(self.getUUID());
        boolean refresh = false;
        if (now != wa$lastPaper) {
            wa$lastPaper = now;
            refresh = true;
        }
        if (fly != wa$lastFly) {
            wa$lastFly = fly;
            refresh = true;
        }
        if (refresh) {
            self.refreshDimensions();
        }
    }

    @Unique
    private Direction wa$lastMoveDir;

    @Unique
    private static double wa$getWallOuterCornerNudge(Player self, boolean axisX) {
        AABB box = self.getBoundingBox();
        double thin = axisX ? box.getXsize() : box.getZsize();
        if (!(thin > 0.0D)) {
            return 0.0D;
        }
        return Math.min(0.02D, Math.max(0.005D, thin * 0.75D));
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void wa$stickToWallPlane(CallbackInfo ci) {
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        if (self.isPassenger()) {
            return;
        }

        var playerId = self.getUUID();
        if (!WaPaperState.isWall(playerId)) {
            return;
        }
        MinecraftServer server = null;
        if (!self.level().isClientSide && self instanceof ServerPlayer serverPlayer) {
            server = serverPlayer.getServer();
        }
        WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
        if (plane == null) {
            WaPaperState.setWall(playerId, false);
            WaPaperState.setPaper(playerId, false);
            if (server != null) {
                StrinovaNetwork.broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
                StrinovaNetwork.broadcastPaper(server, playerId, false);
            }
            return;
        }

        boolean hasWall = false;
        BlockPos pos = self.blockPosition();

        Vec3 motion = self.getDeltaMovement();
        boolean hasInput = Math.abs(self.xxa) > 0.01F || Math.abs(self.zza) > 0.01F;
        boolean handled = false;

        Vec3 currentMotion = motion;
        if (plane.axisX) {
            double zSpeed = currentMotion.z;
            if (Math.abs(zSpeed) > 1.0E-4D) {
                wa$lastMoveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
            }
        } else {
            double xSpeed = currentMotion.x;
            if (Math.abs(xSpeed) > 1.0E-4D) {
                wa$lastMoveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
            }
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos checkPos = pos.relative(dir);
            var state = self.level().getBlockState(checkPos);
            if (!state.isAir() && !state.getCollisionShape(self.level(), checkPos).isEmpty()) {
                hasWall = true;
                break;
            }
        }
        if (!hasWall || self.isInWater() || self.isInLava()) {
            // 检查外角（Outer Corner）
            if (plane.axisX) {
                double zSpeed = motion.z;
                
                // If speed is negligible but input exists, try to infer direction from input or history
                if (Math.abs(zSpeed) < 1.0E-4D && hasInput) {
                     // Try to infer from yaw and input (approximate since we don't have exact key states)
                     // Or just rely on history
                     if (wa$lastMoveDir != null && (wa$lastMoveDir == Direction.SOUTH || wa$lastMoveDir == Direction.NORTH)) {
                         zSpeed = (wa$lastMoveDir == Direction.SOUTH ? 1.0D : -1.0D) * 0.1D;
                     }
                }
                
                if (Math.abs(zSpeed) > 1.0E-4D) {
                    Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                    wa$lastMoveDir = moveDir; // Update history
                    
                    BlockPos nextPos = pos.relative(moveDir);
                    double wallX = plane.value;
                    boolean wallIsEast = self.getX() < wallX;
                    Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;
                    
                    // Fallback: Check previous block for Outer Corner (Standard L-shape)
                    // IMPORTANT: If we are at the air block (cornerCheckPos is air), we must check relative to the AIR block's neighbor in the opposite direction.
                    // Actually, if we are at the air block, 'pos' is the air block.
                    // moveDir is the direction we were moving.
                    // So the PREVIOUS block is pos.relative(moveDir.getOpposite()).
                    // This previous block should be the one we just left (which had the wall).
                    // And that block's wall neighbor should be solid.
                    
                    BlockPos prevPos = pos.relative(moveDir.getOpposite());
                    BlockPos prevWallBlock = prevPos.relative(wallDir);
                    if (!self.level().getBlockState(prevWallBlock).isAir() && 
                        !self.level().getBlockState(prevWallBlock).getCollisionShape(self.level(), prevWallBlock).isEmpty()) {
                        
                        double newVal;
                        double targetZ;
                        double half = self.getBoundingBox().getZsize() * 0.5D;
                        if (moveDir == Direction.SOUTH) {
                            newVal = pos.getZ();
                            targetZ = newVal + half;
                        } else {
                            newVal = pos.getZ() + 1.0D;
                            targetZ = newVal - half;
                        }

                        WaPaperState.setWallPlane(playerId, false, newVal);
                        if (server != null) {
                            WaPaperState.setWallKeepLess(playerId, moveDir == Direction.NORTH);
                        }
                        if (server != null) {
                            StrinovaNetwork.broadcastWall(server, playerId, true, false, newVal, self.getY());
                        }
                        
                        double nudge = wa$getWallOuterCornerNudge(self, true);
                        double targetX = self.getX() + (wallIsEast ? nudge : -nudge);
                        self.setPos(targetX, self.getY(), targetZ);
                        
                        // Transfer Z momentum to X momentum
                        // Outer Corner: move towards the old wall to wrap around
                        double newSpeed = Math.abs(zSpeed) * (wallIsEast ? 1.0D : -1.0D);
                        
                        self.setDeltaMovement(newSpeed, motion.y, 0.0D);
                        handled = true;
                    }
                }
            } else {
                double xSpeed = motion.x;
                
                if (Math.abs(xSpeed) < 1.0E-4D && hasInput) {
                     if (wa$lastMoveDir != null && (wa$lastMoveDir == Direction.EAST || wa$lastMoveDir == Direction.WEST)) {
                         xSpeed = (wa$lastMoveDir == Direction.EAST ? 1.0D : -1.0D) * 0.1D;
                     }
                }

                if (Math.abs(xSpeed) > 1.0E-4D) {
                    Direction moveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
                    wa$lastMoveDir = moveDir;
                    
                    BlockPos nextPos = pos.relative(moveDir);
                    double wallZ = plane.value;
                    boolean wallIsSouth = self.getZ() < wallZ;
                    Direction wallDir = wallIsSouth ? Direction.SOUTH : Direction.NORTH;
                    
                    // Fallback: Check previous block for Outer Corner
                    BlockPos prevPos = pos.relative(moveDir.getOpposite());
                    BlockPos prevWallBlock = prevPos.relative(wallDir);
                    if (!self.level().getBlockState(prevWallBlock).isAir() && 
                        !self.level().getBlockState(prevWallBlock).getCollisionShape(self.level(), prevWallBlock).isEmpty()) {
                        
                        double newVal;
                        double targetX;
                        double half = self.getBoundingBox().getXsize() * 0.5D;
                        if (moveDir == Direction.EAST) {
                            newVal = pos.getX();
                            targetX = newVal + half;
                        } else {
                            newVal = pos.getX() + 1.0D;
                            targetX = newVal - half;
                        }

                        WaPaperState.setWallPlane(playerId, true, newVal);
                        if (server != null) {
                            WaPaperState.setWallKeepLess(playerId, moveDir == Direction.WEST);
                        }
                        if (server != null) {
                            StrinovaNetwork.broadcastWall(server, playerId, true, true, newVal, self.getY());
                        }
                        
                        double nudge = wa$getWallOuterCornerNudge(self, false);
                        double targetZ = self.getZ() + (wallIsSouth ? nudge : -nudge);
                        self.setPos(targetX, self.getY(), targetZ);
                        
                        // Transfer X momentum to Z momentum
                        // Outer Corner: move towards the old wall to wrap around
                        double newSpeed = Math.abs(xSpeed) * (wallIsSouth ? 1.0D : -1.0D);
                        
                        self.setDeltaMovement(0.0D, motion.y, newSpeed);
                        handled = true;
                    }
                }
            }

            if (!handled) {
                // 贴墙空气检测条件：只有上下贴墙检测空气，左右检测空气不解除贴墙
                // Only release if vertical position changed (meaning we moved vertically into air)
                // If horizontal position changed into air, we maintain the wall state (float/bridge)
                int oldY = (int) Math.floor(self.yo);
                int curY = self.blockPosition().getY();
                if (oldY != curY) {
                    WaPaperState.setWall(playerId, false);
                    WaPaperState.setPaper(playerId, false);
                    if (server != null) {
                        StrinovaNetwork.broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
                        StrinovaNetwork.broadcastPaper(server, playerId, false);
                    }
                    return;
                }
            }
        } else {
            if (!handled) {
                if (plane.axisX) {
                    double zSpeed = motion.z;
                    if (Math.abs(zSpeed) < 1.0E-4D && hasInput && wa$lastMoveDir != null && (wa$lastMoveDir == Direction.SOUTH || wa$lastMoveDir == Direction.NORTH)) {
                        zSpeed = (wa$lastMoveDir == Direction.SOUTH ? 1.0D : -1.0D) * 0.1D;
                    }

                    if (Math.abs(zSpeed) > 1.0E-4D) {
                        Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                        wa$lastMoveDir = moveDir;

                        BlockPos nextPos = pos.relative(moveDir);
                        double wallX = plane.value;
                        boolean wallIsEast = self.getX() < wallX;
                        Direction wallDir = wallIsEast ? Direction.EAST : Direction.WEST;
                        BlockPos nextWallBlock = nextPos.relative(wallDir);

                        if ((self.level().getBlockState(nextWallBlock).isAir()
                                || self.level().getBlockState(nextWallBlock).getCollisionShape(self.level(), nextWallBlock).isEmpty())
                                && (self.level().getBlockState(nextPos).isAir()
                                || self.level().getBlockState(nextPos).getCollisionShape(self.level(), nextPos).isEmpty())) {
                            double edgeZ = moveDir == Direction.SOUTH ? pos.getZ() + 1.0D : pos.getZ();
                            double half = self.getBoundingBox().getZsize() * 0.5D;
                            double distToEdge = Math.abs(self.getZ() - edgeZ);
                            // Relaxed threshold to ensure we catch the corner even if moving fast
                            if (distToEdge < half + 0.2D) {
                                double newVal = edgeZ;
                                double targetZ = moveDir == Direction.SOUTH ? edgeZ + half : edgeZ - half;
                                
                                WaPaperState.setWallPlane(playerId, false, newVal);
                                if (server != null) {
                                    WaPaperState.setWallKeepLess(playerId, moveDir == Direction.NORTH);
                                }
                                if (server != null) {
                                    StrinovaNetwork.broadcastWall(server, playerId, true, false, newVal, self.getY());
                                }
                                
                                double nudge = wa$getWallOuterCornerNudge(self, true);
                                double targetX = self.getX() + (wallIsEast ? nudge : -nudge);
                                self.setPos(targetX, self.getY(), targetZ);
                                
                                self.setDeltaMovement(Math.abs(zSpeed) * (wallIsEast ? 1.0D : -1.0D), motion.y, 0.0D);
                                handled = true;
                            }
                        }
                    }
                } else {
                    double xSpeed = motion.x;
                    if (Math.abs(xSpeed) < 1.0E-4D && hasInput && wa$lastMoveDir != null && (wa$lastMoveDir == Direction.EAST || wa$lastMoveDir == Direction.WEST)) {
                        xSpeed = (wa$lastMoveDir == Direction.EAST ? 1.0D : -1.0D) * 0.1D;
                    }

                    if (Math.abs(xSpeed) > 1.0E-4D) {
                        Direction moveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
                        wa$lastMoveDir = moveDir;

                        BlockPos nextPos = pos.relative(moveDir);
                        double wallZ = plane.value;
                        boolean wallIsSouth = self.getZ() < wallZ;
                        Direction wallDir = wallIsSouth ? Direction.SOUTH : Direction.NORTH;
                        BlockPos nextWallBlock = nextPos.relative(wallDir);

                        if ((self.level().getBlockState(nextWallBlock).isAir()
                                || self.level().getBlockState(nextWallBlock).getCollisionShape(self.level(), nextWallBlock).isEmpty())
                                && (self.level().getBlockState(nextPos).isAir()
                                || self.level().getBlockState(nextPos).getCollisionShape(self.level(), nextPos).isEmpty())) {
                            double edgeX = moveDir == Direction.EAST ? pos.getX() + 1.0D : pos.getX();
                            double half = self.getBoundingBox().getXsize() * 0.5D;
                            double distToEdge = Math.abs(self.getX() - edgeX);
                            // Relaxed threshold
                            if (distToEdge < half + 0.2D) {
                                double newVal = edgeX;
                                double targetX = moveDir == Direction.EAST ? edgeX + half : edgeX - half;
                                
                                WaPaperState.setWallPlane(playerId, true, newVal);
                                if (server != null) {
                                    WaPaperState.setWallKeepLess(playerId, moveDir == Direction.WEST);
                                }
                                if (server != null) {
                                    StrinovaNetwork.broadcastWall(server, playerId, true, true, newVal, self.getY());
                                }
                                
                                double nudge = wa$getWallOuterCornerNudge(self, false);
                                double targetZ = self.getZ() + (wallIsSouth ? nudge : -nudge);
                                self.setPos(targetX, self.getY(), targetZ);
                                
                                self.setDeltaMovement(0.0D, motion.y, Math.abs(xSpeed) * (wallIsSouth ? 1.0D : -1.0D));
                                handled = true;
                            }
                        }
                    }
                }
            }

            if (!handled) {
                if (plane.axisX) {
                    double zSpeed = motion.z;
                    if (Math.abs(zSpeed) < 1.0E-4D && hasInput && wa$lastMoveDir != null && (wa$lastMoveDir == Direction.SOUTH || wa$lastMoveDir == Direction.NORTH)) {
                        zSpeed = (wa$lastMoveDir == Direction.SOUTH ? 1.0D : -1.0D) * 0.1D;
                    }

                    if (Math.abs(zSpeed) > 1.0E-4D) {
                        Direction moveDir = zSpeed > 0 ? Direction.SOUTH : Direction.NORTH;
                        wa$lastMoveDir = moveDir;

                        BlockPos nextPos = pos.relative(moveDir);
                        if (!self.level().getBlockState(nextPos).isAir()
                                && !self.level().getBlockState(nextPos).getCollisionShape(self.level(), nextPos).isEmpty()) {
                            double newVal;
                            double targetZ;
                            double half = self.getBoundingBox().getZsize() * 0.5D;
                            if (moveDir == Direction.SOUTH) {
                                newVal = nextPos.getZ();
                                targetZ = newVal - half;
                            } else {
                                newVal = nextPos.getZ() + 1.0D;
                                targetZ = newVal + half;
                            }
                            
                            // 距离检测：防止过早吸附
                            // 只有当玩家几乎撞上墙（距离 < 0.01 + 半径）时才触发转弯，防止瞬移
                            if (Math.abs(self.getZ() - targetZ) < 0.01D + half) {
                                WaPaperState.setWallPlane(playerId, false, newVal);
                                if (server != null) {
                                    WaPaperState.setWallKeepLess(playerId, moveDir == Direction.SOUTH);
                                }
                                if (server != null) {
                                    StrinovaNetwork.broadcastWall(server, playerId, true, false, newVal, self.getY());
                                }
                                self.setPos(self.getX(), self.getY(), targetZ);
                                
                                // 修正动量转换逻辑：始终朝向离开旧墙面的方向移动
                                double newSpeed;
                                // Check surrounding blocks to determine "outward" direction more reliably than coordinates
                                // Old wall was X-axis. We are now at nextPos (Z-axis wall).
                                // We need to move along X axis. Check East and West of our current pos.
                                BlockPos checkEast = pos.relative(Direction.EAST);
                                BlockPos checkWest = pos.relative(Direction.WEST);
                                boolean eastIsAir = self.level().getBlockState(checkEast).isAir() || self.level().getBlockState(checkEast).getCollisionShape(self.level(), checkEast).isEmpty();
                                boolean westIsAir = self.level().getBlockState(checkWest).isAir() || self.level().getBlockState(checkWest).getCollisionShape(self.level(), checkWest).isEmpty();
                                
                                if (eastIsAir && !westIsAir) {
                                    newSpeed = Math.abs(zSpeed); // Move East (+X)
                                } else if (westIsAir && !eastIsAir) {
                                    newSpeed = -Math.abs(zSpeed); // Move West (-X)
                                } else {
                                    // Fallback to coordinate check if both are air or both are walls
                                    newSpeed = Math.abs(zSpeed) * (plane.value > self.getX() ? -1.0D : 1.0D);
                                }
                                
                                // 保留原有的前进方向动量，防止“漂移”
                                // 如果我们在转弯，我们希望保留一部分当前方向的动量，或者更准确地说是“转换”动量
                                // 但如果我们直接清零 motion.z (这里是 motion.y, 0.0D)，就会导致瞬间停止
                                // 实际上，我们已经把 zSpeed 转换为了 newSpeed (X轴速度)
                                // 所以这里的 0.0D (Z轴速度) 是正确的，因为我们要沿着新墙面(X轴)移动
                                // 但是，为了消除“漂移”感（即玩家感觉被甩出去），我们需要确保新速度的方向和大小是完全受控的
                                // 目前逻辑是：newSpeed = abs(zSpeed) * direction. 
                                // 这意味着原本的 Z 速度完全转化为了 X 速度。这是符合物理预期的“完美转弯”。
                                // 如果玩家感觉“不在墙上”，可能是因为客户端插值导致的视觉延迟。
                                // 既然我们已经移除了平滑插值，恢复了直接吸附，那么“漂移”应该会减少。
                                // 我们可以尝试稍微降低一点速度，模拟摩擦力，但这可能会影响流畅度。
                                // 暂时保持完全动量转换。
                                
                                self.setDeltaMovement(newSpeed, motion.y, 0.0D);
                                handled = true;
                            }
                        }
                    }
                } else {
                    double xSpeed = motion.x;
                    if (Math.abs(xSpeed) < 1.0E-4D && hasInput && wa$lastMoveDir != null && (wa$lastMoveDir == Direction.EAST || wa$lastMoveDir == Direction.WEST)) {
                        xSpeed = (wa$lastMoveDir == Direction.EAST ? 1.0D : -1.0D) * 0.1D;
                    }

                    if (Math.abs(xSpeed) > 1.0E-4D) {
                        Direction moveDir = xSpeed > 0 ? Direction.EAST : Direction.WEST;
                        wa$lastMoveDir = moveDir;

                        BlockPos nextPos = pos.relative(moveDir);
                        if (!self.level().getBlockState(nextPos).isAir()
                                && !self.level().getBlockState(nextPos).getCollisionShape(self.level(), nextPos).isEmpty()) {
                            double newVal;
                            double targetX;
                            double half = self.getBoundingBox().getXsize() * 0.5D;
                            if (moveDir == Direction.EAST) {
                                newVal = nextPos.getX();
                                targetX = newVal - half;
                            } else {
                                newVal = nextPos.getX() + 1.0D;
                                targetX = newVal + half;
                            }

                            // 距离检测：防止过早吸附
                            // 只有当玩家几乎撞上墙（距离 < 0.01 + 半径）时才触发转弯，防止瞬移
                            if (Math.abs(self.getX() - targetX) < 0.01D + half) {
                                WaPaperState.setWallPlane(playerId, true, newVal);
                                if (server != null) {
                                    WaPaperState.setWallKeepLess(playerId, moveDir == Direction.EAST);
                                }
                                if (server != null) {
                                    StrinovaNetwork.broadcastWall(server, playerId, true, true, newVal, self.getY());
                                }
                                self.setPos(targetX, self.getY(), self.getZ());
                                
                                // 修正动量转换逻辑：始终朝向离开旧墙面的方向移动
                                double newSpeed;
                                // Old wall was Z-axis. We are now at nextPos (X-axis wall).
                                // We need to move along Z axis. Check South and North.
                                BlockPos checkSouth = pos.relative(Direction.SOUTH);
                                BlockPos checkNorth = pos.relative(Direction.NORTH);
                                boolean southIsAir = self.level().getBlockState(checkSouth).isAir() || self.level().getBlockState(checkSouth).getCollisionShape(self.level(), checkSouth).isEmpty();
                                boolean northIsAir = self.level().getBlockState(checkNorth).isAir() || self.level().getBlockState(checkNorth).getCollisionShape(self.level(), checkNorth).isEmpty();

                                if (southIsAir && !northIsAir) {
                                    newSpeed = Math.abs(xSpeed); // Move South (+Z)
                                } else if (northIsAir && !southIsAir) {
                                    newSpeed = -Math.abs(xSpeed); // Move North (-Z)
                                } else {
                                    // Fallback to coordinate check
                                    newSpeed = Math.abs(xSpeed) * (plane.value > self.getZ() ? -1.0D : 1.0D);
                                }
                                self.setDeltaMovement(0.0D, motion.y, newSpeed);
                                handled = true;
                            }
                        }
                    }
                }
            }
        }

        plane = WaPaperState.getWallPlane(playerId);
        if (plane == null) {
            WaPaperState.setWall(playerId, false);
            WaPaperState.setPaper(playerId, false);
            if (server != null) {
                StrinovaNetwork.broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
                StrinovaNetwork.broadcastPaper(server, playerId, false);
            }
            return;
        }

        if (plane.axisX) {
            double half = self.getBoundingBox().getXsize() * 0.5D;
            double sign = plane.value > self.getX() ? -1.0D : 1.0D;
            if (!self.level().isClientSide && self instanceof ServerPlayer) {
                Boolean keepLess = WaPaperState.getWallKeepLess(playerId);
                if (keepLess != null) {
                    sign = keepLess.booleanValue() ? -1.0D : 1.0D;
                }
            }
            double targetX = plane.value + sign * half;
            if (Math.abs(self.getX() - targetX) > 0.02D) {
                self.setPos(targetX, self.getY(), self.getZ());
            }
            Vec3 current = self.getDeltaMovement();
            self.setDeltaMovement(0.0D, current.y, current.z);
        } else {
            double half = self.getBoundingBox().getZsize() * 0.5D;
            double sign = plane.value > self.getZ() ? -1.0D : 1.0D;
            if (!self.level().isClientSide && self instanceof ServerPlayer) {
                Boolean keepLess = WaPaperState.getWallKeepLess(playerId);
                if (keepLess != null) {
                    sign = keepLess.booleanValue() ? -1.0D : 1.0D;
                }
            }
            double targetZ = plane.value + sign * half;
            if (Math.abs(self.getZ() - targetZ) > 0.02D) {
                self.setPos(self.getX(), self.getY(), targetZ);
            }
            Vec3 current = self.getDeltaMovement();
            self.setDeltaMovement(current.x, current.y, 0.0D);
        }
        self.setOnGround(false);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void wa$resetDoubleJumpState(CallbackInfo ci) {
        Object selfObj = this;
        if (!(selfObj instanceof Player self)) {
            return;
        }
        if (self.level().isClientSide) {
            return;
        }
        if (wa$shouldResetAirState(self)) {
            StrinovaAirJumpRuntime.reset(self.getUUID());
            StrinovaFlyRuntime.reset(self.getUUID());
            self.removeTag(StrinovaDoubleJump.TAG_USED);
        }
    }

    @Unique
    private static boolean wa$shouldResetAirState(Player self) {
        if (self == null) {
            return false;
        }
        if (self.isInWater() || self.isInLava()) {
            return true;
        }
        UUID playerId = self.getUUID();
        if (WaPaperState.isWall(playerId) || WaPaperState.isFly(playerId) || !self.onGround()) {
            return false;
        }
        BlockPos below = BlockPos.containing(self.getX(), self.getBoundingBox().minY - 0.08D, self.getZ());
        var level = self.level();
        if (level == null) {
            return false;
        }
        var state = level.getBlockState(below);
        if (state.isAir()) {
            return false;
        }
        return !state.getCollisionShape(level, below).isEmpty();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void wa$serverStopFlyOnGroundOrLiquid(CallbackInfo ci) {
        Object selfObj = this;
        if (!(selfObj instanceof ServerPlayer self)) {
            return;
        }
        if (self.level().isClientSide) {
            return;
        }
        UUID playerId = self.getUUID();
        if (!WaPaperState.isFly(playerId)) {
            return;
        }
        boolean hitWall = false;
        if (self instanceof EntityCollisionAccessor accessor) {
            hitWall = accessor.strinova$isHorizontalCollision() || accessor.strinova$isMinorHorizontalCollision();
        }
        if (self.onGround() || self.isInWater() || self.isInLava() || self.isPassenger() || WaPaperState.isWall(playerId) || hitWall) {
            WaPaperState.setFly(playerId, false);
            self.refreshDimensions();
            self.move(MoverType.SELF, new Vec3(0.0D, -StrinovaCollisionBoxTuning.getFlyWorldYOffset(), 0.0D));
            Vec3 motion = self.getDeltaMovement();
            self.setDeltaMovement(0.0D, motion.y, 0.0D);
            self.hasImpulse = true;
            MinecraftServer server = self.getServer();
            if (server != null) {
                StrinovaNetwork.broadcastFly(server, playerId, false);
            }
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void wa$serverStopPaperOnLiquid(CallbackInfo ci) {
        Object selfObj = this;
        if (!(selfObj instanceof ServerPlayer self)) {
            return;
        }
        if (self.level().isClientSide) {
            return;
        }
        UUID playerId = self.getUUID();
        if (!WaPaperState.isCtrlPaper(playerId)) {
            return;
        }
        if (self.isInWater() || self.isInLava()) {
            WaPaperState.setPaper(playerId, false);
            self.refreshDimensions();
            MinecraftServer server = self.getServer();
            if (server != null) {
                StrinovaNetwork.broadcastPaper(server, playerId, false);
            }
        }
    }
}
