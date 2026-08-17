package com.hhdxcz.strinova.gameplay;

import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 空中段跳次数持久化数据：按玩家 UUID 存储额外跳跃次数设置
public final class StrinovaAirJumpSettings extends SavedData {

    public static final int DEFAULT_EXTRA_JUMPS = 1;
    public static final int INFINITE_EXTRA_JUMPS = -1;
    public static final int MAX_EXTRA_JUMPS = 8;

    private static final String DATA_NAME = StrinovaMod.MOD_ID + "_air_jump_settings";

    private final Map<UUID, Integer> extraJumps = new ConcurrentHashMap<>();

    // 从服务端世界数据中获取或创建实例
    public static StrinovaAirJumpSettings get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(StrinovaAirJumpSettings::load, StrinovaAirJumpSettings::new, DATA_NAME);
    }

    // 获取玩家的额外跳跃次数，未设置则返回默认值
    public int getExtraJumps(UUID playerId) {
        int defaultExtra = defaultExtraJumps();
        if (playerId == null) {
            return defaultExtra;
        }
        Integer v = extraJumps.get(playerId);
        if (v == null) {
            return defaultExtra;
        }
        return clampExtra(v.intValue());
    }

    // 设置玩家的额外跳跃次数，若设置值与默认值相同则移除记录
    public boolean setExtraJumps(UUID playerId, int extra) {
        if (playerId == null) {
            return false;
        }
        int v = clampExtra(extra);
        if (v == defaultExtraJumps()) {
            Integer removed = extraJumps.remove(playerId);
            if (removed != null) {
                setDirty();
                return true;
            }
            return false;
        }
        Integer prev = extraJumps.put(playerId, v);
        boolean changed = prev == null || prev.intValue() != v;
        if (changed) {
            setDirty();
        }
        return changed;
    }

    // 将额外跳跃次数限制在 [0, MAX_EXTRA_JUMPS] 或 INFINITE_EXTRA_JUMPS
    private static int clampExtra(int extra) {
        if (extra == INFINITE_EXTRA_JUMPS) {
            return INFINITE_EXTRA_JUMPS;
        }
        if (extra < 0) {
            return 0;
        }
        if (extra > MAX_EXTRA_JUMPS) {
            return MAX_EXTRA_JUMPS;
        }
        return extra;
    }

    // 从配置文件获取默认额外跳跃次数
    private static int defaultExtraJumps() {
        int value = StrinovaCommonConfig.getDefaultExtraJumps();
        if (value < 0) {
            return 0;
        }
        if (value > MAX_EXTRA_JUMPS) {
            return MAX_EXTRA_JUMPS;
        }
        return value;
    }

    // 从 NBT 数据加载玩家段跳设置
    public static StrinovaAirJumpSettings load(CompoundTag tag) {
        StrinovaAirJumpSettings data = new StrinovaAirJumpSettings();
        if (tag == null) {
            return data;
        }
        ListTag list = tag.getList("players", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry == null) {
                continue;
            }
            String uuidStr = entry.getString("uuid");
            if (uuidStr == null || uuidStr.isEmpty()) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (Exception ignored) {
                continue;
            }
            int extra = entry.getInt("extra");
            int v = clampExtra(extra);
            if (v != defaultExtraJumps()) {
                data.extraJumps.put(uuid, v);
            }
        }
        return data;
    }

    // 将玩家段跳设置保存为 NBT 数据
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var e : extraJumps.entrySet()) {
            UUID uuid = e.getKey();
            Integer extra = e.getValue();
            if (uuid == null || extra == null) {
                continue;
            }
            int v = clampExtra(extra.intValue());
            if (v == defaultExtraJumps()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("uuid", uuid.toString());
            entry.putInt("extra", v);
            list.add(entry);
        }
        tag.put("players", list);
        return tag;
    }
}