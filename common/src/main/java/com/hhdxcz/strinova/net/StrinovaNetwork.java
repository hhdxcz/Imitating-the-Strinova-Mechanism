package com.hhdxcz.strinova.net;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.client.hud.StrinovaHudMockData;
import com.hhdxcz.strinova.client.hud.StrinovaHudState;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import com.hhdxcz.strinova.client.hud.StrinovaScoreboardState;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpClientState;
import com.hhdxcz.strinova.gameplay.StrinovaCombatManager;
import com.hhdxcz.strinova.gameplay.StrinovaDoubleJump;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaMatchManager;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 网络通信管理器：注册 C2S/S2C 数据包，处理客户端与服务端之间的状态同步
public final class StrinovaNetwork {

    // 客户端到服务端数据包标识
    public static final ResourceLocation C2S_DOUBLE_JUMP = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_double_jump");
    public static final ResourceLocation C2S_SET_WALL = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_set_wall");
    public static final ResourceLocation C2S_SET_PAPER = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_set_paper");
    public static final ResourceLocation C2S_SET_FLY = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_set_fly");
    public static final ResourceLocation C2S_IMPORT_WALL_BLACKLIST = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_import_wall_blacklist");
    public static final ResourceLocation C2S_COLLISION_CONFIG = new ResourceLocation(StrinovaMod.MOD_ID, "c2s_collision_config");

    // 服务端到客户端数据包标识
    public static final ResourceLocation S2C_WALL_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_wall_sync");
    public static final ResourceLocation S2C_PAPER_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_paper_sync");
    public static final ResourceLocation S2C_FLY_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_fly_sync");
    public static final ResourceLocation S2C_AIR_JUMP_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_air_jump_sync");
    public static final ResourceLocation S2C_WALL_BLACKLIST_SYNC = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_wall_blacklist_sync");
    public static final ResourceLocation S2C_COLLISION_CONFIG = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_collision_config");
    public static final ResourceLocation S2C_MOD_ENABLED = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_mod_enabled");
    public static final ResourceLocation S2C_MATCH_STATE = new ResourceLocation(StrinovaMod.MOD_ID, "s2c_match_state");

    // 对局状态广播节流（服务端）：500ms 一次，避免每 tick 发包
    private static long strinova$lastMatchBroadcastMs;
    private static boolean strinova$lastMatchBroadcastActive;

    // 碰撞配置同步字段上限，防止恶意客户端发送超大包
    private static final int MAX_COLLISION_PARTS = 64;
    private static final int MAX_PART_NAME_LENGTH = 64;

    // 一个玩家的完整碰撞配置：碰撞类型、分体部件、两套微调参数
    private record CollisionConfig(
            StrinovaCompoundCollision.CollisionType type,
            List<StrinovaCompoundCollision.Part> syncParts,
            List<StrinovaCompoundCollision.Part> flyParts,
            StrinovaCollisionBoxTuning.Tuning syncTuning,
            StrinovaCollisionBoxTuning.Tuning flyTuning
    ) {
    }

    private StrinovaNetwork() {
    }

    // 初始化服务端网络接收器
    public static void init() {
        // 接收二段跳请求
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_DOUBLE_JUMP, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            if (!StrinovaMod.isEnabled()) return;
            context.queue(() -> StrinovaDoubleJump.tryApply(player));
        });

        // 接收贴墙状态更新
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_SET_WALL, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            if (!StrinovaMod.isEnabled()) return;
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

        // 接收弦化状态更新
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_SET_PAPER, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            if (!StrinovaMod.isEnabled()) return;
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

        // 接收飘飞状态更新
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_SET_FLY, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            if (!StrinovaMod.isEnabled()) return;
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
                } else if (prevFly) {
                    StrinovaFlyRuntime.reset(playerId);
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

        // 接收导入贴墙黑名单请求
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_IMPORT_WALL_BLACKLIST, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            String code = buf.readUtf();
            context.queue(() -> {
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }
                if (!player.hasPermissions(2)) {
                    StrinovaMod.LOGGER.warn("Import wall blacklist rejected: {} lacks permission", player.getName().getString());
                    return;
                }
                try {
                    java.util.List<ResourceLocation> ids = StrinovaWallBlacklist.decodeShareCode(code);
                    if (ids == null) {
                        StrinovaMod.LOGGER.warn("Rejected invalid wall blacklist share code from {}",
                                player.getName().getString());
                        return;
                    }
                    int count = StrinovaWallBlacklist.get(server).replace(ids);
                    StrinovaNetwork.broadcastBlacklist(server);
                    StrinovaMod.LOGGER.info("Imported wall blacklist: {} blocks (code {} chars)", count, code.length());
                } catch (Exception e) {
                    StrinovaMod.LOGGER.error("Failed to import wall blacklist", e);
                }
            });
        });

        // 接收客户端提交的完整碰撞配置（碰撞类型 + 分体部件 + 两套微调参数）
        // 服务端规范化后写入权威状态，再广播给所有客户端，保证联机时判定与显示一致
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_COLLISION_CONFIG, (buf, context) -> {
            var p = context.getPlayer();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            if (!StrinovaMod.isEnabled()) return;
            CollisionConfig config;
            try {
                config = readCollisionConfig(buf);
            } catch (RuntimeException e) {
                StrinovaMod.LOGGER.warn("Rejected invalid collision config from {}: {}",
                        player.getName().getString(), e.getMessage());
                return;
            }
            context.queue(() -> {
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }
                UUID playerId = player.getUUID();
                applyCollisionConfig(playerId, config);
                player.refreshDimensions();
                broadcastCollisionConfig(server, playerId);
            });
        });
    }

    // 检测玩家前方是否有固体方块（用于贴墙判定）
    private static boolean hasSolidWallInFront(ServerPlayer player, Direction dir) {
        if (player == null || dir == null || !dir.getAxis().isHorizontal()) {
            return false;
        }
        Level level = player.level();
        if (level == null) {
            return false;
        }

        BlockPos base = BlockPos.containing(player.getX(), player.getY() + 0.5D, player.getZ());
        BlockPos front = base.relative(dir);
        return isSolid(level, front) || isSolid(level, front.above());
    }

    // 判断方块是否为固体（非空气且有碰撞形状）
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

    // 计算贴墙平面的坐标值
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

    // 查找方块在指定方向上最近的碰撞表面
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

    // 初始化客户端网络接收器
    public static void initClient() {
        // 接收贴墙状态同步
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_WALL_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            boolean wall = buf.readBoolean();
            boolean axisX = false;
            double value = 0.0D;
            double anchorY = 0.0D;
            boolean keepLess = false;
            if (wall) {
                axisX = buf.readBoolean();
                value = buf.readDouble();
                anchorY = buf.readDouble();
                keepLess = buf.readBoolean();
            }

            boolean finalAxisX = axisX;
            double finalValue = value;
            double finalAnchorY = anchorY;
            boolean finalKeepLess = keepLess;
            context.queue(() -> {
                WaPaperState.setWall(playerId, wall);
                if (wall) {
                    WaPaperState.setWallPlane(playerId, finalAxisX, finalValue);
                    WaPaperState.setWallAnchorY(playerId, finalAnchorY);
                    // 贴墙侧由服务端权威下发，客户端不再用坐标比较自行推断，避免预测与权威分歧
                    WaPaperState.setWallKeepLess(playerId, finalKeepLess);
                }
            });
        });

        // 接收贴墙黑名单同步
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

        // 接收弦化状态同步
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_PAPER_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            boolean ctrlPaper = buf.readBoolean();
            context.queue(() -> WaPaperState.setPaper(playerId, ctrlPaper));
        });

        // 接收飘飞状态同步
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_FLY_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            boolean fly = buf.readBoolean();
            context.queue(() -> WaPaperState.setFly(playerId, fly));
        });

        // 接收空中段跳次数同步
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_AIR_JUMP_SYNC, (buf, context) -> {
            UUID playerId = buf.readUUID();
            int extraJumps = buf.readInt();
            context.queue(() -> StrinovaAirJumpClientState.setExtraJumps(playerId, extraJumps));
        });

        // 接收完整碰撞配置同步（碰撞类型 + 分体部件 + 两套微调参数）
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_COLLISION_CONFIG, (buf, context) -> {
            UUID playerId = buf.readUUID();
            CollisionConfig config;
            try {
                config = readCollisionConfig(buf);
            } catch (RuntimeException e) {
                StrinovaMod.LOGGER.warn("Received invalid collision config for {}: {}", playerId, e.getMessage());
                return;
            }
            context.queue(() -> {
                applyCollisionConfig(playerId, config);
                strinova$tryRefreshClientPlayerDimensions(playerId);
            });
        });

        // 接收模组启用状态同步（服务端权威）
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_MOD_ENABLED, (buf, context) -> {
            boolean enabled = buf.readBoolean();
            if (!enabled) {
                try {
                    com.hhdxcz.strinova.client.StrinovaClientNotices.notifyModDisabled();
                } catch (Throwable ignored) {
                }
            }
            context.queue(() -> StrinovaMod.applyRemoteEnabled(enabled));
        });

        // 接收对局状态同步（顶部比分 / TAB 计分板 / 血甲 / 击杀播报的真实数据源）
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_MATCH_STATE, (buf, context) -> {
            // 手动开启模拟数据时忽略服务端对局包，避免 mock 与真实数据互相覆盖
            if (StrinovaHudMockData.isEnabled()) {
                return;
            }
            boolean active = buf.readBoolean();
            if (!active) {
                context.queue(() -> {
                    StrinovaMatchState.clear();
                    StrinovaScoreboardState.clear();
                    StrinovaHudState.setServerMatchActive(false);
                    StrinovaHudState.clearServerSync();
                });
                return;
            }
            String modeKey = buf.readUtf(128);
            int round = buf.readInt();
            int secondsLeft = buf.readInt();
            int alliesScore = buf.readInt();
            int enemiesScore = buf.readInt();
            String alliesTeamKey = buf.readUtf(128);
            String enemiesTeamKey = buf.readUtf(128);

            // 回合记录（左侧竖栏「我方/敌方」那几行）
            int resultCount = Math.min(32, Math.max(0, buf.readVarInt()));
            List<StrinovaMatchState.RoundResult> roundResults = new ArrayList<>(resultCount);
            for (int i = 0; i < resultCount; i++) {
                boolean allyWon = buf.readBoolean();
                String reasonKey = buf.readUtf(128);
                roundResults.add(new StrinovaMatchState.RoundResult(allyWon, reasonKey));
            }

            int killCount = Math.min(64, Math.max(0, buf.readVarInt()));
            List<StrinovaHudState.KillFeedEntry> killEvents = new ArrayList<>(killCount);
            long now = StrinovaHudState.now();
            for (int i = 0; i < killCount; i++) {
                String killer = buf.readUtf(64);
                String weapon = buf.readUtf(64);
                String victim = buf.readUtf(64);
                boolean headshot = buf.readBoolean();
                killEvents.add(new StrinovaHudState.KillFeedEntry(killer, weapon, victim, headshot, now));
            }

            int playerCount = Math.min(64, Math.max(0, buf.readVarInt()));
            List<StrinovaScoreboardState.Entry> entries = new ArrayList<>(playerCount);
            UUID localId = strinova$clientPlayerId();
            float selfHealth = -1.0F;
            float selfArmor = 0.0F;
            int selfUltimate = 0;
            int selfUltimateMax = 7;
            for (int i = 0; i < playerCount; i++) {
                UUID playerId = buf.readUUID();
                String name = buf.readUtf(64);
                boolean ally = buf.readBoolean();
                int kills = buf.readInt();
                int deaths = buf.readInt();
                int assists = buf.readInt();
                float health = buf.readFloat();
                float armor = buf.readFloat();
                int ultimatePoints = buf.readInt();
                int ultimateMax = buf.readInt();
                boolean alive = buf.readBoolean();
                String weaponName = buf.readUtf(64);
                int growthLevel = buf.readInt();
                int damageScore = buf.readInt();
                int skillScore = buf.readInt();
                int assistScore = buf.readInt();
                int ping = buf.readInt();
                boolean self = localId != null && localId.equals(playerId);
                entries.add(new StrinovaScoreboardState.Entry(name, "", ally, self,
                        kills, deaths, assists, health, armor,
                        alive ? StrinovaScoreboardState.Status.ALIVE : StrinovaScoreboardState.Status.DEAD,
                        ultimatePoints, Math.max(1, ultimateMax), growthLevel, weaponName,
                        damageScore, skillScore, assistScore, ping));
                if (self) {
                    selfHealth = health;
                    selfArmor = armor;
                    selfUltimate = ultimatePoints;
                    selfUltimateMax = Math.max(1, ultimateMax);
                }
            }

            float finalSelfHealth = selfHealth;
            float finalSelfArmor = selfArmor;
            int finalSelfUltimate = selfUltimate;
            int finalSelfUltimateMax = selfUltimateMax;
            context.queue(() -> {
                StrinovaMatchState.setActive(true);
                StrinovaMatchState.setModeKey(modeKey);
                StrinovaMatchState.setRound(round);
                StrinovaMatchState.setRoundSecondsLeft(secondsLeft);
                StrinovaMatchState.setScores(alliesScore, enemiesScore);
                StrinovaMatchState.setAlliesTeamKey(alliesTeamKey);
                StrinovaMatchState.setEnemiesTeamKey(enemiesTeamKey);
                StrinovaMatchState.setAlliesAttacking(true);
                StrinovaMatchState.setRoundResults(roundResults);
                StrinovaScoreboardState.setEntries(entries);
                StrinovaHudState.setServerMatchActive(true);
                if (finalSelfHealth >= 0.0F) {
                    StrinovaHudState.setHealth(finalSelfHealth, 150.0F);
                    StrinovaHudState.setArmor(finalSelfArmor, 80.0F);
                    StrinovaHudState.setUltimate(finalSelfUltimate, finalSelfUltimateMax);
                }
                for (StrinovaHudState.KillFeedEntry event : killEvents) {
                    StrinovaHudState.pushKillFeed(event.killer(), event.weapon(), event.victim(), event.headshot());
                }
            });
        });
    }

    // 刷新客户端本地玩家的碰撞箱尺寸
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

    // 发送二段跳请求到服务端
    public static void sendDoubleJump() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NetworkManager.sendToServer(C2S_DOUBLE_JUMP, buf);
    }

    // 发送贴墙状态到服务端
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

    // 发送弦化状态到服务端
    public static void sendPaper(boolean ctrlPaper) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(ctrlPaper);
        NetworkManager.sendToServer(C2S_SET_PAPER, buf);
    }

    // 发送飘飞状态到服务端
    public static void sendFly(boolean fly) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(fly);
        NetworkManager.sendToServer(C2S_SET_FLY, buf);
    }

    // 发送取消贴墙请求到服务端
    public static void sendWallOff() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        NetworkManager.sendToServer(C2S_SET_WALL, buf);
    }

    // 发送导入贴墙黑名单请求到服务端
    public static void sendImportWallBlacklist(String code) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(code);
        NetworkManager.sendToServer(C2S_IMPORT_WALL_BLACKLIST, buf);
    }

    // 向所有在线玩家广播贴墙状态
    public static void broadcastWall(MinecraftServer server, UUID playerId, boolean wall, boolean axisX, double value, double anchorY) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendWallSync(target, playerId, wall, axisX, value, anchorY);
        }
    }

    // 向所有在线玩家广播贴墙黑名单
    public static void broadcastBlacklist(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendWallBlacklist(player);
        }
    }

    // 向指定玩家发送当前贴墙黑名单（用于登录时同步，避免客户端黑名单为空）
    public static void sendWallBlacklist(ServerPlayer target) {
        if (target == null) {
            return;
        }
        MinecraftServer server = target.getServer();
        if (server == null) {
            return;
        }
        java.util.List<ResourceLocation> list = StrinovaWallBlacklist.get(server).list();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(list.size());
        for (ResourceLocation id : list) {
            buf.writeResourceLocation(id);
        }
        NetworkManager.sendToPlayer(target, S2C_WALL_BLACKLIST_SYNC, buf);
    }

    // 向指定玩家发送贴墙状态同步包
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
            // 贴墙侧（保留较小坐标侧）由服务端权威决定并同步给所有客户端
            buf.writeBoolean(Boolean.TRUE.equals(WaPaperState.getWallKeepLess(playerId)));
        }
        NetworkManager.sendToPlayer(target, S2C_WALL_SYNC, buf);
    }

    // 向所有在线玩家广播弦化状态
    public static void broadcastPaper(MinecraftServer server, UUID playerId, boolean ctrlPaper) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendPaperSync(target, playerId, ctrlPaper);
        }
    }

    // 向指定玩家发送弦化状态同步包
    public static void sendPaperSync(ServerPlayer target, UUID playerId, boolean ctrlPaper) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeBoolean(ctrlPaper);
        NetworkManager.sendToPlayer(target, S2C_PAPER_SYNC, buf);
    }

    // 向所有在线玩家广播飘飞状态
    public static void broadcastFly(MinecraftServer server, UUID playerId, boolean fly) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendFlySync(target, playerId, fly);
        }
    }

    // 向指定玩家发送飘飞状态同步包
    public static void sendFlySync(ServerPlayer target, UUID playerId, boolean fly) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeBoolean(fly);
        NetworkManager.sendToPlayer(target, S2C_FLY_SYNC, buf);
    }

    // 向指定玩家发送空中段跳次数同步包
    public static void sendAirJumpSync(ServerPlayer target, UUID playerId, int extraJumps) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        buf.writeInt(extraJumps);
        NetworkManager.sendToPlayer(target, S2C_AIR_JUMP_SYNC, buf);
    }

    // 客户端把自己的完整碰撞配置上报给服务端
    public static void sendCollisionConfig(UUID localPlayerId) {
        if (localPlayerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writeCollisionConfig(buf, localPlayerId);
        NetworkManager.sendToServer(C2S_COLLISION_CONFIG, buf);
    }

    // 向所有在线玩家广播某玩家的完整碰撞配置
    public static void broadcastCollisionConfig(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendCollisionConfig(target, playerId);
        }
    }

    // 向指定玩家发送某玩家的完整碰撞配置
    public static void sendCollisionConfig(ServerPlayer target, UUID playerId) {
        if (target == null || playerId == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(playerId);
        writeCollisionConfig(buf, playerId);
        NetworkManager.sendToPlayer(target, S2C_COLLISION_CONFIG, buf);
    }

    // 向指定玩家发送模组启用状态
    public static void sendModEnabled(ServerPlayer target, boolean enabled) {
        if (target == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(enabled);
        NetworkManager.sendToPlayer(target, S2C_MOD_ENABLED, buf);
    }

    // 向所有在线玩家广播模组启用状态
    public static void broadcastModEnabled(MinecraftServer server, boolean enabled) {
        if (server == null) {
            return;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendModEnabled(target, enabled);
        }
    }

    // ==================== M1：对局/战斗状态同步 ====================

    /** 服务端：立即强制广播一次对局状态（对局开始/停止时调用，绕过节流）。 */
    public static void forceBroadcastMatchState(MinecraftServer server) {
        strinova$lastMatchBroadcastMs = 0L;
        broadcastMatchState(server);
    }

    /**
     * 服务端：广播对局状态（内部 500ms 节流）。
     * 由平台 server tick 钩子每 tick 调用即可；对局从未激活时不发包，停止时补发一次 inactive。
     */
    public static void broadcastMatchState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        boolean active = StrinovaMatchManager.isActive();
        long now = System.currentTimeMillis();
        if (!active && !strinova$lastMatchBroadcastActive) {
            return;
        }
        long interval = active ? 500L : 200L;
        if (now - strinova$lastMatchBroadcastMs < interval) {
            return;
        }
        strinova$lastMatchBroadcastMs = now;
        strinova$lastMatchBroadcastActive = active;

        if (!active) {
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBoolean(false);
                NetworkManager.sendToPlayer(target, S2C_MATCH_STATE, buf);
            }
            return;
        }

        List<StrinovaCombatManager.KillEvent> kills = StrinovaCombatManager.drainKillEvents();
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            sendMatchStateInternal(target, server, kills);
        }
    }

    /** 向指定玩家发送一次对局状态（登录时调用）。 */
    public static void sendMatchState(ServerPlayer target) {
        if (target == null) {
            return;
        }
        MinecraftServer server = target.getServer();
        if (server == null) {
            return;
        }
        sendMatchStateInternal(target, server, List.of());
    }

    private static void sendMatchStateInternal(ServerPlayer target, MinecraftServer server,
                                               List<StrinovaCombatManager.KillEvent> kills) {
        if (target == null || server == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writeMatchState(buf, server, kills);
        NetworkManager.sendToPlayer(target, S2C_MATCH_STATE, buf);
    }

    // 写入对局快照：对局信息 + 击杀事件 + 所有在线玩家战斗数据
    private static void writeMatchState(FriendlyByteBuf buf, MinecraftServer server,
                                        List<StrinovaCombatManager.KillEvent> kills) {
        boolean active = StrinovaMatchManager.isActive();
        buf.writeBoolean(active);
        if (!active) {
            return;
        }
        buf.writeUtf(StrinovaMatchState.getModeKey(), 128);
        buf.writeInt(StrinovaMatchManager.round());
        buf.writeInt(StrinovaMatchManager.secondsLeft());
        buf.writeInt(StrinovaMatchManager.alliesScore());
        buf.writeInt(StrinovaMatchManager.enemiesScore());
        buf.writeUtf(StrinovaMatchManager.alliesTeamKey(), 128);
        buf.writeUtf(StrinovaMatchManager.enemiesTeamKey(), 128);

        // 回合记录：哪方赢下 + 赢法（炸弹爆炸/拆除/灭队/时间耗尽）
        List<StrinovaMatchManager.RoundResult> results = StrinovaMatchManager.roundResults();
        buf.writeVarInt(results.size());
        for (StrinovaMatchManager.RoundResult result : results) {
            buf.writeBoolean(result.allyWon());
            buf.writeUtf(result.reasonKey() == null ? "" : result.reasonKey(), 128);
        }

        List<StrinovaCombatManager.KillEvent> safeKills = kills == null ? List.of() : kills;
        buf.writeVarInt(safeKills.size());
        for (StrinovaCombatManager.KillEvent event : safeKills) {
            buf.writeUtf(event.killer() == null ? "" : event.killer(), 64);
            buf.writeUtf(event.weapon() == null ? "" : event.weapon(), 64);
            buf.writeUtf(event.victim() == null ? "" : event.victim(), 64);
            buf.writeBoolean(event.headshot());
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        buf.writeVarInt(players.size());
        for (ServerPlayer player : players) {
            UUID playerId = player.getUUID();
            StrinovaCombatManager.PlayerState state = StrinovaCombatManager.state(playerId);
            float maxHealth = Math.max(1.0F, player.getMaxHealth());
            float displayHealth = player.getHealth() / maxHealth * 150.0F;
            String weaponName = player.getMainHandItem().isEmpty()
                    ? "近战"
                    : player.getMainHandItem().getHoverName().getString();
            buf.writeUUID(playerId);
            buf.writeUtf(player.getGameProfile().getName(), 64);
            buf.writeBoolean(StrinovaMatchManager.isAlly(playerId));
            buf.writeInt(state.kills());
            buf.writeInt(state.deaths());
            buf.writeInt(state.assists());
            buf.writeFloat(displayHealth);
            buf.writeFloat(state.armor());
            buf.writeInt(state.ultimatePoints());
            buf.writeInt(state.ultimateMax());
            buf.writeBoolean(!player.isDeadOrDying() && player.isAlive());
            buf.writeUtf(weaponName, 64);
            buf.writeInt(1);
            buf.writeInt(state.kills() * 100);
            buf.writeInt(state.ultimatePoints() * 50);
            buf.writeInt(state.assists() * 75);
            buf.writeInt(0);
        }
    }

    // 读取客户端本地玩家 UUID（仅客户端调用，反射避免服务端加载客户端类）
    private static UUID strinova$clientPlayerId() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) {
                return null;
            }
            var playerField = mcClass.getDeclaredField("player");
            playerField.setAccessible(true);
            Object player = playerField.get(mc);
            if (player instanceof net.minecraft.world.entity.player.Player p) {
                return p.getUUID();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // 将指定玩家的完整碰撞配置写入包体（碰撞类型 + 分体部件 + 两套微调参数）
    private static void writeCollisionConfig(FriendlyByteBuf buf, UUID playerId) {
        StrinovaCompoundCollision.CollisionType type = StrinovaCompoundCollision.getCollisionType(playerId);
        buf.writeByte(type.ordinal());
        writeParts(buf, StrinovaCompoundCollision.getSyncParts(playerId));
        writeParts(buf, StrinovaCompoundCollision.getFlyParts(playerId));
        writeTuning(buf, StrinovaCollisionBoxTuning.getSync(playerId));
        writeTuning(buf, StrinovaCollisionBoxTuning.getFly(playerId));
    }

    // 从包体读取完整碰撞配置（带边界校验，拒绝恶意超长数据）
    private static CollisionConfig readCollisionConfig(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        StrinovaCompoundCollision.CollisionType[] types = StrinovaCompoundCollision.CollisionType.values();
        if (ordinal >= types.length) {
            throw new IllegalArgumentException("unknown collision type " + ordinal);
        }
        StrinovaCompoundCollision.CollisionType type = types[ordinal];
        List<StrinovaCompoundCollision.Part> syncParts = readParts(buf);
        List<StrinovaCompoundCollision.Part> flyParts = readParts(buf);
        StrinovaCollisionBoxTuning.Tuning sync = readTuning(buf);
        StrinovaCollisionBoxTuning.Tuning fly = readTuning(buf);
        return new CollisionConfig(type, syncParts, flyParts, sync, fly);
    }

    // 应用碰撞配置：服务端与客户端共用，写入时会由存储层再做范围规范化
    private static void applyCollisionConfig(UUID playerId, CollisionConfig config) {
        if (playerId == null || config == null) {
            return;
        }
        StrinovaCompoundCollision.setCollisionType(playerId, config.type());
        StrinovaCompoundCollision.setSyncParts(playerId,
                config.type() == StrinovaCompoundCollision.CollisionType.GENERIC ? null : config.syncParts());
        StrinovaCompoundCollision.setFlyParts(playerId, config.flyParts());

        StrinovaCollisionBoxTuning.Tuning sync = config.syncTuning();
        StrinovaCollisionBoxTuning.Tuning fly = config.flyTuning();
        StrinovaCollisionBoxTuning.setSyncOffset(playerId, sync.offsetX(), sync.offsetY(), sync.offsetZ());
        StrinovaCollisionBoxTuning.setSyncSize(playerId, sync.sizeX(), sync.sizeY(), sync.sizeZ());
        StrinovaCollisionBoxTuning.setFlyOffset(playerId, fly.offsetX(), fly.offsetY(), fly.offsetZ());
        StrinovaCollisionBoxTuning.setFlySize(playerId, fly.sizeX(), fly.sizeY(), fly.sizeZ());
    }

    // 写入分体碰撞部件列表
    private static void writeParts(FriendlyByteBuf buf, List<StrinovaCompoundCollision.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(parts.size());
        for (StrinovaCompoundCollision.Part part : parts) {
            buf.writeUtf(part.name() == null ? "" : part.name(), MAX_PART_NAME_LENGTH);
            buf.writeDouble(part.offsetX());
            buf.writeDouble(part.offsetY());
            buf.writeDouble(part.offsetZ());
            buf.writeDouble(part.sizeX());
            buf.writeDouble(part.sizeY());
            buf.writeDouble(part.sizeZ());
            boolean hasBone = part.ysmBoneName() != null && !part.ysmBoneName().isEmpty();
            buf.writeBoolean(hasBone);
            if (hasBone) {
                buf.writeUtf(part.ysmBoneName(), MAX_PART_NAME_LENGTH);
            }
        }
    }

    // 读取分体碰撞部件列表
    private static List<StrinovaCompoundCollision.Part> readParts(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_COLLISION_PARTS) {
            throw new IllegalArgumentException("invalid collision part count " + size);
        }
        List<StrinovaCompoundCollision.Part> parts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf(MAX_PART_NAME_LENGTH);
            double offsetX = buf.readDouble();
            double offsetY = buf.readDouble();
            double offsetZ = buf.readDouble();
            double sizeX = buf.readDouble();
            double sizeY = buf.readDouble();
            double sizeZ = buf.readDouble();
            String bone = buf.readBoolean() ? buf.readUtf(MAX_PART_NAME_LENGTH) : null;
            parts.add(new StrinovaCompoundCollision.Part(name, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, bone));
        }
        return parts;
    }

    // 写入一组碰撞箱微调参数
    private static void writeTuning(FriendlyByteBuf buf, StrinovaCollisionBoxTuning.Tuning tuning) {
        StrinovaCollisionBoxTuning.Tuning safe = tuning == null
                ? new StrinovaCollisionBoxTuning.Tuning(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D)
                : tuning;
        buf.writeDouble(safe.offsetX());
        buf.writeDouble(safe.offsetY());
        buf.writeDouble(safe.offsetZ());
        buf.writeDouble(safe.sizeX());
        buf.writeDouble(safe.sizeY());
        buf.writeDouble(safe.sizeZ());
    }

    // 读取一组碰撞箱微调参数
    private static StrinovaCollisionBoxTuning.Tuning readTuning(FriendlyByteBuf buf) {
        double offsetX = buf.readDouble();
        double offsetY = buf.readDouble();
        double offsetZ = buf.readDouble();
        double sizeX = buf.readDouble();
        double sizeY = buf.readDouble();
        double sizeZ = buf.readDouble();
        return new StrinovaCollisionBoxTuning.Tuning(offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ);
    }
}