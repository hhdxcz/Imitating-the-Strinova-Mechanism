package com.hhdxcz.wa.forge.client;

import com.hhdxcz.wa.collision.WaCollisionBoxTuning;
import com.hhdxcz.wa.net.WaNetwork;
import com.hhdxcz.wa.paper.WaPaperState;
import com.hhdxcz.wa.gameplay.WaAirJumpClientState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import com.hhdxcz.wa.client.WaCollisionMenuScreen;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

public final class ClientInitForge {
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

    private ClientInitForge() {
    }

    public static void init() {
        WaNetwork.initClient();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientInitForge::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(ClientInitForge::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(ClientInitForge::onClientLogout);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        WaKeyBindingsForge.register(event);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("wa_client")
                .then(Commands.literal("edit_collision")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            if (client.player != null) {
                                client.tell(() -> client.setScreen(new WaCollisionMenuScreen(client.screen, client.player)));
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
                                            boolean ok = WaKeyBindingsForge.rebindWallKey(key);
                                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "已修改贴墙键位" : "修改失败：无效键位或未初始化"), false);
                                            return ok ? 1 : 0;
                                        }))));
        event.getDispatcher().register(root);
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WaPaperState.clearAll();
        WaAirJumpClientState.clearAll();
        WaCollisionBoxTuning.clearAll();
    }
}
