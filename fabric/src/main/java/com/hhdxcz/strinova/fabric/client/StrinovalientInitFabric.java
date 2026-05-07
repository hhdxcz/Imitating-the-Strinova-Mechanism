package com.hhdxcz.strinova.fabric.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpClientState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.hhdxcz.strinova.client.StrinovaCollisionMenuScreen;

import java.util.List;

public class StrinovalientInitFabric implements ClientModInitializer {
    private static final List<String> WALL_KEY_OPTIONS = List.of(
            "r", "f", "g", "v", "c", "x", "z", "q", "e", "t",
            "1", "2", "3", "4", "5",
            "mouse.3", "mouse.4", "mouse.5",
            "key.keyboard.r", "key.keyboard.f", "key.keyboard.g",
            "default", "reset"
    );
    private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_WALL_KEYS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String option : WALL_KEY_OPTIONS) {
            if (option.contains(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitializeClient() {
        StrinovaNetwork.initClient();
        StrinovaKeyBindings.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WaPaperState.clearAll();
            StrinovaAirJumpClientState.clearAll();
            StrinovaCollisionBoxTuning.clearAll();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("wa_client")
                        .then(ClientCommandManager.literal("edit_collision")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    if (client.player != null) {
                                        client.tell(() -> client.setScreen(new StrinovaCollisionMenuScreen(client.screen, client.player)));
                                        return 1;
                                    }
                                    ctx.getSource().sendFeedback(Component.translatable("command.strinova.client.no_player"));
                                    return 0;
                                }))
                        .then(ClientCommandManager.literal("key")
                                .then(ClientCommandManager.literal("wall")
                                        .then(ClientCommandManager.argument("key", StringArgumentType.word())
                                        .suggests(SUGGEST_WALL_KEYS)
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    boolean ok = StrinovaKeyBindings.rebindWallKey(key);
                                                    ctx.getSource().sendFeedback(Component.literal(ok ? "已修改贴墙键位" : "修改失败：无效键位或未初始化"));
                                                    return ok ? 1 : 0;
                                                }))))));
    }
}
