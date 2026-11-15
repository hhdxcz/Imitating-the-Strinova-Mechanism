package com.hhdxcz.strinova.client.hud;

import java.util.List;
import java.util.Locale;

/**
 * 对局状态数据契约（客户端侧）：模式、阶段、回合、阶段倒计时、双方比分、回合记录。
 * 当前由服务端 S2C 对局包填充（{@code /strinova match start} 开始对局）。
 */
public final class StrinovaMatchState {

    /** 一条回合记录：哪一方赢下该回合（我方 true / 敌方 false），赢法（炸弹爆炸/拆除/灭队/时间到）。 */
    public record RoundResult(boolean allyWon, String reasonKey) {
    }

    /**
     * 对局阶段（与服务端 {@code StrinovaMatchManager.Phase} 顺序一致，网络层按 ordinal 传输）。
     */
    public enum Phase {
        /** 选角阶段（开局 / 中场）：客户端自动弹出选角界面，倒计时结束进入预备。 */
        SELECT("hud.strinova.match.phase.select"),
        /** 预备阶段：倒计时结束后开始战斗。 */
        PREP("hud.strinova.match.phase.prep"),
        /** 战斗阶段。 */
        COMBAT(""),
        /** 休整阶段：回合结算。 */
        BREAK("hud.strinova.match.round_end"),
        /** 对局结束：宣判胜负。 */
        MATCH_END("");

        private final String labelKey;

        Phase(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }

        /** 按 ordinal 取阶段（越界回退到 COMBAT）。 */
        public static Phase byOrdinal(int ordinal) {
            Phase[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return COMBAT;
            }
            return values[ordinal];
        }
    }

    private static volatile boolean active;
    private static volatile String modeKey = "hud.strinova.match.mode.demolition";
    private static volatile Phase phase = Phase.SELECT;
    private static volatile int round = 1;
    /** 当前阶段剩余秒数（选角 20 / 预备 30 / 战斗 120 / 休整 8）。 */
    private static volatile int roundSecondsLeft = 0;
    private static volatile int alliesScore = 0;
    private static volatile int enemiesScore = 0;
    private static volatile boolean alliesAttacking = true;
    private static volatile String alliesTeamKey = "";
    private static volatile String enemiesTeamKey = "";
    /** 获胜所需回合数（顶部面板「目标 9 回合」）。 */
    private static volatile int targetWins = 9;
    /** 胜者：0 = 未决出，1 = 我方，2 = 敌方。 */
    private static volatile int winner = 0;
    /** 已结束回合的记录（左侧竖栏「我方/敌方」那几行、中场选角的回合胜利计数）。 */
    private static volatile List<RoundResult> roundResults = List.of();
    /**
     * 对局中按 H 换角色时的待生效角色 id：锁定后不立即改配置，下一回合预备阶段才应用。
     */
    private static volatile String pendingAgentId = "";

    private StrinovaMatchState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static String getModeKey() {
        return modeKey;
    }

    public static void setModeKey(String key) {
        modeKey = key == null ? "hud.strinova.match.mode.demolition" : key;
    }

    public static Phase getPhase() {
        return phase;
    }

    public static void setPhase(Phase value) {
        phase = value == null ? Phase.SELECT : value;
    }

    /** 是否处于选角阶段（开局选角 / 中场换边选角）。 */
    public static boolean isSelectPhase() {
        return active && phase == Phase.SELECT;
    }

    /** 是否处于对局进行中（除选角/结束外的战斗流程阶段）。 */
    public static boolean isInRoundFlow() {
        return active && (phase == Phase.PREP || phase == Phase.COMBAT || phase == Phase.BREAK);
    }

    public static int getRound() {
        return round;
    }

    public static void setRound(int value) {
        round = Math.max(1, value);
    }

    public static int getRoundSecondsLeft() {
        return roundSecondsLeft;
    }

    public static void setRoundSecondsLeft(int seconds) {
        roundSecondsLeft = Math.max(0, seconds);
    }

    public static int getAlliesScore() {
        return alliesScore;
    }

    public static int getEnemiesScore() {
        return enemiesScore;
    }

    public static void setScores(int allies, int enemies) {
        alliesScore = Math.max(0, allies);
        enemiesScore = Math.max(0, enemies);
    }

    public static boolean areAlliesAttacking() {
        return alliesAttacking;
    }

    public static void setAlliesAttacking(boolean value) {
        alliesAttacking = value;
    }

    /** 队伍名 lang key（真实界面显示剪刀手/欧泊等阵营名），空字符串时回退到「我方/对方」。 */
    public static String getAlliesTeamKey() {
        return alliesTeamKey;
    }

    public static void setAlliesTeamKey(String key) {
        alliesTeamKey = key == null ? "" : key;
    }

    public static String getEnemiesTeamKey() {
        return enemiesTeamKey;
    }

    public static void setEnemiesTeamKey(String key) {
        enemiesTeamKey = key == null ? "" : key;
    }

    public static List<RoundResult> roundResults() {
        return roundResults;
    }

    public static void setRoundResults(List<RoundResult> results) {
        roundResults = results == null ? List.of() : List.copyOf(results);
    }

    public static int getTargetWins() {
        return targetWins;
    }

    public static void setTargetWins(int value) {
        targetWins = Math.max(1, value);
    }

    public static int getWinner() {
        return winner;
    }

    public static void setWinner(int value) {
        winner = Math.max(0, Math.min(2, value));
    }

    public static String getPendingAgentId() {
        return pendingAgentId;
    }

    public static void setPendingAgentId(String agentId) {
        pendingAgentId = agentId == null ? "" : agentId;
    }

    /** 回合阶段倒计时格式化为 mm:ss。 */
    public static String formattedTime() {
        int total = Math.max(0, roundSecondsLeft);
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }

    public static void clear() {
        active = false;
        modeKey = "hud.strinova.match.mode.demolition";
        phase = Phase.SELECT;
        round = 1;
        roundSecondsLeft = 0;
        alliesScore = 0;
        enemiesScore = 0;
        alliesAttacking = true;
        alliesTeamKey = "";
        enemiesTeamKey = "";
        targetWins = 9;
        winner = 0;
        roundResults = List.of();
        pendingAgentId = "";
    }
}
