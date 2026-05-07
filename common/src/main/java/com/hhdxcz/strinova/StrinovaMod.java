package com.hhdxcz.strinova;

import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import com.hhdxcz.strinova.net.StrinovaNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrinovaMod {
    public static final String MOD_ID = "klbq";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info(MOD_ID + " initializing...");
        StrinovaCommonConfig.init();
        StrinovaNetwork.init();
    }
}
