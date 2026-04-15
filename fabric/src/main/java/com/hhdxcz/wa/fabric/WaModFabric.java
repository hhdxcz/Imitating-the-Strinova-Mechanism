package com.hhdxcz.wa.fabric;

import com.hhdxcz.wa.WaMod;
import com.hhdxcz.wa.collision.WaCollisionBoxTuning;
import com.hhdxcz.wa.command.WaServerCommands;
import com.hhdxcz.wa.gameplay.WaAirJumpRuntime;
import com.hhdxcz.wa.gameplay.WaAirJumpSettings;
import com.hhdxcz.wa.gameplay.WaFlyRuntime;
import com.hhdxcz.wa.net.WaNetwork;
import com.hhdxcz.wa.paper.WaPaperState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class WaModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        WaMod.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WaServerCommands.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer target = handler.getPlayer();
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
                boolean wall = WaPaperState.isWall(playerId);
                if (wall) {
                    WaPaperState.WallPlane plane = WaPaperState.getWallPlane(playerId);
                    Double anchorY = WaPaperState.getWallAnchorY(playerId);
                    if (plane == null || anchorY == null) {
                        continue;
                    }
                    WaNetwork.sendWallSync(target, playerId, true, plane.axisX, plane.value, anchorY.doubleValue());
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            WaPaperState.clearPlayer(playerId);
            WaCollisionBoxTuning.clearPlayer(playerId);
            WaAirJumpRuntime.reset(playerId);
            WaFlyRuntime.reset(playerId);
            WaNetwork.broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
            WaNetwork.broadcastPaper(server, playerId, false);
            WaNetwork.broadcastFly(server, playerId, false);
        });
    }
}
