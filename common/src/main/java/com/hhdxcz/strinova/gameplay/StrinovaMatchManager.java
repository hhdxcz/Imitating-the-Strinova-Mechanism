package com.hhdxcz.strinova.gameplay;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

// 对局状态管理器（服务端）：维护两队名单、比分、回合、倒计时与回合记录，供 HUD / 命令 / 网络层读取。
// 只维护内存数据，不直接操作玩家实体。演示规则：倒计时归零时防守方得分；某队灭队时另一队得分。
// 并发约定：名单/回合记录使用 CopyOnWriteArrayList（读多写少），比分使用 AtomicInteger，标量字段 volatile。
public final class StrinovaMatchManager {

    /** 单回合时长（秒）。 */
    private static final int ROUND_SECONDS = 90;
    /** 进攻方（我方）HUD 本地化键。 */
    private static final String ALLIES_TEAM_KEY = "hud.strinova.faction.scissors";
    /** 防守方（敌方）HUD 本地化键。 */
    private static final String ENEMIES_TEAM_KEY = "hud.strinova.faction.opal";
    /** 回合记录上限。 */
    private static final int MAX_ROUND_RESULTS = 16;

    /** 进攻方（我方）队伍名单。 */
    private static final List<UUID> ALLIES = new CopyOnWriteArrayList<>();
    /** 防守方（敌方）队伍名单。 */
    private static final List<UUID> ENEMIES = new CopyOnWriteArrayList<>();
    /** 我方比分。 */
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
    /** 当前回合数（从 1 开始）。 */
    private static volatile int round = 1;
    /** 当前回合剩余秒数。 */
    private static volatile int secondsLeft = ROUND_SECONDS;
    /** 倒计时节流：上次处理所在的 server 秒（server tick / 20）。 */
    private static volatile int lastCountdownSecond = Integer.MIN_VALUE;

    private StrinovaMatchManager() {
    }

    // 开始对局：按在线玩家列表顺序交替分配两队，比分 0:0，round=1，seconds=90，active=true
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
        secondsLeft = ROUND_SECONDS;
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
        secondsLeft = ROUND_SECONDS;
        lastCountdownSecond = Integer.MIN_VALUE;
    }

    // 查询对局是否进行中
    public static boolean isActive() {
        return active;
    }

    // 每 server tick 调用：内部按 server 秒节流，每秒倒计时一次；
    // 先判定灭队，再处理时间耗尽（演示规则，暂未接入炸弹爆炸/拆除）
    public static void tick(MinecraftServer server) {
        if (server == null || !active) {
            return;
        }
        int serverSecond = server.getTickCount() / 20;
        if (serverSecond == lastCountdownSecond) {
            return; // 同一秒内只倒计时一次，兼容"每 tick 调用"与"每秒调用"两种接入方式
        }
        lastCountdownSecond = serverSecond;

        // 灭队判定优先：某队全部阵亡则由另一队赢下本回合
        if (checkTeamWipe(server)) {
            return;
        }

        int remaining = secondsLeft - 1;
        if (remaining > 0) {
            secondsLeft = remaining;
            return;
        }

        // 时间耗尽：防守方（敌方）得分并进入下一回合
        finishRound(false, "hud.strinova.match.reason.timeout");
    }

    // 回合结束：记录赢家/赢法、加分并进入下一回合
    private static void finishRound(boolean allyWon, String reasonKey) {
        ROUND_RESULTS.add(new RoundResult(allyWon, reasonKey));
        while (ROUND_RESULTS.size() > MAX_ROUND_RESULTS) {
            ROUND_RESULTS.remove(0);
        }
        if (allyWon) {
            ALLIES_SCORE.incrementAndGet();
        } else {
            ENEMIES_SCORE.incrementAndGet();
        }
        round++;
        secondsLeft = ROUND_SECONDS;
    }

    // 灭队判定：某队全部阵亡、另一队还有存活时直接结束回合
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

    // 查询某玩家是否属于我方（进攻方）；null/不在名单内返回 false
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

    public static int secondsLeft() {
        return secondsLeft;
    }

    // 我方（进攻方）HUD 队伍本地化键（固定值）
    public static String alliesTeamKey() {
        return ALLIES_TEAM_KEY;
    }

    // 敌方（防守方）HUD 队伍本地化键（固定值）
    public static String enemiesTeamKey() {
        return ENEMIES_TEAM_KEY;
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
