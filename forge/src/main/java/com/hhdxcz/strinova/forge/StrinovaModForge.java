package com.hhdxcz.strinova.forge;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.command.StrinovaServerCommands;
import com.hhdxcz.strinova.forge.client.StrinovaClientInitForge;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

@Mod(StrinovaMod.MOD_ID)
public class StrinovaModForge {
    public StrinovaModForge() {
        StrinovaMod.init();
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(StrinovaModForge::onPlayerLogout);
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
        WaPaperState.clearPlayer(playerId);
        StrinovaCollisionBoxTuning.clearPlayer(playerId);
        StrinovaAirJumpRuntime.reset(playerId);
        StrinovaFlyRuntime.reset(playerId);
        StrinovaNetwork.broadcastWall(player.getServer(), playerId, false, false, 0.0D, 0.0D);
        StrinovaNetwork.broadcastPaper(player.getServer(), playerId, false);
        StrinovaNetwork.broadcastFly(player.getServer(), playerId, false);
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
        StrinovaNetwork.sendCollisionBoxTuningSync(target, target.getUUID());
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
}
