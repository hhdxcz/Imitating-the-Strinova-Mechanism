package com.hhdxcz.wa;

import com.hhdxcz.wa.config.WaCommonConfig;
import com.hhdxcz.wa.net.WaNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaMod {
    public static final String MOD_ID = "klbq";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info(MOD_ID + " initializing...");
        WaCommonConfig.init();
        WaNetwork.init();
    }
}
