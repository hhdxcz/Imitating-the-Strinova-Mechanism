package com.hhdxcz.strinova.gameplay;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对局状态管理器（服务端）：回合制爆破流程 + 比分 + 回合记录，供 HUD / 命令 / 网络层读取。
 *
 * <p>完整流程（对齐真实界面）：</p>
 * <ol>
 *   <li>对局开始 → 选角阶段 40 秒（客户端自动弹出选角界面）；</li>
 *   <li>预备阶段 30 秒 → 战斗阶段 120 秒 → 休整阶段 8 秒；</li>
 *   <li>每回合结束记一分，先赢 {@link #TARGET_WINS} 回合的一方获胜；</li>
 *   <li>打满 {@link #ROUNDS_PER_HALF} 回合进入中场：攻防互换 + 再弹一次选角界面（20 秒）。</li>
 * </ol>
 *
 * <p>演示规则：倒计时归零时防守方得分；某队灭队时另一队得分。</p>
 * <p>并发约定：名单/回合记录使用 CopyOnWriteArrayList（读多写少），比分使用 AtomicInteger，标量字段 volatile。</p>
 */
public final class StrinovaMatchManager {

    /** 获胜所需回合数（先赢 9 回合）。 */
    public static final int TARGET_WINS = 9;
    /** 半场回合数：打满 8 回合进入中场换边。 */
    public static final int ROUNDS_PER_HALF = 8;
    /** 选角阶段时长（秒）。 */
    public static final int SELECT_SECONDS = 40;
    /** 预备阶段时长（秒）。 */
    public static final int PREP_SECONDS = 30;
    /** 战斗阶段时长（秒）。 */
    public static final int COMBAT_SECONDS = 120;
    /** 休整阶段时长（秒）。 */
    public static final int BREAK_SECONDS = 8;
    /** 对局结束（宣判胜负）展示时长（秒），到点自动停掉对局。 */
    public static final int MATCH_END_SECONDS = 15;

    /** 对局阶段。 */
    public enum Phase {
        /** 选角阶段：客户端自动弹出选角界面。 */
        SELECT,
        /** 预备阶段：倒计时结束后开始战斗。 */
        PREP,
        /** 战斗阶段：灭队或时间耗尽结束回合。 */
        COMBAT,
        /** 休整阶段：回合结算，结束后进入下一回合预备/中场。 */
        BREAK,
        /** 对局结束：已宣判胜负。 */
        MATCH_END
    }

    /** 回合记录上限（最多 17 回合）。 */
    private static final int MAX_ROUND_RESULTS = 32;
    /** 进攻方阵营 HUD 本地化键。 */
    private static final String ATTACK_TEAM_KEY = "hud.strinova.faction.scissors";
    /** 防守方阵营 HUD 本地化键。 */
    private static final String DEFEND_TEAM_KEY = "hud.strinova.faction.opal";

    /** 我方队伍名单。 */
    private static final List<UUID> ALLIES = new CopyOnWriteArrayList<>();
    /** 敌方队伍名单。 */
    private static final List<UUID> ENEMIES = new CopyOnWriteArrayList<>();
    /** 我方（玩家所在队伍）比分。 */
    private static final AtomicInteger ALLIES_SCORE = new AtomicInteger(0);
    /** 敌方比分。 */
    private static final AtomicInteger ENEMIES_SCORE = new AtomicInteger(0);
    /** 已结束回合记录：哪方赢下 + 赢法（炸弹爆炸/拆除炸弹/灭队/时间耗尽）。 */
    private static final List<RoundResult> ROUND_RESULTS = new CopyOnWriteArrayList<>();

    /** 一条回合记录：allyWon=true 表示我方赢下该回合，reasonKey 指向赢法 lang key。 */
    public record RoundResult(boolean allyWon, String reasonKey) {
    }

    /** 对局是否进行中。 */
    private static volatile boolean active = false;
    /** 当前阶段。 */
    private static volatile Phase phase = Phase.SELECT;
    /** 当前阶段剩余秒数。 */
    private static volatile int phaseSecondsLeft = SELECT_SECONDS;
    /** 当前回合数（从 1 开始）。 */
    private static volatile int round = 1;
    /** 我方是否处于进攻方（中场换边后翻转）。 */
    private static volatile boolean alliesAttacking = true;
    /** 是否已经完成中场换边。 */
    private static volatile boolean halftimeDone = false;
    /** 胜者：0 = 未决出，1 = 我方，2 = 敌方。 */
    private static volatile int winner = 0;
    /** 倒计时节流：上次处理所在的 server 秒（server tick / 20）。 */
    private static volatile int lastCountdownSecond = Integer.MIN_VALUE;

    private StrinovaMatchManager() {
    }

    // 开始对局：按在线玩家列表顺序交替分配两队，比分 0:0，round=1，进入选角阶段
    public static void start(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ALLIES.clear();
        ENEMIES.clear();
        ROUND_RESULTS.clear();
        int index = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }
            UUID playerId = player.getUUID();
            if (playerId == null) {
                continue;
            }
            if ((index & 1) == 0) {
                ALLIES.add(playerId);
            } else {
                ENEMIES.add(playerId);
            }
            index++;
        }
        ALLIES_SCORE.set(0);
        ENEMIES_SCORE.set(0);
        round = 1;
        alliesAttacking = true;
        halftimeDone = false;
        winner = 0;
        phase = Phase.SELECT;
        phaseSecondsLeft = SELECT_SECONDS;
        // 以开始对局时的 server 秒为基准，避免 start 后首个 tick 立刻扣掉 1 秒
        lastCountdownSecond = server.getTickCount() / 20;
        active = true;
    }

    // 停止对局并清空队伍/比分/回合记录，回到初始状态
    public static void stop() {
        active = false;
        ALLIES.clear();
        ENEMIES.clear();
        ROUND_RESULTS.clear();
        ALLIES_SCORE.set(0);
        ENEMIES_SCORE.set(0);
        round = 1;
        alliesAttacking = true;
        halftimeDone = false;
        winner = 0;
        phase = Phase.SELECT;
        phaseSecondsLeft = SELECT_SECONDS;
        lastCountdownSecond = Integer.MIN_VALUE;
    }

    // 查询对局是否进行中
    public static boolean isActive() {
        return active;
    }

    // 调试用：让当前阶段立即结束（下一秒 tick 时推进）
    public static void skipPhase() {
        if (active) {
            phaseSecondsLeft = 1;
        }
    }

    // 每 server tick 调用：内部按 server 秒节流，每秒推进一次阶段倒计时/结算
    public static void tick(MinecraftServer server) {
        if (server == null || !active) {
            return;
        }
        int serverSecond = server.getTickCount() / 20;
        if (serverSecond == lastCountdownSecond) {
            return; // 同一秒内只处理一次，兼容"每 tick 调用"与"每秒调用"两种接入方式
        }
        lastCountdownSecond = serverSecond;

        if (phase == Phase.MATCH_END) {
            // 宣判胜负后展示一段时间，到点自动结束对局（客户端 HUD 恢复）
            if (phaseSecondsLeft > 0) {
                phaseSecondsLeft--;
                if (phaseSecondsLeft <= 0) {
                    stop();
                }
            }
            return;
        }
        // 战斗阶段：灭队判定优先
        if (phase == Phase.COMBAT && checkTeamWipe(server)) {
            return;
        }

        int remaining = phaseSecondsLeft - 1;
        if (remaining > 0) {
            phaseSecondsLeft = remaining;
            return;
        }
        switch (phase) {
            case SELECT -> startPrep();
            case PREP -> startCombat();
            case COMBAT -> {
                // 时间耗尽：本回合防守方获胜
                finishRound(!alliesAttacking, "hud.strinova.match.reason.timeout");
            }
            case BREAK -> {
                // 半场：打满 ROUNDS_PER_HALF 回合后中场换边并重新选角
                if (round > ROUNDS_PER_HALF && !halftimeDone) {
                    enterHalftime();
                } else {
                    startPrep();
                }
            }
            default -> {
            }
        }
    }

    /** 进入预备阶段。 */
    private static void startPrep() {
        phase = Phase.PREP;
        phaseSecondsLeft = PREP_SECONDS;
    }

    /** 进入战斗阶段。 */
    private static void startCombat() {
        phase = Phase.COMBAT;
        phaseSecondsLeft = COMBAT_SECONDS;
    }

    /** 中场：攻防互换 + 重新选角。 */
    private static void enterHalftime() {
        halftimeDone = true;
        alliesAttacking = !alliesAttacking;
        phase = Phase.SELECT;
        phaseSecondsLeft = SELECT_SECONDS;
    }

    // 回合结束：记录赢家/赢法、加分；达到目标回合数直接宣判胜负，否则进入休整阶段
    private static void finishRound(boolean allyWon, String reasonKey) {
        if (phase == Phase.MATCH_END) {
            return;
        }
        ROUND_RESULTS.add(new RoundResult(allyWon, reasonKey));
        while (ROUND_RESULTS.size() > MAX_ROUND_RESULTS) {
            ROUND_RESULTS.remove(0);
        }
        int allyScore = allyWon ? ALLIES_SCORE.incrementAndGet() : ALLIES_SCORE.get();
        int enemyScore = allyWon ? ENEMIES_SCORE.get() : ENEMIES_SCORE.incrementAndGet();
        if (allyScore >= TARGET_WINS || enemyScore >= TARGET_WINS) {
            winner = allyScore >= TARGET_WINS ? 1 : 2;
            phase = Phase.MATCH_END;
            phaseSecondsLeft = MATCH_END_SECONDS;
            return;
        }
        round++;
        phase = Phase.BREAK;
        phaseSecondsLeft = BREAK_SECONDS;
    }

    /** 灭队判定：某队全部阵亡、另一队还有存活时直接结束回合。 */
    private static boolean checkTeamWipe(MinecraftServer server) {
        if (ALLIES.isEmpty() || ENEMIES.isEmpty()) {
            return false;
        }
        int alliesAlive = 0;
        int enemiesAlive = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.isDeadOrDying()) {
                continue;
            }
            UUID playerId = player.getUUID();
            if (playerId == null) {
                continue;
            }
            if (ALLIES.contains(playerId)) {
                alliesAlive++;
            } else if (ENEMIES.contains(playerId)) {
                enemiesAlive++;
            }
        }
        if (alliesAlive == 0 && enemiesAlive > 0) {
            finishRound(false, "hud.strinova.match.reason.elimination");
            return true;
        }
        if (enemiesAlive == 0 && alliesAlive > 0) {
            finishRound(true, "hud.strinova.match.reason.elimination");
            return true;
        }
        return false;
    }

    // 查询某玩家是否属于我方；null/不在名单内返回 false
    public static boolean isAlly(UUID playerId) {
        return playerId != null && ALLIES.contains(playerId);
    }

    // 我方名单快照（只读，取快照后不受后续对局状态变化影响）
    public static List<UUID> allies() {
        return List.copyOf(ALLIES);
    }

    // 敌方名单快照（只读）
    public static List<UUID> enemies() {
        return List.copyOf(ENEMIES);
    }

    public static int alliesScore() {
        return ALLIES_SCORE.get();
    }

    public static int enemiesScore() {
        return ENEMIES_SCORE.get();
    }

    public static int round() {
        return round;
    }

    /** 当前阶段剩余秒数（HUD 倒计时显示这个值）。 */
    public static int secondsLeft() {
        return phaseSecondsLeft;
    }

    public static int phaseSecondsLeft() {
        return phaseSecondsLeft;
    }

    public static Phase phase() {
        return phase;
    }

    public static boolean alliesAttacking() {
        return alliesAttacking;
    }

    public static int targetWins() {
        return TARGET_WINS;
    }

    /** 胜者：0 = 未决出，1 = 我方，2 = 敌方。 */
    public static int winner() {
        return winner;
    }

    /** 我方阵营 HUD 队伍本地化键（进攻时剪刀手 / 防守时欧泊）。 */
    public static String alliesTeamKey() {
        return alliesAttacking ? ATTACK_TEAM_KEY : DEFEND_TEAM_KEY;
    }

    /** 敌方阵营 HUD 队伍本地化键。 */
    public static String enemiesTeamKey() {
        return alliesAttacking ? DEFEND_TEAM_KEY : ATTACK_TEAM_KEY;
    }

    // 回合记录快照（左侧竖栏「我方/敌方 + 赢法」用）
    public static List<RoundResult> roundResults() {
        return List.copyOf(ROUND_RESULTS);
    }

    // 玩家退出：从两队名单中移除（不补位，演示用）
    public static void onPlayerQuit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ALLIES.remove(playerId);
        ENEMIES.remove(playerId);
    }
}
