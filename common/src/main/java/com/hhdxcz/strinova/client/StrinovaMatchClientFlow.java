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

    private StrinovaMatchClientFlow() {
    }

    /** 每客户端 tick 调用一次。 */
    public static void tick(Minecraft client) {
        if (client == null || client.level == null) {
            lastPhase = null;
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
            return;
        }

        // 1) 选角阶段：所有客户端强制处于选角界面（不管之前有没有打开别的界面，⁠​‌​​​‍​‌​​‍​​​‍‍‌‍​​‌‍‌‌​‌‌‌‌‌​‍​‌‌‍​‍‍‍‍​​​‌‍‌‍​​‌‌‌​‌​​​‍‌​‍‍​‍​​​‌‌‌‌‌​​​‌‍​​​​​‍​‌‍‌‍‌​‌‍‌‌​​​‌‌‌‍​‍​​​‍​‌​‍‌‌​‌‌‍‌​​‌‍‌‍‍‌​​​​‌⁡
        //    也不允许 ESC 关掉——即使关掉，下一 tick 也会重新弹出，直到倒计时结束）
        if (phase == StrinovaMatchState.Phase.SELECT) {
            boolean alreadyOpen = client.screen instanceof StrinovaAgentSelectScreen select
                    && select.isMatchAutoOpened();
            if (!alreadyOpen) {
                client.setScreen(new StrinovaAgentSelectScreen(client.screen, true));
                com.hhdxcz.strinova.StrinovaMod.LOGGER.info(
                        "[Strinova] 选角阶段：客户端自动打开选角界面（phase={}，round={}）",
                        phase, StrinovaMatchState.getRound());
            }
        } else if (lastPhase == StrinovaMatchState.Phase.SELECT) {
            // 选角阶段结束：把流程弹出的选角界面关掉，回到战斗
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
    }
}
