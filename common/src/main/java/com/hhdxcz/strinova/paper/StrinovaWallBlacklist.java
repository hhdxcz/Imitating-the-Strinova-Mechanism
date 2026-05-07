package com.hhdxcz.strinova.paper;

import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StrinovaWallBlacklist {
    private static final StrinovaWallBlacklist INSTANCE = new StrinovaWallBlacklist();
    private static final Set<ResourceLocation> CLIENT_BLOCKED = ConcurrentHashMap.newKeySet();

    private final Set<ResourceLocation> blocked = ConcurrentHashMap.newKeySet();

    private StrinovaWallBlacklist() {
    }

    public static StrinovaWallBlacklist get(MinecraftServer server) {
        return INSTANCE;
    }

    public static boolean isBlockedClient(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isClientSide) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (id == null) {
            return false;
        }
        return CLIENT_BLOCKED.contains(id);
    }

    public static void updateClient(List<ResourceLocation> list) {
        CLIENT_BLOCKED.clear();
        if (list != null) {
            CLIENT_BLOCKED.addAll(list);
        }
    }

    public static List<ResourceLocation> listClient() {
        return new ArrayList<>(CLIENT_BLOCKED);
    }

    public static boolean isBlocked(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || pos == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (id == null) {
            return false;
        }
        return INSTANCE.blocked.contains(id);
    }

    public boolean add(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        boolean changed = blocked.add(id);
        if (changed) {
            StrinovaCommonConfig.setWallBlacklist(list());
        }
        return changed;
    }

    public boolean remove(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        boolean changed = blocked.remove(id);
        if (changed) {
            StrinovaCommonConfig.setWallBlacklist(list());
        }
        return changed;
    }

    public int clear() {
        int size = blocked.size();
        if (size > 0) {
            blocked.clear();
            StrinovaCommonConfig.setWallBlacklist(list());
        }
        return size;
    }

    public List<ResourceLocation> list() {
        return new ArrayList<>(blocked);
    }

    public static void replaceServerList(List<ResourceLocation> list) {
        INSTANCE.blocked.clear();
        if (list != null) {
            INSTANCE.blocked.addAll(list);
        }
    }
}
