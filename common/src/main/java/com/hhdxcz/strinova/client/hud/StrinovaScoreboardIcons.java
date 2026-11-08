package com.hhdxcz.strinova.client.hud;

import net.minecraft.resources.ResourceLocation;

/**
 * TAB 计分板状态图标（来自官方 UI 精灵表 SS_UI_ScoreboardPC 的切片）。
 *
 * <p>对应关系（用户提供）：</p>
 * <ul>
 *   <li>{@link #DISCONNECT}：玩家断链，显示在 PING 列；</li>
 *   <li>{@link #MEDAL}：输出/技能/协作分最高的一项，显示在数值右侧；</li>
 *   <li>{@link #BOMB_ALLY}/{@link #BOMB_ENEMY}：炸弹爆炸（我方/敌方回合）；</li>
 *   <li>{@link #DEFUSE_ALLY}/{@link #DEFUSE_ENEMY}：成功拆除炸弹；</li>
 *   <li>{@link #TIMEOUT_ALLY}/{@link #TIMEOUT_ENEMY}：进攻超时；</li>
 *   <li>{@link #ELIMINATION_ALLY}/{@link #ELIMINATION_ENEMY}：全部击杀（灭队）；</li>
 *   <li>{@link #FACTION_BANNER}：阵营名下方的横幅（白色原图，按队伍色染色）。</li>
 * </ul>
 */
public final class StrinovaScoreboardIcons {

    public static final ResourceLocation DISCONNECT = icon("disconnect");
    public static final ResourceLocation MEDAL = icon("medal");
    public static final ResourceLocation BOMB_ALLY = icon("bomb_ally");
    public static final ResourceLocation BOMB_ENEMY = icon("bomb_enemy");
    public static final ResourceLocation DEFUSE_ALLY = icon("defuse_ally");
    public static final ResourceLocation DEFUSE_ENEMY = icon("defuse_enemy");
    public static final ResourceLocation TIMEOUT_ALLY = icon("timeout_ally");
    public static final ResourceLocation TIMEOUT_ENEMY = icon("timeout_enemy");
    public static final ResourceLocation ELIMINATION_ALLY = icon("elimination_ally");
    public static final ResourceLocation ELIMINATION_ENEMY = icon("elimination_enemy");
    public static final ResourceLocation FACTION_BANNER = icon("faction_banner");
    /** 剪刀手阵营徽记（粉色翅膀）。 */
    public static final ResourceLocation SCISSORS_EMBLEM = icon("faction_scissors");
    /** 欧泊阵营徽记（蓝色 P.U.S 盾）。 */
    public static final ResourceLocation OPAL_EMBLEM = icon("faction_opal");

    private StrinovaScoreboardIcons() {
    }

    /** 回合结果图标：reasonKey 来自 StrinovaMatchManager，allyWon 决定蓝/红变体。 */
    public static ResourceLocation roundIcon(String reasonKey, boolean allyWon) {
        if (reasonKey == null) {
            return null;
        }
        return switch (reasonKey) {
            case "hud.strinova.match.reason.explode" -> allyWon ? BOMB_ALLY : BOMB_ENEMY;
            case "hud.strinova.match.reason.defuse" -> allyWon ? DEFUSE_ALLY : DEFUSE_ENEMY;
            case "hud.strinova.match.reason.timeout" -> allyWon ? TIMEOUT_ALLY : TIMEOUT_ENEMY;
            case "hud.strinova.match.reason.elimination" -> allyWon ? ELIMINATION_ALLY : ELIMINATION_ENEMY;
            default -> null;
        };
    }

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("strinova", "textures/gui/scoreboard/" + name + ".png");
    }
}
