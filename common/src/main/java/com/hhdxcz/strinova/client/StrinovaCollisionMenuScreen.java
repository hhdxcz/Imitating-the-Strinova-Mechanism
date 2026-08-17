package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

/**
 * 碰撞箱功能菜单界面，提供碰撞箱编辑、黑名单管理、快捷键绑定和命令功能的入口。
 */
public class StrinovaCollisionMenuScreen extends Screen {
    private final Screen parent;
    private final AbstractClientPlayer previewPlayer;

    public StrinovaCollisionMenuScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        super(Component.translatable("config.strinova.category.collision"));
        this.parent = parent;
        this.previewPlayer = previewPlayer;
    }

    /** 初始化菜单按钮：碰撞箱编辑、黑名单、快捷键、纸跳命令、描边命令。 */
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.collision_preview.entry"), b -> {
                    AbstractClientPlayer player = previewPlayer != null ? previewPlayer : (minecraft == null ? null : minecraft.player);
                    if (minecraft != null && player != null) {
                        minecraft.setScreen(new StrinovaCollisionEditScreen(this, player));
                    }
                })
                .bounds(centerX - 80, centerY - 24, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaWallBlacklistScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 2, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_key.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaWallKeyBindScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 28, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command_menu.paper_jump"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaPaperJumpCommandScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 54, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command_menu.outline"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaOutlineCommandScreen(this));
                    }
                })
                .bounds(centerX - 80, centerY + 80, 160, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX - 40, this.height - 28, 80, 20)
                .build());
    }

    /** 渲染界面：背景和半透明面板。 */
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

    /** 关闭界面返回父界面。 */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}