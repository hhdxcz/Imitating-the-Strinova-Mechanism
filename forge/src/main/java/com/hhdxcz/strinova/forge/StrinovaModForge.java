package com.hhdxcz.strinova.forge;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
import com.hhdxcz.strinova.command.StrinovaServerCommands;
import com.hhdxcz.strinova.forge.client.StrinovaClientInitForge;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.gameplay.StrinovaCombatManager;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaMatchManager;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

@Mod(StrinovaMod.MOD_ID)
public class StrinovaModForge {
    // M1：死亡去重标记（必须与 PlayerMixin 中的 Tag 名称保持一致）
    private static final String STRINOVA_DEATH_TAG = "strinova$death_recorded";

    public StrinovaModForge() {
        StrinovaMod.init();
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onPlayerLogout);
        // H8：注册重生克隆与换维度清理事件
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onPlayerClone);
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onPlayerChangeDimension);
        // M1：服务端 tick 驱动对局倒计时/护甲回复，LivingDeathEvent 作为死亡记录兜底
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onLivingDeath);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> StrinovaClientInitForge::init);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        StrinovaServerCommands.register(event.getDispatcher());
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        UUID playerId = player.getUUID();
        // M1：移除战斗/对局状态（避免状态泄漏）
        StrinovaCombatManager.remove(playerId);
        StrinovaMatchManager.onPlayerQuit(playerId);
        WaPaperState.clearPlayer(playerId);
        StrinovaCollisionBoxTuning.clearPlayer(playerId);
        StrinovaCompoundCollision.clearPlayer(playerId);
        StrinovaAirJumpRuntime.reset(playerId);
        StrinovaFlyRuntime.reset(playerId);
        StrinovaNetwork.broadcastWall(player.getServer(), playerId, false, false, 0.0D, 0.0D);
        StrinovaNetwork.broadcastPaper(player.getServer(), playerId, false);
        StrinovaNetwork.broadcastFly(player.getServer(), playerId, false);
        cleanPlayerState(player);
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) {
            return;
        }
        if (target.level().isClientSide) {
            return;
        }
        var server = target.getServer();
        if (server == null) {
            return;
        }
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
            if (!WaPaperState.isWall(playerId)) {
                continue;
            }
            WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
            Double anchorY = WaPaperState.getWallAnchorY(playerId);
            if (plane == null || anchorY == null) {
                continue;
            }
            StrinovaNetwork.sendWallSync(target, playerId, true, plane.axisX, plane.value, anchorY.doubleValue());
        }
    }

    private static void cleanPlayerState(Player player) {
        UUID id = player.getUUID();
        WaPaperState.setPaper(id, false);
        WaPaperState.setWall(id, false);
        WaPaperState.setFly(id, false);
        StrinovaFlyRuntime.clearPlayer(id);
        StrinovaAirJumpRuntime.clearPlayer(id);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            cleanPlayerState(event.getEntity());
            // M1：重生后护甲回满（保留 KDA），并清除死亡去重标记
            if (event.getEntity() instanceof ServerPlayer newPlayer) {
                StrinovaCombatManager.respawn(newPlayer);
                newPlayer.removeTag(STRINOVA_DEATH_TAG);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanPlayerState(event.getEntity());
    }

    // M1：服务端 tick 驱动对局倒计时与护甲回复（CombatManager 内部 1 秒节流）
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        StrinovaMatchManager.tick(server);
        StrinovaCombatManager.tick(server);
        // 广播对局状态（网络层内部 500ms 节流；驱动 HUD 倒计时/比分/击杀播报刷新）
        StrinovaNetwork.broadcastMatchState(server);
    }

    // M1：死亡记录兜底。正常情况下 PlayerMixin 已在 die() 头部记录并打 Tag，这里检测到 Tag 直接跳过，
    // 只有绕开伤害路径的死亡才会由本兜底记录，避免 KDA/击杀播报重复计数。
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
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
        StrinovaCombatManager.recordDeath(victim, event.getSource() == null ? null : event.getSource().getEntity());
    }
}
