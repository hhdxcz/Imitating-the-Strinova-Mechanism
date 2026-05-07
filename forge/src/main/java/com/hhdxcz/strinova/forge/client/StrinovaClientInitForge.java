package com.hhdxcz.strinova.forge.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.paper.WaPaperState;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpClientState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import com.hhdxcz.strinova.client.StrinovaCollisionMenuScreen;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

public final class StrinovaClientInitForge {
    private static final List<String> WALL_KEY_OPTIONS = List.of(
            "r", "f", "g", "v", "c", "x", "z", "q", "e", "t",
            "1", "2", "3", "4", "5",
            "mouse.3", "mouse.4", "mouse.5",
            "key.keyboard.r", "key.keyboard.f", "key.keyboard.g",
            "default", "reset"
    );
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_WALL_KEYS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String option : WALL_KEY_OPTIONS) {
            if (option.contains(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    };

    private StrinovaClientInitForge() {
    }

    public static void init() {
        StrinovaNetwork.initClient();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(StrinovaClientInitForge::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(StrinovaClientInitForge::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(StrinovaClientInitForge::onClientLogout);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        StrinovaKeyBindingsForge.register(event);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("wa_client")
                .then(Commands.literal("edit_collision")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            if (client.player != null) {
                                client.tell(() -> client.setScreen(new StrinovaCollisionMenuScreen(client.screen, client.player)));
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.translatable("command.klbq.client.no_player"));
                            return 0;
                        }))
                .then(Commands.literal("key")
                        .then(Commands.literal("wall")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(SUGGEST_WALL_KEYS)
                                        .executes(ctx -> {
                                            String key = StringArgumentType.getString(ctx, "key");
                                            boolean ok = StrinovaKeyBindingsForge.rebindWallKey(key);
                                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "已修改贴墙键位" : "修改失败：无效键位或未初始化"), false);
                                            return ok ? 1 : 0;
                                        }))));
        event.getDispatcher().register(root);
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WaPaperState.clearAll();
        StrinovaAirJumpClientState.clearAll();
        StrinovaCollisionBoxTuning.clearAll();
    }
}
