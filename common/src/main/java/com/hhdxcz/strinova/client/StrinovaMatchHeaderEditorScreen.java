package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import com.hhdxcz.strinova.client.hud.StrinovaHudMockData;
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
 * 顶部面板图形化编辑器：拖动头像行 / 左比分板 / 右比分板 / 中间计时区，
 * 切换显示隐藏，保存到 {@link StrinovaHudConfig}。
 *
 * <p>打开方式：{@code /strinova_client topbar}，或战斗设置里的「顶部面板…」。</p>
 */
public class StrinovaMatchHeaderEditorScreen extends Screen {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 60;
    private static final int DRAG_BUTTON = 0;

    private final Screen parent;
    private String dragging;
    private String hovered;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartOffsetX;
    private int dragStartOffsetY;
    private int previewX;
    private int previewY;

    public StrinovaMatchHeaderEditorScreen(@Nullable Screen parent) {
        super(Component.literal("顶部面板编辑器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        StrinovaHudMockData.ensureScoreboardPreview();
        this.previewX = Math.max(4, (this.width - WIDTH) / 2);
        this.previewY = 76;
        StrinovaHudConfig config = StrinovaHudConfig.get();

        this.addRenderableWidget(Button.builder(
                Component.literal("头像：" + onOff(config.matchHeaderShowPortraits)), b -> {
                    config.matchHeaderShowPortraits = !config.matchHeaderShowPortraits;
                    b.setMessage(Component.literal("头像：" + onOff(config.matchHeaderShowPortraits)));
                }).bounds(this.width / 2 - 220, this.height - 52, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("比分：" + onOff(config.matchHeaderShowPlates)), b -> {
                    config.matchHeaderShowPlates = !config.matchHeaderShowPlates;
                    b.setMessage(Component.literal("比分：" + onOff(config.matchHeaderShowPlates)));
                }).bounds(this.width / 2 - 110, this.height - 52, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("中间：" + onOff(config.matchHeaderShowCenter)), b -> {
                    config.matchHeaderShowCenter = !config.matchHeaderShowCenter;
                    b.setMessage(Component.literal("中间：" + onOff(config.matchHeaderShowCenter)));
                }).bounds(this.width / 2, this.height - 52, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("位置复位"), b -> {
            config.matchHeaderPortraitOffsetX = 0;
            config.matchHeaderPortraitOffsetY = 0;
            config.matchHeaderPlateOffsetX = 0;
            config.matchHeaderPlateOffsetY = 0;
            config.matchHeaderCenterOffsetX = 0;
            config.matchHeaderCenterOffsetY = 0;
        }).bounds(this.width / 2 + 110, this.height - 52, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.saveAndClose())
                .bounds(this.width / 2 - 100, this.height - 26, 90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.onClose())
                .bounds(this.width / 2 + 10, this.height - 26, 90, 20).build());
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        StrinovaProgrammaticPainter painter = new StrinovaProgrammaticPainter(graphics);
        new StrinovaMatchHeaderWidget().render(painter, this.previewX, this.previewY);

        // 可拖动区域提示
        for (StrinovaMatchHeaderWidget.EditorRegion region
                : StrinovaMatchHeaderWidget.editorRegions(this.previewX, this.previewY)) {
            boolean active = region.key().equals(this.dragging) || region.key().equals(this.hovered);
            int color = active ? 0xFFFF4FA3 : 0x55FFFFFF;
            painter.border(region.x() - 1, region.y() - 1, region.width() + 2, region.height() + 2, color, 1);
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("拖动：头像行 / 左比分板 / 右比分板 / 中间计时区；整体位置请用 HUD 布局编辑器"),
                this.width / 2, 30, 0xFFAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.hovered = regionAt(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != DRAG_BUTTON) {
            return false;
        }
        String key = regionAt(mouseX, mouseY);
        if (key == null) {
            return false;
        }
        this.dragging = key;
        this.hovered = key;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        int[] offset = offset(key);
        this.dragStartOffsetX = offset[0];
        this.dragStartOffsetY = offset[1];
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging != null && button == DRAG_BUTTON) {
            int dx = (int) Math.round(mouseX - this.dragStartMouseX);
            int dy = (int) Math.round(mouseY - this.dragStartMouseY);
            setOffset(this.dragging, this.dragStartOffsetX + dx, this.dragStartOffsetY + dy);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging != null && button == DRAG_BUTTON) {
            this.dragging = null;
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

    private String regionAt(double mouseX, double mouseY) {
        List<StrinovaMatchHeaderWidget.EditorRegion> regions =
                StrinovaMatchHeaderWidget.editorRegions(this.previewX, this.previewY);
        for (StrinovaMatchHeaderWidget.EditorRegion region : regions) {
            if (mouseX >= region.x() && mouseX <= region.x() + region.width()
                    && mouseY >= region.y() && mouseY <= region.y() + region.height()) {
                return region.key();
            }
        }
        return null;
    }

    private static int[] offset(String key) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        return switch (key) {
            case "portraits" -> new int[]{config.matchHeaderPortraitOffsetX, config.matchHeaderPortraitOffsetY};
            case "leftPlate", "rightPlate" -> new int[]{config.matchHeaderPlateOffsetX,
                    config.matchHeaderPlateOffsetY};
            case "center" -> new int[]{config.matchHeaderCenterOffsetX, config.matchHeaderCenterOffsetY};
            default -> new int[]{0, 0};
        };
    }

    private static void setOffset(String key, int x, int y) {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        switch (key) {
            case "portraits" -> {
                config.matchHeaderPortraitOffsetX = x;
                config.matchHeaderPortraitOffsetY = y;
            }
            case "leftPlate", "rightPlate" -> {
                config.matchHeaderPlateOffsetX = x;
                config.matchHeaderPlateOffsetY = y;
            }
            case "center" -> {
                config.matchHeaderCenterOffsetX = x;
                config.matchHeaderCenterOffsetY = y;
            }
            default -> {
            }
        }
    }
}
