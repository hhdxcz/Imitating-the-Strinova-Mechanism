package com.hhdxcz.strinova.fabric;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.command.StrinovaServerCommands;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.gameplay.StrinovaFlyRuntime;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class StrinovaModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        StrinovaMod.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StrinovaServerCommands.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer target = handler.getPlayer();
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
            WaPaperState.clearPlayer(playerId);
            StrinovaCollisionBoxTuning.clearPlayer(playerId);
            StrinovaAirJumpRuntime.reset(playerId);
            StrinovaFlyRuntime.reset(playerId);
            StrinovaNetwork.broadcastWall(server, playerId, false, false, 0.0D, 0.0D);
            StrinovaNetwork.broadcastPaper(server, playerId, false);
            StrinovaNetwork.broadcastFly(server, playerId, false);
        });
    }
}
