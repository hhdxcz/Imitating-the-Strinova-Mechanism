package com.hhdxcz.strinova.client;

import com.hhdxcz.strinova.net.StrinovaNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 黑名单导入界面，通过粘贴分享码来导入方块黑名单。
 */
public class StrinovaWallBlacklistImportScreen extends Screen {
    private final Screen parent;
    private EditBox codeBox;
    private String error = "";

    public StrinovaWallBlacklistImportScreen(Screen parent) {
        super(Component.translatable("config.strinova.wall_blacklist.import_title"));
        this.parent = parent;
    }

    /** 初始化界面：分享码输入框、确认和取消按钮。 */
    @Override
    protected void init() {
        int boxWidth = 300;
        int boxX = this.width / 2 - boxWidth / 2;
        int boxY = this.height / 2 - 20;

        this.codeBox = new EditBox(this.font, boxX, boxY, boxWidth, 20,
                Component.translatable("config.strinova.wall_blacklist.import_hint"));
        this.codeBox.setMaxLength(2000);
        addRenderableWidget(this.codeBox);

        addRenderableWidget(Button.builder(Component.translatable("config.strinova.wall_blacklist.import_confirm"), b -> doImport())
                .bounds(boxX + boxWidth - 100, boxY + 30, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(boxX, boxY + 30, 100, 20)
                .build());

        setFocused(this.codeBox);
    }

    /** 执行导入操作：检查输入是否为空，发送网络包导入黑名单。 */
    private void doImport() {
        String code = codeBox.getValue().trim();
        if (code.isEmpty()) {
            error = Component.translatable("config.strinova.wall_blacklist.import_empty").getString();
            return;
        }
        StrinovaNetwork.sendImportWallBlacklist(code);
        onClose();
    }

    /** 渲染界面：标题、提示文字和错误信息。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("config.strinova.wall_blacklist.import_hint"),
                this.width / 2 - 150, this.height / 2 - 36, 0xB0B0B0, false);
        if (!error.isEmpty()) {
            graphics.drawString(this.font, Component.literal(error),
                    this.width / 2 - 150, this.height / 2 + 56, 0xFF5555, false);
        }
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