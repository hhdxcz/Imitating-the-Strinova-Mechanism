package com.hhdxcz.wa.forge;

import com.hhdxcz.wa.WaMod;
import com.hhdxcz.wa.collision.WaCollisionBoxTuning;
import com.hhdxcz.wa.command.WaServerCommands;
import com.hhdxcz.wa.forge.client.ClientInitForge;
import com.hhdxcz.wa.gameplay.WaAirJumpRuntime;
import com.hhdxcz.wa.gameplay.WaAirJumpSettings;
import com.hhdxcz.wa.gameplay.WaFlyRuntime;
import com.hhdxcz.wa.net.WaNetwork;
import com.hhdxcz.wa.paper.WaPaperState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

@Mod(WaMod.MOD_ID)
public class WaModForge {
    public WaModForge() {
        WaMod.init();
        MinecraftForge.EVENT_BUS.addListener(WaModForge::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(WaModForge::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(WaModForge::onPlayerLogout);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientInitForge::init);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        WaServerCommands.register(event.getDispatcher());
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
        WaCollisionBoxTuning.clearPlayer(playerId);
        WaAirJumpRuntime.reset(playerId);
        WaFlyRuntime.reset(playerId);
        WaNetwork.broadcastWall(player.getServer(), playerId, false, false, 0.0D, 0.0D);
        WaNetwork.broadcastPaper(player.getServer(), playerId, false);
        WaNetwork.broadcastFly(player.getServer(), playerId, false);
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
        int extraJumps = WaAirJumpSettings.get(server).getExtraJumps(target.getUUID());
        WaNetwork.sendAirJumpSync(target, target.getUUID(), extraJumps);
        WaNetwork.sendCollisionBoxTuningSync(target, target.getUUID());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID playerId = p.getUUID();
            if (WaPaperState.isCtrlPaper(playerId)) {
                WaNetwork.sendPaperSync(target, playerId, true);
            }
            if (WaPaperState.isFly(playerId)) {
                WaNetwork.sendFlySync(target, playerId, true);
            }
            if (!WaPaperState.isWall(playerId)) {
                continue;
            }
            WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
            Double anchorY = WaPaperState.getWallAnchorY(playerId);
            if (plane == null || anchorY == null) {
                continue;
            }
            WaNetwork.sendWallSync(target, playerId, true, plane.axisX, plane.value, anchorY.doubleValue());
        }
    }
}
