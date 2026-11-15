package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaMatchState;
import net.minecraft.client.Minecraft;

/**
 * 对局流程的客户端侧（每客户端 tick 调用一次）：
 * <ul>
 *   <li>选角阶段（开局 / 中场换边）自动弹出选角界面，阶段结束后自动关闭；</li>
 *   <li>对局中按 H 换角色：锁定后先挂起，下一回合预备阶段才真正应用（下回合生效）。</li>
 * </ul>
 */
public final class StrinovaMatchClientFlow {

    /** 上一 tick 看到的阶段（用于检测阶段切换）。 */
    private static StrinovaMatchState.Phase lastPhase;
    /** 本次选角阶段是否已经自动弹出过（玩家手动关掉后不再反复弹）。 */
    private static boolean autoOpenedThisSelect;

    private StrinovaMatchClientFlow() {
    }

    /** 每客户端 tick 调用一次。 */
    public static void tick(Minecraft client) {
        if (client == null || client.level == null) {
            lastPhase = null;
            autoOpenedThisSelect = false;
            return;
        }
        boolean active = StrinovaMatchState.isActive();
        StrinovaMatchState.Phase phase = StrinovaMatchState.getPhase();
        if (!active) {
            if (lastPhase != null && client.screen instanceof StrinovaAgentSelectScreen select
                    && select.isMatchAutoOpened()) {
                client.setScreen(null);
            }
            lastPhase = null;
            autoOpenedThisSelect = false;
            return;
        }

        // 1) 选角阶段自动弹出选角界面（开局 20 秒 / 中场换边 20 秒）
        if (phase == StrinovaMatchState.Phase.SELECT) {
            if (!autoOpenedThisSelect) {
                autoOpenedThisSelect = true;
                if (client.screen == null) {
                    client.setScreen(new StrinovaAgentSelectScreen(client.screen, true));
                }
            }
        } else if (lastPhase == StrinovaMatchState.Phase.SELECT) {
            // 选角阶段结束：把流程弹出的选角界面关掉，回到战斗
            autoOpenedThisSelect = false;
            if (client.screen instanceof StrinovaAgentSelectScreen select && select.isMatchAutoOpened()) {
                client.setScreen(null);
            }
        }

        // 2) 下一回合预备阶段：应用「按 H 换角色」挂起的角色
        if (phase == StrinovaMatchState.Phase.PREP && lastPhase != StrinovaMatchState.Phase.PREP) {
            applyPendingAgent();
        }
        lastPhase = phase;
    }

    /** 对局中按 H：打开选角界面（对局中锁定 → 下回合生效）。 */
    public static boolean tryOpenChangeAgent(Minecraft client) {
        if (client == null || client.screen != null) {
            return false;
        }
        if (!StrinovaMatchState.isActive()) {
            return false;
        }
        client.setScreen(new StrinovaAgentSelectScreen(client.screen));
        return true;
    }

    /** 把挂起的换角色结果写进配置（下一回合开始时调用）。 */
    private static void applyPendingAgent() {
        String pending = StrinovaMatchState.getPendingAgentId();
        if (pending == null || pending.isEmpty()) {
            return;
        }
        StrinovaHudConfig config = StrinovaHudConfig.get();
        config.selectedAgentId = pending;
        config.save();
        StrinovaMatchState.setPendingAgentId("");
        // 上报服务端（TAB 计分板 / 顶部头像拿着角色名）
        StrinovaClientProfileSync.markDirty();
    }

    /** 对局结束/断开时清理流程状态。 */
    public static void reset() {
        lastPhase = null;
        autoOpenedThisSelect = false;
    }
}
