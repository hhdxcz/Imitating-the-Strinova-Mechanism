package com.hhdxcz.strinova.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * TAB 计分板数据契约（客户端侧）：双方每名玩家的名字、超弦体、K/D/A、生命/护甲、状态。
 * 当前由模拟数据填充；未来由服务端对局系统 S2C 同步。
 */
public final class StrinovaScoreboardState {

    public enum Status {
        ALIVE("hud.strinova.scoreboard.alive"),
        DOWNED("hud.strinova.scoreboard.downed"),
        DEAD("hud.strinova.scoreboard.dead");

        private final String labelKey;

        Status(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    public record Entry(
            String name,
            String agentName,
            boolean ally,
            boolean self,
            int kills,
            int deaths,
            int assists,
            float health,
            float armor,
            Status status,
            int ultimatePoints,
            int ultimateMax,
            int growthLevel,
            String weaponName,
            int damageScore,
            int skillScore,
            int assistScore,
            int ping
    ) {
    }

    private static volatile List<Entry> entries = List.of();

    private StrinovaScoreboardState() {
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static void setEntries(List<Entry> list) {
        entries = list == null ? List.of() : List.copyOf(list);
    }

    public static List<Entry> allies() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.ally()) {
                out.add(entry);
            }
        }
        return out;
    }

    public static List<Entry> enemies() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.ally()) {
                out.add(entry);
            }
        }
        return out;
    }

    public static int aliveCount(boolean ally) {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.ally() == ally && entry.status() == Status.ALIVE) {
                count++;
            }
        }
        return count;
    }

    public static void clear() {
        entries = List.of();
    }
}
