package com.hhdxcz.wa.config;

import com.hhdxcz.wa.gameplay.WaAirJumpSettings;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

public final class WaConfigScreenSchema {
    private WaConfigScreenSchema() {
    }

    public static void populate(
            WaCommonConfig.ConfigData data,
            EntryWriter gameplay,
            EntryWriter compat,
            EntryWriter command
    ) {
        gameplay.intField("config.klbq.default_extra_jumps", data.defaultExtraJumps, 1, 0, WaAirJumpSettings.MAX_EXTRA_JUMPS, v -> data.defaultExtraJumps = v);
        gameplay.doubleField("config.klbq.paper_damage_reduction", data.paperDamageReduction, 0.4D, 0.0D, 1.0D, v -> data.paperDamageReduction = v);

        compat.booleanToggle("config.klbq.third_person_bypass", data.thirdPersonCompatBypass, true, v -> data.thirdPersonCompatBypass = v);
        compat.booleanToggle("config.klbq.tps_exit_fly_on_action", data.tpsExitFlyOnAction, true, v -> data.tpsExitFlyOnAction = v);
        compat.booleanToggle("config.klbq.tacz_exit_states_on_action", data.taczExitStatesOnAction, true, v -> data.taczExitStatesOnAction = v);
        compat.booleanToggle("config.klbq.tacz_force_first_person", data.taczForceFirstPerson, true, v -> data.taczForceFirstPerson = v);

        command.stringField("config.klbq.wall_blacklist_csv", data.wallBlacklistCsv, "", v -> data.wallBlacklistCsv = v);
        command.doubleField("config.klbq.default_sync_offset_x", data.defaultSyncOffsetX, 0.0D, -8.0D, 8.0D, v -> data.defaultSyncOffsetX = v);
        command.doubleField("config.klbq.default_sync_offset_y", data.defaultSyncOffsetY, 0.0D, -8.0D, 8.0D, v -> data.defaultSyncOffsetY = v);
        command.doubleField("config.klbq.default_sync_offset_z", data.defaultSyncOffsetZ, 0.0D, -8.0D, 8.0D, v -> data.defaultSyncOffsetZ = v);
        command.doubleField("config.klbq.default_sync_size_x", data.defaultSyncSizeX, 0.0D, -8.0D, 8.0D, v -> data.defaultSyncSizeX = v);
        command.doubleField("config.klbq.default_sync_size_y", data.defaultSyncSizeY, 0.0D, -8.0D, 8.0D, v -> data.defaultSyncSizeY = v);
        command.doubleField("config.klbq.default_sync_size_z", data.defaultSyncSizeZ, 0.0D, -8.0D, 8.0D, v -> data.defaultSyncSizeZ = v);
        command.doubleField("config.klbq.default_fly_offset_x", data.defaultFlyOffsetX, 0.0D, -8.0D, 8.0D, v -> data.defaultFlyOffsetX = v);
        command.doubleField("config.klbq.default_fly_offset_z", data.defaultFlyOffsetZ, 0.0D, -8.0D, 8.0D, v -> data.defaultFlyOffsetZ = v);
        command.doubleField("config.klbq.default_fly_size_x", data.defaultFlySizeX, 0.0D, -8.0D, 8.0D, v -> data.defaultFlySizeX = v);
        command.doubleField("config.klbq.default_fly_size_z", data.defaultFlySizeZ, 0.0D, -8.0D, 8.0D, v -> data.defaultFlySizeZ = v);
    }

    public interface EntryWriter {
        void intField(String key, int value, int defaultValue, int min, int max, IntConsumer saveConsumer);

        void doubleField(String key, double value, double defaultValue, double min, double max, DoubleConsumer saveConsumer);

        void booleanToggle(String key, boolean value, boolean defaultValue, Consumer<Boolean> saveConsumer);

        void stringField(String key, String value, String defaultValue, Consumer<String> saveConsumer);
    }
}
