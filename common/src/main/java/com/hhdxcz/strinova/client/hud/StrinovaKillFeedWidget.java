package com.hhdxcz.strinova.client.hud;

import net.minecraft.network.chat.Component;

import java.util.List;

/** 击杀播报部件（右上）：谁用什么武器击杀了谁，是否爆头。 */
public final class StrinovaKillFeedWidget implements StrinovaHudWidget {

    private static final int WIDTH = 262;
    private static final int ROW_HEIGHT = 13;
    private static final int MAX_ROWS = 5;
    private static final int FADE_MS = 1000;

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.KILL_FEED;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        return WIDTH;
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        int rows = Math.min(MAX_ROWS, StrinovaHudState.killFeed().size());
        if (rows <= 0) {
            rows = StrinovaHudRenderer.isEditorRendering() ? 2 : 1;
        }
        return rows * ROW_HEIGHT;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        List<StrinovaHudState.KillFeedEntry> entries = StrinovaHudState.killFeed();
        long now = StrinovaHudState.now();
        int rows = Math.min(MAX_ROWS, entries.size());
        for (int i = 0; i < rows; i++) {
            StrinovaHudState.KillFeedEntry entry = entries.get(i);
            long age = now - entry.timeMs();
            float fade = 1.0F;
            long remaining = StrinovaHudState.KILL_FEED_LIFETIME_MS - age;
            if (remaining < FADE_MS) {
                fade = Math.max(0.0F, remaining / (float) FADE_MS);
            }
            int alpha = Math.max(0, Math.min(255, Math.round(fade * 255.0F)));
            int rowY = y + i * ROW_HEIGHT;
            painter.rect(x, rowY, WIDTH, ROW_HEIGHT - 1, (Math.round(alpha * 0.45F) << 24) | 0x101820);
            painter.rect(x, rowY, 2, ROW_HEIGHT - 1,
                    (alpha << 24) | (StrinovaProgrammaticPainter.COLOR_ACCENT_MAGENTA & 0x00FFFFFF));
            int textColor = (alpha << 24) | 0x00EAF6FF;
            String line = entry.killer() + " [" + entry.weapon() + "] " + entry.victim();
            painter.text(line, x + 6, rowY + 2, textColor, false);
            if (entry.headshot()) {
                String tag = Component.translatable("hud.strinova.killfeed.headshot").getString();
                painter.textRightAligned(tag, x + WIDTH - 4, rowY + 2,
                        (alpha << 24) | (StrinovaProgrammaticPainter.COLOR_WARN & 0x00FFFFFF), false);
            }
        }
        if (StrinovaHudRenderer.isEditorRendering() && entries.isEmpty()) {
            painter.rect(x, y, WIDTH, ROW_HEIGHT - 1, 0x55101820);
            painter.text("[KILL FEED]", x + 6, y + 2, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        }
    }
}
