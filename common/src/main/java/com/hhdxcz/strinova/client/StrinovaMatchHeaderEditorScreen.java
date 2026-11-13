package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudMockData;
import com.hhdxcz.strinova.client.hud.StrinovaHudPainter;
import com.hhdxcz.strinova.client.hud.StrinovaMatchHeaderWidget;
import com.hhdxcz.strinova.client.hud.StrinovaProgrammaticPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 顶部面板画图式编辑器：
 * 单击选中，Ctrl 单击多选，拖动移动；8 控制点缩放（Shift 等比）；
 * 支持旋转、透明度、镜像、层级顺序。
 */
public class StrinovaMatchHeaderEditorScreen extends Screen {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 60;
    private static final int HANDLE = 5;

    private final Screen parent;
    private final Set<String> selected = new LinkedHashSet<>();
    private int previewX;
    private int previewY;
    private String dragMode;
    private String activeLayer;
    private double dragStartX;
    private double dragStartY;
    private int startLayerX;
    private int startLayerY;
    private int startLayerW;
    private int startLayerH;
    private int startBaseW;
    private int startBaseH;
    private int startBaseX;
    private int startBaseY;
    private final Map<String, int[]> startOffsets = new LinkedHashMap<>();
    private String status = "";

    public StrinovaMatchHeaderEditorScreen(@Nullable Screen parent) {
        super(Component.literal("顶部面板编辑器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        StrinovaHudMockData.ensureScoreboardPreview();
        this.previewX = Math.max(4, (this.width - WIDTH) / 2);
        this.previewY = 94;
        int cx = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("旋转 -15"), b -> rotate(-15.0F))
                .bounds(cx - 172, this.height - 78, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("旋转 +15"), b -> rotate(15.0F))
                .bounds(cx - 88, this.height - 78, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("透明 -"), b -> opacity(-0.1F))
                .bounds(cx - 4, this.height - 78, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("透明 +"), b -> opacity(0.1F))
                .bounds(cx + 80, this.height - 78, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("镜像 X"), b -> mirror(true))
                .bounds(cx - 172, this.height - 54, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("镜像 Y"), b -> mirror(false))
                .bounds(cx - 88, this.height - 54, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("上移一层"), b -> order(1))
                .bounds(cx - 4, this.height - 54, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("下移一层"), b -> order(-1))
                .bounds(cx + 80, this.height - 54, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("导出布局"), b -> this.exportLayout())
                .bounds(cx - 172, this.height - 102, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("导入剪贴板"), b -> this.importLayout())
                .bounds(cx - 88, this.height - 102, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("全部复位"), b -> {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            for (StrinovaHudConfig.HeaderLayerStyle style : config.matchHeaderLayers.values()) {
                style.offsetX = 0;
                style.offsetY = 0;
                style.scaleX = 1.0F;
                style.scaleY = 1.0F;
                style.rotation = 0.0F;
                style.opacity = 1.0F;
                style.mirrorX = false;
                style.mirrorY = false;
                style.visible = true;
                style.order = 0;
            }
        }).bounds(cx - 172, this.height - 28, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("显隐选中"), b -> {
            for (String key : this.selected) {
                StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(key);
                style.visible = !style.visible;
            }
        }).bounds(cx - 88, this.height - 28, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.saveAndClose())
                .bounds(cx - 4, this.height - 28, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.onClose())
                .bounds(cx + 80, this.height - 28, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        StrinovaProgrammaticPainter painter = new StrinovaProgrammaticPainter(graphics);
        new StrinovaMatchHeaderWidget().render(painter, this.previewX, this.previewY);

        List<StrinovaMatchHeaderWidget.Layer> layers =
                StrinovaMatchHeaderWidget.layers(painter, this.previewX, this.previewY);
        for (StrinovaMatchHeaderWidget.Layer layer : layers) {
            boolean isSelected = this.selected.contains(layer.key());
            int color = isSelected ? 0xFFFF4FA3 : 0x33FFFFFF;
            painter.border(layer.x() - 1, layer.y() - 1, layer.w() + 2, layer.h() + 2, color, 1);
            if (!layer.visible()) {
                painter.text("OFF", layer.x() + 1, layer.y() + 1, 0x99FFD166, false);
            }
        }
        if (this.selected.size() == 1) {
            for (StrinovaMatchHeaderWidget.Layer layer : layers) {
                if (layer.key().equals(this.selected.iterator().next())) {
                    drawHandles(painter, layer);
                    break;
                }
            }
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("单击选中，Ctrl+单击多选；拖动移动；8点缩放（Shift 等比）；Delete 隐藏"),
                this.width / 2, 32, 0xFFAAAAAA);
        String info = this.selected.isEmpty() ? "选中：无" : "选中：" + String.join(", ", this.selected);
        graphics.drawCenteredString(this.font, Component.literal(info), this.width / 2, 50, 0xFFFFD166);
        if (!this.status.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(this.status),
                    this.width / 2, 76, 0xFF7CFF9E);
        }
        StrinovaHudConfig.HeaderLayerStyle active = this.selected.size() == 1
                ? StrinovaHudConfig.get().matchHeaderLayer(this.selected.iterator().next()) : null;
        if (active != null) {
            String detail = String.format(java.util.Locale.ROOT,
                    "旋转 %.0f°  透明度 %.0f%%  层级 %d  镜像 X:%s Y:%s",
                    active.rotation, active.opacity * 100.0F, active.order,
                    active.mirrorX ? "开" : "关", active.mirrorY ? "开" : "关");
            graphics.drawCenteredString(this.font, Component.literal(detail), this.width / 2, 64, 0xFFFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void drawHandles(StrinovaHudPainter painter, StrinovaMatchHeaderWidget.Layer layer) {
        int x0 = layer.x();
        int y0 = layer.y();
        int x1 = layer.x() + layer.w() - 1;
        int y1 = layer.y() + layer.h() - 1;
        int xm = (x0 + x1) / 2;
        int ym = (y0 + y1) / 2;
        for (int[] p : new int[][]{{x0, y0}, {xm, y0}, {x1, y0}, {x0, ym}, {x1, ym},
                {x0, y1}, {xm, y1}, {x1, y1}}) {
            painter.rect(p[0] - 2, p[1] - 2, HANDLE, HANDLE, 0xFFFFFFFF);
            painter.border(p[0] - 2, p[1] - 2, HANDLE, HANDLE, 0xFF000000, 1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        StrinovaProgrammaticPainter painter = new StrinovaProgrammaticPainter(null);
        List<StrinovaMatchHeaderWidget.Layer> layers =
                StrinovaMatchHeaderWidget.layers(painter, this.previewX, this.previewY);

        if (this.selected.size() == 1) {
            String key = this.selected.iterator().next();
            for (StrinovaMatchHeaderWidget.Layer layer : layers) {
                if (!layer.key().equals(key)) {
                    continue;
                }
                String mode = handleAt(layer, mouseX, mouseY);
                if (mode != null) {
                    this.beginDrag(layer, mode, mouseX, mouseY);
                    return true;
                }
            }
        }

        StrinovaMatchHeaderWidget.Layer hit = null;
        for (int i = layers.size() - 1; i >= 0; i--) {
            if (inside(layers.get(i), mouseX, mouseY)) {
                hit = layers.get(i);
                break;
            }
        }
        if (hit == null) {
            if (!Screen.hasControlDown()) {
                this.selected.clear();
            }
            return false;
        }
        if (Screen.hasControlDown()) {
            if (this.selected.contains(hit.key())) {
                this.selected.remove(hit.key());
            } else {
                this.selected.add(hit.key());
            }
            return true;
        }
        if (!this.selected.contains(hit.key())) {
            this.selected.clear();
            this.selected.add(hit.key());
        }
        this.beginDrag(hit, "move", mouseX, mouseY);
        return true;
    }

    private void beginDrag(StrinovaMatchHeaderWidget.Layer layer, String mode, double mouseX, double mouseY) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        this.dragMode = mode;
        this.activeLayer = layer.key();
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
        this.startLayerX = layer.x();
        this.startLayerY = layer.y();
        this.startLayerW = layer.w();
        this.startLayerH = layer.h();
        this.startBaseW = layer.baseW();
        this.startBaseH = layer.baseH();
        this.startBaseX = layer.baseX();
        this.startBaseY = layer.baseY();
        this.startOffsets.clear();
        for (String key : this.selected) {
            StrinovaHudConfig.HeaderLayerStyle style = config.matchHeaderLayer(key);
            this.startOffsets.put(key, new int[]{style.offsetX, style.offsetY});
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragMode == null || this.activeLayer == null || button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        StrinovaHudConfig config = StrinovaHudConfig.get();
        int dx = (int) Math.round(mouseX - this.dragStartX);
        int dy = (int) Math.round(mouseY - this.dragStartY);

        if ("move".equals(this.dragMode)) {
            for (Map.Entry<String, int[]> entry : this.startOffsets.entrySet()) {
                StrinovaHudConfig.HeaderLayerStyle style = config.matchHeaderLayer(entry.getKey());
                style.offsetX = entry.getValue()[0] + dx;
                style.offsetY = entry.getValue()[1] + dy;
            }
            return true;
        }

        if (this.selected.size() != 1) {
            return true;
        }
        StrinovaHudConfig.HeaderLayerStyle style = config.matchHeaderLayer(this.activeLayer);
        boolean west = this.dragMode.contains("w");
        boolean east = this.dragMode.contains("e");
        boolean north = this.dragMode.contains("n");
        boolean south = this.dragMode.contains("s");

        int left = this.startLayerX + (west ? dx : 0);
        int right = this.startLayerX + this.startLayerW + (east ? dx : 0);
        int top = this.startLayerY + (north ? dy : 0);
        int bottom = this.startLayerY + this.startLayerH + (south ? dy : 0);
        int newW = Math.max(6, right - left);
        int newH = Math.max(6, bottom - top);

        float scaleX = clamp(newW / (float) this.startBaseW);
        float scaleY = clamp(newH / (float) this.startBaseH);
        boolean uniform = isTextLayer(this.activeLayer) || Screen.hasShiftDown();
        if (uniform) {
            float scale = Math.max(scaleX, scaleY);
            scaleX = scale;
            scaleY = scale;
            newW = Math.max(6, Math.round(this.startBaseW * scale));
            newH = Math.max(6, Math.round(this.startBaseH * scale));
            if (west) {
                left = right - newW;
            } else if (east) {
                right = left + newW;
            }
            if (north) {
                top = bottom - newH;
            } else if (south) {
                bottom = top + newH;
            }
        }

        style.scaleX = scaleX;
        style.scaleY = scaleY;
        style.offsetX = left - this.startBaseX + (newW - this.startBaseW) / 2;
        style.offsetY = top - this.startBaseY + (newH - this.startBaseH) / 2;
        return true;
    }

    private static boolean isTextLayer(String key) {
        return "target".equals(key) || "separator".equals(key) || "timer".equals(key)
                || "leftScore".equals(key) || "rightScore".equals(key);
    }

    private static float clamp(float value) {
        return Math.max(0.2F, Math.min(3.0F, value));
    }

    private static boolean inside(StrinovaMatchHeaderWidget.Layer layer, double mouseX, double mouseY) {
        return mouseX >= layer.x() && mouseX <= layer.x() + layer.w()
                && mouseY >= layer.y() && mouseY <= layer.y() + layer.h();
    }

    private static String handleAt(StrinovaMatchHeaderWidget.Layer layer, double mouseX, double mouseY) {
        int x0 = layer.x();
        int y0 = layer.y();
        int x1 = layer.x() + layer.w() - 1;
        int y1 = layer.y() + layer.h() - 1;
        int xm = (x0 + x1) / 2;
        int ym = (y0 + y1) / 2;
        Object[][] handles = {
                {"nw", x0, y0}, {"n", xm, y0}, {"ne", x1, y0},
                {"w", x0, ym}, {"e", x1, ym},
                {"sw", x0, y1}, {"s", xm, y1}, {"se", x1, y1}
        };
        for (Object[] handle : handles) {
            int hx = (Integer) handle[1];
            int hy = (Integer) handle[2];
            if (Math.abs(mouseX - hx) <= HANDLE && Math.abs(mouseY - hy) <= HANDLE) {
                return (String) handle[0];
            }
        }
        return null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragMode != null && button == 0) {
            this.dragMode = null;
            this.activeLayer = null;
            StrinovaHudConfig.get().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            for (String key : this.selected) {
                StrinovaHudConfig.get().matchHeaderLayer(key).visible =
                        !StrinovaHudConfig.get().matchHeaderLayer(key).visible;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            rotate(-15.0F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            rotate(15.0F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS) {
            opacity(-0.1F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL) {
            opacity(0.1F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            mirror(!Screen.hasShiftDown());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            order(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            order(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void rotate(float delta) {
        for (String key : this.selected) {
            StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(key);
            style.rotation = Math.max(-180.0F, Math.min(180.0F, style.rotation + delta));
        }
    }

    private void opacity(float delta) {
        for (String key : this.selected) {
            StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(key);
            style.opacity = Math.max(0.0F, Math.min(1.0F, style.opacity + delta));
        }
    }

    private void mirror(boolean xAxis) {
        for (String key : this.selected) {
            StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(key);
            if (xAxis) {
                style.mirrorX = !style.mirrorX;
            } else {
                style.mirrorY = !style.mirrorY;
            }
        }
    }

    private void order(int delta) {
        if (this.selected.isEmpty()) {
            return;
        }
        StrinovaProgrammaticPainter painter = new StrinovaProgrammaticPainter(null);
        List<StrinovaMatchHeaderWidget.Layer> layers =
                StrinovaMatchHeaderWidget.layers(painter, this.previewX, this.previewY);
        layers.sort(Comparator.comparingInt(StrinovaMatchHeaderWidget.Layer::order));
        List<String> keys = new ArrayList<>();
        for (StrinovaMatchHeaderWidget.Layer layer : layers) {
            keys.add(layer.key());
        }
        List<String> moved = new ArrayList<>(this.selected);
        moved.sort(Comparator.comparingInt(keys::indexOf));
        if (delta > 0) {
            java.util.Collections.reverse(moved);
        }
        for (String key : moved) {
            int index = keys.indexOf(key);
            if (index < 0) {
                continue;
            }
            int target = Math.max(0, Math.min(keys.size() - 1, index + delta));
            if (target == index) {
                continue;
            }
            keys.remove(index);
            keys.add(target, key);
        }
        StrinovaHudConfig config = StrinovaHudConfig.get();
        for (int i = 0; i < keys.size(); i++) {
            config.matchHeaderLayer(keys.get(i)).order = i;
        }
    }

    private void exportLayout() {
        try {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            String json = config.matchHeaderExportJson();
            java.nio.file.Path path = StrinovaHudConfig.matchHeaderExportPath();
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, json, java.nio.charset.StandardCharsets.UTF_8);
            if (Minecraft.getInstance().keyboardHandler != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(json);
            }
            this.status = "已导出并复制到剪贴板: " + path;
        } catch (Exception e) {
            this.status = "导出失败: " + e.getMessage();
        }
    }

    private void importLayout() {
        try {
            String clip = "";
            if (Minecraft.getInstance().getWindow() != null) {
                clip = GLFW.glfwGetClipboardString(Minecraft.getInstance().getWindow().getWindow());
            }
            if (clip != null && StrinovaHudConfig.get().matchHeaderImportJson(clip)) {
                StrinovaHudConfig.get().save();
                this.status = "已导入剪贴板中的顶部面板布局";
            } else {
                this.status = "导入失败：剪贴板不是有效的顶部面板布局 JSON";
            }
        } catch (Exception e) {
            this.status = "导入失败: " + e.getMessage();
        }
    }

    @Override
    public void onClose() {
        StrinovaHudConfig.get().save();
        Minecraft.getInstance().setScreen(this.parent);
    }

    private void saveAndClose() {
        StrinovaHudConfig.get().save();
        Minecraft.getInstance().setScreen(this.parent);
    }
}
