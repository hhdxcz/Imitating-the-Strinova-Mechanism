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

import java.util.List;

/**
 * 顶部面板图形化编辑器（类画图工具）：
 * 选择单个图层后拖动移动，拖动 8 个控制点可拉伸/缩放；支持显隐、复位。
 *
 * <p>打开：{@code /strinova_client topbar} 或战斗设置「顶部面板…」。</p>
 */
public class StrinovaMatchHeaderEditorScreen extends Screen {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 60;
    private static final int HANDLE = 5;

    private final Screen parent;
    private int previewX;
    private int previewY;
    private String selected;
    private String dragMode;
    private double dragStartX;
    private double dragStartY;
    private int startOffsetX;
    private int startOffsetY;
    private float startScaleX;
    private float startScaleY;
    private int startLayerX;
    private int startLayerY;
    private int startLayerW;
    private int startLayerH;
    private int startBaseW;
    private int startBaseH;
    private int startBaseX;
    private int startBaseY;

    public StrinovaMatchHeaderEditorScreen(@Nullable Screen parent) {
        super(Component.literal("顶部面板编辑器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        StrinovaHudMockData.ensureScoreboardPreview();
        this.previewX = Math.max(4, (this.width - WIDTH) / 2);
        this.previewY = 78;

        this.addRenderableWidget(Button.builder(Component.literal("全部复位"), b -> {
            StrinovaHudConfig config = StrinovaHudConfig.get();
            for (StrinovaHudConfig.HeaderLayerStyle style : config.matchHeaderLayers.values()) {
                style.offsetX = 0;
                style.offsetY = 0;
                style.scaleX = 1.0F;
                style.scaleY = 1.0F;
                style.visible = true;
            }
        }).bounds(this.width / 2 - 155, this.height - 28, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("显隐选中"), b -> {
            if (this.selected != null) {
                StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(this.selected);
                style.visible = !style.visible;
            }
        }).bounds(this.width / 2 - 70, this.height - 28, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.saveAndClose())
                .bounds(this.width / 2 + 15, this.height - 28, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.onClose())
                .bounds(this.width / 2 + 80, this.height - 28, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        StrinovaProgrammaticPainter painter = new StrinovaProgrammaticPainter(graphics);
        StrinovaMatchHeaderWidget widget = new StrinovaMatchHeaderWidget();
        widget.render(painter, this.previewX, this.previewY);

        List<StrinovaMatchHeaderWidget.Layer> layers =
                StrinovaMatchHeaderWidget.layers(painter, this.previewX, this.previewY);
        for (StrinovaMatchHeaderWidget.Layer layer : layers) {
            boolean isSelected = layer.key().equals(this.selected);
            int color = isSelected ? 0xFFFF4FA3 : 0x33FFFFFF;
            painter.border(layer.x() - 1, layer.y() - 1, layer.w() + 2, layer.h() + 2, color, 1);
            if (!layer.visible()) {
                painter.text("OFF", layer.x() + 1, layer.y() + 1, 0x99FFD166, false);
            }
        }
        if (this.selected != null) {
            for (StrinovaMatchHeaderWidget.Layer layer : layers) {
                if (layer.key().equals(this.selected)) {
                    drawHandles(painter, layer);
                    break;
                }
            }
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("单击选中图层；拖动内部移动；拖动 8 个控制点拉伸大小；「显隐选中」切换显示"),
                this.width / 2, 32, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font,
                Component.literal("选中：" + (this.selected == null ? "无" : this.selected)),
                this.width / 2, 50, 0xFFFFD166);
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

        // 先判断当前选中图层的控制点
        if (this.selected != null) {
            for (StrinovaMatchHeaderWidget.Layer layer : layers) {
                if (!layer.key().equals(this.selected)) {
                    continue;
                }
                String mode = handleAt(layer, mouseX, mouseY);
                if (mode != null) {
                    this.beginDrag(layer, mode, mouseX, mouseY);
                    return true;
                }
            }
        }
        // 再选中最上层图层
        for (int i = layers.size() - 1; i >= 0; i--) {
            StrinovaMatchHeaderWidget.Layer layer = layers.get(i);
            if (inside(layer, mouseX, mouseY)) {
                this.selected = layer.key();
                this.beginDrag(layer, "move", mouseX, mouseY);
                return true;
            }
        }
        this.selected = null;
        return false;
    }

    private void beginDrag(StrinovaMatchHeaderWidget.Layer layer, String mode, double mouseX, double mouseY) {
        StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(layer.key());
        this.dragMode = mode;
        this.selected = layer.key();
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
        this.startOffsetX = style.offsetX;
        this.startOffsetY = style.offsetY;
        this.startScaleX = style.scaleX;
        this.startScaleY = style.scaleY;
        this.startLayerX = layer.x();
        this.startLayerY = layer.y();
        this.startLayerW = layer.w();
        this.startLayerH = layer.h();
        this.startBaseW = layer.baseW();
        this.startBaseH = layer.baseH();
        this.startBaseX = layer.baseX();
        this.startBaseY = layer.baseY();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragMode != null && this.selected != null && button == 0) {
            StrinovaHudConfig.HeaderLayerStyle style = StrinovaHudConfig.get().matchHeaderLayer(this.selected);
            int dx = (int) Math.round(mouseX - this.dragStartX);
            int dy = (int) Math.round(mouseY - this.dragStartY);

            if ("move".equals(this.dragMode)) {
                style.offsetX = this.startOffsetX + dx;
                style.offsetY = this.startOffsetY + dy;
                return true;
            }

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
            boolean uniform = isTextLayer(this.selected);
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
            StrinovaHudConfig.get().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
