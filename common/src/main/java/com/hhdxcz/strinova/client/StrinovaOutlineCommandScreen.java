package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StrinovaOutlineCommandScreen extends Screen {
    private final Screen parent;
    private EditBox colorInput;
    private EditBox targetsInput;
    private String status = "";

    public StrinovaOutlineCommandScreen(Screen parent) {
        super(Component.translatable("config.strinova.command_menu.outline"));
        this.parent = parent;
    }

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

    private void applyOutline() {
        String color = this.colorInput == null ? "" : this.colorInput.getValue().trim();
        if (color.isEmpty()) {
            this.status = Component.translatable("config.strinova.command.invalid").getString();
            return;
        }
        String targets = this.targetsInput == null ? "" : this.targetsInput.getValue().trim();
        if (targets.isEmpty()) {
            sendCommand("wa outline " + color);
        } else {
            sendCommand("wa outline set " + targets + " " + color);
        }
    }

    private void clearOutline(boolean off) {
        String targets = this.targetsInput == null ? "" : this.targetsInput.getValue().trim();
        String base = off ? "wa outline off" : "wa outline clear";
        if (targets.isEmpty()) {
            sendCommand(base);
        } else {
            sendCommand(base + " " + targets);
        }
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            this.status = Component.translatable("command.strinova.client.no_player").getString();
            return;
        }
        minecraft.player.connection.sendCommand(command);
        this.status = Component.translatable("config.strinova.command.sent").getString();
    }

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

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
