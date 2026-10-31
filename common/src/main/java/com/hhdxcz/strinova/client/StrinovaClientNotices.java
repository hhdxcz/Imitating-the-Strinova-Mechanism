package com.hhdxcz.strinova.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 客户端一次性提示：用于把“服务端未启用/未安装模组”“旧配置导致功能失效”等
 * 玩家无法从画面上看出来的问题，直接显示在聊天栏里。
 */
public final class StrinovaClientNotices {

    private static boolean disabledNotified;
    private static boolean missingServerNotified;

    private StrinovaClientNotices() {
    }

    /** 服务端下发“模组已禁用”。 */
    public static void notifyModDisabled() {
        if (disabledNotified) {
            return;
        }
        disabledNotified = true;
        send(Component.translatable("config.strinova.notice.disabled"));
    }

    /** 进入世界较久仍未收到模组握手包，说明服务端没有安装/启用本模组。 */
    public static void notifyServerMissingMod() {
        if (missingServerNotified) {
            return;
        }
        missingServerNotified = true;
        send(Component.translatable("config.strinova.notice.server_missing"));
    }

    /** 断开连接/切换世界时允许再次提示。 */
    public static void reset() {
        disabledNotified = false;
        missingServerNotified = false;
    }

    private static void send(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }
}
