package com.hhdxcz.strinova.client.hud;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * HUD 贴图资源索引：官方 UI 贴图 → 模组资源路径。
 *
 * <p>素材来源：skowt.cc 的 Strinova 资产包（用户本地 dump，见 docs/ui-assets/README.md）。
 * 已接入的贴图都在 {@code assets/strinova/textures/gui/hud/} 下。</p>
 */
public final class StrinovaHudTextures {

    /** 7 段数码数字精灵表（DP_Num，11 格 170×256）。 */
    public static final ResourceLocation DIGITS = hud("digits");

    /** 角色 id → skowt 角色数字 ID（只收录有 Scoreboard 头像的角色）。 */
    private static final Map<String, String> AVATAR_FILE_BY_AGENT = Map.ofEntries(
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

    /** 角色名 → 头像贴图（启动时构建一次，避免每帧遍历图鉴）。 */
    private static final Map<String, ResourceLocation> AVATAR_BY_NAME = new HashMap<>();

    static {
        for (StrinovaAgentRoster.Agent agent : StrinovaAgentRoster.agents()) {
            ResourceLocation avatar = avatar(agent.id());
            if (avatar != null && agent.name() != null) {
                AVATAR_BY_NAME.put(agent.name(), avatar);
            }
        }
    }

    private StrinovaHudTextures() {
    }

    /** 按角色 id 取头像贴图；没有对应素材时返回 null（调用方回退首字头像）。 */
    public static ResourceLocation avatar(String agentId) {
        if (agentId == null) {
            return null;
        }
        String file = AVATAR_FILE_BY_AGENT.get(agentId);
        return file == null ? null : hud("avatars/" + file);
    }

    /** 按图鉴展示名取头像贴图；未收录角色返回 null。 */
    public static ResourceLocation avatarByAgentName(String agentName) {
        return agentName == null ? null : AVATAR_BY_NAME.get(agentName);
    }

    private static ResourceLocation hud(String path) {
        return new ResourceLocation("strinova", "textures/gui/hud/" + path + ".png");
    }
}
