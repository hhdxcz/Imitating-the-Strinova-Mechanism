package com.hhdxcz.strinova.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

/**
 * 碰撞箱功能菜单界面，提供碰撞箱编辑、黑名单管理、快捷键绑定、命令功能、
 * 战斗 HUD 设置与超弦体选角的入口。
 */
public class StrinovaCollisionMenuScreen extends Screen {
    /** 功能入口按钮宽度。 */
    private static final int ENTRY_WIDTH = 160;
    /** 功能入口按钮高度。 */
    private static final int ENTRY_HEIGHT = 20;
    /** 相邻功能入口按钮的行间距（改为 24 后 240 逻辑高度下 7 个入口与底部返回按钮不重叠）。 */
    private static final int ENTRY_STEP = 24;
    /** 功能入口数量：碰撞箱编辑、黑名单、穿墙快捷键、纸跳命令、描边命令、战斗 HUD 设置、超弦体选角。 */
    private static final int ENTRY_COUNT = 7;

    private final Screen parent;
    private final AbstractClientPlayer previewPlayer;

    public StrinovaCollisionMenuScreen(Screen parent, AbstractClientPlayer previewPlayer) {
        super(Component.translatable("config.strinova.category.collision"));
        this.parent = parent;
        this.previewPlayer = previewPlayer;
    }

    /** 初始化菜单按钮：碰撞箱编辑、黑名单、快捷键、纸跳命令、描边命令、战斗 HUD 设置、超弦体选角。 */
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int firstY = this.entryFirstY();
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.collision_preview.entry"), b -> {
                    AbstractClientPlayer player = previewPlayer != null ? previewPlayer : (minecraft == null ? null : minecraft.player);
                    if (minecraft != null && player != null) {
                        minecraft.setScreen(new StrinovaCollisionEditScreen(this, player));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaWallBlacklistScreen(this));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY + ENTRY_STEP, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_key.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaWallKeyBindScreen(this));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY + ENTRY_STEP * 2, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command_menu.paper_jump"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaPaperJumpCommandScreen(this));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY + ENTRY_STEP * 3, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.command_menu.outline"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaOutlineCommandScreen(this));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY + ENTRY_STEP * 4, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        // 战斗 HUD 设置入口
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.hud.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaCombatSettingsScreen(this));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY + ENTRY_STEP * 5, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        // 超弦体选角入口
        addRenderableWidget(Button.builder(Component.translatable("config.strinova.agent.entry"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new StrinovaAgentSelectScreen(this));
                    }
                })
                .bounds(centerX - ENTRY_WIDTH / 2, firstY + ENTRY_STEP * 6, ENTRY_WIDTH, ENTRY_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX - 40, this.height - 28, 80, 20)
                .build());
    }

    /** 计算第一个功能入口按钮的 Y 坐标：整组入口在屏幕中垂直居中。 */
    private int entryFirstY() {
        int groupHeight = (ENTRY_COUNT - 1) * ENTRY_STEP + ENTRY_HEIGHT;
        return this.height / 2 - groupHeight / 2;
    }

    /** 渲染界面：背景和半透明面板。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centerX = this.width / 2;
        int firstY = this.entryFirstY();
        int lastY = firstY + (ENTRY_COUNT - 1) * ENTRY_STEP;
        int left = centerX - 92;
        int top = firstY - 10;
        int right = centerX + 92;
        int bottom = lastY + ENTRY_HEIGHT + 10;
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
