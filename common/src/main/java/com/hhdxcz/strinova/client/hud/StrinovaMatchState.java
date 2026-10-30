package com.hhdxcz.strinova.client.hud;

import java.util.Locale;

/**
 * 对局状态数据契约（客户端侧）：模式、阶段、回合、倒计时、双方比分。
 * 当前由模拟数据填充；未来由服务端对局系统 S2C 同步。
 */
public final class StrinovaMatchState {

    public enum Phase {
        WARMUP("hud.strinova.match.warmup"),
        ROUND(""),
        ROUND_END("hud.strinova.match.round_end"),
        MATCH_END("");

        private final String labelKey;

        Phase(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    private static volatile boolean active;
    private static volatile String modeKey = "hud.strinova.match.mode.demolition";
    private static volatile Phase phase = Phase.WARMUP;
    private static volatile int round = 1;
    private static volatile int roundSecondsLeft = 0;
    private static volatile int alliesScore = 0;
    private static volatile int enemiesScore = 0;
    private static volatile boolean alliesAttacking = true;
    private static volatile String alliesTeamKey = "";
    private static volatile String enemiesTeamKey = "";

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
        phase = value == null ? Phase.WARMUP : value;
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

    /** 队伍名 lang key（真实界面显示剪刀手/欧泊等阵营名），空字符串时回退为「我方/对方」。 */
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

    /** 回合倒计时格式化为 mm:ss。 */
    public static String formattedTime() {
        int total = Math.max(0, roundSecondsLeft);
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }

    public static void clear() {
        active = false;
        modeKey = "hud.strinova.match.mode.demolition";
        phase = Phase.WARMUP;
        round = 1;
        roundSecondsLeft = 0;
        alliesScore = 0;
        enemiesScore = 0;
        alliesAttacking = true;
        alliesTeamKey = "";
        enemiesTeamKey = "";
    }
}
