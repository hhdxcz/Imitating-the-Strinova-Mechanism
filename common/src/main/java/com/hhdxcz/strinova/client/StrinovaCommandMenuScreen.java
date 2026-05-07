package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StrinovaCommandMenuScreen extends Screen {
    private final Screen parent;

    public StrinovaCommandMenuScreen(Screen parent) {
        super(Component.translatable("config.klbq.command_menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command_menu.paper_jump"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaPaperJumpCommandScreen(this));
                    }
                })
                .bounds(centerX - 90, centerY - 18, 180, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command_menu.outline"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaOutlineCommandScreen(this));
                    }
                })
                .bounds(centerX - 90, centerY + 8, 180, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX - 40, this.height - 28, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 52;
        int right = this.width / 2 + 120;
        int bottom = this.height / 2 + 52;
        graphics.fill(left, top, right, bottom, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
