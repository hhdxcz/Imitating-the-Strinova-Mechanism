package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaGrowthState;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import net.minecraft.client.Minecraft;

/**
 * 客户端档案上报（角色 + 成长等级 + 三类增幅点数）。
 *
 * <p>服务端靠 {@code strinova:c2s_profile} 才知道客户端选了谁、增幅网络点到几级、三类点数各多少，
 * 再通过 {@code s2c_match_state} 把角色名 / 成长等级 / 分色点数回填到 TAB 计分板与头像，
 * 让全队都能看到彼此的方块 / 圆 / 三角次数。上报时机：</p>
 * <ul>
 *   <li>进入世界后的第一帧 tick 发一次；</li>
 *   <li>选角锁定（{@link StrinovaAgentSelectScreen#lockSelection()}）、增幅网络购买 / 刷新返还 /
 *       切换觉醒后调用 {@link #markDirty()}，下一 tick 立即发送；</li>
 *   <li>其余时间每 {@value #HEARTBEAT_MS} 毫秒心跳补发一次，兜住服务端重载 / 丢包。</li>
 * </ul>
 *
 * <p>客户端 tick 由 fabric / forge 的平台客户端入口注册（见 StrinovaClientInitFabric / Forge）。</p>
 */
public final class StrinovaClientProfileSync {

    /** 无变化时的心跳间隔（毫秒）。 */
    private static final long HEARTBEAT_MS = 2000L;

    /** 有变化待上报（选角 / 购买 / 切换觉醒后置 true）。 */
    private static volatile boolean dirty = true;
    /** 当前世界是否已经完成首帧上报。 */
    private static volatile boolean sentInWorld;
    /** 上次发送时间戳（毫秒）。 */
    private static long lastSentMs;

    private StrinovaClientProfileSync() {
    }

    /** 标记档案已变化：下一个客户端 tick 立即上报服务端。 */
    public static void markDirty() {
        dirty = true;
    }

    /** 离开世界 / 断线时调用：下次进入世界重新做首帧上报。 */
    public static void reset() {
        dirty = true;
        sentInWorld = false;
        lastSentMs = 0L;
    }

    /** 客户端 tick 钩子：满足「首帧 / 有变化 / 心跳到期」时发送 C2S_PROFILE。 */
    public static void tick() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.level == null) {
                // 不在世界里：下次进世界重新首帧上报
                sentInWorld = false;
                return;
            }
            long now = System.currentTimeMillis();
            boolean firstTick = !sentInWorld;
            if (!firstTick && !dirty && now - lastSentMs < HEARTBEAT_MS) {
                return;
            }

            String agentId = "";
            try {
                StrinovaHudConfig config = StrinovaHudConfig.get();
                if (config != null && config.selectedAgentId != null) {
                    agentId = config.selectedAgentId;
                }
            } catch (Throwable ignored) {
                // 配置尚未加载时按未选角处理
            }

            int growthLevel = 1;
            int weaponPoints = 0;
            int skillPoints = 0;
            int defensePoints = 0;
            int awakeningMask = 0;
            try {
                growthLevel = StrinovaGrowthState.growthLevel();
                weaponPoints = StrinovaGrowthState.purchasedWeapon();
                skillPoints = StrinovaGrowthState.purchasedSkill();
                defensePoints = StrinovaGrowthState.purchasedDefense();
                awakeningMask = StrinovaGrowthState.activeAwakeningMask();
            } catch (Throwable ignored) {
                // 成长数据不可用时回退默认值（等级 1、点数 0）
            }

            StrinovaNetwork.sendProfile(agentId, growthLevel, weaponPoints, skillPoints, defensePoints,
                    awakeningMask);
            dirty = false;
            sentInWorld = true;
            lastSentMs = now;
        } catch (Throwable ignored) {
            // tick 链路不允许异常外溢；保持 dirty 状态，下一个 tick 重试
        }
    }
}
