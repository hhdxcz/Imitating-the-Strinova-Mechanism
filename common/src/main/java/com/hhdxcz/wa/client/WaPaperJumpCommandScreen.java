   package com.hhdxcz.wa.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaPaperJumpCommandScreen extends Screen {
    private final Screen parent;
    private EditBox damageInput;
    private EditBox segmentsInput;
    private EditBox targetsInput;
    private String status = "";

    public WaPaperJumpCommandScreen(Screen parent) {
        super(Component.translatable("config.klbq.command_menu.paper_jump"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = 36;
        this.damageInput = new EditBox(this.font, centerX - 140, top + 34, 120, 20, Component.translatable("config.klbq.command.paper.value"));
        this.damageInput.setValue("0.8");
        addRenderableWidget(this.damageInput);
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command.paper.get"), b -> sendCommand("wa paper damage_reduction get"))
                .bounds(centerX - 12, top + 34, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command.paper.set"), b -> applyDamage())
                .bounds(centerX + 64, top + 34, 76, 20)
                .build());

        this.segmentsInput = new EditBox(this.font, centerX - 140, top + 82, 120, 20, Component.translatable("config.klbq.command.jump.segments"));
        this.segmentsInput.setValue("2");
        addRenderableWidget(this.segmentsInput);
        this.targetsInput = new EditBox(this.font, centerX - 12, top + 82, 152, 20, Component.translatable("config.klbq.command.jump.targets"));
        addRenderableWidget(this.targetsInput);
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command.jump.get"), b -> sendCommand("wa jump get"))
                .bounds(centerX - 140, top + 108, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("config.klbq.command.jump.set"), b -> applyJump())
                .bounds(centerX - 64, top + 108, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("ALL"), b -> {
                    if (segmentsInput != null) {
                        segmentsInput.setValue("ALL");
                    }
                })
                .bounds(centerX + 12, top + 108, 56, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(centerX + 72, top + 108, 68, 20)
                .build());
        setFocused(this.damageInput);
    }

    private void applyDamage() {
        String raw = this.damageInput == null ? "" : this.damageInput.getValue().trim();
        if (raw.isEmpty()) {
            this.status = Component.translatable("config.klbq.command.invalid").getString();
            return;
        }
        sendCommand("wa paper damage_reduction " + raw);
    }

    private void applyJump() {
        String seg = this.segmentsInput == null ? "" : this.segmentsInput.getValue().trim();
        if (seg.isEmpty()) {
            this.status = Component.translatable("config.klbq.command.invalid").getString();
            return;
        }
        String targets = this.targetsInput == null ? "" : this.targetsInput.getValue().trim();
        if (targets.isEmpty()) {
            if ("ALL".equalsIgnoreCase(seg)) {
                sendCommand("wa jump set @s ALL");
                return;
            }
            sendCommand("wa jump set " + seg);
            return;
        }
        sendCommand("wa jump set " + targets + " " + seg);
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            this.status = Component.translatable("command.klbq.client.no_player").getString();
            return;
        }
        minecraft.player.connection.sendCommand(command);
        this.status = Component.translatable("config.klbq.command.sent").getString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centerX = this.width / 2;
        int left = centerX - 170;
        int top = 24;
        int right = centerX + 170;
        int bottom = 178;
        graphics.fill(left, top, right, bottom, 0xA0101010);
        graphics.drawCenteredString(this.font, this.title, centerX, top + 10, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("config.klbq.command.paper.tip"), centerX - 140, top + 22, 0xB0B0B0, false);
        graphics.drawString(this.font, Component.translatable("config.klbq.command.jump.tip"), centerX - 140, top + 70, 0xB0B0B0, false);
        graphics.drawString(this.font, Component.literal(this.status), centerX - 140, top + 140, 0xA0E0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
