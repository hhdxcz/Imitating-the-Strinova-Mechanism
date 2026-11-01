package com.hhdxcz.strinova.gameplay;

import com.hhdxcz.strinova.client.hud.StrinovaAgentRoster;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端玩家档案（选角 + 成长等级 + 三类增幅点数）。
 *
 * <p>数据来源：客户端通过 {@code strinova:c2s_profile} 上报（选角锁定 / 增幅网络购买 / 刷新返还 /
 * 切换觉醒后）。服务端不信任客户端：{@link #set(UUID, String, int, int, int, int)} 会校验角色 id
 * 必须存在于 {@link StrinovaAgentRoster}（纯数据类，服务端可用），成长等级 clamp 到 0~9，
 * 三类点数 clamp 到 0~99。</p>
 *
 * <p>下发：{@code strinova:s2c_match_state} 的玩家条目里附带 {@link #agentName(UUID)}、
 * {@link #growthLevel(UUID)} 以及 {@link #weaponPoints(UUID)} / {@link #skillPoints(UUID)} /
 * {@link #defensePoints(UUID)}，供 TAB 计分板显示全队的分色点数。</p>
 *
 * <p>并发：使用 {@link ConcurrentHashMap}，网络线程写入、服务端主线程读取均安全。</p>
 */
public final class StrinovaPlayerProfile {

    /** 一名玩家的档案快照。{@code agentId} 为空串表示尚未选角。 */
    public record Profile(String agentId, int growthLevel,
                          int weaponPoints, int skillPoints, int defensePoints) {
    }

    /** 成长等级下限。 */
    public static final int MIN_GROWTH_LEVEL = 0;
    /** 成长等级上限（与计分板成长等级一致）。 */
    public static final int MAX_GROWTH_LEVEL = 9;
    /** 未上报时的默认成长等级。 */
    public static final int DEFAULT_GROWTH_LEVEL = 1;
    /** 单类增幅点数上限（防恶意包）。 */
    public static final int MAX_GROWTH_POINTS = 99;

    private static final Profile DEFAULT = new Profile("", DEFAULT_GROWTH_LEVEL, 0, 0, 0);
    private static final Map<UUID, Profile> PROFILES = new ConcurrentHashMap<>();

    private StrinovaPlayerProfile() {
    }

    /**
     * 写入玩家档案（服务端校验入口）。
     *
     * @param playerId     玩家 UUID
     * @param agentId      角色 id，可为空串（表示未选角）；非空时必须能在图鉴中查到
     * @param growthLevel  成长等级，越界会被 clamp 到 0~9
     * @param weaponPoints 蓝方块（武器网络）点数，clamp 到 0~99
     * @param skillPoints  黄圆（主动+被动技能）点数，clamp 到 0~99
     * @param defensePoints 绿三角（护甲+弦化）点数，clamp 到 0~99
     * @return true 表示已写入；false 表示参数非法（角色 id 查不到 / UUID 为空），本次上报被忽略
     */
    public static boolean set(UUID playerId, String agentId, int growthLevel,
                              int weaponPoints, int skillPoints, int defensePoints) {
        if (playerId == null) {
            return false;
        }
        String id = agentId == null ? "" : agentId;
        // 空串表示「尚未选角」，允许；非空必须是图鉴里存在的角色 id，否则整包忽略
        if (!id.isEmpty() && StrinovaAgentRoster.byId(id) == null) {
            return false;
        }
        PROFILES.put(playerId, new Profile(id, clampGrowthLevel(growthLevel),
                clampGrowthPoints(weaponPoints), clampGrowthPoints(skillPoints),
                clampGrowthPoints(defensePoints)));
        return true;
    }

    /** 兼容旧调用：分色点数按 0 处理。 */
    public static boolean set(UUID playerId, String agentId, int growthLevel) {
        return set(playerId, agentId, growthLevel, 0, 0, 0);
    }

    /** 读取玩家档案；不存在时返回默认档案（agentId 空串、成长等级 1、点数全 0），永不为 null。 */
    public static Profile get(UUID playerId) {
        if (playerId == null) {
            return DEFAULT;
        }
        Profile profile = PROFILES.get(playerId);
        return profile == null ? DEFAULT : profile;
    }

    /** 玩家当前角色名（图鉴展示名）；未选角或已下线时为空串。 */
    public static String agentName(UUID playerId) {
        Profile profile = get(playerId);
        if (profile.agentId() == null || profile.agentId().isEmpty()) {
            return "";
        }
        StrinovaAgentRoster.Agent agent = StrinovaAgentRoster.byId(profile.agentId());
        return agent == null || agent.name() == null ? "" : agent.name();
    }

    /** 玩家当前成长等级（0~9，未上报时为 1）。 */
    public static int growthLevel(UUID playerId) {
        return get(playerId).growthLevel();
    }

    /** 蓝方块（武器网络）点数。 */
    public static int weaponPoints(UUID playerId) {
        return get(playerId).weaponPoints();
    }

    /** 黄圆（主动+被动技能）点数。 */
    public static int skillPoints(UUID playerId) {
        return get(playerId).skillPoints();
    }

    /** 绿三角（护甲+弦化）点数。 */
    public static int defensePoints(UUID playerId) {
        return get(playerId).defensePoints();
    }

    /** 成长等级规范化：clamp 到 0~9。 */
    public static int clampGrowthLevel(int value) {
        return Math.max(MIN_GROWTH_LEVEL, Math.min(MAX_GROWTH_LEVEL, value));
    }

    /** 分色点数规范化：clamp 到 0~99。 */
    public static int clampGrowthPoints(int value) {
        return Math.max(0, Math.min(MAX_GROWTH_POINTS, value));
    }

    /** 移除单个玩家档案（玩家退出时调用）。 */
    public static void remove(UUID playerId) {
        if (playerId != null) {
            PROFILES.remove(playerId);
        }
    }

    /** 清空全部档案（服务器停止 / 切换存档时调用，避免同一 JVM 内残留旧数据）。 */
    public static void removeAll() {
        PROFILES.clear();
    }
}
