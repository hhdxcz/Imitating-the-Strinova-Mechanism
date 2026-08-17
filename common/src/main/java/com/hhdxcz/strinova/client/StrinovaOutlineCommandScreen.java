package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 描边命令配置界面。
 * 支持设置描边颜色、目标选择器，以及应用/清除/关闭描边效果。
 */
public class StrinovaOutlineCommandScreen extends Screen {
    private final Screen parent;
    private EditBox colorInput;
    private EditBox targetsInput;
    private String status = "";

    public StrinovaOutlineCommandScreen(Screen parent) {
        super(Component.translatable("config.strinova.command_menu.outline"));
        this.parent = parent;
    }

    /** 初始化界面：颜色输入框、目标输入框和操作按钮。 */
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 54;
        this.colorInput = new EditBox(this.font, centerX - 140, top + 28, 132, 20, Component.translatable("config.strinova.command.outline.color"));
        this.colorInput.setValue("white");
        addRenderableWidget(this.colorInput);
        this.targetsInput = new EditBox(this.font, centerX - 4, top + 28, 144, 20, Component.translatable("config.strinova.command.outline.targets"));
        addRenderableWidget(this.targetsInput);

        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command.outline.apply"), b -> applyOutline())
                .bounds(centerX - 140, top + 56, 86, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command.outline.clear"), b -> clearOutline(false))
                .bounds(centerX - 50, top + 56, 86, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command.outline.off"), b -> clearOutline(true))
                .bounds(centerX + 40, top + 56, 64, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX + 108, top + 56, 56, 20)
                .build());
        setFocused(this.colorInput);
    }

    /** 应用描边效果：发送对应颜色的描边命令。 */
    private void applyOutline() {
        String color = this.colorInput == null ? "" : this.colorInput.getValue().trim();
        if (color.isEmpty()) {
            this.status = Component.translatable("config.strinova.command.invalid").getString();
            return;
        }
        String targets = this.targetsInput == null ? "" : this.targetsInput.getValue().trim();
        if (targets.isEmpty()) {
            sendCommand("strinova outline " + color);
        } else {
            sendCommand("strinova outline set " + targets + " " + color);
        }
    }

    /** 清除或关闭描边效果。 */
    private void clearOutline(boolean off) {
        String targets = this.targetsInput == null ? "" : this.targetsInput.getValue().trim();
        String base = off ? "strinova outline off" : "strinova outline clear";
        if (targets.isEmpty()) {
            sendCommand(base);
        } else {
            sendCommand(base + " " + targets);
        }
    }

    /** 向服务端发送命令。 */
    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            this.status = Component.translatable("command.strinova.client.no_player").getString();
            return;
        }
        minecraft.player.connection.sendCommand(command);
        this.status = Component.translatable("config.strinova.command.sent").getString();
    }

    /** 渲染界面：面板、标题、提示和状态信息。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = this.width / 2 - 180;
        int top = this.height / 2 - 72;
        int right = this.width / 2 + 180;
        int bottom = this.height / 2 + 92;
        graphics.fill(left, top, right, bottom, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("config.strinova.command.outline.tip"), this.width / 2 - 140, top + 16, 0xB0B0B0, false);
        graphics.drawString(this.font, Component.literal(this.status), this.width / 2 - 140, top + 86, 0xA0E0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 关闭界面返回父界面。 */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}