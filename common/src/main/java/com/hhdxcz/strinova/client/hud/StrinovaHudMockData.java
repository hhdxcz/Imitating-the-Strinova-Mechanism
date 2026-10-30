package com.hhdxcz.strinova.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟战斗数据：战斗系统完成前用于调试 UI。
 * 通过客户端命令 /strinova_client hud mock 开关；关闭后恢复“生命值取 MC 真实血量”的默认行为。
 */
public final class StrinovaHudMockData {

    private static final String[] WEAPONS = {"警探", "空境", "幻霜", "鸣火"};
    private static final String[] MOCK_AGENTS = {"米雪儿·李", "信", "心夏", "拉薇", "星绘"};
    private static final String[] ENEMY_AGENTS = {"明", "白墨", "伊薇特", "香奈美", "蕾欧娜"};

    private static boolean enabled;
    private static long startMs = System.currentTimeMillis();
    private static long lastFillMs;
    private static long lastKillFeedMs;

    private StrinovaHudMockData() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        startMs = System.currentTimeMillis();
        lastFillMs = 0L;
        lastKillFeedMs = System.currentTimeMillis();
        if (enabled) {
            fill(0.0F);
            fillMatch(0.0F);
            fillScoreboard();
            StrinovaHudState.pushKillFeed("PlayerA", "警探", "PlayerB", false);
            StrinovaHudState.pushKillFeed("PlayerA", "空境", "PlayerC", true);
            StrinovaHudState.pushKillFeed("PlayerD", "鸣火", "PlayerA", false);
        } else {
            StrinovaHudState.clearCombatData();
            StrinovaMatchState.clear();
            StrinovaScoreboardState.clear();
        }
        return enabled;
    }

    public static void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFillMs < 100L) {
            return;
        }
        lastFillMs = now;
        float seconds = (now - startMs) / 1000.0F;
        fill(seconds);
        fillMatch(seconds);
        if (StrinovaScoreboardState.entries().isEmpty()) {
            fillScoreboard();
        }
        if (now - lastKillFeedMs > 4500L) {
            lastKillFeedMs = now;
            long bucket = now / 4500L;
            StrinovaHudState.pushKillFeed("PlayerA", WEAPONS[(int) (bucket % WEAPONS.length)],
                    "PlayerB", bucket % 3L == 0L);
        }
    }

    private static void fill(float seconds) {
        // 真实数值参考：生命约 130/150、护甲 80（截图左下角 130 / 80）
        StrinovaHudState.setHealth(130.0F, 150.0F);
        StrinovaHudState.setArmor(80.0F, 80.0F);
        StrinovaHudState.setBuffs(List.of(
                new StrinovaHudState.BuffEntry("护甲恢复", 0xFF7CE38B),
                new StrinovaHudState.BuffEntry("静心凝势", 0xFF7CE38B)));

        StrinovaHudState.setWeapon("枫鸣", StrinovaHudState.FireMode.SEMI, 20, 205);
        StrinovaHudState.setSelectedSlot(0);
        StrinovaHudState.setWeaponSlots(List.of(
                new StrinovaHudState.WeaponSlot(0, "枫鸣", true),
                new StrinovaHudState.WeaponSlot(1, "小蜜蜂", false),
                new StrinovaHudState.WeaponSlot(2, "忍锋", false),
                new StrinovaHudState.WeaponSlot(3, "减速雷", false),
                new StrinovaHudState.WeaponSlot(4, "治疗雷", false)));

        float cooldownQ = (float) Math.max(0.0D, Math.sin(seconds * 0.9D));
        float cooldownC = (float) Math.max(0.0D, Math.sin(seconds * 0.6D + 1.2D));
        StrinovaHudState.setSkills(new float[]{cooldownQ, 0.0F, cooldownC},
                new boolean[]{cooldownQ <= 0.0F, true, cooldownC <= 0.0F});

        int ultimate = 3 + (int) ((seconds / 5.0F) % 5.0F);
        StrinovaHudState.setUltimate(Math.min(7, ultimate), 7);

        StrinovaHudState.setCrosshairSpread(
                (float) ((Math.sin(seconds * 2.0D) + 1.0D) * 0.25D + 0.1D));
    }

    /** 模拟一局进行中的对局：比分 3:2、回合 1、倒计时循环。 */
    private static void fillMatch(float seconds) {
        StrinovaMatchState.setActive(true);
        StrinovaMatchState.setModeKey("hud.strinova.match.mode.demolition");
        StrinovaMatchState.setPhase(StrinovaMatchState.Phase.ROUND);
        StrinovaMatchState.setScores(3, 2);
        StrinovaMatchState.setRound(1);
        int remaining = (int) (95.0F - (seconds % 96.0F));
        StrinovaMatchState.setRoundSecondsLeft(Math.max(0, remaining));
        StrinovaMatchState.setAlliesAttacking(true);
        StrinovaMatchState.setAlliesTeamKey("hud.strinova.faction.scissors");
        StrinovaMatchState.setEnemiesTeamKey("hud.strinova.faction.opal");
    }

    /** 模拟 5v5 计分板数据（列数据参考真实 TAB 计分板截图）。 */
    private static void fillScoreboard() {
        List<StrinovaScoreboardState.Entry> entries = new ArrayList<>();
        entries.add(new StrinovaScoreboardState.Entry("hhdxcz", "千代", true, true,
                8, 3, 2, 130.0F, 80.0F, StrinovaScoreboardState.Status.ALIVE,
                0, 6, 1, "枫鸣", 0, 0, 0, 86));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 1", "玛德蕾娜", true, false,
                5, 4, 1, 150.0F, 80.0F, StrinovaScoreboardState.Status.ALIVE,
                0, 6, 1, "警探", 0, 0, 0, 37));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 2", "蕾欧娜", true, false,
                3, 5, 4, 42.0F, 0.0F, StrinovaScoreboardState.Status.DOWNED,
                0, 6, 1, "空境", 0, 0, 0, 66));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 3", "诺诺", true, false,
                2, 6, 3, 0.0F, 0.0F, StrinovaScoreboardState.Status.DEAD,
                0, 6, 0, "幻霜", 0, 0, 0, 35));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 4", "伊薇特", true, false,
                6, 2, 7, 118.0F, 50.0F, StrinovaScoreboardState.Status.ALIVE,
                0, 6, 1, "鸣火", 0, 0, 0, 56));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 5", "明", false, false,
                7, 4, 3, 104.0F, 30.0F, StrinovaScoreboardState.Status.ALIVE,
                0, 6, 1, "逆焰", 0, 0, 0, 62));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 6", "拉薇", false, false,
                4, 5, 2, 150.0F, 80.0F, StrinovaScoreboardState.Status.ALIVE,
                0, 6, 1, "谢幕曲", 0, 0, 0, 59));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 7", "玛拉", false, false,
                3, 6, 5, 0.0F, 0.0F, StrinovaScoreboardState.Status.DEAD,
                0, 6, 0, "雨晦", 0, 0, 0, 33));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 8", "忧雾", false, false,
                5, 5, 1, 65.0F, 20.0F, StrinovaScoreboardState.Status.DOWNED,
                0, 6, 1, "空境", 0, 0, 0, 59));
        entries.add(new StrinovaScoreboardState.Entry("AI Bot 9", "艾卡", false, false,
                2, 7, 4, 150.0F, 80.0F, StrinovaScoreboardState.Status.ALIVE,
                0, 6, 1, "忍锋", 0, 0, 0, 57));
        StrinovaScoreboardState.setEntries(entries);
    }

    /** 演示一次普通命中：伤害数字 + 命中标记 + 受击方向。 */
    public static void triggerTestHit() {
        StrinovaHudState.pushDamageNumber(24.0F + (float) (Math.random() * 6.0F), false, false);
        StrinovaHudState.markHit(false);
        StrinovaHudState.pushDamageIndicator((float) (Math.random() * 360.0D - 180.0D));
    }

    /** 演示一次爆头击杀：伤害数字 + 击杀播报 + 红色命中标记。 */
    public static void triggerTestKill() {
        StrinovaHudState.pushDamageNumber(36.0F, true, false);
        StrinovaHudState.pushKillFeed("PlayerA", "警探", "PlayerB", true);
        StrinovaHudState.markHit(true);
    }
}
