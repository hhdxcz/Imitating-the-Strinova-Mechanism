package com.hhdxcz.wa.gameplay;

import com.hhdxcz.wa.WaMod;
import com.hhdxcz.wa.config.WaCommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaAirJumpSettings extends SavedData {

    public static final int DEFAULT_EXTRA_JUMPS = 1;
    public static final int INFINITE_EXTRA_JUMPS = -1;
    public static final int MAX_EXTRA_JUMPS = 8;

    private static final String DATA_NAME = WaMod.MOD_ID + "_air_jump_settings";

    private final Map<UUID, Integer> extraJumps = new ConcurrentHashMap<>();

    public static WaAirJumpSettings get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(WaAirJumpSettings::load, WaAirJumpSettings::new, DATA_NAME);
    }

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

    private static int defaultExtraJumps() {
        int value = WaCommonConfig.getDefaultExtraJumps();
        if (value < 0) {
            return 0;
        }
        if (value > MAX_EXTRA_JUMPS) {
            return MAX_EXTRA_JUMPS;
        }
        return value;
    }

    public static WaAirJumpSettings load(CompoundTag tag) {
        WaAirJumpSettings data = new WaAirJumpSettings();
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
