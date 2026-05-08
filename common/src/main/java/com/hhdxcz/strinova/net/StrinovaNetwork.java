package com.hhdxcz.strinova.net;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpClientState;
import com.hhdxcz.strinova.gameplay.StrinovaDoubleJump;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.paper.StrinovaWallBlacklist;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;

public final class StrinovaNetwork {

    public static final ResourceLocation C2S_DOUBLE_JUMP = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_double_jump");
    public static final ResourceLocation C2S_SET_WALL = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_set_wall");
    public static final ResourceLocation C2S_SET_PAPER = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_set_paper");
    public static final ResourceLocation C2S_SET_FLY = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_set_fly");
    public static final ResourceLocation S2C_WALL_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_wall_sync");
    public static final ResourceLocation S2C_PAPER_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_paper_sync");
    public static final ResourceLocation S2C_FLY_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_fly_sync");
    public static final ResourceLocation S2C_AIR_JUMP_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_air_jump_sync");
    public static final ResourceLocation S2C_COLLISION_BOX_TUNING_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_collision_box_tuning_sync");
    public static final ResourceLocation S2C_WALL_BLACKLIST_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_wall_blacklist_sync");

    private StrinovaNetwork() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_DOUBLE_JUMP, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            context.queue(() -> StrinovaDoubleJump.tryApply(player));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_SET_WALL, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            boolean wall = buf.readBoolean();
            boolean ignoredAxisX;
            double ignoredValue;
            double ignoredAnchorY;
            if (wall) {
                ignoredAxisX = buf.readBoolean();
                ignoredValue = buf.readDouble();
                ignoredAnchorY = buf.readDouble();
            }

            context.queue(() -> {
                UUID playerId = player.getUUID();
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }

                Direction dir = player.getDirection();
                boolean canWall = wall
                        && !player.isInWater()
                        && !player.isInLava()
                        && dir.getAxis().isHorizontal()
                        && hasSolidWallInFront(player, dir);
                if (canWall) {
                    boolean axisX = dir.getAxis() == Direction.Axis.X;
                    Double valueObj = computeWallPlaneValue(player, dir);
                    if (valueObj == null) {
                        boolean wasWall = WaPaperState.isWall(playerId);
                        boolean wasCtrlPaper = WaPaperState.isCtrlPaper(playerId);
                        WaPaperState.setWall(playerId, false);
                        if (wasCtrlPaper) {
                            WaPaperState.setPaper(playerId, false);
                        }
                        if (wasWall || wasCtrlPaper) {
                            player.refreshDimensions();
                        }
                        broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
                        if (wasCtrlPaper) {
                            broadcastPaper(server, playerId, false);
                        }
                        return;
                    }
                    double value = valueObj.doubleValue();
                    double anchorY = player.getY();
                    boolean keepLess = axisX ? player.getX() < value : player.getZ() < value;

                    if (WaPaperState.isFly(playerId)) {
                        WaPaperState.setFly(playerId, false);
                        player.refreshDimensions();
                        player.move(MoverType.SELF, new Vec3(0.0D, -StrinovaCollisionBoxTuning.getFlyWorldYOffset(), 0.0D));
                        broadcastFly(server, playerId, false);
                    }
                    WaPaperState.setWall(playerId, true);
                    WaPaperState.setWallPlane(playerId, axisX, value);
                    WaPaperState.setWallAnchorY(playerId, anchorY);
                    WaPaperState.setWallKeepLess(playerId, keepLess);
                    broadcastWall(server, playerId, true, axisX, value, anchorY);
                } else {
                    boolean wasWall = WaPaperState.isWall(playerId);
                    boolean wasCtrlPaper = WaPaperState.isCtrlPaper(playerId);
                    WaPaperState.setWall(playerId, false);
                    if (wasCtrlPaper) {
                        WaPaperState.setPaper(playerId, false);
                    }
                    if (wasWall || wasCtrlPaper) {
                        player.refreshDimensions();
                    }
                    broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
                    if (wasCtrlPaper) {
                        broadcastPaper(server, playerId, false);
                    }
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_SET_PAPER, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            boolean ctrlPaper = buf.readBoolean();

            context.queue(() -> {
                UUID playerId = player.getUUID();
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }
                if (WaPaperState.isWall(playerId)) {
                    return;
                }
                boolean canPaper = ctrlPaper
                        && !player.isInWater()
                        && !player.isInLava();
                boolean finalPaper = ctrlPaper && canPaper;
                WaPaperState.setPaper(playerId, finalPaper);
                player.refreshDimensions();
                broadcastPaper(server, playerId, finalPaper);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_SET_FLY, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            boolean fly = buf.readBoolean();

            context.queue(() -> {
                UUID playerId = player.getUUID();
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }

                boolean prevFly = WaPaperState.isFly(playerId);
                boolean canFly = fly
                        && !player.isInWater()
                        && !player.isInLava()
                        && !player.onGround()
                        && !player.isPassenger()
                        && !WaPaperState.isWall(playerId)
                        && !StrinovaFlyRuntime.hasUsed(playerId);
                boolean finalFly = fly && canFly;
                WaPaperState.setFly(playerId, finalFly);
                if (finalFly) {
                    StrinovaFlyRuntime.markUsed(playerId);
                }
                player.refreshDimensions();
                if (prevFly != finalFly) {
                    double dy = finalFly ? StrinovaCollisionBoxTuning.getFlyWorldYOffset() : -StrinovaCollisionBoxTuning.getFlyWorldYOffset();
                    if (Math.abs(dy) > 1.0E-4D) {
                        player.move(MoverType.SELF, new Vec3(0.0D, dy, 0.0D));
                    }
                }
                broadcastFly(server, playerId, finalFly);
            });
        });
    }

    private static boolean hasSolidWallInFront(ServerPlayer player, Direction dir) {
        if (player == null || dir == null || !dir.getAxis().isHorizontal()) {
            return false;
        }
        Level level = player.level();
        if (level == null) {
            return false;
        }
        // 使用玩家身体中心高度来获取 BlockPos，避免因轻微下沉（Y < 整数位）导致误判地面为前方墙壁
        BlockPos base = BlockPos.containing(player.getX(), player.getY() + 0.5D, player.getZ());
        BlockPos front = base.relative(dir);
        return isSolid(level, front) || isSolid(level, front.above());
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static Double computeWallPlaneValue(ServerPlayer player, Direction dir) {
        if (player == null || dir == null || !dir.getAxis().isHorizontal()) {
            return null;
        }
        Level level = player.level();
        if (level == null) {
            return null;
        }
        BlockPos base = player.blockPosition();
        BlockPos front = base.relative(dir);
        Double surface = findNearestSurface(level, front, dir, player.getX(), player.getZ());
        if (surface == null) {
            surface = findNearestSurface(level, front.above(), dir, player.getX(), player.getZ());
        }
        if (surface == null) {
            return null;
        }
        double gap = WaPaperState.WALL_GAP;
        boolean towardPositive = dir == Direction.EAST || dir == Direction.SOUTH;
        return surface + (towardPositive ? gap : -gap);
    }

    private static Double findNearestSurface(Level level, BlockPos pos, Direction dir, double playerX, double playerZ) {
        if (level == null || pos == null || dir == null || !dir.getAxis().isHorizontal()) {
            return null;
        }
        if (StrinovaWallBlacklist.isBlocked(level, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return null;
        }
        double bestDist = Double.POSITIVE_INFINITY;
        double bestSurface = 0.0D;
        for (AABB aabb : shape.toAabbs()) {
            double surface;
            double dist;
            switch (dir) {
                case EAST -> {
                    surface = pos.getX() + aabb.minX;
                    dist = surface - playerX;
                }
                case WEST -> {
                    surface = pos.getX() + aabb.maxX;
                    dist = playerX - surface;
                }
                case SOUTH -> {
                    surface = pos.getZ() + aabb.minZ;
                    dist = surface - playerZ;
                }
                case NORTH -> {
                    surface = pos.getZ() + aabb.maxZ;
                    dist = playerZ - surface;
                }
                default -> {
                    continue;
                }
            }
            if (dist < -1.0E-6D) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                bestSurface = surface;
            }
        }
        if (bestDist == Double.POSITIVE_INFINITY) {
            return null;
        }
        return bestSurface;
    }

    public static void initClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_WALL_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            boolean wall = buf.readBoolean();
            boolean axisX = false;
            double value = 0.0D;
            double anchorY = 0.0D;
            if (wall) {
                axisX = buf.readBoolean();
                value = buf.readDouble();
                anchorY = buf.readDouble();
            }

            boolean finalAxisX = axisX;
            double finalValue = value;
            double finalAnchorY = anchorY;
            context.queue(() -> {
                WaPaperState.setWall(playerId, wall);
                if (wall) {
                    WaPaperState.setWallPlane(playerId, finalAxisX, finalValue);
                    WaPaperState.setWallAnchorY(playerId, finalAnchorY);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_WALL_BLACKLIST_SYNC, (buf, context) -> {
            int size = buf.readVarInt();
            java.util.List<ResourceLocation> list = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(buf.readResourceLocation());
            }
            context.queue(() -> {
                StrinovaWallBlacklist.updateClient(list);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_PAPER_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            boolean ctrlPaper = buf.readBoolean();
            context.queue(() -> WaPaperState.setPaper(playerId, ctrlPaper));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_FLY_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            boolean fly = buf.readBoolean();
            context.queue(() -> WaPaperState.setFly(playerId, fly));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_AIR_JUMP_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            int extraJumps = buf.readInt();
            context.queue(() -> StrinovaAirJumpClientState.setExtraJumps(playerId, extraJumps));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_COLLISION_BOX_TUNING_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            double syncOffX = buf.readDouble();
            double syncOffY = buf.readDouble();
            double syncOffZ = buf.readDouble();
            double syncSizeX = buf.readDouble();
            double syncSizeY = buf.readDouble();
            double syncSizeZ = buf.readDouble();
            double flyOffX = buf.readDouble();
            double flyOffY = buf.readDouble();
            double flyOffZ = buf.readDouble();
            double flySizeX = buf.readDouble();
            double flySizeY = buf.readDouble();
            double flySizeZ = buf.readDouble();
            context.queue(() -> {
                StrinovaCollisionBoxTuning.setSyncOffset(playerId, syncOffX, syncOffY, syncOffZ);
                StrinovaCollisionBoxTuning.setSyncSize(playerId, syncSizeX, syncSizeY, syncSizeZ);
                StrinovaCollisionBoxTuning.setFlyOffset(playerId, flyOffX, flyOffY, flyOffZ);
                StrinovaCollisionBoxTuning.setFlySize(playerId, flySizeX, flySizeY, flySizeZ);
                strinova$tryRefreshClientPlayerDimensions(playerId);
            });
        });
    }

    private static void strinova$tryRefreshClientPlayerDimensions(UUID playerId) {
        if (playerId == null) {
            return;
        }
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) {
                return;
            }
            var playerField = mcClass.getDeclaredField("player");
            playerField.setAccessible(true);
            Object player = playerField.get(mc);
            if (!(player instanceof net.minecraft.world.entity.player.Player p)) {
                return;
            }
            if (!playerId.equals(p.getUUID())) {
                return;
            }
            p.refreshDimensions();
        } catch (Throwable ignored) {
        }
    }

    public static void sendDoubleJump() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NetworkManager.sendToServer(C2S_DOUBLE_JUMP, buf);
    }

    public static void sendWall(boolean wall, boolean axisX, double value, double anchorY) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(wall);
        if (wall) {
            buf.writeBoolean(axisX);
            buf.writeDouble(value);
            buf.writeDouble(anchorY);
        }
        NetworkManager.sendToServer(C2S_SET_WALL, buf);
    }

    public static void sendPaper(boolean ctrlPaper) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(ctrlPaper);
        NetworkManager.sendToServer(C2S_SET_PAPER, buf);
    }

    public static void sendFly(boolean fly) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(fly);
        NetworkManager.sendToServer(C2S_SET_FLY, buf);
    }

    public static void sendWallOff() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        NetworkManager.sendToServer(C2S_SET_WALL, buf);
    }

    public static void broadcastWall(MinecraftServer server, UUID playerId, boolean wall, boolean axisX, double value, double anchorY) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendWallSync(target, playerId, wall, axisX, value, anchorY);
        }
    }

    public static void broadcastBlacklist(MinecraftServer server) {
        if (server == null) {
            return;
        }
        java.util.List<ResourceLocation> list = StrinovaWallBlacklist.get(server).list();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(list.size());
        for (ResourceLocation id : list) {
            buf.writeResourceLocation(id);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, S2C_WALL_BLACKLIST_SYNC, buf);
        }
    }

    public static void sendWallSync(ServerPlayer target, UUID playerId, boolean wall, boolean axisX, double value, double anchorY) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeBoolean(wall);
        if (wall) {
            buf.writeBoolean(axisX);
            buf.writeDouble(value);
            buf.writeDouble(anchorY);
        }
        NetworkManager.sendToPlayer(target, S2C_WALL_SYNC, buf);
    }

    public static void broadcastPaper(MinecraftServer server, UUID playerId, boolean ctrlPaper) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendPaperSync(target, playerId, ctrlPaper);
        }
    }

    public static void sendPaperSync(ServerPlayer target, UUID playerId, boolean ctrlPaper) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeBoolean(ctrlPaper);
        NetworkManager.sendToPlayer(target, S2C_PAPER_SYNC, buf);
    }

    public static void broadcastFly(MinecraftServer server, UUID playerId, boolean fly) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendFlySync(target, playerId, fly);
        }
    }

    public static void sendFlySync(ServerPlayer target, UUID playerId, boolean fly) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeBoolean(fly);
        NetworkManager.sendToPlayer(target, S2C_FLY_SYNC, buf);
    }

    public static void sendAirJumpSync(ServerPlayer target, UUID playerId, int extraJumps) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeInt(extraJumps);
        NetworkManager.sendToPlayer(target, S2C_AIR_JUMP_SYNC, buf);
    }

    public static void sendCollisionBoxTuningSync(ServerPlayer target, UUID playerId) {
        if (target == null || playerId == null) {
            return;
        }
        StrinovaCollisionBoxTuning.Tuning sync = StrinovaCollisionBoxTuning.getSync(playerId);
        StrinovaCollisionBoxTuning.Tuning fly = StrinovaCollisionBoxTuning.getFly(playerId);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeDouble(sync.offsetX());
        buf.writeDouble(sync.offsetY());
        buf.writeDouble(sync.offsetZ());
        buf.writeDouble(sync.sizeX());
        buf.writeDouble(sync.sizeY());
        buf.writeDouble(sync.sizeZ());
        buf.writeDouble(fly.offsetX());
        buf.writeDouble(fly.offsetY());
        buf.writeDouble(fly.offsetZ());
        buf.writeDouble(fly.sizeX());
        buf.writeDouble(fly.sizeY());
        buf.writeDouble(fly.sizeZ());
        NetworkManager.sendToPlayer(target, S2C_COLLISION_BOX_TUNING_SYNC, buf);
    }
}
