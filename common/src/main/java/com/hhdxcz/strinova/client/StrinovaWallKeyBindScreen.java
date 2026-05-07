package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StrinovaWallKeyBindScreen extends Screen {
    private final Screen parent;
    private EditBox keyInput;
    private String status = "";

    public StrinovaWallKeyBindScreen(Screen parent) {
        super(Component.translatable("config.klbq.wall_key.entry"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 46;
        this.keyInput = new EditBox(this.font, centerX - 140, top + 28, 280, 20, Component.translatable("config.klbq.wall_key.input"));
        this.keyInput.setMaxLength(64);
        this.keyInput.setValue("r");
        addRenderableWidget(this.keyInput);

        addRenderableWidget(Button.builder(Component.translatable("config.klbq.wall_key.apply"), b -> applyCurrent())
                .bounds(centerX - 140, top + 56, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.wall_key.default"), b -> applyPreset("default"))
                .bounds(centerX - 48, top + 56, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.wall_key.reset"), b -> applyPreset("reset"))
                .bounds(centerX + 44, top + 56, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX - 40, this.height - 28, 80, 20)
                .build());
        setFocused(this.keyInput);
    }

    private void applyCurrent() {
        if (this.keyInput == null) {
            return;
        }
        String key = this.keyInput.getValue().trim();
        if (key.isEmpty()) {
            this.status = Component.translatable("config.klbq.wall_key.invalid").getString();
            return;
        }
        sendCommand("wa_client key wall " + key);
    }

    private void applyPreset(String preset) {
        if (this.keyInput != null) {
            this.keyInput.setValue(preset);
        }
        sendCommand("wa_client key wall " + preset);
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            this.status = Component.translatable("command.klbq.client.no_player").getString();
            return;
        }
        minecraft.player.connection.sendCommand(command);
        this.status = Component.translatable("config.klbq.wall_key.sent", command).getString();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.keyInput != null && this.keyInput.isFocused() && this.keyInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.keyInput != null && this.keyInput.isFocused() && this.keyInput.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = this.width / 2 - 170;
        int top = this.height / 2 - 66;
        int right = this.width / 2 + 170;
        int bottom = this.height / 2 + 80;
        graphics.fill(left, top, right, bottom, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("config.klbq.wall_key.tip"), this.width / 2 - 140, top + 16, 0xB0B0B0, false);
        graphics.drawString(this.font, Component.literal(this.status), this.width / 2 - 140, top + 84, 0xA0E0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
