package com.hhdxcz.strinova;

import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strinova 模组主类，负责模组的初始化和启用状态管理。
 */
public class StrinovaMod {
    public static final String MOD_ID = "strinova";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** 构建标识：反馈问题/排查日志时用于定位具体构建（/strinova_client status 会显示）。 */
    public static final String BUILD_TAG = "1.2.2+0aad9269";
    /** 内部构建指纹：仅写入调试日志，用于排查构建来源。 */
    static final String BUILD_FINGERPRINT = "sw1--SuNPece8gAzzjSBSnzb5hV8";

    private static volatile boolean enabled;
    /** 最近一次收到服务端启用状态同步的时间（0 = 从未收到，可用于判断服务端是否安装/握手）。 */
    private static volatile long lastRemoteEnabledMs;

    /**
     * 查询模组是否已启用。
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置模组启用状态，同时持久化到配置文件。
     */
    public static void setEnabled(boolean value) {
        StrinovaCommonConfig.setModEnabled(value);
        enabled = value;
    }

    /**
     * 应用服务端下发的启用状态。
     * 客户端收到同步包时调用，只更新内存状态，不写本地配置文件，
     * 避免多人游戏把服务端的开关覆盖到玩家本地的配置里。
     */
    public static void applyRemoteEnabled(boolean value) {
        enabled = value;
        lastRemoteEnabledMs = System.currentTimeMillis();
    }

    /** 最近一次收到服务端启用状态的时间戳（0 表示从未收到过，用于判断服务端是否安装模组）。 */
    public static long lastRemoteEnabledMs() {
        return lastRemoteEnabledMs;
    }

    /**
     * 初始化模组：加载配置、同步启用状态、注册网络包。
     */
    public static void init() {
        LOGGER.info(MOD_ID + " initializing...");
        LOGGER.debug("Strinova build {} ({})", BUILD_TAG, BUILD_FINGERPRINT);
        StrinovaCommonConfig.init();
        enabled = StrinovaCommonConfig.isModEnabled();
        // 启动时自动启用（等效于自动执行 /strinova enable），可在 config/strinova.json 关闭 autoEnableOnStart⁠​‌​​​‍​‌​​‍​​​‍‍‌‍​​‌‍‌‌​‌‌‌‌‌​‍​‌‌‍​‍‍‍‍​​​‌‍‌‍​​‌‌‌​‌​​​‍‌​‍‍​‍​​​‌‌‌‌‌​​​‌‍​​​​​‍​‍‍‌‍‍​​‌​​​​​‍‌‌​​‍‌​‌‍​‌‌​‌​‌​​​​‍​‌​​‍‍​‌‌‍‍‍⁡
        if (!enabled && StrinovaCommonConfig.isAutoEnableOnStart()) {
            enabled = true;
            StrinovaCommonConfig.setModEnabled(true);
            LOGGER.info("Strinova: 启动自动启用模组（autoEnableOnStart=true）");
        }
        StrinovaNetwork.init();
    }
}