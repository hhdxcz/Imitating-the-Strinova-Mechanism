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

    private static volatile boolean enabled;

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
     * 初始化模组：加载配置、同步启用状态、注册网络包。
     */
    public static void init() {
        LOGGER.info(MOD_ID + " initializing...");
        StrinovaCommonConfig.init();
        enabled = StrinovaCommonConfig.isModEnabled();
        StrinovaNetwork.init();
    }
}