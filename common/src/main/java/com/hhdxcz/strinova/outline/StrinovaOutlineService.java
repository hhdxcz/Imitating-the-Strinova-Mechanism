package com.hhdxcz.strinova.outline;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 玩家描边服务：通过记分板队伍颜色实现玩家发光描边效果
public final class StrinovaOutlineService {
    private static final String TEAM_PREFIX = "strinova_outline_";
    private static final ConcurrentHashMap<UUID, String> PREV_TEAM_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> PREV_GLOWING_BY_PLAYER = new ConcurrentHashMap<>();

    private StrinovaOutlineService() {
    }

    // 设置玩家的描边颜色，保存原始队伍和发光状态以便恢复
    public static boolean setOutline(ServerPlayer player, String colorName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(colorName, "colorName");
        ChatFormatting color = parseColor(colorName);
        if (color == null) {
            return false;
        }

        MinecraftServer server = player.server;
        Scoreboard scoreboard = server.getScoreboard();

        UUID id = player.getUUID();
        String playerKey = player.getScoreboardName();

        // 保存玩家原始队伍（非本 mod 创建的队伍）
        PlayerTeam prevTeam = scoreboard.getPlayersTeam(playerKey);
        if (prevTeam != null && !prevTeam.getName().startsWith(TEAM_PREFIX)) {
            PREV_TEAM_BY_PLAYER.putIfAbsent(id, prevTeam.getName());
        }
        PREV_GLOWING_BY_PLAYER.putIfAbsent(id, player.isCurrentlyGlowing());

        PlayerTeam targetTeam = getOrCreateTeam(scoreboard, color);
        scoreboard.addPlayerToTeam(playerKey, targetTeam);
        player.setGlowingTag(true);
        return true;
    }

    // 清除玩家的描边，恢复原始队伍和发光状态
    public static void clearOutline(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        MinecraftServer server = player.server;
        Scoreboard scoreboard = server.getScoreboard();

        UUID id = player.getUUID();
        String playerKey = player.getScoreboardName();

        PlayerTeam current = scoreboard.getPlayersTeam(playerKey);
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            scoreboard.removePlayerFromTeam(playerKey, current);
        }

        String prevTeamName = PREV_TEAM_BY_PLAYER.remove(id);
        if (prevTeamName != null && !prevTeamName.isBlank()) {
            PlayerTeam prev = scoreboard.getPlayerTeam(prevTeamName);
            if (prev != null) {
                scoreboard.addPlayerToTeam(playerKey, prev);
            }
        }

        Boolean prevGlowing = PREV_GLOWING_BY_PLAYER.remove(id);
        if (prevGlowing != null) {
            player.setGlowingTag(prevGlowing.booleanValue());
        } else {
            player.setGlowingTag(false);
        }
    }

    // 获取或创建指定颜色的小队
    private static PlayerTeam getOrCreateTeam(Scoreboard scoreboard, ChatFormatting color) {
        String teamName = TEAM_PREFIX + color.getName();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            if (team.getColor() != color) {
                team.setColor(color);
            }
            return team;
        }

        PlayerTeam created = scoreboard.addPlayerTeam(teamName);
        created.setColor(color);
        return created;
    }

    // 解析颜色名：支持英文 ChatFormatting 名和中文名
    private static ChatFormatting parseColor(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return switch (s) {
            case "black" -> ChatFormatting.BLACK;
            case "dark_blue" -> ChatFormatting.DARK_BLUE;
            case "dark_green" -> ChatFormatting.DARK_GREEN;
            case "dark_aqua" -> ChatFormatting.DARK_AQUA;
            case "dark_red" -> ChatFormatting.DARK_RED;
            case "dark_purple" -> ChatFormatting.DARK_PURPLE;
            case "gold" -> ChatFormatting.GOLD;
            case "gray", "gra" -> ChatFormatting.GRAY;
            case "dark_gray" -> ChatFormatting.DARK_GRAY;
            case "blue" -> ChatFormatting.BLUE;
            case "green" -> ChatFormatting.GREEN;
            case "aqua" -> ChatFormatting.AQUA;
            case "red" -> ChatFormatting.RED;
            case "light_purple" -> ChatFormatting.LIGHT_PURPLE;
            case "yellow" -> ChatFormatting.YELLOW;
            case "white" -> ChatFormatting.WHITE;
            case "黑色" -> ChatFormatting.BLACK;
            case "深蓝" -> ChatFormatting.DARK_BLUE;
            case "深绿" -> ChatFormatting.DARK_GREEN;
            case "深青" -> ChatFormatting.DARK_AQUA;
            case "深红" -> ChatFormatting.DARK_RED;
            case "深紫" -> ChatFormatting.DARK_PURPLE;
            case "金色" -> ChatFormatting.GOLD;
            case "灰色" -> ChatFormatting.GRAY;
            case "深灰" -> ChatFormatting.DARK_GRAY;
            case "蓝色" -> ChatFormatting.BLUE;
            case "绿色" -> ChatFormatting.GREEN;
            case "青色" -> ChatFormatting.AQUA;
            case "红色" -> ChatFormatting.RED;
            case "淡紫" -> ChatFormatting.LIGHT_PURPLE;
            case "黄色" -> ChatFormatting.YELLOW;
            case "白色" -> ChatFormatting.WHITE;
            default -> null;
        };
    }
}