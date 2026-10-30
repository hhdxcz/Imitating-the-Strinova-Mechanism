package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudLayout;
import com.hhdxcz.strinova.client.hud.StrinovaHudRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 战斗 HUD 布局编辑器。
 * 在全屏预览画面上直接拖动 HUD 元素调整位置：按下命中元素开始拖拽，
 * 拖动过程中实时修改布局偏移，松开鼠标、重置布局或关闭界面时保存到客户端配置。
 */
public class StrinovaHudEditorScreen extends Screen {

    /** 拖拽只响应鼠标左键。 */
    private static final int DRAG_BUTTON = 0;
    /** 底部按钮宽度。 */
    private static final int BUTTON_WIDTH = 100;

    private final Screen parent;
    /** 拖拽开始（或 init）时各元素的布局偏移快照，用于把鼠标位移换算成布局偏移。 */
    private final Map<StrinovaHudLayout.Element, int[]> initialOffsets = new EnumMap<>(StrinovaHudLayout.Element.class);

    private StrinovaHudLayout.Element dragging;
    private StrinovaHudLayout.Element hovered;

    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragInitialOffsetX;
    private int dragInitialOffsetY;

    public StrinovaHudEditorScreen(Screen parent) {
        super(Component.translatable("config.strinova.hud.editor.title"));
        this.parent = parent;
    }

    /** 初始化界面：记录各元素初始偏移并添加“重置布局/完成”按钮。 */
    @Override
    protected void init() {
        this.clearWidgets();
        this.dragging = null;
        this.hovered = null;
        this.captureInitialOffsets();

        int bottomY = this.height - 28;
        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.hud.editor.reset"),
                        button -> this.resetLayout())
                .bounds(centerX - BUTTON_WIDTH - 4, bottomY, BUTTON_WIDTH, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.strinova.hud.done"),
                        button -> this.finishEditing())
                .bounds(centerX + 4, bottomY, BUTTON_WIDTH, 20)
                .build());
    }

    /** 记录每个可编辑元素当前的布局偏移，作为拖拽增量基准。 */
    private void captureInitialOffsets() {
        this.initialOffsets.clear();
        StrinovaHudLayout layout = StrinovaHudConfig.get().layout();
        for (StrinovaHudLayout.Element element : StrinovaHudRenderer.editableElements()) {
            this.initialOffsets.put(element, new int[]{layout.offsetX(element), layout.offsetY(element)});
        }
    }

    /** 渲染界面：半透明黑底 → HUD 预览（含拖拽/悬停高亮）→ 顶部提示 → 底部按钮。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明黑底：压暗游戏画面，突出 HUD 布局预览
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        this.updateHovered(mouseX, mouseY);
        StrinovaHudRenderer.renderEditorPreview(graphics, this.dragging, this.hovered);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("config.strinova.hud.editor.tip"), this.width / 2, 24, 0xA8D8E8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 每帧更新鼠标悬停的 HUD 元素（拖拽中始终保持拖拽元素高亮）。 */
    private void updateHovered(int mouseX, int mouseY) {
        if (this.dragging != null) {
            this.hovered = this.dragging;
            return;
        }
        StrinovaHudLayout.Element found = null;
        for (StrinovaHudLayout.Element element : StrinovaHudRenderer.editableElements()) {
            if (isInside(element, mouseX, mouseY)) {
                found = element;
                break;
            }
        }
        this.hovered = found;
    }

    /** 判断鼠标坐标是否落在某个 HUD 元素的屏幕包围盒内。 */
    private static boolean isInside(StrinovaHudLayout.Element element, double mouseX, double mouseY) {
        int[] bounds = StrinovaHudRenderer.elementBounds(element);
        if (bounds == null || bounds.length < 4 || bounds[2] <= 0 || bounds[3] <= 0) {
            return false;
        }
        return mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3];
    }

    /** 按下鼠标：优先交给按钮控件，未命中按钮时再判断是否命中 HUD 元素并开始拖拽。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != DRAG_BUTTON) {
            return false;
        }
        for (StrinovaHudLayout.Element element : StrinovaHudRenderer.editableElements()) {
            if (isInside(element, mouseX, mouseY)) {
                this.beginDrag(element, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    /** 开始拖拽：记录鼠标起点与元素初始偏移（init 快照在拖拽前刷新，确保连续拖拽的基准正确）。 */
    private void beginDrag(StrinovaHudLayout.Element element, double mouseX, double mouseY) {
        this.captureInitialOffsets();
        StrinovaHudLayout layout = StrinovaHudConfig.get().layout();
        int[] offset = this.initialOffsets.get(element);
        this.dragging = element;
        this.hovered = element;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        this.dragInitialOffsetX = offset == null ? layout.offsetX(element) : offset[0];
        this.dragInitialOffsetY = offset == null ? layout.offsetY(element) : offset[1];
    }

    /** 拖拽中：把鼠标位移增量叠加到拖拽元素的布局偏移上，实现实时跟随。 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging != null && button == DRAG_BUTTON) {
            int offsetX = this.dragInitialOffsetX + (int) Math.round(mouseX - this.dragStartMouseX);
            int offsetY = this.dragInitialOffsetY + (int) Math.round(mouseY - this.dragStartMouseY);
            StrinovaHudConfig.get().layout().setOffset(this.dragging, offsetX, offsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 松开鼠标：结束拖拽并保存布局。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging != null && button == DRAG_BUTTON) {
            this.dragging = null;
            this.captureInitialOffsets();
            StrinovaHudConfig.get().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 重置为默认布局并保存。 */
    private void resetLayout() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        config.layout().reset();
        config.save();
        this.dragging = null;
        this.hovered = null;
        this.captureInitialOffsets();
    }

    /** 点击“完成”：保存布局并返回父界面。 */
    private void finishEditing() {
        StrinovaHudConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** 关闭界面（ESC）：保存布局后返回父界面。 */
    @Override
    public void onClose() {
        StrinovaHudConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** 布局编辑器不暂停游戏。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
