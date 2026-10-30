package com.hhdxcz.strinova.client.hud;

import com.hhdxcz.strinova.gameplay.StrinovaAirJumpClientState;
import com.hhdxcz.strinova.gameplay.StrinovaAirJumpSettings;
import com.hhdxcz.strinova.paper.WaPaperState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 状态指示部件：弦化 / 贴墙 / 飘飞 / 剩余段跳 / 模拟数据标记。
 * 数据来自模组已有的真实状态机（无需等待战斗系统）。
 */
public final class StrinovaStateWidget implements StrinovaHudWidget {

    private static final int HEIGHT = 18;
    private static final int DEFAULT_WIDTH = 180;
    private static final int CHIP_GAP = 4;
    private static final int CHIP_PADDING = 10;

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.STATE;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        List<Chip> chips = collectChips(painter);
        if (chips.isEmpty()) {
            return StrinovaHudRenderer.isEditorRendering() ? DEFAULT_WIDTH : 0;
        }
        int width = 0;
        for (Chip chip : chips) {
            width += chip.width + CHIP_GAP;
        }
        return Math.max(0, width - CHIP_GAP);
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        return HEIGHT;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        List<Chip> chips = collectChips(painter);
        int cursor = x;
        for (Chip chip : chips) {
            painter.rect(cursor, y, chip.width, HEIGHT, 0x66101820);
            painter.rect(cursor, y, 2, HEIGHT, chip.accent);
            painter.border(cursor, y, chip.width, HEIGHT, 0x55335F73, 1);
            painter.text(chip.text, cursor + CHIP_PADDING / 2 + 1, y + (HEIGHT - painter.textHeight()) / 2,
                    chip.accent, false);
            cursor += chip.width + CHIP_GAP;
        }
    }

    private List<Chip> collectChips(StrinovaHudPainter painter) {
        List<Chip> chips = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            UUID playerId = player.getUUID();
            if (WaPaperState.isPaper(playerId)) {
                chips.add(chip(painter,
                        Component.translatable("hud.strinova.state.paper").getString() + " -33%",
                        StrinovaProgrammaticPainter.COLOR_ACCENT_CYAN));
            }
            if (WaPaperState.isWall(playerId)) {
                chips.add(chip(painter,
                        Component.translatable("hud.strinova.state.wall").getString(),
                        StrinovaProgrammaticPainter.COLOR_ACCENT_CYAN));
            }
            if (WaPaperState.isFly(playerId)) {
                chips.add(chip(painter,
                        Component.translatable("hud.strinova.state.fly").getString(),
                        StrinovaProgrammaticPainter.COLOR_ACCENT_MAGENTA));
            }
            int extraJumps = StrinovaAirJumpClientState.getExtraJumps(playerId);
            if (extraJumps != 0 && !player.onGround()) {
                String jumps = extraJumps == StrinovaAirJumpSettings.INFINITE_EXTRA_JUMPS
                        ? Component.translatable("hud.strinova.state.jumps.infinite").getString()
                        : String.valueOf(extraJumps + 1);
                chips.add(chip(painter,
                        Component.translatable("hud.strinova.state.jumps").getString() + " " + jumps,
                        StrinovaProgrammaticPainter.COLOR_WARN));
            }
        }
        if (StrinovaHudRenderer.isMockEnabled()) {
            chips.add(chip(painter,
                    Component.translatable("hud.strinova.mock").getString(),
                    StrinovaProgrammaticPainter.COLOR_TEXT_DIM));
        }
        return chips;
    }

    private static Chip chip(StrinovaHudPainter painter, String text, int accent) {
        return new Chip(text, painter.textWidth(text) + CHIP_PADDING, accent);
    }

    private record Chip(String text, int width, int accent) {
    }
}
