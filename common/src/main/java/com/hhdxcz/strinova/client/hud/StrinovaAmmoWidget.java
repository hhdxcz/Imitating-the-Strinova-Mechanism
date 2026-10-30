package com.hhdxcz.strinova.client.hud;

import java.util.List;

/**
 * 武器 / 弹药部件（右下，对齐真实布局）：
 * 竖排 1~5 槽位（主武器 / 副武器 / 近战 / 战术道具 ×2），当前槽高亮并展开武器名 + 大号弹药。
 */
public final class StrinovaAmmoWidget implements StrinovaHudWidget {

    private static final int WIDTH = 172;
    private static final int HEIGHT = 132;
    private static final int SLOT = 22;
    private static final int SLOT_STEP = 24;
    private static final String[] DEFAULT_SLOT_NAMES = {"主武器", "副武器", "近战", "战术", "战术"};

    @Override
    public StrinovaHudLayout.Element element() {
        return StrinovaHudLayout.Element.AMMO;
    }

    @Override
    public int measureWidth(StrinovaHudPainter painter) {
        return WIDTH;
    }

    @Override
    public int measureHeight(StrinovaHudPainter painter) {
        return HEIGHT;
    }

    @Override
    public void render(StrinovaHudPainter painter, int x, int y) {
        int selected = StrinovaHudState.getSelectedSlot();
        List<StrinovaHudState.WeaponSlot> slots = StrinovaHudState.getWeaponSlots();
        int columnX = x + WIDTH - SLOT;

        // 竖排 5 个槽位：1 号在最下方
        for (int i = 0; i < 5; i++) {
            int slotY = y + HEIGHT - SLOT - (4 - i) * SLOT_STEP;
            boolean isSelected = i == selected;
            painter.rect(columnX, slotY, SLOT, SLOT,
                    isSelected ? 0xAA35D6E8 : 0x66101820);
            painter.border(columnX, slotY, SLOT, SLOT,
                    isSelected ? 0xFF35D6E8 : 0x55445566, 1);
            painter.text(String.valueOf(i + 1), columnX + 2, slotY + 2,
                    isSelected ? 0xFF0A111C : 0xAAEAF6FF, false);
            String label = slotLabel(slots, i);
            String icon = label.isEmpty() ? "?" : label.substring(0, 1);
            painter.text(icon, columnX + (SLOT - painter.textWidth(icon)) / 2, slotY + 9,
                    isSelected ? 0xFF0A111C : 0xCCEAF6FF, false);
        }

        // 当前武器信息卡（位于槽位列左侧）
        int cardY = y + HEIGHT - SLOT - 18;
        int cardHeight = 44;
        int cardWidth = WIDTH - SLOT - 4;
        painter.panel(x, cardY, cardWidth, cardHeight, StrinovaProgrammaticPainter.COLOR_ACCENT_MAGENTA);

        String weapon = StrinovaHudState.getWeaponName();
        if (weapon == null || weapon.isEmpty()) {
            weapon = "未装备";
        }
        painter.text(weapon, x + 6, cardY + 5, StrinovaProgrammaticPainter.COLOR_TEXT, false);
        String fireMode = StrinovaHudState.getFireModeTranslationKey();
        if (fireMode != null) {
            painter.textRightAligned(net.minecraft.network.chat.Component.translatable(fireMode).getString(),
                    x + cardWidth - 6, cardY + 5, StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);
        }

        int magazine = StrinovaHudState.getMagazine();
        String magText = String.valueOf(magazine);
        int magColor = magazine <= 5
                ? StrinovaProgrammaticPainter.COLOR_HP_LOW
                : StrinovaProgrammaticPainter.COLOR_TEXT;
        float magScale = 1.6F;
        painter.textScaled(magText, x + 6, cardY + 16, magScale, magColor, true);
        int reserveX = x + 10 + Math.round(painter.textWidth(magText) * magScale);
        painter.text("/ " + StrinovaHudState.getReserve(), reserveX, cardY + 25,
                StrinovaProgrammaticPainter.COLOR_TEXT_DIM, false);

        float reload = StrinovaHudState.reloadProgress();
        if (reload > 0.0F) {
            painter.bar(x + 6, cardY + cardHeight - 8, cardWidth - 12, 4, reload,
                    StrinovaProgrammaticPainter.COLOR_WARN, 0x66222B33);
        }
    }

    private static String slotLabel(List<StrinovaHudState.WeaponSlot> slots, int index) {
        if (slots != null) {
            for (StrinovaHudState.WeaponSlot slot : slots) {
                if (slot.index() == index && slot.name() != null && !slot.name().isEmpty()) {
                    return slot.name();
                }
            }
        }
        return DEFAULT_SLOT_NAMES[Math.max(0, Math.min(DEFAULT_SLOT_NAMES.length - 1, index))];
    }
}
