package com.hhdxcz.wa.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

public class WaCollisionMenuScreen extends Screen {
    private final Screen parent;
    private final AbstractClientPlayer previewPlayer;

    public WaCollisionMenuScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        super(Component.translatable("config.klbq.category.collision"));
        this.parent = parent;
        this.previewPlayer = previewPlayer;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.collision_preview.entry"), b -> {
                    AbstractClientPlayer player = previewPlayer != null ? previewPlayer : (minecraft == null ? null : minecraft.player);
                    if (minecraft != null && player != null) {
                        minecraft.setScreen(new WaCollisionEditScreen(this, player));
                    }
                })
                .bounds(centerX - 80, centerY - 24, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.wall_blacklist.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new WaWallBlacklistScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 2, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.wall_key.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new WaWallKeyBindScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 28, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command_menu.paper_jump"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new WaPaperJumpCommandScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 54, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command_menu.outline"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new WaOutlineCommandScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 80, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX - 40, this.height - 28, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int firstY = centerY - 24;
        int lastY = centerY + 80;
        int left = centerX - 92;
        int top = firstY - 10;
        int right = centerX + 92;
        int bottom = lastY + 30;
        graphics.fill(left, top, right, bottom, 0xA0101010);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
