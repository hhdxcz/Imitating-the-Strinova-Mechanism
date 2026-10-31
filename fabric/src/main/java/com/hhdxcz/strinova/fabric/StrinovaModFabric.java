package com.hhdxcz.strinova.fabric;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
import com.hhdxcz.strinova.command.StrinovaServerCommands;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.gameplay.StrinovaCombatManager;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaMatchManager;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class StrinovaModFabric implements ModInitializer {
    // M1：死亡去重标记（必须与 PlayerMixin 中的 Tag 名称保持一致）
    private static final String STRINOVA_DEATH_TAG = "strinova$death_recorded";

    @Override
    public void onInitialize() {
        StrinovaMod.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StrinovaServerCommands.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer target = handler.getPlayer();
            int extraJumps = StrinovaAirJumpSettings.get(server).getExtraJumps(target.getUUID());
            StrinovaNetwork.sendAirJumpSync(target, target.getUUID(), extraJumps);
            // M7：登录时下发模组启用状态与禁止贴墙方块名单
            StrinovaNetwork.sendModEnabled(target, StrinovaMod.isEnabled());
            StrinovaNetwork.sendWallBlacklist(target);
            // M1：登录初始化战斗状态，并下发当前对局状态（进入服务端后立即同步）
            StrinovaCombatManager.ensure(target.getUUID());
            target.removeTag(STRINOVA_DEATH_TAG);
            StrinovaNetwork.sendMatchState(target);
            // H4：新玩家登录时同步所有在线玩家（含自身）的完整碰撞配置
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                StrinovaNetwork.sendCollisionConfig(target, p.getUUID());
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UUID playerId = p.getUUID();
                if (WaPaperState.isCtrlPaper(playerId)) {
                    StrinovaNetwork.sendPaperSync(target, playerId, true);
                }
                if (WaPaperState.isFly(playerId)) {
                    StrinovaNetwork.sendFlySync(target, playerId, true);
                }
                boolean wall = WaPaperState.isWall(playerId);
                if (wall) {
                    WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
                    Double anchorY = WaPaperState.getWallAnchorY(playerId);
                    if (plane == null || anchorY == null) {
                        continue;
                    }
                    StrinovaNetwork.sendWallSync(target, playerId, true, plane.axisX, plane.value, anchorY.doubleValue());
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            // M1：移除战斗/对局状态（避免状态泄漏）
            StrinovaCombatManager.remove(playerId);
            StrinovaMatchManager.onPlayerQuit(playerId);
            WaPaperState.clearPlayer(playerId);
            StrinovaCollisionBoxTuning.clearPlayer(playerId);
            StrinovaCompoundCollision.clearPlayer(playerId);
            StrinovaAirJumpRuntime.reset(playerId);
            StrinovaFlyRuntime.reset(playerId);
            StrinovaNetwork.broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
            StrinovaNetwork.broadcastPaper(server, playerId, false);
            StrinovaNetwork.broadcastFly(server, playerId, false);
            cleanPlayerState(handler.getPlayer());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            cleanPlayerState(newPlayer);
            // M1：重生后护甲回满（保留 KDA），并清除死亡去重标记
            StrinovaCombatManager.respawn(newPlayer);
            newPlayer.removeTag(STRINOVA_DEATH_TAG);
        });
        // M1：服务端 tick 驱动对局倒计时与护甲回复（CombatManager 内部 1 秒节流）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StrinovaMatchManager.tick(server);
            StrinovaCombatManager.tick(server);
            // 广播对局状态（网络层内部 500ms 节流；驱动 HUD 倒计时/比分/击杀播报刷新）
            StrinovaNetwork.broadcastMatchState(server);
        });
        // M1：死亡兜底记录。正常情况下 PlayerMixin 已在 die() 头部记录并打 Tag，这里检测到 Tag 直接跳过，
        // 只有绕开伤害路径的死亡才会由本兜底记录，避免 KDA/击杀播报重复计数。
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim)) {
                return;
            }
            if (victim.level().isClientSide) {
                return;
            }
            if (victim.isCreative() || victim.isSpectator()) {
                return;
            }
            if (victim.getTags().contains(STRINOVA_DEATH_TAG)) {
                return;
            }
            victim.addTag(STRINOVA_DEATH_TAG);
            StrinovaCombatManager.recordDeath(victim, source == null ? null : source.getEntity());
        });
    }

    private static void cleanPlayerState(net.minecraft.world.entity.player.Player player) {
        UUID id = player.getUUID();
        WaPaperState.setPaper(id, false);
        WaPaperState.setWall(id, false);
        WaPaperState.setFly(id, false);
        StrinovaFlyRuntime.clearPlayer(id);
        StrinovaAirJumpRuntime.clearPlayer(id);
    }
}
