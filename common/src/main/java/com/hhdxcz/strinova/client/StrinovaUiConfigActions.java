package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 「选角界面 / 弦能增幅网络」可视化自定义配置的导出、导入与数值查看命令实现。
 * Fabric / Forge 的客户端命令树共用这组方法，避免两边重复写文件与剪贴板逻辑。
 */
public final class StrinovaUiConfigActions {

    private StrinovaUiConfigActions() {
    }

    public static String exportSelect() {
        return export(true);
    }

    public static String exportGrowth() {
        return export(false);
    }

    private static String export(boolean select) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        String json = select ? config.agentSelectExportJson() : config.growthExportJson();
        Path path = select ? StrinovaHudConfig.agentSelectExportPath() : StrinovaHudConfig.growthExportPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "写入文件失败: " + e.getMessage();
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(json);
        }
        return (select ? "选角界面" : "增幅网络界面") + "配置已导出（已复制到剪贴板）: " + path;
    }

    public static String importSelect() {
        return importFromClipboard(true);
    }

    public static String importGrowth() {
        return importFromClipboard(false);
    }

    private static String importFromClipboard(boolean select) {
        String clip = readClipboard();
        String label = select ? "选角界面" : "增幅网络界面";
        if (clip == null || clip.isBlank()) {
            return "剪贴板为空：先执行 /strinova_client " + (select ? "agent" : "growth")
                    + " export，或粘贴导出的 JSON 到剪贴板";
        }
        StrinovaHudConfig config = StrinovaHudConfig.get();
        boolean ok = select ? config.agentSelectImportJson(clip) : config.growthImportJson(clip);
        if (!ok) {
            return "导入失败：剪贴板内容不是有效的" + label + "配置 JSON";
        }
        config.save();
        return "已导入并保存" + label + "配置";
    }

    public static String valuesSelect() {
        return StrinovaHudConfig.get().agentSelectExportJson();
    }

    public static String valuesGrowth() {
        return StrinovaHudConfig.get().growthExportJson();
    }

    private static String readClipboard() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getWindow() != null) {
                return org.lwjgl.glfw.GLFW.glfwGetClipboardString(client.getWindow().getWindow());
            }
        } catch (Throwable ignored) {
            // 剪贴板不可用时按空处理
        }
        return "";
    }
}
