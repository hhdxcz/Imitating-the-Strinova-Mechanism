package com.hhdxcz.strinova.forge.client;

import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.collision.StrinovaCompoundCollision;
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
import com.hhdxcz.strinova.client.StrinovaAgentSelectScreen;
import com.hhdxcz.strinova.client.StrinovaGrowthScreen;
import com.hhdxcz.strinova.client.StrinovaCollisionMenuScreen;
import com.hhdxcz.strinova.client.StrinovaCombatSettingsScreen;
import com.hhdxcz.strinova.client.StrinovaHudEditorScreen;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudRenderer;
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
        // 战斗 HUD：加载本地配置并注册渲染层
        StrinovaHudConfig.load();
        StrinovaHudRenderer.register();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(StrinovaClientInitForge::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(StrinovaClientInitForge::onRegisterClientCommands);
        // H4：客户端加入服务器时把本地完整碰撞配置上报给服务端
        MinecraftForge.EVENT_BUS.addListener(StrinovaClientInitForge::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(StrinovaClientInitForge::onClientLogout);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        StrinovaKeyBindingsForge.register(event);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("strinova_client")
                .then(Commands.literal("edit_collision")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            if (client.player != null) {
                                client.tell(() -> client.setScreen(new StrinovaCollisionMenuScreen(client.screen, client.player)));
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.translatable("command.strinova.client.no_player"));
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
                                        }))))
                .then(Commands.literal("hud")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            client.tell(() -> client.setScreen(new StrinovaCombatSettingsScreen(client.screen)));
                            return 1;
                        })
                        .then(Commands.literal("editor")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    client.tell(() -> client.setScreen(new StrinovaHudEditorScreen(client.screen)));
                                    return 1;
                                }))
                        .then(Commands.literal("mock")
                                .executes(ctx -> {
                                    boolean on = StrinovaHudRenderer.toggleMock();
                                    ctx.getSource().sendSuccess(() -> Component.translatable(on
                                            ? "config.strinova.hud.mock.on"
                                            : "config.strinova.hud.mock.off"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("test")
                                .executes(ctx -> {
                                    StrinovaHudRenderer.triggerTestHit();
                                    StrinovaHudRenderer.triggerTestKill();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("config.strinova.hud.mock.test"), false);
                                    return 1;
                                })))
                .then(Commands.literal("agent")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            client.tell(() -> client.setScreen(new StrinovaAgentSelectScreen(client.screen)));
                            return 1;
                        }))
                .then(Commands.literal("growth")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            client.tell(() -> client.setScreen(new StrinovaGrowthScreen(client.screen)));
                            return 1;
                        }))
                .then(Commands.literal("scoreboard")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            client.tell(() -> client.setScreen(
                                    new com.hhdxcz.strinova.client.StrinovaScoreboardEditorScreen(client.screen)));
                            return 1;
                        })
                        .then(Commands.literal("style")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    client.tell(() -> client.setScreen(
                                            new com.hhdxcz.strinova.client.StrinovaScoreboardStyleScreen(client.screen)));
                                    return 1;
                                }))
                        .then(Commands.literal("export")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    com.hhdxcz.strinova.client.hud.StrinovaHudConfig config =
                                            com.hhdxcz.strinova.client.hud.StrinovaHudConfig.get();
                                    String json = config.scoreboardExportJson();
                                    java.nio.file.Path path =
                                            com.hhdxcz.strinova.client.hud.StrinovaHudConfig.scoreboardExportPath();
                                    try {
                                        java.nio.file.Files.createDirectories(path.getParent());
                                        java.nio.file.Files.writeString(path, json, java.nio.charset.StandardCharsets.UTF_8);
                                    } catch (Exception e) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("写入文件失败: " + e.getMessage()), false);
                                    }
                                    if (client.keyboardHandler != null) {
                                        client.keyboardHandler.setClipboard(json);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "已导出计分板配置（已复制到剪贴板）: " + path), false);
                                    return 1;
                                }))
                        .then(Commands.literal("import")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    String clip = "";
                                    try {
                                        if (client.getWindow() != null) {
                                            clip = org.lwjgl.glfw.GLFW.glfwGetClipboardString(
                                                    client.getWindow().getWindow());
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                    if (clip == null || clip.isBlank()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "剪贴板为空：先执行 /strinova_client scoreboard export，或粘贴导出的 JSON 到剪贴板"), false);
                                        return 0;
                                    }
                                    com.hhdxcz.strinova.client.hud.StrinovaHudConfig config =
                                            com.hhdxcz.strinova.client.hud.StrinovaHudConfig.get();
                                    if (config.scoreboardImportJson(clip)) {
                                        config.save();
                                        ctx.getSource().sendSuccess(() -> Component.literal("已导入并保存计分板配置"), false);
                                        return 1;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "导入失败：剪贴板内容不是有效的计分板配置 JSON"), false);
                                    return 0;
                                }))
                        .then(Commands.literal("values")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    String values = com.hhdxcz.strinova.client.hud.StrinovaHudConfig.get().scoreboardValuesSummary();
                                    String json = com.hhdxcz.strinova.client.hud.StrinovaHudConfig.get().scoreboardValuesJson();
                                    if (client.keyboardHandler != null) {
                                        client.keyboardHandler.setClipboard(values);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("计分板数值（已复制到剪贴板）："), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(values), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("配置文件片段：" + json), false);
                                    return 1;
                                })))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            var player = client.player;
                            String uuidState = player == null ? "n/a" : ("wall=" + com.hhdxcz.strinova.paper.WaPaperState.isWall(player.getUUID())
                                    + ",paper=" + com.hhdxcz.strinova.paper.WaPaperState.isPaper(player.getUUID())
                                    + ",fly=" + com.hhdxcz.strinova.paper.WaPaperState.isFly(player.getUUID()));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Strinova: enabled=" + com.hhdxcz.strinova.StrinovaMod.isEnabled()
                                            + " serverHandshake=" + (com.hhdxcz.strinova.StrinovaMod.lastRemoteEnabledMs() != 0L)
                                            + " mock=" + StrinovaHudRenderer.isMockEnabled()
                                            + " match=" + com.hhdxcz.strinova.client.hud.StrinovaHudState.isServerMatchActive()
                                            + " | taczLoaded=" + com.hhdxcz.strinova.client.hud.StrinovaTaczCompat.isTaczLoaded()
                                            + " held=" + com.hhdxcz.strinova.client.hud.StrinovaTaczCompat.heldItemId(player)
                                            + " cam=" + (client.options == null ? "?" : client.options.getCameraType())
                                            + " screen=" + (client.screen != null)
                                            + " " + uuidState), false);
                            return 1;
                        }));
        event.getDispatcher().register(root);
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            StrinovaNetwork.sendCollisionConfig(client.player.getUUID());
        }
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WaPaperState.clearAll();
        StrinovaAirJumpClientState.clearAll();
        StrinovaCollisionBoxTuning.clearAll();
        StrinovaCompoundCollision.clearAll();
        com.hhdxcz.strinova.client.StrinovaClientNotices.reset();
    }
}
