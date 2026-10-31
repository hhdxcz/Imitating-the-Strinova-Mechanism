package com.hhdxcz.strinova.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 武器 / 弹药部件（右下，1:1 对齐真实截图）：
 * 屏幕右侧贴边竖排 5 个武器槽（1 在下、5 在上，数字键徽标挂在槽位右侧），
 * 当前槽向左展开一张武器卡：大字枪械剪影 + 武器名 + 大号弹匣数（白）+ 备弹（蓝）。
 *
 * <p>数据源：模拟数据开启时用 {@link StrinovaHudState}；否则读取玩家快捷栏 1~5 号位，
 * TACZ 枪械通过 {@link StrinovaTaczCompat} 反射读取弹匣/备弹，其他模组物品只显示名称与剪影。</p>
 */
public final class StrinovaAmmoWidget implements StrinovaHudWidget {

    private static final int WIDTH = 204;
    private static final int HEIGHT = 148;
    private static final int SLOT = 22;
    private static final int SLOT_STEP = 24;
    private static final int BADGE_WIDTH = 11;
    private static final int BADGE_HEIGHT = 10;
    private static final int CARD_HEIGHT = 52;
    private static final int CARD_WIDTH = WIDTH - 38;
    private static final int AMMO_BLUE = 0xFF6FB6FF;

    private static final String[] DEFAULT_SLOT_NAMES = {"主武器", "副武器", "近战", "战术道具", "战术道具"};

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
        LocalPlayer player = Minecraft.getInstance().player;
        boolean mock = StrinovaHudMockData.isEnabled();

        int selected = mock
                ? StrinovaHudState.getSelectedSlot()
                : player == null ? 0 : Math.min(4, Math.max(0, player.getInventory().selected));

        String[] names = new String[5];
        StrinovaTaczCompat.IconKind[] kinds = new StrinovaTaczCompat.IconKind[5];
        for (int i = 0; i < 5; i++) {
            ItemStack stack = mock ? ItemStack.EMPTY : hotbarStack(player, i);
            if (mock) {
                names[i] = mockSlotName(i);
                kinds[i] = mockIconKind(names[i]);
            } else if (stack.isEmpty()) {
                names[i] = "";
                kinds[i] = StrinovaTaczCompat.IconKind.EMPTY;
            } else {
                names[i] = stack.getHoverName().getString();
                kinds[i] = StrinovaTaczCompat.iconKind(stack);
            }
        }

        // 当前选中槽的弹药
        int magazine = -1;
        int reserve = -1;
        if (mock) {
            magazine = StrinovaHudState.getMagazine();
            reserve = StrinovaHudState.getReserve();
        } else if (player != null) {
            ItemStack held = hotbarStack(player, selected);
            if (StrinovaTaczCompat.isTaczGun(held)) {
                magazine = StrinovaTaczCompat.magazine(held);
                reserve = StrinovaTaczCompat.reserve(player, held);
            }
        }

        int slotX = x + WIDTH - BADGE_WIDTH - 2 - SLOT;
        int badgeX = x + WIDTH - BADGE_WIDTH;
        int bottomY = y + HEIGHT - SLOT;

        // 竖排 5 个槽位（1 号在最下方）
        for (int i = 0; i < 5; i++) {
            int slotY = bottomY - (4 - i) * SLOT_STEP;
            boolean isSelected = i == selected;
            painter.rect(slotX, slotY, SLOT, SLOT, isSelected ? 0xCC12222C : 0x88101820);
            painter.border(slotX, slotY, SLOT, SLOT, isSelected ? 0xFFEAF6FF : 0x66445566, 1);
            drawWeaponIcon(painter, kinds[i], slotX + 3, slotY + 3, SLOT - 6,
                    isSelected ? 0xFFEAF6FF : 0xCCEAF6FF);

            // 数字键徽标挂在槽位右侧
            painter.rect(badgeX, slotY + (SLOT - BADGE_HEIGHT) / 2, BADGE_WIDTH, BADGE_HEIGHT, 0x88101820);
            painter.border(badgeX, slotY + (SLOT - BADGE_HEIGHT) / 2, BADGE_WIDTH, BADGE_HEIGHT,
                    isSelected ? 0xFFEAF6FF : 0x66445566, 1);
            String number = String.valueOf(i + 1);
            painter.text(number, badgeX + (BADGE_WIDTH - painter.textWidth(number)) / 2,
                    slotY + (SLOT - BADGE_HEIGHT) / 2 + 1,
                    isSelected ? 0xFFEAF6FF : 0xFF9FC6D8, false);
        }

        // 当前武器卡片（向左展开，与最下方槽位底对齐）
        int cardY = y + HEIGHT - CARD_HEIGHT - 2;
        int cardX = x;
        painter.panel(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, StrinovaProgrammaticPainter.COLOR_ACCENT_CYAN);

        // 卡片左上角的小型弹匣/导轨指示（几何线条）
        int markX = cardX + 5;
        int markY = cardY + 4;
        painter.rect(markX, markY, 2, 8, 0xCCEAF6FF);
        painter.rect(markX + 4, markY + 2, 2, 6, 0xCCEAF6FF);
        painter.rect(markX + 8, markY + 4, 2, 4, 0xCCEAF6FF);

        // 大号武器剪影
        StrinovaTaczCompat.IconKind selectedKind = kinds[selected];
        if (selectedKind == null) {
            selectedKind = StrinovaTaczCompat.IconKind.EMPTY;
        }
        drawWeaponIcon(painter, selectedKind, cardX + 8, cardY + 12, 52, 0xFFEAF6FF);

        // 左下：武器名
        String selectedName = names[selected];
        if (selectedName == null || selectedName.isEmpty()) {
            selectedName = mock ? DEFAULT_SLOT_NAMES[selected] : "未装备";
        }
        painter.text(selectedName, cardX + 6, cardY + CARD_HEIGHT - 12,
                StrinovaProgrammaticPainter.COLOR_TEXT, false);

        // 右侧：弹匣（白，大号）+ 备弹（蓝，稍小）
        if (magazine >= 0) {
            String magText = String.valueOf(magazine);
            float magScale = 1.9F;
            int magWidth = Math.round(painter.textWidth(magText) * magScale);
            float magX = cardX + CARD_WIDTH - 6 - magWidth;
            int magColor = magazine <= 5
                    ? StrinovaProgrammaticPainter.COLOR_HP_LOW
                    : StrinovaProgrammaticPainter.COLOR_TEXT;
            painter.textScaled(magText, magX, cardY + 8, magScale, magColor, true);
            if (reserve >= 0) {
                painter.textRightAligned(String.valueOf(reserve), cardX + CARD_WIDTH - 8,
                        cardY + 30, AMMO_BLUE, false);
            }
        }

        // 换弹进度（有换弹时卡片底部细条）
        float reload = mock ? StrinovaHudState.reloadProgress() : 0.0F;
        if (reload > 0.0F) {
            painter.bar(cardX + 4, cardY + CARD_HEIGHT - 3, CARD_WIDTH - 8, 2, reload,
                    StrinovaProgrammaticPainter.COLOR_WARN, 0x66222B33);
        }
    }

    private static ItemStack hotbarStack(LocalPlayer player, int index) {
        if (player == null || index < 0 || index > 4) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> items = player.getInventory().items;
        if (index >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = items.get(index);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static String mockSlotName(int index) {
        List<StrinovaHudState.WeaponSlot> slots = StrinovaHudState.getWeaponSlots();
        if (slots != null) {
            for (StrinovaHudState.WeaponSlot slot : slots) {
                if (slot.index() == index && slot.name() != null && !slot.name().isEmpty()) {
                    return slot.name();
                }
            }
        }
        return DEFAULT_SLOT_NAMES[Math.max(0, Math.min(DEFAULT_SLOT_NAMES.length - 1, index))];
    }

    private static StrinovaTaczCompat.IconKind mockIconKind(String name) {
        if (name == null) {
            return StrinovaTaczCompat.IconKind.EMPTY;
        }
        if (name.contains("雷")) {
            return StrinovaTaczCompat.IconKind.GRENADE;
        }
        if (name.contains("刀") || name.contains("忍锋")) {
            return StrinovaTaczCompat.IconKind.KNIFE;
        }
        if (name.contains("手枪") || name.contains("副")) {
            return StrinovaTaczCompat.IconKind.PISTOL;
        }
        return StrinovaTaczCompat.IconKind.RIFLE;
    }

    // ==================== 程序化几何武器剪影 ====================

    private static void drawWeaponIcon(StrinovaHudPainter painter, StrinovaTaczCompat.IconKind kind,
                                       int x, int y, int size, int color) {
        if (kind == null) {
            kind = StrinovaTaczCompat.IconKind.EMPTY;
        }
        switch (kind) {
            case RIFLE -> {
                rect(painter, x, y, size, 0.18F, 0.38F, 0.50F, 0.12F, color);
                rect(painter, x, y, size, 0.68F, 0.40F, 0.28F, 0.06F, color);
                rect(painter, x, y, size, 0.03F, 0.42F, 0.16F, 0.10F, color);
                rect(painter, x, y, size, 0.30F, 0.50F, 0.10F, 0.16F, color);
                rect(painter, x, y, size, 0.44F, 0.50F, 0.11F, 0.16F, color);
                rect(painter, x, y, size, 0.44F, 0.28F, 0.18F, 0.05F, color);
            }
            case PISTOL -> {
                rect(painter, x, y, size, 0.22F, 0.31F, 0.52F, 0.12F, color);
                rect(painter, x, y, size, 0.70F, 0.33F, 0.20F, 0.06F, color);
                rect(painter, x, y, size, 0.30F, 0.43F, 0.14F, 0.28F, color);
                rect(painter, x, y, size, 0.46F, 0.40F, 0.08F, 0.04F, color);
            }
            case KNIFE -> {
                line(painter, x, y, size, 0.16F, 0.72F, 0.72F, 0.30F, color);
                line(painter, x, y, size, 0.18F, 0.76F, 0.74F, 0.34F, color);
                rect(painter, x, y, size, 0.66F, 0.30F, 0.18F, 0.07F, color);
            }
            case GRENADE -> {
                rect(painter, x, y, size, 0.30F, 0.34F, 0.34F, 0.36F, color);
                rect(painter, x, y, size, 0.38F, 0.24F, 0.18F, 0.09F, color);
                line(painter, x, y, size, 0.56F, 0.22F, 0.68F, 0.30F, color);
                rect(painter, x, y, size, 0.36F, 0.42F, 0.06F, 0.06F, 0x66000000);
                rect(painter, x, y, size, 0.50F, 0.42F, 0.06F, 0.06F, 0x66000000);
                rect(painter, x, y, size, 0.36F, 0.54F, 0.06F, 0.06F, 0x66000000);
            }
            case MEDKIT -> {
                rect(painter, x, y, size, 0.22F, 0.28F, 0.56F, 0.44F, color);
                rect(painter, x, y, size, 0.44F, 0.34F, 0.12F, 0.32F, 0xFF0A111C);
                rect(painter, x, y, size, 0.34F, 0.44F, 0.32F, 0.12F, 0xFF0A111C);
            }
            case BOW -> {
                line(painter, x, y, size, 0.28F, 0.18F, 0.52F, 0.50F, color);
                line(painter, x, y, size, 0.52F, 0.50F, 0.28F, 0.82F, color);
                line(painter, x, y, size, 0.28F, 0.18F, 0.28F, 0.82F, color);
                line(painter, x, y, size, 0.28F, 0.50F, 0.82F, 0.50F, color);
            }
            case GENERIC -> rect(painter, x, y, size, 0.26F, 0.26F, 0.48F, 0.48F, color);
            default -> {
                rect(painter, x, y, size, 0.26F, 0.48F, 0.48F, 0.04F, 0x88EAF6FF);
            }
        }
    }

    private static void rect(StrinovaHudPainter painter, int x, int y, int size,
                             float u, float v, float w, float h, int color) {
        int px = x + Math.round(u * size);
        int py = y + Math.round(v * size);
        int pw = Math.max(1, Math.round(w * size));
        int ph = Math.max(1, Math.round(h * size));
        painter.rect(px, py, pw, ph, color);
    }

    private static void line(StrinovaHudPainter painter, int x, int y, int size,
                             float x1, float y1, float x2, float y2, int color) {
        painter.line(x + Math.round(x1 * size), y + Math.round(y1 * size),
                x + Math.round(x2 * size), y + Math.round(y2 * size), color);
    }
}
