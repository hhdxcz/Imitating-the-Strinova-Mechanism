package com.hhdxcz.strinova.command;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.collision.StrinovaCollisionBoxTuning;
import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpRuntime;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import com.hhdxcz.strinova.outline.StrinovaOutlineService;
import com.hhdxcz.strinova.paper.StrinovaPaperDamageReduction;
import com.hhdxcz.strinova.paper.StrinovaWallBlacklist;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrinovaServerCommands {
    private static final Map<String, String> OUTLINE_COLOR_OPTIONS = createOutlineColorOptions();
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BLOCKS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            String idStr = id.toString();
            if (idStr.contains(remaining)) {
                builder.suggest(idStr, Component.translatable(entry.getValue().getDescriptionId()));
            }
            String path = id.getPath();
            if (!path.equals(idStr) && path.contains(remaining)) {
                builder.suggest(path, Component.translatable(entry.getValue().getDescriptionId()));
            }
            String display = Component.translatable(entry.getValue().getDescriptionId()).getString();
            if (!display.isBlank() && !display.contains(" ") && display.contains(builder.getRemaining())) {
                builder.suggest(display, Component.literal(idStr));
            }
        });
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BLACKLISTED_BLOCKS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        List<ResourceLocation> list = StrinovaWallBlacklist.get(ctx.getSource().getServer()).list();
        for (ResourceLocation id : list) {
            String idStr = id.toString();
            var block = BuiltInRegistries.BLOCK.get(id);
            String display = block == null ? "" : Component.translatable(block.getDescriptionId()).getString();
            boolean matched = idStr.contains(remaining);
            if (!matched && !display.isBlank() && !display.contains(" ") && display.contains(builder.getRemaining())) {
                matched = true;
            }
            if (!matched) {
                continue;
            }
            if (block == null || block.defaultBlockState().isAir()) {
                builder.suggest(idStr);
            } else {
                builder.suggest(idStr, Component.translatable(block.getDescriptionId()));
                if (!display.isBlank() && !display.contains(" ")) {
                    builder.suggest(display, Component.literal(idStr));
                }
            }
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_OUTLINE_COLORS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        OUTLINE_COLOR_OPTIONS.forEach((key, zh) -> {
            if (key.contains(remaining)) {
                builder.suggest(key, Component.literal(zh));
            }
            if (zh.contains(remaining)) {
                builder.suggest(zh, Component.literal(key));
            }
        });
        if ("gra".contains(remaining)) {
            builder.suggest("gra", Component.literal("gray"));
        }
        return builder.buildFuture();
    };

    private StrinovaServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var outline = Commands.literal("outline");
        var wall = Commands.literal("wall")
                .requires(source -> source.hasPermission(2));
        var paper = Commands.literal("paper")
                .requires(source -> source.hasPermission(2));
        var jump = Commands.literal("jump");
        var boxPos = Commands.literal("boxpos");
        var boxLen = Commands.literal("boxlen");

        outline.then(Commands.literal("clear")
                .executes(ctx -> {
                    try {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        StrinovaOutlineService.clearOutline(self);
                        ctx.getSource().sendSuccess(() -> Component.literal("已清除描边"), false);
                        return 1;
                    } catch (Exception e) {
                        StrinovaMod.LOGGER.error("Failed to clear outline (self)", e);
                        ctx.getSource().sendFailure(Component.literal("执行失败：清除描边时发生错误"));
                        return 0;
                    }
                })
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            try {
                                Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                for (ServerPlayer player : players) {
                                    StrinovaOutlineService.clearOutline(player);
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal("已清除描边：" + players.size() + " 个玩家"), true);
                                return players.size();
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to clear outline (targets)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：清除描边时发生错误"));
                                return 0;
                            }
                        })));

        outline.then(Commands.literal("off")
                .executes(ctx -> {
                    try {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        StrinovaOutlineService.clearOutline(self);
                        ctx.getSource().sendSuccess(() -> Component.literal("已清除描边"), false);
                        return 1;
                    } catch (Exception e) {
                        StrinovaMod.LOGGER.error("Failed to clear outline via off (self)", e);
                        ctx.getSource().sendFailure(Component.literal("执行失败：清除描边时发生错误"));
                        return 0;
                    }
                })
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            try {
                                Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                for (ServerPlayer player : players) {
                                    StrinovaOutlineService.clearOutline(player);
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal("已清除描边：" + players.size() + " 个玩家"), true);
                                return players.size();
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to clear outline via off (targets)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：清除描边时发生错误"));
                                return 0;
                            }
                        })));

        for (Map.Entry<String, String> entry : OUTLINE_COLOR_OPTIONS.entrySet()) {
            String color = entry.getKey();
            String zh = entry.getValue();
            outline.then(Commands.literal(color)
                    .executes(ctx -> setOutlineColorSelf(ctx, color, zh)));
            outline.then(Commands.literal(zh)
                    .executes(ctx -> setOutlineColorSelf(ctx, color, zh)));
        }

        outline.then(Commands.argument("color", StringArgumentType.word())
                .suggests(SUGGEST_OUTLINE_COLORS)
                .executes(ctx -> {
                    try {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        String color = StringArgumentType.getString(ctx, "color");
                        boolean ok = StrinovaOutlineService.setOutline(self, color);
                        if (!ok) {
                            ctx.getSource().sendFailure(Component.literal("设置失败：无效颜色（仅支持 16 种 ChatFormatting 颜色名，例如 red / blue / dark_purple）"));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("已设置描边颜色为 " + color), false);
                        return 1;
                    } catch (Exception e) {
                        StrinovaMod.LOGGER.error("Failed to set outline (self)", e);
                        ctx.getSource().sendFailure(Component.literal("执行失败：设置描边时发生错误"));
                        return 0;
                    }
                }));

        LiteralArgumentBuilder<CommandSourceStack> outlineSet = Commands.literal("set")
                .requires(source -> source.hasPermission(2));
        RequiredArgumentBuilder<CommandSourceStack, ?> outlineSetTargets = Commands.argument("targets", EntityArgument.players());
        for (Map.Entry<String, String> entry : OUTLINE_COLOR_OPTIONS.entrySet()) {
            String color = entry.getKey();
            String zh = entry.getValue();
            outlineSetTargets.then(Commands.literal(color)
                    .executes(ctx -> setOutlineColorTargets(ctx, color, zh)));
            outlineSetTargets.then(Commands.literal(zh)
                    .executes(ctx -> setOutlineColorTargets(ctx, color, zh)));
        }
        outlineSetTargets.then(Commands.argument("color", StringArgumentType.word())
                .suggests(SUGGEST_OUTLINE_COLORS)
                .executes(ctx -> {
                    try {
                        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                        String color = StringArgumentType.getString(ctx, "color");
                        int ok = 0;
                        for (ServerPlayer player : players) {
                            if (StrinovaOutlineService.setOutline(player, color)) {
                                ok++;
                            }
                        }
                        if (ok == 0) {
                            ctx.getSource().sendFailure(Component.literal("设置失败：无效颜色（仅支持 16 种 ChatFormatting 颜色名，例如 red / blue / dark_purple）"));
                            return 0;
                        }
                        int finalOk = ok;
                        ctx.getSource().sendSuccess(() -> Component.literal("已设置描边颜色为 " + color + "：" + finalOk + " 个玩家"), true);
                        return ok;
                    } catch (Exception e) {
                        StrinovaMod.LOGGER.error("Failed to set outline (targets)", e);
                        ctx.getSource().sendFailure(Component.literal("执行失败：设置描边时发生错误"));
                        return 0;
                    }
                }));
        outlineSet.then(outlineSetTargets);
        outline.then(outlineSet);

        wall.then(Commands.literal("blacklist")
                .then(Commands.literal("add")
                        .then(Commands.argument("block", StringArgumentType.greedyString())
                                .suggests(SUGGEST_BLOCKS)
                                .executes(ctx -> {
                                    try {
                                        String raw = StringArgumentType.getString(ctx, "block");
                                        ResourceLocation id = resolveBlockId(raw);
                                        if (id == null) {
                                            ctx.getSource().sendFailure(Component.literal("无效方块标识：" + raw + "（可用命名空间ID、路径ID或中文方块名）"));
                                            return 0;
                                        }
                                        boolean changed = StrinovaWallBlacklist.get(ctx.getSource().getServer()).add(id);
                                        if (changed) {
                                            StrinovaNetwork.broadcastBlacklist(ctx.getSource().getServer());
                                        }
                                        ctx.getSource().sendSuccess(() -> Component.literal(changed
                                                ? "已加入禁止贴墙名单：" + id
                                                : "已在禁止贴墙名单中：" + id), true);
                                        return changed ? 1 : 0;
                                    } catch (Exception e) {
                                        StrinovaMod.LOGGER.error("Failed to add wall blacklist block", e);
                                        ctx.getSource().sendFailure(Component.literal("执行失败：添加禁止贴墙方块时发生错误"));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("block", StringArgumentType.greedyString())
                                .suggests(SUGGEST_BLACKLISTED_BLOCKS)
                                .executes(ctx -> {
                                    try {
                                        String raw = StringArgumentType.getString(ctx, "block");
                                        ResourceLocation id = resolveBlockId(raw);
                                        if (id == null) {
                                            ctx.getSource().sendFailure(Component.literal("无效方块标识：" + raw + "（可用命名空间ID、路径ID或中文方块名）"));
                                            return 0;
                                        }
                                        boolean changed = StrinovaWallBlacklist.get(ctx.getSource().getServer()).remove(id);
                                        if (changed) {
                                            StrinovaNetwork.broadcastBlacklist(ctx.getSource().getServer());
                                        }
                                        ctx.getSource().sendSuccess(() -> Component.literal(changed
                                                ? "已移除禁止贴墙名单：" + id
                                                : "不在禁止贴墙名单中：" + id), true);
                                        return changed ? 1 : 0;
                                    } catch (Exception e) {
                                        StrinovaMod.LOGGER.error("Failed to remove wall blacklist block", e);
                                        ctx.getSource().sendFailure(Component.literal("执行失败：移除禁止贴墙方块时发生错误"));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            try {
                                int removed = StrinovaWallBlacklist.get(ctx.getSource().getServer()).clear();
                                if (removed > 0) {
                                    StrinovaNetwork.broadcastBlacklist(ctx.getSource().getServer());
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal("已清空禁止贴墙名单：" + removed + " 项"), true);
                                return removed;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to clear wall blacklist", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：清空禁止贴墙名单时发生错误"));
                                return 0;
                            }
                        }))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            try {
                                List<ResourceLocation> list = StrinovaWallBlacklist.get(ctx.getSource().getServer()).list();
                                if (list.isEmpty()) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("禁止贴墙名单为空"), false);
                                    return 0;
                                }
                                list.sort(ResourceLocation::compareTo);
                                int limit = 20;
                                int shown = Math.min(limit, list.size());
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < shown; i++) {
                                    if (i > 0) {
                                        sb.append(", ");
                                    }
                                    sb.append(list.get(i));
                                }
                                if (list.size() > limit) {
                                    sb.append(" ...（共 ").append(list.size()).append(" 项）");
                                }
                                String out = sb.toString();
                                ctx.getSource().sendSuccess(() -> Component.literal(out), false);
                                return list.size();
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to list wall blacklist", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：查询禁止贴墙名单时发生错误"));
                                return 0;
                            }
                        })));

        paper.then(Commands.literal("damage_reduction")
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            try {
                                double v = StrinovaPaperDamageReduction.get();
                                ctx.getSource().sendSuccess(() -> Component.literal("当前弦化减伤倍率：" + v), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to get paper damage reduction", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：查询弦化减伤倍率时发生错误"));
                                return 0;
                            }
                        }))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 1.0D))
                        .executes(ctx -> {
                            try {
                                double raw = DoubleArgumentType.getDouble(ctx, "value");
                                StrinovaCommonConfig.ConfigData next = StrinovaCommonConfig.snapshot();
                                next.paperDamageReduction = raw;
                                StrinovaCommonConfig.update(next);
                                double v = StrinovaPaperDamageReduction.get();
                                ctx.getSource().sendSuccess(() -> Component.literal("已设置弦化减伤倍率为：" + v), true);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to set paper damage reduction", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：设置弦化减伤倍率时发生错误"));
                                return 0;
                            }
                        })));

        jump.then(Commands.literal("get")
                .executes(ctx -> {
                    try {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        int extra = StrinovaAirJumpSettings.get(ctx.getSource().getServer()).getExtraJumps(self.getUUID());
                        if (extra == StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS) {
                            ctx.getSource().sendSuccess(() -> Component.literal("当前段跳次数：无限（额外空中跳：无限）"), false);
                            return 1;
                        }
                        int segments = extra + 1;
                        ctx.getSource().sendSuccess(() -> Component.literal("当前段跳次数：" + segments + "（额外空中跳：" + extra + "）"), false);
                        return 1;
                    } catch (Exception e) {
                        StrinovaMod.LOGGER.error("Failed to get air jump segments", e);
                        ctx.getSource().sendFailure(Component.literal("执行失败：查询段跳次数时发生错误"));
                        return 0;
                    }
                }));

        int maxSegments = StrinovaAirJumpSettings.MAX_EXTRA_JUMPS + 1;
        jump.then(Commands.literal("set")
                .then(Commands.argument("segments", IntegerArgumentType.integer(1, maxSegments))
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                int segments = IntegerArgumentType.getInteger(ctx, "segments");
                                int extra = Math.max(0, segments - 1);
                                var server = ctx.getSource().getServer();
                                StrinovaAirJumpSettings.get(server).setExtraJumps(self.getUUID(), extra);
                                StrinovaAirJumpRuntime.reset(self.getUUID());
                                StrinovaNetwork.sendAirJumpSync(self, self.getUUID(), extra);
                                ctx.getSource().sendSuccess(() -> Component.literal("已设置段跳次数为：" + segments), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to set air jump segments (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：设置段跳次数时发生错误"));
                                return 0;
                            }
                        }))
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("ALL")
                                .executes(ctx -> {
                                    try {
                                        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                        int extra = StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS;
                                        var server = ctx.getSource().getServer();
                                        StrinovaAirJumpSettings settings = StrinovaAirJumpSettings.get(server);
                                        int affected = 0;
                                        for (ServerPlayer player : players) {
                                            settings.setExtraJumps(player.getUUID(), extra);
                                            StrinovaAirJumpRuntime.reset(player.getUUID());
                                            StrinovaNetwork.sendAirJumpSync(player, player.getUUID(), extra);
                                            affected++;
                                        }
                                        int finalAffected = affected;
                                        ctx.getSource().sendSuccess(() -> Component.literal("已设置段跳次数为：无限（影响 " + finalAffected + " 个玩家）"), true);
                                        return affected;
                                    } catch (Exception e) {
                                        StrinovaMod.LOGGER.error("Failed to set air jump segments ALL (targets)", e);
                                        ctx.getSource().sendFailure(Component.literal("执行失败：设置段跳次数时发生错误"));
                                        return 0;
                                    }
                                }))
                        .then(Commands.argument("segments", IntegerArgumentType.integer(1, maxSegments))
                                .executes(ctx -> {
                                    try {
                                        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                        int segments = IntegerArgumentType.getInteger(ctx, "segments");
                                        int extra = Math.max(0, segments - 1);
                                        var server = ctx.getSource().getServer();
                                        StrinovaAirJumpSettings settings = StrinovaAirJumpSettings.get(server);
                                        int changed = 0;
                                        for (ServerPlayer player : players) {
                                            boolean ok = settings.setExtraJumps(player.getUUID(), extra);
                                            StrinovaAirJumpRuntime.reset(player.getUUID());
                                            StrinovaNetwork.sendAirJumpSync(player, player.getUUID(), extra);
                                            if (ok) {
                                                changed++;
                                            }
                                        }
                                        int finalChanged = changed;
                                        ctx.getSource().sendSuccess(() -> Component.literal("已设置段跳次数为：" + segments + "（影响 " + finalChanged + " 个玩家）"), true);
                                        return changed;
                                    } catch (Exception e) {
                                        StrinovaMod.LOGGER.error("Failed to set air jump segments (targets)", e);
                                        ctx.getSource().sendFailure(Component.literal("执行失败：设置段跳次数时发生错误"));
                                        return 0;
                                    }
                                }))));

        double boxMin = -8.0D;
        double boxMax = 8.0D;

        boxPos.then(Commands.literal("reset")
                .executes(ctx -> {
                    try {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        StrinovaCollisionBoxTuning.setSyncOffset(self.getUUID(), 0.0D, 0.0D, 0.0D);
                        StrinovaCollisionBoxTuning.setFlyOffset(self.getUUID(), 0.0D, 0.0D, 0.0D);
                        self.refreshDimensions();
                        StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal("已恢复默认位置（同步/飘飞）"), false);
                        return 1;
                    } catch (Exception e) {
                        StrinovaMod.LOGGER.error("Failed to reset collision box offsets (self)", e);
                        ctx.getSource().sendFailure(Component.literal("执行失败：恢复默认位置时发生错误"));
                        return 0;
                    }
                })
                .then(Commands.literal("sync")
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                StrinovaCollisionBoxTuning.setSyncOffset(self.getUUID(), 0.0D, 0.0D, 0.0D);
                                self.refreshDimensions();
                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal("已恢复默认位置（同步）"), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to reset collision box sync offset (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：恢复默认位置（同步）时发生错误"));
                                return 0;
                            }
                        }))
                .then(Commands.literal("fly")
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                StrinovaCollisionBoxTuning.setFlyOffset(self.getUUID(), 0.0D, 0.0D, 0.0D);
                                self.refreshDimensions();
                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal("已恢复默认位置（飘飞）"), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to reset collision box fly offset (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：恢复默认位置（飘飞）时发生错误"));
                                return 0;
                            }
                        })));

        boxPos.then(Commands.literal("sync")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(boxMin, boxMax))
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(boxMin, boxMax))
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg(boxMin, boxMax))
                                        .executes(ctx -> {
                                            try {
                                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                                double y = DoubleArgumentType.getDouble(ctx, "y");
                                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                                StrinovaCollisionBoxTuning.setSyncOffset(self.getUUID(), x, y, z);
                                                StrinovaCollisionBoxTuning.Tuning t = StrinovaCollisionBoxTuning.getSync(self.getUUID());
                                                self.refreshDimensions();
                                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                                ctx.getSource().sendSuccess(() -> Component.literal("已设置同步碰撞箱位置偏移为：("
                                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetX()) + ", "
                                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetY()) + ", "
                                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetZ()) + ")"), false);
                                                return 1;
                                            } catch (Exception e) {
                                                StrinovaMod.LOGGER.error("Failed to set collision box sync offset (self)", e);
                                                ctx.getSource().sendFailure(Component.literal("执行失败：设置同步碰撞箱位置偏移时发生错误"));
                                                return 0;
                                            }
                                        })))));

        var boxPosFly = Commands.literal("fly");
        var boxPosFlyX = Commands.argument("x", DoubleArgumentType.doubleArg(boxMin, boxMax));
        boxPosFlyX.then(Commands.literal("0")
                .then(Commands.argument("z", DoubleArgumentType.doubleArg(boxMin, boxMax))
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                StrinovaCollisionBoxTuning.setFlyOffset(self.getUUID(), x, 0.0D, z);
                                StrinovaCollisionBoxTuning.Tuning t = StrinovaCollisionBoxTuning.getFly(self.getUUID());
                                self.refreshDimensions();
                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal("已设置飘飞碰撞箱位置偏移为：("
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetX()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetY()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetZ()) + ")"), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to set collision box fly offset (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：设置飘飞碰撞箱位置偏移时发生错误"));
                                return 0;
                            }
                        })));
        boxPosFlyX.then(Commands.argument("y", DoubleArgumentType.doubleArg(boxMin, boxMax))
                .then(Commands.argument("z", DoubleArgumentType.doubleArg(boxMin, boxMax))
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                double y = DoubleArgumentType.getDouble(ctx, "y");
                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                StrinovaCollisionBoxTuning.setFlyOffset(self.getUUID(), x, y, z);
                                StrinovaCollisionBoxTuning.Tuning t = StrinovaCollisionBoxTuning.getFly(self.getUUID());
                                self.refreshDimensions();
                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal("已设置飘飞碰撞箱位置偏移为：("
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetX()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetY()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.offsetZ()) + ")"), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to set collision box fly offset (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：设置飘飞碰撞箱位置偏移时发生错误"));
                                return 0;
                            }
                        })));
        boxPosFly.then(boxPosFlyX);
        boxPos.then(boxPosFly);

        boxLen.then(Commands.literal("sync")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(boxMin, boxMax))
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(boxMin, boxMax))
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg(boxMin, boxMax))
                                        .executes(ctx -> {
                                            try {
                                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                                double y = DoubleArgumentType.getDouble(ctx, "y");
                                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                                StrinovaCollisionBoxTuning.setSyncSize(self.getUUID(), x, y, z);
                                                StrinovaCollisionBoxTuning.Tuning t = StrinovaCollisionBoxTuning.getSync(self.getUUID());
                                                self.refreshDimensions();
                                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                                ctx.getSource().sendSuccess(() -> Component.literal("已设置同步碰撞箱长度增量为：("
                                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeX()) + ", "
                                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeY()) + ", "
                                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeZ()) + ")"), false);
                                                return 1;
                                            } catch (Exception e) {
                                                StrinovaMod.LOGGER.error("Failed to set collision box sync size (self)", e);
                                                ctx.getSource().sendFailure(Component.literal("执行失败：设置同步碰撞箱长度增量时发生错误"));
                                                return 0;
                                            }
                                        })))));

        var boxLenFly = Commands.literal("fly");
        var boxLenFlyX = Commands.argument("x", DoubleArgumentType.doubleArg(boxMin, boxMax));
        boxLenFlyX.then(Commands.literal("0")
                .then(Commands.argument("z", DoubleArgumentType.doubleArg(boxMin, boxMax))
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                StrinovaCollisionBoxTuning.setFlySize(self.getUUID(), x, 0.0D, z);
                                StrinovaCollisionBoxTuning.Tuning t = StrinovaCollisionBoxTuning.getFly(self.getUUID());
                                self.refreshDimensions();
                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal("已设置飘飞碰撞箱长度增量为：("
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeX()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeY()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeZ()) + ")"), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to set collision box fly size (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：设置飘飞碰撞箱长度增量时发生错误"));
                                return 0;
                            }
                        })));
        boxLenFlyX.then(Commands.argument("y", DoubleArgumentType.doubleArg(boxMin, boxMax))
                .then(Commands.argument("z", DoubleArgumentType.doubleArg(boxMin, boxMax))
                        .executes(ctx -> {
                            try {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                double y = DoubleArgumentType.getDouble(ctx, "y");
                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                StrinovaCollisionBoxTuning.setFlySize(self.getUUID(), x, y, z);
                                StrinovaCollisionBoxTuning.Tuning t = StrinovaCollisionBoxTuning.getFly(self.getUUID());
                                self.refreshDimensions();
                                StrinovaNetwork.sendCollisionBoxTuningSync(self, self.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.literal("已设置飘飞碰撞箱长度增量为：("
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeX()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeY()) + ", "
                                        + String.format(java.util.Locale.ROOT, "%.2f", t.sizeZ()) + ")"), false);
                                return 1;
                            } catch (Exception e) {
                                StrinovaMod.LOGGER.error("Failed to set collision box fly size (self)", e);
                                ctx.getSource().sendFailure(Component.literal("执行失败：设置飘飞碰撞箱长度增量时发生错误"));
                                return 0;
                            }
                        })));
        boxLenFly.then(boxLenFlyX);
        boxLen.then(boxLenFly);

        dispatcher.register(Commands.literal("wa").then(outline).then(wall).then(paper).then(jump).then(boxPos).then(boxLen));
    }

    private static int setOutlineColorSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String color, String zh) {
        try {
            ServerPlayer self = ctx.getSource().getPlayerOrException();
            boolean ok = StrinovaOutlineService.setOutline(self, color);
            if (!ok) {
                ctx.getSource().sendFailure(Component.literal("设置失败：无效颜色"));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("已设置描边颜色为 " + zh + "（" + color + "）"), false);
            return 1;
        } catch (Exception e) {
            StrinovaMod.LOGGER.error("Failed to set outline (self)", e);
            ctx.getSource().sendFailure(Component.literal("执行失败：设置描边时发生错误"));
            return 0;
        }
    }

    private static int setOutlineColorTargets(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String color, String zh) {
        try {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
            int ok = 0;
            for (ServerPlayer player : players) {
                if (StrinovaOutlineService.setOutline(player, color)) {
                    ok++;
                }
            }
            if (ok == 0) {
                ctx.getSource().sendFailure(Component.literal("设置失败：无效颜色"));
                return 0;
            }
            int finalOk = ok;
            ctx.getSource().sendSuccess(() -> Component.literal("已设置描边颜色为 " + zh + "（" + color + "）：" + finalOk + " 个玩家"), true);
            return ok;
        } catch (Exception e) {
            StrinovaMod.LOGGER.error("Failed to set outline (targets)", e);
            ctx.getSource().sendFailure(Component.literal("执行失败：设置描边时发生错误"));
            return 0;
        }
    }

    private static Map<String, String> createOutlineColorOptions() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("black", "黑色");
        map.put("dark_red", "深红");
        map.put("dark_gray", "深灰");
        map.put("red", "红色");
        map.put("dark_blue", "深蓝");
        map.put("dark_purple", "深紫");
        map.put("blue", "蓝色");
        map.put("light_purple", "淡紫");
        map.put("dark_green", "深绿");
        map.put("gold", "金色");
        map.put("green", "绿色");
        map.put("yellow", "黄色");
        map.put("dark_aqua", "深青");
        map.put("gray", "灰色");
        map.put("aqua", "青色");
        map.put("white", "白色");
        return map;
    }

    private static ResourceLocation resolveBlockId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        ResourceLocation direct = ResourceLocation.tryParse(s);
        if (direct != null && BuiltInRegistries.BLOCK.containsKey(direct)) {
            return direct;
        }
        if (!s.contains(":")) {
            ResourceLocation minecraftPath = ResourceLocation.tryParse("minecraft:" + s);
            if (minecraftPath != null && BuiltInRegistries.BLOCK.containsKey(minecraftPath)) {
                return minecraftPath;
            }
        }
        String lower = s.toLowerCase();
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (id.getPath().equalsIgnoreCase(s) || id.toString().equalsIgnoreCase(s)) {
                return id;
            }
            String display = Component.translatable(entry.getValue().getDescriptionId()).getString();
            if (!display.isBlank() && display.equalsIgnoreCase(s)) {
                return id;
            }
            if (!display.isBlank() && display.toLowerCase().equals(lower)) {
                return id;
            }
        }
        return null;
    }
}
