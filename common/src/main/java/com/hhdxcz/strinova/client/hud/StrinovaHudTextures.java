package com.hhdxcz.strinova.client.hud;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * HUD 贴图资源索引：官方 UI 贴图 → 模组资源路径。
 *
 * <p>素材来源：skowt.cc 的 Strinova 资产包（用户本地 dump，见 docs/ui-assets/README.md）。
 * 已接入的贴图都在 {@code assets/strinova/textures/gui/hud/} 下：</p>
 * <ul>
 *   <li>{@code growth_*.png}：TAB 成长形状（T_UI_HUD_Square/Circle/Triangle）</li>
 *   <li>{@code digits.png}：DP_Num 七段数码数字表</li>
 *   <li>{@code avatars/<agentId>.png}：顶部面板/计分板头像（T_Dynamic_RoleScoreboard_* 56×32）</li>
 *   <li>{@code rolehud/<agentId>.png}：选角界面左侧头像（T_Dynamic_RoleHUD_*，统一 256×256）</li>
 *   <li>{@code select/<agentId>.png}：选角界面底部可选角色头像（T_Dynamic_SelectTalent_* 98×78）</li>
 * </ul>
 */
public final class StrinovaHudTextures {

    /** 7 段数码数字精灵表（DP_Num，11 格 170×256）。 */
    public static final ResourceLocation DIGITS = hud("digits");

    /** 计分板/顶部面板：中途加入/未选角玩家头像（T_Dynamic_RoleScoreboard_HalfEnterEmpty）。 */
    public static final ResourceLocation SCOREBOARD_EMPTY = hud("avatars/empty");

    /** 选角界面左侧默认头像（T_Dynamic_RoleHUD_HalfEnterEmpty）。 */
    public static final ResourceLocation ROLE_HUD_EMPTY = hud("rolehud/empty");

    /** 角色 id → skowt 数字 ID（三套头像共用；部分角色缺某个资源）。 */
    private static final Map<String, String> NUM_BY_AGENT = Map.ofEntries(
            Map.entry("michele", "101"),
            Map.entry("audrey", "105"),
            Map.entry("madeleine", "107"),
            Map.entry("xin", "108"),
            Map.entry("ling", "109"),
            Map.entry("baimo", "110"),
            Map.entry("freesia", "112"),
            Map.entry("flavia", "115"),
            Map.entry("aika", "119"),
            Map.entry("fagrans", "120"),
            Map.entry("xinxia", "124"),
            Map.entry("lawi", "128"),
            Map.entry("yvette", "131"),
            Map.entry("ming", "132"),
            Map.entry("meridith", "133"),
            Map.entry("kanami", "137"),
            Map.entry("xinghui", "146")
    );

    /** 有移动端 HUD 头像（T_Dynamic_RoleHUDMobile_*）的角色。 */
    private static final Set<String> MOBILE_IDS = Set.of(
            "michele", "audrey", "madeleine", "xin", "ling", "baimo", "freesia", "flavia",
            "aika", "xinxia", "lawi", "yvette", "ming", "meridith", "kanami", "xinghui");

    /** 选角左侧面板头像：缺少 flavia 的 RoleHUD 素材。 */
    private static final Map<String, ResourceLocation> ROLE_HUD_BY_NAME = new HashMap<>();

    /** 选角底部可选角色头像（SelectTalent）。 */
    private static final Map<String, ResourceLocation> SELECT_BY_NAME = new HashMap<>();

    /** 顶部面板/计分板头像（RoleScoreboard）。 */
    private static final Map<String, ResourceLocation> SCOREBOARD_BY_NAME = new HashMap<>();

    /** TAB 计分板头像（RoleHUDMobile，100×100 方图）。 */
    private static final Map<String, ResourceLocation> MOBILE_BY_NAME = new HashMap<>();

    static {
        for (StrinovaAgentRoster.Agent agent : StrinovaAgentRoster.agents()) {
            if (agent.id() == null || agent.name() == null) {
                continue;
            }
            String num = NUM_BY_AGENT.get(agent.id());
            if (num == null) {
                continue;
            }
            if (!"flavia".equals(agent.id())) {
                ROLE_HUD_BY_NAME.put(agent.name(), hud("rolehud/" + agent.id()));
            }
            SELECT_BY_NAME.put(agent.name(), hud("select/" + agent.id()));
            SCOREBOARD_BY_NAME.put(agent.name(), hud("avatars/" + agent.id()));
            if (MOBILE_IDS.contains(agent.id())) {
                MOBILE_BY_NAME.put(agent.name(), hud("avatars_mobile/" + agent.id()));
            }
        }
    }

    private StrinovaHudTextures() {
    }

    /** 按角色 id 取计分板头像贴图；没有对应素材时返回 null（调用方可回退空头像）。 */
    public static ResourceLocation avatar(String agentId) {
        if (agentId == null) {
            return null;
        }
        for (StrinovaAgentRoster.Agent agent : StrinovaAgentRoster.agents()) {
            if (agentId.equals(agent.id())) {
                return SCOREBOARD_BY_NAME.get(agent.name());
            }
        }
        return null;
    }

    /** 按图鉴展示名取顶部面板/计分板头像（RoleScoreboard）；未收录返回 null。 */
    public static ResourceLocation avatarByAgentName(String agentName) {
        return agentName == null ? null : SCOREBOARD_BY_NAME.get(agentName);
    }

    /** 按图鉴展示名取 TAB 计分板头像（RoleHUDMobile 100×100）；未收录返回 null。 */
    public static ResourceLocation mobileAvatarByAgentName(String agentName) {
        return agentName == null ? null : MOBILE_BY_NAME.get(agentName);
    }

    /** 按图鉴展示名取选角界面左侧头像（RoleHUD）；未收录返回 null。 */
    public static ResourceLocation roleHudByAgentName(String agentName) {
        return agentName == null ? null : ROLE_HUD_BY_NAME.get(agentName);
    }

    /** 按图鉴展示名取选角界面底部可选角色头像（SelectTalent）；未收录返回 null。 */
    public static ResourceLocation selectTalentByAgentName(String agentName) {
        return agentName == null ? null : SELECT_BY_NAME.get(agentName);
    }

    private static ResourceLocation hud(String path) {
        return new ResourceLocation("strinova", "textures/gui/hud/" + path + ".png");
    }
}
